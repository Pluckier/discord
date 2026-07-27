package uk.co.pluckier.discordbot.webhooks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import com.gargoylesoftware.htmlunit.*;

import uk.co.pluckier.discordbot.webparser.SportingLifeParser;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.utils.RaceResultPersistence;

public class ResultBotSender {

    public static void main(String[] args) {
        System.out.println("--- Starting Single Test Run ---");
        ResultBotSender bot = new ResultBotSender();
        bot.checkForNewResults();
        System.out.println("--- Single Test Run Finished ---");
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Set<String> knownResultsCache = ConcurrentHashMap.newKeySet();

    public ResultBotSender() {
        loadResultsFromStorage();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void loadResultsFromStorage() {
        File file = new File(ConfigLoader.getStorageFile());
        if (!file.exists()) {
            System.out.println("No local history file found. Starting with an empty cache.");
            return;
        }

        System.out.println("Loading historical results from " + ConfigLoader.getStorageFile() + "...");
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
            System.out.println("Successfully restored " + loadedCount + " past results into memory cache.");
        } catch (IOException e) {
            System.err.println("Failed to read history storage file: " + e.getMessage());
        }
    }

    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::checkForNewResults, 0, 5, TimeUnit.MINUTES);
        System.out.println("ResultBotSender started. Checking for new results every 5 minutes.");
    }

    private void checkForNewResults() {
        if (!isWithinRacingHours()) {
            System.out.println("Skipping check. Current UK time is outside active racing hours (11:00 AM - 9:30 PM).");
            return;
        }
        BrowserVersion.BrowserVersionBuilder browserBuilder = new BrowserVersion.BrowserVersionBuilder(
                BrowserVersion.CHROME);
        browserBuilder.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        BrowserVersion customChrome = browserBuilder.build();

        try (WebClient webClient = new WebClient(customChrome)) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false);

            String url = ConfigLoader.getResultsURL();
            String pageHtml = webClient.getPage(url).getWebResponse().getContentAsString();

            // Refactored to use dedicated Parser Class
            List<RaceResult> results = SportingLifeParser.parseRaceResults(pageHtml);
            System.out.println("Found " + results.size() + " total results on page.");

            List<RaceResult> newResults = filterNewResults(results);
            System.out.println("Found " + newResults.size() + " genuine new results.");

            if (!newResults.isEmpty()) {
                System.out.println("Processing " + newResults.size() + " new results individually...");

                for (RaceResult singleResult : newResults) {
                    // Refactored to use dedicated Discord Messaging Client Utility
                    DiscordWebhookClient.sendSingleResultToDiscord(webClient, singleResult);

                    knownResultsCache.add(singleResult.time() + "|" + singleResult.place());

                    // Refactored to use dedicated File Persistence Manager
                    RaceResultPersistence.storeSingleResult(singleResult);

                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Triggers cache file pruning and returns the new memory mappings
                Set<String> synchronizedCache = RaceResultPersistence.pruneStorageFile();
                if (synchronizedCache != null) {
                    knownResultsCache.clear();
                    knownResultsCache.addAll(synchronizedCache);
                }
            } else {
                System.out.println("No new results found.");
            }
        } catch (Exception e) {
            System.err.println("Error tracking results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<RaceResult> filterNewResults(List<RaceResult> currentResults) {
        List<RaceResult> newResults = new ArrayList<>();
        for (RaceResult webResult : currentResults) {
            String lookupKey = webResult.time() + "|" + webResult.place();
            if (!knownResultsCache.contains(lookupKey)) {
                newResults.add(webResult);
            }
        }
        return newResults;
    }

    private boolean isWithinRacingHours() {
        // Force evaluation using the UK/London time window explicitly
        java.time.ZonedDateTime ukTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/London"));
        java.time.LocalTime currentTime = ukTime.toLocalTime();

        java.time.LocalTime startWindow = java.time.LocalTime.of(11, 0); // 11:00 AM
        java.time.LocalTime endWindow = java.time.LocalTime.of(21, 30); // 09:30 PM

        // Returns true only if the clock sits squarely between 11:00 and 21:30
        return !currentTime.isBefore(startWindow) && !currentTime.isAfter(endWindow);
    }

    public void stop() {
        scheduler.shutdown();
    }
}
