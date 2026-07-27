package uk.co.pluckier.discordbot.racedata;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import uk.co.pluckier.discordbot.filters.HorseAnalyzer;

import java.awt.Color;
import java.time.Instant;

/**
 * Utility for building Discord embeds for racing information.
 */
public class RaceEmbedBuilder {

    private static final Color RACING_GREEN = new Color(30, 130, 76);
    private static final Color WARNING_RED = new Color(217, 30, 30);
    private static final Color INFO_BLUE = new Color(52, 152, 219);
    private static final Color CARD_BLUE = new Color(41, 128, 185);

    /**
     * Build the help menu embed
     */
    public static MessageEmbed buildHelpEmbed() {
        EmbedBuilder helpEmbed = new EmbedBuilder();

        helpEmbed.setColor(INFO_BLUE);
        helpEmbed.setTitle("📚 Pluckier Racing Bot - Help Menu");
        helpEmbed.setDescription("Here is a list of all available commands you can use with this bot:");

        helpEmbed.addField("🔧 Utility Commands",
                "`!help` - Display this help menu.\n" +
                        "`!site` - Get the link to the racing site.\n" +
                        "`!tips` - Get the link to the tips site.",
                false);

        helpEmbed.addField("🏇 Racing Commands",
                "`!winner` - Get the predicted winner for the next race.\n" +
                        "`!wX` - Get the predicted winners for the next X races.\n" +
                        "`!next` - Get info on the next upcoming race.\n" +
                        "`!nice` - List races with experienced fields.\n" +
                        "`!races` - Get the complete list of today's races.",
                false);

        helpEmbed.setFooter("Tip: Type commands exactly as shown above.");
        helpEmbed.setTimestamp(Instant.now());

        return helpEmbed.build();
    }

    /**
     * Build the no races remaining error embed
     */
    public static MessageEmbed buildNoRacesEmbed() {
        return new EmbedBuilder()
                .setColor(WARNING_RED)
                .setTitle("🚫 No Races Remaining")
                .setDescription("There are no further races scheduled after your local system time for today.")
                .build();
    }

    /**
     * Build the no data available error embed
     */
    public static MessageEmbed buildNoDataEmbed() {
        return new EmbedBuilder()
                .setColor(WARNING_RED)
                .setTitle("❌ No Data Available")
                .setDescription("No racing data available for today.")
                .build();
    }

    /**
     * Build the next race winner prediction embed with dual metrics
     */
    public static MessageEmbed buildNextRaceWinnerEmbed(String raceTime, String racePlace,
            String favouriteHorse,
            HorseAnalyzer.HorsePrediction bestHistorical,
            HorseAnalyzer.HorsePrediction bestFirst3) {
        if (bestHistorical == null && bestFirst3 == null) {
            return buildNoRacesEmbed();
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(RACING_GREEN)
                .setTitle("🏁 Next Race Winner Prediction")
                .setDescription("Favourite in the next is **" + favouriteHorse + "** but here is our data:")
                .addField("⏰ ", "`" + raceTime + "` " + racePlace, true);

        if (bestHistorical != null) {
            embedBuilder.addField(
                    "📊 Best Historical",
                    "**" + bestHistorical.name() + "**\nOdds: `" + bestHistorical.currentOdds() + "`",
                    true);
        }

        if (bestFirst3 != null) {
            embedBuilder.addField(
                    "📈 Best Recent Form (3)",
                    "**" + bestFirst3.name() + "**\nOdds: `" + bestFirst3.currentOdds() + "`",
                    true);
        }

        embedBuilder.setFooter("Data sourced dynamically from PluckierAI")
                .setTimestamp(Instant.now());

        return embedBuilder.build();
    }

    /**
     * Build the next N races winner predictions embed
     */
    public static EmbedBuilder buildNextNRacesEmbed(int maxRaces) {
        return new EmbedBuilder()
                .setColor(RACING_GREEN)
                .setTitle("🏁 Upcoming Race Winner Predictions")
                .setDescription("Analyzing the next " + maxRaces + " cards based on historical performance values.")
                .setFooter("Data sourced dynamically from PluckierAI")
                .setTimestamp(Instant.now());
    }

    /**
     * Build the today's races card embed
     */
    public static EmbedBuilder buildTodaysRacesEmbed() {
        return new EmbedBuilder()
                .setColor(CARD_BLUE)
                .setTitle("📅 Today's Racing Card");
    }
}
