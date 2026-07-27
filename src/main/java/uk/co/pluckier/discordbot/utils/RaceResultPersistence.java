package uk.co.pluckier.discordbot.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.model.Position;

public class RaceResultPersistence {

    public static void storeSingleResult(RaceResult result) {
        // 1. Flatten your structured positions list into a simple readable text segment
        String positionsString = result.details().positions().stream()
                .map(p -> String.format("%s) No.%s %s [%s]", p.position(), p.number(), p.horseName(), p.odds()))
                .collect(Collectors.joining(", "));

        // 2. Flatten your extra metadata tags (Trainer, Jockey, Dividends)
        String extraString = String.join(" | ", result.details().details());

        // 3. Combine everything into one single safe file entry line
        String combinedRawLine = positionsString + " || " + extraString;
        String newLine = result.time() + "|" + result.place() + "|" + combinedRawLine;

        try (java.io.FileWriter writer = new java.io.FileWriter(ConfigLoader.getStorageFile(), true)) {
            writer.write(newLine + "\n");
        } catch (IOException e) {
            System.err.println("Local file persistence failed for " + result.time() + ": " + e.getMessage());
        }
    }

    public static Set<String> pruneStorageFile() {
        try {
            File file = new File(ConfigLoader.getStorageFile());
            if (!file.exists())
                return null;

            List<String> allLines = Files.readAllLines(file.toPath());

            if (allLines.size() > 150) {
                List<String> trimmedLines = allLines.subList(allLines.size() - 100, allLines.size());
                Files.write(file.toPath(), trimmedLines);

                Set<String> updatedCacheKeys = new HashSet<>();
                for (String line : trimmedLines) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 2) {
                        updatedCacheKeys.add(parts[0].trim() + "|" + parts[1].trim());
                    }
                }
                System.out.println("Housekeeping complete: Cleaned file and RAM cache bounds safely.");
                return updatedCacheKeys;
            }
        } catch (IOException e) {
            System.err.println("Failed to prune historical storage file: " + e.getMessage());
        }
        return null;
    }
}
