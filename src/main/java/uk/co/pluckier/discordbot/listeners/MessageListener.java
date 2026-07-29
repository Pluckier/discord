package uk.co.pluckier.discordbot.listeners;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import uk.co.pluckier.discordbot.RaceAnalysisFilter;
import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.filters.HorseAnalyzer;
import uk.co.pluckier.discordbot.filters.RaceFilter;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;
import uk.co.pluckier.discordbot.racedata.RaceEmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Discord message listener. Handles command routing and message
 * processing.
 * Delegates heavy lifting to utility classes for better separation of concerns.
 */
public class MessageListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    // private static final String TARGET_CHANNEL_ID = "1527431734889807952";

    private final RaceDataManager data;
    private LocalDate lastFetchedDate;

    public MessageListener(RaceDataManager data) {
        this.data = data;
        this.lastFetchedDate = LocalDate.now(ZoneId.of("Europe/London"));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages sent by bots to avoid infinite loops
        if (event.getAuthor().isBot())
            return;

        // Only respond in the target channel
        if (!event.getChannel().getId().equals(ConfigLoader.getChannelId())) {
            return;
        }

        String message = event.getMessage().getContentRaw();

        // Reload data if it's a new day
        reloadRaceDataIfNewDay();

        // Route command
        routeCommand(message, event);
    }

    /**
     * Reload race data if it's a new day
     */
    private void reloadRaceDataIfNewDay() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        if (!today.equals(lastFetchedDate)) {
            log.info("New day detected! Reloading race data...");
            data.forceFetchTodaysRaces();

            // Guard: Only advance tracking date if data was successfully fetched
            if (data.getRootNode() != null) {
                this.lastFetchedDate = today;
            }
        }
    }

    /**
     * Route message to appropriate command handler
     */
    private void routeCommand(String message, MessageReceivedEvent event) {
        data.fetchTodaysRaces();
        if (message.equalsIgnoreCase("!winner")) {
            event.getChannel().sendMessageEmbeds(getNextRaceWinnerEmbed()).queue();
        } else if (message.equalsIgnoreCase("!values")) {
            event.getChannel().sendMessageEmbeds(processTipsters()).queue();
        } else if (message.matches("^!values\\d+$")) {
            int odds = Integer.parseInt(message.substring(7));
            event.getChannel().sendMessageEmbeds(processTipsters(odds)).queue();
        } else if (message.equalsIgnoreCase("!value")) {
            event.getChannel().sendMessageEmbeds(processTipster()).queue();
        } else if (message.matches("^!value\\d+$")) {
            int odds = Integer.parseInt(message.substring(6));
            event.getChannel().sendMessageEmbeds(processTipster(odds)).queue();
        } else if (message.equalsIgnoreCase("!site")) {
            event.getChannel().sendMessage("https://pluckier.github.io/racing 🏇").queue();
        } else if (message.equalsIgnoreCase("!tips")) {
            event.getChannel().sendMessage("https://pluckier.github.io/tips 📝").queue();
        } else if (message.equalsIgnoreCase("!races")) {
            event.getChannel().sendMessageEmbeds(getTodaysRacesEmbed(data.getRootNode())).queue();
        } else if (message.matches("^!w\\d+$")) {
            int numRaces = Integer.parseInt(message.substring(2));
            event.getChannel().sendMessageEmbeds(getNextRacesWinnerEmbed(numRaces)).queue();
        } else if (message.equalsIgnoreCase("!next")) {
            event.getChannel().sendMessage(getNextRaceEmbeds(LocalTime.now(ZoneId.of("Europe/London")))).queue();
        } else if (message.equalsIgnoreCase("!help")) {
            event.getChannel().sendMessageEmbeds(RaceEmbedBuilder.buildHelpEmbed()).queue();
        } else if (message.equalsIgnoreCase("!nice")) {
            event.getChannel().sendMessageEmbeds(findRacesWithExperiencedFields()).queue();
        } else if (message.equalsIgnoreCase("!test1")) {
            event.getChannel().sendMessageEmbeds(getSpecial()).queue();
        } else if (message.equalsIgnoreCase("!test2")) {
            event.getChannel().sendMessage(getButtonsOn().build()).queue();
        }
    }

    public MessageEmbed processTipster(int odds) {
        RaceAnalysisFilter analysisFilter = new RaceAnalysisFilter(data);
        MessageEmbed filteredResults = analysisFilter.analyzeSingleRace(odds);
        return filteredResults;
    }

    public MessageEmbed processTipster() {
        RaceAnalysisFilter analysisFilter = new RaceAnalysisFilter(data);
        MessageEmbed filteredResults = analysisFilter.analyzeSingleRace(10);
        return filteredResults;
    }

    public MessageEmbed processTipsters(int odds) {
        RaceAnalysisFilter analysisFilter = new RaceAnalysisFilter(data);
        MessageEmbed filteredResults = analysisFilter.analyzeRaces(odds);

        return filteredResults;
    }

    public MessageEmbed processTipsters() {
        RaceAnalysisFilter analysisFilter = new RaceAnalysisFilter(data);
        MessageEmbed filteredResults = analysisFilter.analyzeRaces(10);
        return filteredResults;
    }

    public MessageEmbed findRacesWithExperiencedFields() {
        List<JsonNode> qualifyingRaces = new ArrayList<>();

        JsonNode races = data.getRootNode();
        List<JsonNode> racesNode = RaceFilter.findAllRacesAfter(races, LocalTime.now(ZoneId.of("Europe/London")));

        // Loop through every race in the dataset
        for (JsonNode raceNode : racesNode) {
            JsonNode horsesNode = raceNode.path("horses");

            // Skip races that have no horses listed yet
            if (!horsesNode.isArray() || horsesNode.isEmpty()) {
                continue;
            }

            boolean allHorsesQualify = true;

            // Check every single runner inside this race
            for (JsonNode horseNode : horsesNode) {
                JsonNode pastRacesNode = horseNode.path("past");

                // Get the count of past runs (array size). Treats missing/non-arrays as 0.
                int pastRunCount = pastRacesNode.isArray() ? pastRacesNode.size() : 0;

                // If even ONE horse has fewer than 6 runs, the whole race fails the condition
                if (pastRunCount < 6) {
                    allHorsesQualify = false;
                    break; // Stop checking this race immediately, move to next
                }
            }

            // If the loop finished without tripping the flag, this race meets your criteria
            if (allHorsesQualify) {
                qualifyingRaces.add(raceNode);
            }
        }

        JsonNode qualifyingRacesNode = convertListToJsonNode(qualifyingRaces);

        return getTodaysRacesEmbed(qualifyingRacesNode);
    }

    private MessageCreateBuilder getButtonsOn() {
        // 1. Create the visual layout
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏁 RACECARD: Leopardstown - 15:30")
                .setDescription("Select a runner below to view deep form analysis.")
                .setColor(0x24FEDB); // Dark green

        // 2. Attach interactive buttons underneath the card
        MessageCreateBuilder message = new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .addActionRow(
                        Button.primary("btn_horse_1", "1. Galopin Des Champs"),
                        Button.primary("btn_horse_2", "2. Fastorslow"),
                        Button.secondary("btn_refresh", "🔄 Refresh Odds"));

        return message;
    }

    public MessageCreateData getNextRaceEmbedsFromTime(String raceTimeStr) {
        try {
            // Parse the "14:45" string into a LocalTime object
            LocalTime parsedTime = LocalTime.parse(raceTimeStr);

            // Pass the parsed time into your existing logic
            return getNextRaceEmbeds(parsedTime);

        } catch (DateTimeParseException e) {
            // Fallback embed if the string format is invalid or corrupted
            List<MessageEmbed> errorEmbeds = new ArrayList<>();
            EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("❌ Invalid Time Format")
                    .setDescription("Could not parse the provided race time: " + raceTimeStr)
                    .setColor(0xE74C3C);
            errorEmbeds.add(errorEmbed.build());

            return new MessageCreateBuilder().setEmbeds(errorEmbeds).build();
        }
    }

    /**
     * Get the next race information as a simple string
     */
    public MessageCreateData getNextRaceEmbeds(LocalTime now) {
        List<MessageEmbed> embeds = new ArrayList<>();
        JsonNode rootNode = data.getRootNode();
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, now);

        if (nextRace.isEmpty()) {
            EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("❌ No Races Found")
                    .setDescription("There are no upcoming races scheduled for today.")
                    .setColor(0xE74C3C);
            embeds.add(errorEmbed.build());
            return new MessageCreateBuilder().setEmbeds(embeds).build();
        }

        JsonNode raceNode = nextRace.get();
        String raceTime = raceNode.path("time").asText("Unknown Time");
        String raceName = raceNode.path("place").asText("Unknown Location");
        String going = raceNode.path("going").asText("Not Specified");

        // 1. Primary Overview Card
        EmbedBuilder mainEmbed = new EmbedBuilder()
                .setTitle("🏇 Upcoming Race: " + raceName)
                .setColor(0x2ECC71)
                .addField("🕒 Post Time", raceTime, true)
                .addField("🌱 Going", going, true);
        embeds.add(mainEmbed.build());

        // 2. Horse Silk Cards List
        JsonNode runnersNode = raceNode.path("horses");
        if (runnersNode.isArray() && !runnersNode.isEmpty()) {
            for (JsonNode runner : runnersNode) {
                String horseNumber = runner.path("number").asText("?");
                String horseName = runner.path("name").asText("Unknown Horse");
                String silkUrl = runner.path("silks").asText(null);
                String trainer = runner.path("trainer").asText(null);
                String jockey = runner.path("jockey").asText(null);
                String odds = HorseAnalyzer.getCurrentOdds(HorseAnalyzer.extractOddsList(runner.path("odds")));

                EmbedBuilder horseEmbed = new EmbedBuilder()
                        .setColor(0x3498DB); // Light blue accent for the runner list cards

                // horseEmbed.setTitle(horseNumber + ". " + horseName);
                // horseEmbed.setDescription(horseNumber + ". d " + horseName);
                if (silkUrl != null && !silkUrl.isEmpty()) {
                    // Arguments: setAuthor(textLabel, clickableLinkUrl, iconImageUrl)
                    horseEmbed.setFooter(
                            horseNumber + ". " + horseName + " -- " + odds + " -- " + trainer + "/" + jockey, silkUrl);
                } else {
                    // Fallback text if the horse has no silk image data available
                    horseEmbed.setTitle(horseNumber + ". " + horseName);
                }
                if (embeds.size() < 10) {
                    embeds.add(horseEmbed.build());
                }
            }
        }

        // 3. Create Navigation Buttons
        // We pass the current race's time string in the ID so our button listener knows
        // where we are
        Button prevButton = Button.primary("prev:" + raceTime, "⏮️ Previous Race");
        Button nextButton = Button.primary("next:" + raceTime, "Next Race ⏭️");

        // Assemble everything into a final Message Data structure
        return new MessageCreateBuilder()
                .setEmbeds(embeds)
                .setComponents(ActionRow.of(prevButton, nextButton))
                .build();

    }

    private MessageEmbed getSpecial() {

        EmbedBuilder mainEmbed = new EmbedBuilder()
                .setColor(0x0099ff)
                .setTitle("Some title")
                // .setURL("https://discord.js.org/")
                .setAuthor("Some name", "https://i.imgur.com/AfFp7pu.png", "https://discord.js.org")
                .setDescription("Some description here")
                .setThumbnail("https://i.imgur.com/AfFp7pu.png")
                .addField("Fa", "Fb", true)
                .setImage("https://i.imgur.com/AfFp7pu.png")
                .setTimestamp(Instant.now())
                .setFooter("Some footer text here", "https://i.imgur.com/AfFp7pu.png");

        return mainEmbed.build();
    }

    public static JsonNode convertListToJsonNode(List<JsonNode> list) {
        // Directly pull the global node factory without creating an ObjectMapper
        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();

        // Add items
        for (JsonNode node : list) {
            arrayNode.add(node);
        }

        return arrayNode;
    }

    /**
     * Get today's races as an embed
     */
    private MessageEmbed getTodaysRacesEmbed(JsonNode rootNode) {

        if (rootNode == null || !rootNode.isArray() || rootNode.size() == 0) {
            return RaceEmbedBuilder.buildNoDataEmbed();
        }

        var embed = RaceEmbedBuilder.buildTodaysRacesEmbed();

        StringBuilder listBuilder = new StringBuilder();
        listBuilder.append("Here is the selected schedule of races available today:\n\n");

        for (JsonNode raceNode : rootNode) {
            String raceName = raceNode.path("place").asText("Unknown Location");
            String raceTime = raceNode.path("time").asText("--:--");
            String raceRunners = raceNode.path("runners").asText("--");
            String raceDetails = raceNode.path("detail").asText("-");

            listBuilder.append("⏰ `")
                    .append(raceTime)
                    .append("` 📍 **")
                    .append(raceName)
                    .append(" - ")
                    .append(raceDetails.contains("Handicap") ? "Handicap" : "")
                    .append(raceDetails.contains("Class 1") ? " ⭐ Class 1" : "")
                    .append("** (")
                    .append(raceRunners)
                    .append(" runners)\n");
        }

        embed.setDescription(listBuilder.toString());
        embed.setFooter("Use !winner to get the top pick for the upcoming post-time");
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }

    /**
     * Get the next race winner prediction embed
     */
    private MessageEmbed getNextRaceWinnerEmbed() {
        JsonNode rootNode = data.getRootNode();
        Optional<JsonNode> nextRace = RaceFilter.findNextRace(rootNode, LocalTime.now(ZoneId.of("Europe/London")));

        if (!nextRace.isPresent()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        JsonNode raceNode = nextRace.get();
        String raceTimeStr = raceNode.get("time").asText();
        String racePlaceStr = raceNode.get("place").asText();

        JsonNode horsesNode = raceNode.get("horses");
        if (horsesNode == null || !horsesNode.isArray()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        // Find the favourite horse (lowest odds)
        String favouriteHorse = HorseAnalyzer.findFavouriteHorse(horsesNode);

        HorseAnalyzer.HorsePrediction bestHistorical = null;
        HorseAnalyzer.HorsePrediction bestFirst3 = null;

        for (JsonNode horse : horsesNode) {
            HorseAnalyzer.HorsePrediction prediction = HorseAnalyzer.analyzeHorse(horse);
            if (prediction == null)
                continue;

            // Track best historical rating
            if (bestHistorical == null || prediction.highestRating() > bestHistorical.highestRating()) {
                bestHistorical = prediction;
            }

            // Track best first 3 races average
            if (prediction.avgRatingFirst3() > 0) {
                if (bestFirst3 == null || prediction.avgRatingFirst3() > bestFirst3.avgRatingFirst3()) {
                    bestFirst3 = prediction;
                }
            }
        }

        return RaceEmbedBuilder.buildNextRaceWinnerEmbed(raceTimeStr, racePlaceStr, favouriteHorse, bestHistorical,
                bestFirst3);
    }

    /**
     * Get the next N races winner predictions embed
     */
    private MessageEmbed getNextRacesWinnerEmbed(int maxRaces) {
        JsonNode rootNode = data.getRootNode();
        List<JsonNode> races = RaceFilter.getNextNRaces(rootNode, LocalTime.now(ZoneId.of("Europe/London")), maxRaces);

        if (races.isEmpty()) {
            return RaceEmbedBuilder.buildNoRacesEmbed();
        }

        var embed = RaceEmbedBuilder.buildNextNRacesEmbed(maxRaces);

        for (JsonNode raceNode : races) {
            String raceTimeStr = raceNode.get("time").asText();
            String racePlace = raceNode.has("place") ? raceNode.get("place").asText() : "Unknown Track";

            JsonNode horsesNode = raceNode.get("horses");
            if (horsesNode == null || !horsesNode.isArray()) {
                continue;
            }

            HorseAnalyzer.HorsePrediction bestHorse = null;

            for (JsonNode horse : horsesNode) {
                HorseAnalyzer.HorsePrediction prediction = HorseAnalyzer.analyzeHorse(horse);
                if (prediction == null)
                    continue;

                if (bestHorse == null || prediction.highestRating() > bestHorse.highestRating()) {
                    bestHorse = prediction;
                }
            }

            if (bestHorse != null) {
                embed.addField(
                        "⏰ " + raceTimeStr + " - " + racePlace,
                        "🐎 **" + bestHorse.name() + "** — Current Odds: `" + bestHorse.currentOdds() + "`",
                        false);
            }
        }

        return embed.build();
    }
}
