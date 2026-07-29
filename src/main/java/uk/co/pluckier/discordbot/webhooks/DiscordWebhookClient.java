package uk.co.pluckier.discordbot.webhooks;

import java.util.stream.Collectors;

import uk.co.pluckier.discordbot.model.RaceResult;

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

    // Roll a random integer between 0 and 11 to choose from 12 distinct options
    String randomColour = switch ((int) (Math.random() * 12)) {
      case 0 -> "3447003"; // Vibrant Blue (#3498DB)
      case 1 -> "3066993"; // Emerald Green (#2ECC71)
      case 2 -> "15158332"; // Crimson Red (#E74C3C)
      case 3 -> "16766720"; // Gold Yellow (#F1C40F)
      case 4 -> "10181046"; // Amethyst Purple (#9B59B6)
      case 5 -> "15548997"; // Vibrant Orange (#ED7D31)
      case 6 -> "16580797"; // Hot Pink (#FC46AA)
      case 7 -> "1752220"; // Aqua Teal (#1ABC9C)
      case 8 -> "2123412"; // Blurple / Discord Blue (#2066F4)
      case 9 -> "7419530"; // Deep Magenta / Plum (#71368A)
      case 10 -> "5763719"; // Mint / Light Green (#57F287)
      case 11 -> "15462144"; // Fuchsia Pink (#EC407A)
      default -> "3066993"; // Default Fallback (Emerald Green)
    };

    String jsonPayload = """
        {
          "embeds": [
            {
              "title": "🏇 New Result: %s",
              "description": "⏱️ **Time:** %s\\n\\n🏆 **Standings:**\\n%s\\n\\n📋 **Race Details & Dividends:**\\n%s",
              "color": %s,
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
            detailsMarkdown,
            randomColour);

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
