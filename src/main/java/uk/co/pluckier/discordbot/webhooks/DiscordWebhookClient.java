package uk.co.pluckier.discordbot.webhooks;

import java.io.IOException;
import java.util.stream.Collectors;
import com.gargoylesoftware.htmlunit.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.model.Position;

/**
 * Helper for building and sending Discord webhook payloads.
 *
 * Note: For async sends from background threads we prefer using
 * SharedHttpClient
 * (java.net.HttpClient) with a pre-built JSON payload to avoid capturing heavy
 * HtmlUnit WebClient instances in task runnables.
 */
public class DiscordWebhookClient {

    /**
     * Build the JSON payload for a RaceResult without using HtmlUnit.
     * This method is safe to call on the scheduler thread and returns a plain
     * String that can be passed to java.net.HttpClient for sending.
     */
    public static String buildPayload(RaceResult result) {
        String positionsMarkdown = result.details().positions().stream()
                .map(p -> {
                    String cleanName = sanitizeJsonString(p.horseName());
                    String cleanOdds = sanitizeJsonString(p.odds());
                    String cleanNum = sanitizeJsonString(p.number());

                    String emoji = "🔹";
                    if ("1".equals(p.position()))
                        emoji = "🥇";
                    else if ("2".equals(p.position()))
                        emoji = "🥈";
                    else if ("3".equals(p.position()))
                        emoji = "🥉";

                    return String.format("%s **%s** (No. %s) **%s** — *%s*", emoji, p.position(), cleanNum,
                            cleanName, cleanOdds);
                })
                .collect(Collectors.joining("\\n"));

        String detailsMarkdown = result.details().details().stream()
                .map(detail -> "🔸 " + sanitizeJsonString(detail))
                .collect(Collectors.joining("\\n"));

        String jsonPayload = """
                {
                  "embeds": [
                    {
                      "title": "🏇 New Fast Result: %s",
                      "description": "⏱️ **Time:** %s\\n\\n🏆 **Standings:**\\n%s\\n\\n📋 **Race Details & Dividends:**\\n%s",
                      "color": 3066993,
                      "footer": {
                        "text": "PluckierAI Racing Engine"
                      }
                    }
                  ]
                }
                """
                .formatted(
                        sanitizeJsonString(result.place()),
                        sanitizeJsonString(result.time()),
                        positionsMarkdown,
                        detailsMarkdown);

        return jsonPayload;
    }

    private static String sanitizeJsonString(String input) {
        if (input == null)
            return "";
        return input
                .replace("\\", "\\\\")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replace("\"", "\\\"");
    }
}
