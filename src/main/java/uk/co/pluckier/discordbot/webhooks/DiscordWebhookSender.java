package uk.co.pluckier.discordbot.webhooks;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;

import uk.co.pluckier.discordbot.filters.HorseAnalyzer;
import uk.co.pluckier.discordbot.filters.RaceFilter;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;
import uk.co.pluckier.discordbot.config.ConfigLoader;

import com.fasterxml.jackson.databind.JsonNode;

public class DiscordWebhookSender {

    // Use a daemon-thread ScheduledExecutorService so threads don't keep JVM alive
    // unexpectedly
    private static final ScheduledExecutorService scheduler = Executors
            .newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "discord-webhook-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    private static String lastAlertedRaceTime = "";

    private static LocalDate lastTrackingDate = LocalDate.MIN;

    // Memory fix: Use a primitive timestamp to calculate when it's safe to poll the
    // API again
    private static long nextAllowedCheckTimeMillis = 0;

    public static void main(String[] args) {
        DiscordWebhookSender program = new DiscordWebhookSender();
        program.startScheduler();
    }

    public void startScheduler() {
        System.out.println("🏁 Automated Racing Engine Started with State-Driven Fixed Rate Loop!");

        // Schedule exactly ONE recurring task. It never adds new tasks to the
        // queue.
        scheduler.scheduleAtFixedRate(DiscordWebhookSender::executeEngineCycle, 0, 1, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down Automated Racing Engine scheduler pool cleanly...");
            scheduler.shutdown();
        }));
    }

    private static void executeEngineCycle() {
        // Safe check: If we are in "smart sleep" mode, instantly exit.
        // No network calls made, no objects allocated, completely free.
        if (System.currentTimeMillis() < nextAllowedCheckTimeMillis) {
            return;
        }

        try {
            ZoneId londonZone = ZoneId.of("Europe/London");
            LocalTime now = LocalTime.now(londonZone);
            LocalDate today = LocalDate.now(londonZone);

            if (!today.equals(lastTrackingDate)) {
                lastAlertedRaceTime = "";
                lastTrackingDate = today;
            }

            System.out.println(now + " 🔄 Fetching fresh schedule data...");
            RaceDataManager data = new RaceDataManager();
            data.fetchTodaysRaces();
            JsonNode rootNode = data.getRootNode();

            Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, now);

            if (nextRace.isEmpty()) {
                System.out.println("📭 No more races found for today. Smart sleep for 1 hour...");
                setSmartSleepMinutes(60);
                return;
            }

            JsonNode raceNode = nextRace.get();
            String raceTimeStr = raceNode.path("time").asText("Unknown Time");

            LocalTime raceTime = LocalTime.parse(raceTimeStr);
            long minutesUntilRace = now.until(raceTime, ChronoUnit.MINUTES);

            long minutesToSleep = minutesUntilRace - 4;

            if (minutesToSleep <= 0) {
                if (!raceTimeStr.equals(lastAlertedRaceTime)) {
                    System.out.printf("🚨 Race at %s is due! Firing alert now.\n", raceTimeStr);
                    lastAlertedRaceTime = raceTimeStr;
                    sendRaceAlertPayload(raceNode, raceTimeStr, now);
                }
                // Allow checking again next minute to clear the race window safely
                setSmartSleepMinutes(0);
            } else {
                System.out.printf("💤 Next race at %s (%d mins away). Smart sleeping for %d minutes...\n",
                        raceTimeStr, minutesUntilRace, minutesToSleep);
                setSmartSleepMinutes(minutesToSleep);
            }

        } catch (DateTimeParseException e) {
            System.err.println("❌ Could not parse race time string. Retrying in 5 mins.");
            setSmartSleepMinutes(5);
        } catch (Exception e) {
            System.err.println("❌ Error encountered in engine: " + e.getMessage() + ". Retrying in 5 mins.");
            setSmartSleepMinutes(5);
        }
    }

    public void stop() {
        System.out.println("Stopping DiscordWebhookSender scheduler and cleaning up...");
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private static void setSmartSleepMinutes(long minutes) {
        if (minutes <= 0) {
            nextAllowedCheckTimeMillis = 0;
        } else {
            nextAllowedCheckTimeMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        }
    }

    private static void sendRaceAlertPayload(JsonNode raceNode, String raceTimeStr, LocalTime now) {
        String raceName = raceNode.path("place").asText("Unknown Location");
        String going = raceNode.path("going").asText("Not Specified");
        String detail = raceNode.path("detail").asText("Not Specified").replace("\"", "");
        String runners = raceNode.path("runners").asText("Not Specified");

        StringBuilder fieldsJsonBuilder = new StringBuilder();
        JsonNode runnersNode = raceNode.path("horses");

        if (runnersNode.isArray() && !runnersNode.isEmpty()) {
            for (int i = 0; i < runnersNode.size(); i++) {
                JsonNode runner = runnersNode.get(i);
                String horseNumber = runner.path("number").asText("?");
                String horseName = runner.path("name").asText("Unknown Horse");
                String trainer = runner.path("trainer").asText("Unknown");
                String jockey = runner.path("jockey").asText("Unknown");
                String odds = HorseAnalyzer.getCurrentOdds(HorseAnalyzer.extractOddsList(runner.path("odds")));

                String fieldSnippet = """
                        {
                        "name": "%s. %s (%s)",
                        "value": "👟 *J/T:* %s / %s",
                        "inline": true
                        }
                        """.formatted(horseNumber, horseName, odds, jockey, trainer);

                fieldsJsonBuilder.append(fieldSnippet);
                if (i < runnersNode.size() - 1) {
                    fieldsJsonBuilder.append(",\n");
                }
            }
        }

        String timeNowStr = now.toString().substring(0, 5);

        String jsonPayload = """
                {
                "content": "🚨 **Upcoming Race Alert! Starts in 4 minutes!**",
                "embeds": [
                    {
                    "title": "🏁 RACECARD: %s %s %s",
                    "description": "💰 ** %s ** \\nTotal Runners: %s",
                    "color": 2424619,
                    "fields": [
                        %s
                    ],
                    "footer": {
                        "text": "Data refreshed: %s | PluckierAI Racing Engine"
                    }
                    }
                ]
                }
                """.formatted(raceTimeStr, raceName, going, detail, runners, fieldsJsonBuilder.toString(), timeNowStr);

        sendRaceTip(jsonPayload);
    }

    public static void sendRaceTip(String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ConfigLoader.getWebhookURL()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            SharedHttpClient.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 204 && response.statusCode() != 200) {
                            System.err.println("Discord Error Code: " + response.statusCode());
                        } else {
                            System.out.println("Success! Message sent to Discord.");
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("Failed to send webhook: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Error building request: " + e.getMessage());
        }
    }
}
