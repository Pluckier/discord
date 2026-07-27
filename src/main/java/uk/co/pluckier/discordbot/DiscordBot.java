package uk.co.pluckier.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.listeners.MessageListener;
import uk.co.pluckier.discordbot.listeners.RacingButtonListener;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;
import uk.co.pluckier.discordbot.webhooks.DiscordWebhookSender;
import uk.co.pluckier.discordbot.webhooks.ResultBotSender;

public class DiscordBot {

    public static void main(String[] args) {
        System.out.println("Discord Bot is starting...");

        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces(); // Fetch and load today's races

        try {
            MessageListener messageListener = new MessageListener(data);

            JDABuilder builder = JDABuilder.createLight(ConfigLoader.getToken(),
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT);

            builder.disableCache(
                    CacheFlag.VOICE_STATE,
                    CacheFlag.EMOJI,
                    CacheFlag.STICKER,
                    CacheFlag.CLIENT_STATUS,
                    CacheFlag.ACTIVITY,
                    CacheFlag.ONLINE_STATUS,
                    CacheFlag.MEMBER_OVERRIDES);

            // Tell JDA not to save user lists or profiles in your RAM
            builder.setChunkingFilter(ChunkingFilter.NONE);
            builder.setMemberCachePolicy(MemberCachePolicy.NONE);

            // Build JDA and keep reference so we can shutdown cleanly
            final JDA jda = builder.addEventListeners(messageListener)
                    .addEventListeners(new RacingButtonListener(messageListener))
                    .build();

            // Block until ready
            jda.awaitReady();

            // Start background schedulers and keep references
            final DiscordWebhookSender webhookSender = new DiscordWebhookSender();
            webhookSender.startScheduler();
            final ResultBotSender resultBotSender = new ResultBotSender();
            resultBotSender.startScheduler();

            // Add a JVM shutdown hook to clean up threads and JDA
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 Shutdown hook: stopping schedulers and JDA...");
                try {
                    webhookSender.stop();
                } catch (Exception ignored) {
                }
                try {
                    resultBotSender.stop();
                } catch (Exception ignored) {
                }
                try {
                    if (jda != null) {
                        jda.shutdownNow();
                    }
                } catch (Exception ignored) {
                }
            }));

            System.out.println("Bot is successfully connected and online!");

        } catch (InterruptedException e) {
            System.err.println("Bot startup was interrupted.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to start the bot. Check your token!");
            e.printStackTrace();
        }
    }

}