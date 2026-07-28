package uk.co.pluckier.discordbot.racedata;

import com.fasterxml.jackson.databind.JsonNode;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RaceDataManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    private JsonNode rootNode;
    private LocalTime firstRaceTime;
    private LocalTime lastRaceTime;

    public void fetchTodaysRaces() {
        // Aggressively clear old references to let GC free up memory immediately
        this.rootNode = null;
        this.firstRaceTime = null;
        this.lastRaceTime = null;

        try {
            LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
            String formattedDate = today.format(DATE_FORMATTER);
            String urlString = ConfigLoader.getWebSite() + formattedDate + ConfigLoader.getFileExtension();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            // OPTIMIZATION: Switched to ofInputStream() to stream network bytes straight
            // into Jackson
            HttpResponse<InputStream> response = SharedHttpClient.getClient()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // Read from the data stream inside a try-with-resources block to close it
                // safely
                try (InputStream responseStream = response.body()) {
                    this.rootNode = SharedHttpClient.getMapper().readTree(responseStream);

                    // CRITICAL FIX: Added the missing extraction logic trigger here
                    extractFirstAndLastRaceTimes();
                }
            } else {
                System.err.println("Failed to fetch data! Code: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("An error occurred while fetching or parsing race data.");
            e.printStackTrace();
        }
    }

    private void extractFirstAndLastRaceTimes() {
        if (this.rootNode == null || this.rootNode.isEmpty()) {
            return;
        }

        try {
            // Updated mapping logic matching a flat root JSON array structure
            JsonNode meetings = rootNode;

            if (meetings.isArray() && !meetings.isEmpty()) {
                // Get the very first race object
                JsonNode firstMeetingRaces = meetings.get(0);
                if (firstMeetingRaces != null && !firstMeetingRaces.path("time").isMissingNode()) {
                    String firstTimeStr = firstMeetingRaces.path("time").asText();
                    this.firstRaceTime = LocalTime.parse(firstTimeStr, TIME_FORMATTER);
                }

                // Get the very last race object
                JsonNode lastMeetingRaces = meetings.get(meetings.size() - 1);
                if (lastMeetingRaces != null && !lastMeetingRaces.path("time").isMissingNode()) {
                    String lastTimeStr = lastMeetingRaces.path("time").asText();
                    this.lastRaceTime = LocalTime.parse(lastTimeStr, TIME_FORMATTER);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse first/last race times. Check JSON structure or TIME_FORMATTER.");
            e.printStackTrace();
        }
    }

    public JsonNode getRootNode() {
        return this.rootNode;
    }

    public LocalTime getFirstRaceTime() {
        return this.firstRaceTime;
    }

    public LocalTime getLastRaceTime() {
        return this.lastRaceTime;
    }
}
