package uk.co.pluckier.discordbot.webhooks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import uk.co.pluckier.discordbot.webparser.SportingLifeParser;
import uk.co.pluckier.discordbot.DiscordBot;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;
import uk.co.pluckier.discordbot.utils.RaceResultPersistence;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;
import uk.co.pluckier.discordbot.utils.Utils;

public class ResultBotSender {

    private static final Logger log = LoggerFactory.getLogger(ResultBotSender.class);
    private RaceDataManager data;
    private ScheduledFuture<?> future;

    public static void main(String[] args) {
        log.info("--- Starting Single Test Run ---");
        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces();
        ResultBotSender bot = new ResultBotSender(data);
        bot.checkForNewResults();
        log.info("--- Single Test Run Finished ---");
    }

    private final ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, r -> {
        // 2. Sleek Java 25 Lambda syntax replaces the anonymous inner class block
        Thread t = new Thread(r, "result-bot-scheduler");
        t.setDaemon(true);
        return t;
    });

    // Bounded, concurrent set for known results
    private final Set<String> knownResultsCache = ConcurrentHashMap.newKeySet();

    // Executor to handle sending individual results without blocking scheduler
    // Use a bounded queue to avoid unbounded accumulation and provide backpressure
    private final ThreadPoolExecutor resultSenderExecutor = new ThreadPoolExecutor(
            1, // core pool size
            1, // max pool size
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(200),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "result-sender");
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    public ResultBotSender(RaceDataManager data) {
        this.data = data;
        this.scheduler.setRemoveOnCancelPolicy(true);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void loadResultsFromStorage() {
        File file = new File(ConfigLoader.getStorageFile());
        if (!file.exists()) {
            log.info("No local history file found. Starting with an empty cache.");
            return;
        }

        log.info("Loading historical results from " + ConfigLoader.getStorageFile() + "...");
        int loadedCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    knownResultsCache.add(parts[0].trim() + "|" + parts[1].trim());
                    loadedCount++;
                }
            }
            log.info("Successfully restored " + loadedCount + " past results into memory cache.");
        } catch (IOException e) {
            log.error("Failed to read history storage file: " + e.getMessage());
        }
    }

        public void startScheduler() {
        long initialDelay = 0;
        LocalTime now = LocalTime.now(ZoneId.of("Europe/London"));

        if (!Utils.isWithinRacingHours(data)) {
            LocalTime nextRace = data.getNextRaceTime();
            LocalTime lastRace = data.getLastRaceTime();

            // SIMPLE CHECK: If next and last race times match, today's racing is finished
            if (nextRace.equals(lastRace) && now.isAfter(lastRace.plusMinutes(30))) {
                // Sleep until 11:00 AM tomorrow morning
                LocalTime sevenAM = LocalTime.of(11, 0);
                long minutesUntilTomorrowSevenAM = Duration.between(now, LocalTime.MAX).toMinutes()
                        + Duration.between(LocalTime.MIN, sevenAM).toMinutes();
                initialDelay = minutesUntilTomorrowSevenAM;

                log.info("Racing finished for today. Sleeping for " + initialDelay
                        + " minutes until 11:00 AM tomorrow.");
            } else {
                // Racing hasn't started yet today. Wait until 1 minute after the first race expected time.
                LocalTime expectedResultTime = nextRace.plusMinutes(1);
                long minutesToWait = Duration.between(now, expectedResultTime).toMinutes();
                initialDelay = Math.max(0, minutesToWait);

                log.info("Waiting " + initialDelay + " minutes until the first result is expected ("
                        + expectedResultTime + ").");
            }
        }

        // Cancel any lingering task execution references safely before spinning up a new one
        if (this.future != null && !this.future.isDone()) {
            this.future.cancel(false);
        }
        this.scheduler.purge(); // Instantly wipe references to help Generational ZGC

        future = scheduler.scheduleAtFixedRate(this::checkForNewResults, initialDelay, 5, TimeUnit.MINUTES);
        log.info("ResultBotSender started. Active check interval is set to 5 minutes after initial delay of " + initialDelay + " minutes.");
    }

    private void checkForNewResults() {
        LocalTime now = LocalTime.now(ZoneId.of("Europe/London"));

        if (!Utils.isWithinRacingHours(data)) {
            LocalTime nextRace = data.getNextRaceTime();
            LocalTime lastRace = data.getLastRaceTime();

            // 🔥 TARGETED RESET TRIGGER: Detect if racing is completed for the day
            if (nextRace.equals(lastRace) && now.isAfter(lastRace.plusMinutes(30))) {
                log.info("🏁 Racing has concluded for the day. Initiating scheduler reset routine...");
                
                // 🔥 THE LIVE DEADBOCK FIREWALL: Use an independent worker task thread 
                // to kill and reschedule this execution loop from the outside.
                scheduler.execute(this::startScheduler);
                return; 
            }

            log.info("Skipping check. Current UK time is outside active racing hours." +
                    " First race at " + data.getFirstRaceTime().toString() + " " +
                    " Last race at " + data.getLastRaceTime().toString());
            return;
        }

        org.jsoup.nodes.Document doc = null;
        try {
            String url = ConfigLoader.getResultsURL();

            doc = org.jsoup.Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                    .timeout(15000) 
                    .get();

            List<RaceResult> results = SportingLifeParser.parseRaceResults(doc);
            log.info("Found " + results.size() + " total results on page.");

            loadResultsFromStorage();
            List<RaceResult> newResults = Utils.filterNewResults(results, knownResultsCache);
            log.info("Found " + newResults.size() + " genuine new results.");

            if (!newResults.isEmpty()) {
                processResults(newResults);
            } else {
                log.info("No new results found.");
            }
        } catch (Exception e) {
            log.error("Error tracking results: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (doc != null) {
                doc.empty();
                doc = null; 
            }
        }
    }


    private void processResults(List<RaceResult> newResults) {
        log.info("Processing " + newResults.size() + " new results individually...");

        // Create a tracker list to wait for all submitted tasks in this batch
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (RaceResult singleResult : newResults) {
            String payload = DiscordWebhookClient.buildPayload(singleResult);

            // Submit the task and capture its future status
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(ConfigLoader.getResultsWebhookURL()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    HttpResponse<String> response = SharedHttpClient.getClient()
                            .send(req, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 204 || response.statusCode() == 200) {
                        log.info("Dispatched webhook for result: " + singleResult.time() + " " + singleResult.place());

                        // REMOVED: knownResultsCache.add(...)
                        // Append directly to the file first; file is our source of truth
                        RaceResultPersistence.storeSingleResult(singleResult);
                    } else {
                        log.error("Discord rejected payload with status " + response.statusCode());
                    }
                } catch (Exception e) {
                    log.error("Error sending/storing result for " + singleResult.place() + ": " + e.getMessage());
                }
            }, resultSenderExecutor);

            tasks.add(task);

            try {
                Thread.sleep(2000); // Simple pacing delay to honor rate limits
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for all webhooks and file writes to fully finish
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Timeout or error waiting for webhooks to finish processing: " + e.getMessage());
        }

        // REMOVED: 'if (synchronizedCache != null)' check
        // Always refresh your memory cache directly from the storage file data
        Set<String> synchronizedCache = RaceResultPersistence.pruneStorageFile();
        knownResultsCache.clear();
        knownResultsCache.addAll(synchronizedCache);
        log.info("Cache successfully refreshed from disk storage after processing batch.");
    }

    public void stop() {
        log.info("Stopping ResultBotSender scheduler and executors...");
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            resultSenderExecutor.shutdown(); // allow tasks to finish
            if (!resultSenderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                resultSenderExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            resultSenderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
