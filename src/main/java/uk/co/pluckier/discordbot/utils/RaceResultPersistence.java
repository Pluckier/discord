package uk.co.pluckier.discordbot.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.pluckier.discordbot.config.ConfigLoader;
import uk.co.pluckier.discordbot.model.RaceResult;

public class RaceResultPersistence {

    private static final Logger log = LoggerFactory.getLogger(RaceResultPersistence.class);

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
            log.error("Local file persistence failed for " + result.time() + ": " + e.getMessage());
        }
    }

    public static Set<String> pruneStorageFile() {
        Set<String> updatedCacheKeys = new HashSet<>();
        try {
            File file = new File(ConfigLoader.getStorageFile());
            if (!file.exists()) {
                return updatedCacheKeys; // Return empty set instead of null
            }

            List<String> allLines = Files.readAllLines(file.toPath());

            // Housekeeping: Trim the file only if it exceeds our threshold
            if (allLines.size() > 100) {
                allLines = allLines.subList(allLines.size() - 50, allLines.size());
                Files.write(file.toPath(), allLines);
                log.info("Housekeeping complete: Trimmed historical storage file down to 50 entries.");
            }

            // ALWAYS map the active lines to keys, whether we trimmed the file or not!
            for (String line : allLines) {
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    updatedCacheKeys.add(parts[0].trim() + "|" + parts[1].trim());
                }
            }

        } catch (IOException e) {
            log.error("Failed to prune historical storage file: " + e.getMessage());
        }

        // Always returns the true state of the file, never null!
        return updatedCacheKeys;
    }

}
