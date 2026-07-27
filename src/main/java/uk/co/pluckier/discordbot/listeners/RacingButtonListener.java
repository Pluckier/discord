package uk.co.pluckier.discordbot.listeners;

import java.time.LocalTime;
import java.time.ZoneId;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RacingButtonListener extends ListenerAdapter {

    private MessageListener messageListener;

    public RacingButtonListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Check WHICH button the user clicked using its unique ID
        if (event.getComponentId().equals("btn_horse_1")) {
            
            // Instantly reply to the user with a private message showing the form
            event.reply("📊 **Galopin Des Champs Form:** 1-1-F-1. Won this race last year by 4 lengths.")
                 .setEphemeral(true) // "Ephemeral" means only the clicker can see this text!
                 .queue();
                 
        } else if (event.getComponentId().equals("btn_refresh")) {
            // Your code here to fetch live odds from your server and edit the message
            event.editMessage("Odds updated!").queue();
        } else {
            if (event.getComponentId().contains("prev")){
                event.reply(messageListener.getNextRaceEmbeds(LocalTime.now(ZoneId.of("Europe/London")))).queue(); 
            } else if (event.getComponentId().contains("next")){
                event.reply(messageListener.getNextRaceEmbedsFromTime(event.getComponentId().substring(5))).queue(); 
            }
            //event.reply("Button ID: " + event.getComponentId()).queue();
            
        }
    }
}

