package uk.co.pluckier.discordbot.racedata;

import com.fasterxml.jackson.databind.JsonNode;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.utils.SharedHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RaceDataManager {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private JsonNode rootNode; 

    public void fetchTodaysRaces() {
        // Aggressively clear the old reference to let GC free up old memory immediately
        this.rootNode = null; 

        try {
            LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
            String formattedDate = today.format(DATE_FORMATTER);
            String urlString = ConfigLoader.getWebSite() + formattedDate + ConfigLoader.getFileExtension();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Accept", "application/json") 
                    .GET()
                    .build();

            // OPTIMIZATION: Switched to ofInputStream() to stream network bytes straight into Jackson
            HttpResponse<InputStream> response = SharedHttpClient.getClient()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // Read from the data stream inside a try-with-resources block to close it safely
                try (InputStream responseStream = response.body()) {
                    this.rootNode = SharedHttpClient.getMapper().readTree(responseStream);
                }
            } else {
                System.err.println("Failed to fetch data! Code: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("An error occurred while fetching or parsing race data.");
            e.printStackTrace();
        }
    }

    public JsonNode getRootNode() {
        return this.rootNode;
    }
}
