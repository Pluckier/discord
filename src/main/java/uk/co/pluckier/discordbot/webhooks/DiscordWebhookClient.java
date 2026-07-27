package uk.co.pluckier.discordbot.webhooks;

import java.io.IOException;
import java.util.stream.Collectors;
import com.gargoylesoftware.htmlunit.*;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.model.Position;

public class DiscordWebhookClient {

    public static void sendSingleResultToDiscord(WebClient webClient, RaceResult result) {
        // 1. FIXED: Added correct mapping placement parameters and position indicators
        String positionsMarkdown = result.details().positions().stream()
                .map(p -> {
                    String cleanName = sanitizeJsonString(p.horseName());
                    String cleanOdds = sanitizeJsonString(p.odds());
                    String cleanNum = sanitizeJsonString(p.number());

                    // Dynamic podium emoji selection based on placement string
                    String emoji = "🔹";
                    if ("1".equals(p.position()))
                        emoji = "🥇";
                    else if ("2".equals(p.position()))
                        emoji = "🥈";
                    else if ("3".equals(p.position()))
                        emoji = "🥉";

                    // The layout: [Emoji] [Position] (No. [Number]) [Horse Name] — *Odds: [Odds]*
                    return String.format("%s **%s** (No. %s) **%s** — *%s*", emoji, p.position(), cleanNum,
                            cleanName, cleanOdds);
                })
                .collect(Collectors.joining("\\n"));

        // 2. Format the Extra Details (Trainer, Jockey, Dividends) into a clean list
        String detailsMarkdown = result.details().details().stream()
                .map(detail -> "🔸 " + sanitizeJsonString(detail))
                .collect(Collectors.joining("\\n"));

        // 3. Construct a beautiful Discord Rich Embed JSON Payload
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

        try {
            WebRequest webRequest = new WebRequest(new java.net.URL(ConfigLoader.getWebhookURL()), HttpMethod.POST);
            byte[] payloadBytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            webRequest.setRequestBody(new String(payloadBytes, java.nio.charset.StandardCharsets.ISO_8859_1));

            webRequest.setAdditionalHeader("Content-Type", "application/json; charset=UTF-8");
            webRequest.setAdditionalHeader("Accept", "application/json");

            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            WebResponse response = webClient.getPage(webRequest).getWebResponse();

            if (response.getStatusCode() == 204 || response.getStatusCode() == 200) {
                System.out.println("Dispatched webhook for result: " + result.time() + " " + result.place());
            } else {
                System.err.println("Discord rejected payload with status " + response.getStatusCode() + ": "
                        + response.getContentAsString());
            }
        } catch (IOException e) {
            System.err.println("Webhook transport failure for " + result.time() + ": " + e.getMessage());
        }
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
