package uk.co.pluckier.discordbot.utils;

import java.util.ArrayList;
import java.util.List;

import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.racedata.RaceDataManager;

import java.util.Set;

public class Utils {

    public static List<RaceResult> filterNewResults(List<RaceResult> currentResults, Set<String> knownResultsCache) {
        List<RaceResult> newResults = new ArrayList<>();
        for (RaceResult webResult : currentResults) {
            String lookupKey = webResult.time() + "|" + webResult.place();
            if (!knownResultsCache.contains(lookupKey)) {
                newResults.add(webResult);
            }
        }
        return newResults;
    }

    public static boolean isWithinRacingHours(RaceDataManager data) {
        java.time.ZonedDateTime ukTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/London"));
        java.time.LocalTime currentTime = ukTime.toLocalTime();

        java.time.LocalTime startWindow = data.getFirstRaceTime();
        java.time.LocalTime endWindow = data.getLastRaceTime().plusMinutes(30);

        return !currentTime.isBefore(startWindow) && !currentTime.isAfter(endWindow);
    }

}
