package uk.co.pluckier.discordbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import uk.co.pluckier.discordbot.filters.RaceFilter;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;

import java.awt.Color;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RaceAnalysisFilter {

    private RaceDataManager data;

    public RaceAnalysisFilter(RaceDataManager data) {
        this.data = data;
    }

    /**
     * Accepts a top-level JsonNode representing an array list of races along with
     * a dynamic minimum odds threshold. Returns a beautifully structured Discord
     * MessageEmbed.
     */
    public MessageEmbed analyzeRaces(double minOdds) {
        JsonNode rootNode = data.getRootNode();
        EmbedBuilder embedBuilder = new EmbedBuilder();

        // 1. Set global embed properties and card theme styling
        embedBuilder.setTitle("🎯 Analysed Racing Selections")
                .setColor(new Color(46, 204, 113)) // Emerald Green accent line
                .setDescription("High-utility selections filtered by your background metrics criteria.");

        // Fallback guard for empty or invalid data states
        if (rootNode == null || !rootNode.isArray() || rootNode.isEmpty()) {
            embedBuilder.setDescription("❌ No valid race data was provided for analysis.");
            return embedBuilder.build();
        }

        boolean foundAnyQualifiedRunners = false;

        int activeFieldCount = 0;
        // 2. Loop through the list of races and add fields inside the embed builder
        // state
        for (JsonNode raceNode : rootNode) {
            if (activeFieldCount >= 25) {
                break; // Stops adding fields immediately before hitting 26
            }
            boolean raceHasSelections = processSingleRace(raceNode, minOdds, embedBuilder);
            if (raceHasSelections) {
                foundAnyQualifiedRunners = true;
                activeFieldCount++;
            }
        }

        // 3. Fallback description layout update if every race was eliminated by the min
        // odds filter
        if (!foundAnyQualifiedRunners) {
            embedBuilder.setDescription(
                    "⚠️ No horses qualified across any of the analysed races at the current odds limit.");
        }

        // 4. Append clean live system refresh clock tracking string to footer
        LocalTime timestamp = data.getLastRefreshTime();
        embedBuilder.setFooter("Last refreshed: " + timestamp, null);

        return embedBuilder.build();
    }

    public MessageEmbed analyzeSingleRace(double minOdds) {
        Optional<JsonNode> raceNodeOptional = RaceFilter.findNextRace(data.getRootNode(),
                LocalTime.now(ZoneId.of("Europe/London")));
        JsonNode raceNode = null;
        if (raceNodeOptional.isPresent()) {
            raceNode = raceNodeOptional.get();
        }
        EmbedBuilder embedBuilder = new EmbedBuilder();

        if (raceNode == null) {
            embedBuilder.setTitle("🏁 Single Race Analysis")
                    .setColor(new Color(231, 76, 60)) // Crimson Red for error states
                    .setDescription("❌ No valid race node data was supplied for analysis.");
            return embedBuilder.build();
        }

        String time = raceNode.path("time").asText();
        String place = raceNode.path("place").asText();

        embedBuilder.setTitle("🏁 Race Analysis: " + time + " " + place)
                .setColor(new Color(46, 204, 113))
                .setDescription("High-utility selections filtered by your background metrics criteria.");

        // Process the individual race nodes into the builder state
        boolean hasQualifiedRunners = processSingleRace(raceNode, minOdds, embedBuilder);

        if (!hasQualifiedRunners) {
            embedBuilder.setDescription(
                    "⚠️ No horses from our analysis qualified at the current **" + minOdds + "** odds limit.");
        }

        LocalTime timestamp = data.getLastRefreshTime();
        embedBuilder.setFooter("Last refreshed: " + timestamp, null);

        return embedBuilder.build();
    }

    /**
     * Internal processor for an individual race block. Adds an Embed Field
     * ONLY if there is at least one qualified horse. Returns true if a field was
     * added.
     */
    private boolean processSingleRace(JsonNode raceNode, double minOdds, EmbedBuilder embedBuilder) {
        JsonNode horsesNode = raceNode.path("horses");
        if (!horsesNode.isArray()) {
            return false;
        }

        // 1. Separate valid active runners from non-runners
        List<JsonNode> activeRunners = new ArrayList<>();
        for (JsonNode horse : horsesNode) {
            if (!isNonRunner(horse)) {
                activeRunners.add(horse);
            }
        }

        // 2. Perform Category Logic on active runners
        JsonNode bestLatestHorse = activeRunners.stream()
                .max(Comparator.comparingDouble(RaceAnalysisFilter::getLatestRunRating))
                .orElse(null);

        List<JsonNode> top2BestEver = activeRunners.stream()
                .sorted(Comparator.comparingDouble(RaceAnalysisFilter::getBestEverRating).reversed())
                .limit(2)
                .toList();

        List<JsonNode> top2Average3 = activeRunners.stream()
                .sorted(Comparator.comparingDouble(RaceAnalysisFilter::getAverageOfLatest3).reversed())
                .limit(2)
                .toList();

        // 3. Deduplicate selections across analytical pools using a Map
        Map<String, JsonNode> uniqueSelectionsMap = new LinkedHashMap<>();
        if (bestLatestHorse != null) {
            uniqueSelectionsMap.put(bestLatestHorse.path("name").asText(), bestLatestHorse);
        }
        for (JsonNode h : top2BestEver) {
            uniqueSelectionsMap.put(h.path("name").asText(), h);
        }
        for (JsonNode h : top2Average3) {
            uniqueSelectionsMap.put(h.path("name").asText(), h);
        }

        // 4. Filter selections strictly matching min odds, then sort by lowest odds
        // first
        List<JsonNode> qualifiedHorses = uniqueSelectionsMap.values().stream()
                .filter(horse -> getCurrentOddsAsDouble(horse) > minOdds)
                .sorted(Comparator.comparingDouble(RaceAnalysisFilter::getCurrentOddsAsDouble))
                .toList();

        // 5. CRITICAL GUARD: If the final qualified list is empty, exit immediately.
        // This prevents empty race headers from cluttering up your Discord embed card.
        if (qualifiedHorses.isEmpty()) {
            return false;
        }

        // 6. Build the text block for this specific race's field value content
        String time = raceNode.path("time").asText();
        String place = raceNode.path("place").asText();
        String fieldTitle = "🏁 " + time + " " + place;

        StringBuilder fieldContent = new StringBuilder();
        for (JsonNode horse : qualifiedHorses) {
            JsonNode numNode = horse.path("number");
            String number = numNode.isMissingNode() || numNode.isNull() ? "" : numNode.asText() + ". ";

            String name = horse.path("name").asText();
            String odds = getCurrentOdds(horse);

            // Clean Discord inline styling format (blue diamond with bold horse details)
            fieldContent.append("🔹 **").append(number).append(name).append("** (").append(odds).append(")\n");
        }

        // 7. Push data directly into JDA Embed Field state builder
        // Parameters: Title string, Field text, Inline flag (false creates neat stacked
        // blocks)
        embedBuilder.addField(fieldTitle, fieldContent.toString(), false);
        return true;
    }

    // ==========================================
    // INTERNAL UTILITY PARSERS
    // ==========================================

    private static boolean isNonRunner(JsonNode horse) {
        JsonNode oddsArray = horse.path("odds");
        if (!oddsArray.isArray() || oddsArray.isEmpty())
            return true;

        JsonNode latestOddsNode = oddsArray.get(oddsArray.size() - 1);
        if (latestOddsNode == null || latestOddsNode.isNull())
            return true;

        String oddsText = latestOddsNode.asText().trim();
        return oddsText.isEmpty() || "null".equalsIgnoreCase(oddsText);
    }

    private static String getCurrentOdds(JsonNode horse) {
        JsonNode oddsArray = horse.path("odds");
        if (!oddsArray.isArray() || oddsArray.isEmpty())
            return "N/A";
        return oddsArray.get(oddsArray.size() - 1).asText();
    }

    private static double getCurrentOddsAsDouble(JsonNode horse) {
        return safeParseDouble(getCurrentOdds(horse));
    }

    private static double getLatestRunRating(JsonNode horse) {
        JsonNode pastArray = horse.path("past");
        if (!pastArray.isArray() || pastArray.isEmpty())
            return 0.0;
        return safeParseDouble(pastArray.get(0).path("name").asText());
    }

    private static double getBestEverRating(JsonNode horse) {
        JsonNode pastArray = horse.path("past");
        if (!pastArray.isArray() || pastArray.isEmpty())
            return 0.0;

        return StreamSupport.stream(pastArray.spliterator(), false)
                .mapToDouble(pastRace -> safeParseDouble(pastRace.path("name").asText()))
                .max()
                .orElse(0.0);
    }

    private static double getAverageOfLatest3(JsonNode horse) {
        JsonNode pastArray = horse.path("past");
        if (!pastArray.isArray() || pastArray.isEmpty())
            return 0.0;

        return StreamSupport.stream(pastArray.spliterator(), false)
                .limit(3)
                .mapToDouble(pastRace -> safeParseDouble(pastRace.path("name").asText()))
                .average()
                .orElse(0.0);
    }

    private static double safeParseDouble(String str) {
        try {
            return (str == null || str.isEmpty()) ? 0.0 : Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ==========================================
    // EXECUTABLE MAIN RUNNER
    // ==========================================
    public static void main(String[] args) {
        String jsonInput = """
                [
                  {
                    "time": "14:15",
                    "place": "Cheltenham",
                    "horses": [
                      { "number": 1, "name": "Swift Lightning", "odds": ["5.1", "5.0", "3.85"], "past": [{"name": "85"}, {"name": "70"}, {"name": "60"}, {"name": "65"}, {"name": "72"}, {"name": "68"}, {"name": "74"}, {"name": "81"}, {"name": "77"}, {"name": "70"}] },
                      { "number": 2, "name": "Galloping Ghost",  "odds": ["10.0", "12.0"],        "past": [{"name": "92"}, {"name": "50"}, {"name": "45"}, {"name": "55"}, {"name": "62"}, {"name": "58"}, {"name": "60"}, {"name": "51"}, {"name": "49"}, {"name": "53"}] },
                      { "number": 3, "name": "Shadow Fax",       "odds": ["2.5", null],            "past": [{"name": "78"}, {"name": "88"}, {"name": "84"}, {"name": "80"}, {"name": "82"}, {"name": "85"}, {"name": "79"}, {"name": "83"}, {"name": "81"}, {"name": "86"}] },
                      { "number": 4, "name": "Red Rumbler",     "odds": ["7.0", "7.5"],           "past": [{"name": "80"}, {"name": "82"}, {"name": "79"}, {"name": "81"}, {"name": "83"}, {"name": "78"}, {"name": "84"}, {"name": "80"}, {"name": "82"}, {"name": "85"}] },
                      { "number": 5, "name": "Blue Biscuit",    "odds": [],                       "past": [{"name": "65"}, {"name": "11"}, {"name": "71"}, {"name": "68"}, {"name": "62"}, {"name": "64"}, {"name": "70"}, {"name": "66"}, {"name": "63"}, {"name": "67"}] }
                    ]
                  },
                  {
                    "time": "15:30",
                    "place": "Aintree",
                    "horses": [
                      { "number": 1, "name": "Comet Tail",       "odds": ["6.0", "6.5"],           "past": [{"name": "70"}, {"name": "72"}, {"name": "75"}, {"name": "71"}, {"name": "73"}, {"name": "70"}, {"name": "74"}, {"name": "72"}, {"name": "71"}, {"name": "76"}] },
                      { "number": 2, "name": "Thunder Bolt",    "odds": ["4.0", "5.0"],           "past": [{"name": "89"}, {"name": "91"}, {"name": "87"}, {"name": "85"}, {"name": "88"}, {"name": "90"}, {"name": "86"}, {"name": "84"}, {"name": "89"}, {"name": "92"}] },
                      { "number": 3, "name": "Golden Fleece",   "odds": ["14.0", "11.0"],         "past": [{"name": "74"}, {"name": "76"}, {"name": "80"}, {"name": "78"}, {"name": "75"}, {"name": "77"}, {"name": "79"}, {"name": "82"}, {"name": "81"}, {"name": "73"}] },
                      { "number": 4, "name": "Silver Bullet",   "odds": ["9.0", "10.0"],          "past": [{"name": "83"}, {"name": "81"}, {"name": "78"}, {"name": "82"}, {"name": "80"}, {"name": "84"}, {"name": "85"}, {"name": "79"}, {"name": "82"}, {"name": "81"}] },
                      { "number": 5, "name": "Pegasus Flying",  "odds": ["3.0", "3.2"],           "past": [{"name": "88"}, {"name": "85"}, {"name": "86"}, {"name": "87"}, {"name": "84"}, {"name": "89"}, {"name": "88"}, {"name": "90"}, {"name": "87"}, {"name": "86"}] },
                      { "number": 6, "name": "Majestic Prince", "odds": ["15.0", "16.0"],         "past": [{"name": "60"}, {"name": "62"}, {"name": "61"}, {"name": "65"}, {"name": "63"}, {"name": "64"}, {"name": "66"}, {"name": "62"}, {"name": "61"}, {"name": "63"}] },
                      { "number": 7, "name": "Eclipse Chaser",  "odds": ["8.0", "8.5"],           "past": [{"name": "82"}, {"name": "80"}, {"name": "84"}, {"name": "83"}, {"name": "81"}, {"name": "85"}, {"name": "82"}, {"name": "84"}, {"name": "83"}, {"name": "80"}] },
                      { "number": 8, "name": "Wild Stallion",   "odds": ["20.0", "22.0"],         "past": [{"name": "55"}, {"name": "58"}, {"name": "57"}, {"name": "54"}, {"name": "56"}, {"name": "59"}, {"name": "55"}, {"name": "57"}, {"name": "58"}, {"name": "56"}] }
                    ]
                  }
                ]
                """;

        try {

            // Instantiate once without passing specific limits to constructor
            RaceAnalysisFilter filter = new RaceAnalysisFilter(new RaceDataManager());

            // Pass the minOdds threshold value (e.g. 4.0) directly to the execution method
            // call
            filter.analyzeRaces(10.0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
