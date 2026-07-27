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

    //String message = "Hello";
    public static void main(String[] args) {
        //var event = new BotMessageEvent();
        //event.begin();
        //event.message = "Hello event from Discord Bot";

        // Initialize the bot here
        System.out.println("Discord Bot is starting...");
    
        RaceDataManager data = new RaceDataManager();
        data.fetchTodaysRaces(); // Fetch and load today's races

        //event.commit();

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
                CacheFlag.MEMBER_OVERRIDES
            );

            // Tell JDA not to save user lists or profiles in your RAM
            builder.setChunkingFilter(ChunkingFilter.NONE);
            builder.setMemberCachePolicy(MemberCachePolicy.NONE);

            // Your listener attachments...
            JDA jda = builder.addEventListeners(messageListener)
                    .addEventListeners(new RacingButtonListener(messageListener)) 
                    .build();

            // CRUCIAL: This blocks the main thread so the program doesn't exit
            jda.awaitReady(); 

            DiscordWebhookSender webhookSender = new DiscordWebhookSender();
            webhookSender.startScheduler(); 
            ResultBotSender resultBotSender = new ResultBotSender();
            resultBotSender.startScheduler();

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