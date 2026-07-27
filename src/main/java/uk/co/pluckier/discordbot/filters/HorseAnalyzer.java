package uk.co.pluckier.discordbot.filters;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes horse performance data from JSON race data.
 * Handles rating extraction, odds retrieval, and performance calculations.
 */
public class HorseAnalyzer {

    /**
     * Immutable record to store horse prediction data
     */
    public record HorsePrediction(String name, int highestRating, double avgRatingFirst3, String currentOdds) {}

    /**
     * Check if a horse is a non-runner based on odds data
     */
    public static boolean isNonRunner(JsonNode oddsNode) {
        if (oddsNode == null || oddsNode.isNull() || "null".equalsIgnoreCase(oddsNode.asText())) {
            return true;
        }

        if (oddsNode.isArray() && oddsNode.size() > 0) {
            JsonNode lastOdds = oddsNode.get(oddsNode.size() - 1);
            if (lastOdds == null || lastOdds.isNull() || "null".equalsIgnoreCase(lastOdds.asText())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract odds as a list of strings from JsonNode
     */
    public static List<String> extractOddsList(JsonNode oddsNode) {
        List<String> oddsList = new ArrayList<>();

        if (oddsNode != null && oddsNode.isArray()) {
            for (JsonNode odd : oddsNode) {
                oddsList.add(odd.asText());
            }
        }

        return oddsList;
    }

    /**
     * Get the current (most recent) odds from a list
     */
    public static String getCurrentOdds(List<String> oddsList) {
        return oddsList.isEmpty() ? "N/A" : oddsList.get(oddsList.size() - 1);
    }

    /**
     * Parse odds string to double for comparison
     */
    public static double parseOdds(String oddsStr) {
        try {
            return Double.parseDouble(oddsStr);
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE; // Non-runners or invalid odds get highest value
        }
    }

    /**
     * Find the highest rating from a horse's past performances
     */
    public static int getHighestRating(JsonNode pastNode) {
        int highestRating = -1;

        if (pastNode != null && pastNode.isArray()) {
            for (JsonNode pastRace : pastNode) {
                String ratingStr = pastRace.get("name").asText();
                try {
                    int rating = Integer.parseInt(ratingStr);
                    if (rating > highestRating) {
                        highestRating = rating;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return highestRating;
    }

    /**
     * Calculate the average rating from the first 3 races in the past history
     */
    public static double calculateFirst3RacesAverage(JsonNode pastNode) {
        if (pastNode == null || !pastNode.isArray() || pastNode.size() == 0) {
            return 0.0;
        }

        // Get the first 3 races (or fewer if less than 3 exist)
        int endIndex = Math.min(3, pastNode.size());
        List<Integer> firstThreeRatings = new ArrayList<>();

        for (int i = 0; i < endIndex; i++) {
            JsonNode pastRace = pastNode.get(i);
            String ratingStr = pastRace.get("name").asText();
            try {
                int rating = Integer.parseInt(ratingStr);
                firstThreeRatings.add(rating);
            } catch (NumberFormatException ignored) {}
        }

        if (firstThreeRatings.isEmpty()) {
            return 0.0;
        }

        return firstThreeRatings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Analyze a single horse and return its prediction data
     */
    public static HorsePrediction analyzeHorse(JsonNode horse) {
        JsonNode oddsNode = horse.get("odds");
        if (isNonRunner(oddsNode)) {
            return null;
        }

        String horseName = horse.get("name").asText();
        List<String> oddsList = extractOddsList(oddsNode);
        String currentOdds = getCurrentOdds(oddsList);

        JsonNode pastNode = horse.get("past");
        int highestRating = getHighestRating(pastNode);
        double avgFirst3 = calculateFirst3RacesAverage(pastNode);

        if (highestRating == -1) {
            return null;
        }

        return new HorsePrediction(horseName, highestRating, avgFirst3, currentOdds);
    }

    /**
     * Find the favourite horse (lowest odds) in a race
     */
    public static String findFavouriteHorse(JsonNode horsesNode) {
        if (horsesNode == null || !horsesNode.isArray()) {
            return "Unknown";
        }

        String favouriteHorse = "Unknown";
        double lowestOdds = Double.MAX_VALUE;

        for (JsonNode horse : horsesNode) {
            JsonNode oddsNode = horse.get("odds");
            
            if (isNonRunner(oddsNode)) {
                continue;
            }

            List<String> oddsList = extractOddsList(oddsNode);
            String currentOdds = getCurrentOdds(oddsList);
            double oddsValue = parseOdds(currentOdds);

            if (oddsValue < lowestOdds) {
                lowestOdds = oddsValue;
                favouriteHorse = horse.get("name").asText();
            }
        }

        return favouriteHorse;
    }
}
