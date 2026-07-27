package uk.co.pluckier.discordbot.webparser;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import uk.co.pluckier.discordbot.model.Position;
import uk.co.pluckier.discordbot.model.RaceResult;
import uk.co.pluckier.discordbot.model.ResultDetails;

public class SportingLifeParser {

    public static List<RaceResult> parseRaceResults(String html) {
        List<RaceResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements raceContainers = doc.select("div.FastResultsList__FastResultCardContainer-sc-9e8caae5-1");

        for (Element container : raceContainers) {
            String checkText = container.text();
            if (!checkText.contains("Full Result")) {
                continue;
            }

            // 1. Parse the Placed Horses (Positions)
            List<Position> positionsList = new ArrayList<>();
            Elements horses = container.select("div.FastResultRide__RideRow-sc-bf4bc8c6-0");

            for (Element horse : horses) {
                Element posEl = horse.selectFirst("span.FastResultRide__RidePosition-sc-bf4bc8c6-1");
                Element numEl = horse.selectFirst("span.FastResultRide__RideNumber-sc-bf4bc8c6-2");
                Element nameEl = horse.selectFirst("span.FastResultRide__RideHorseName-sc-bf4bc8c6-3");
                Element oddsEl = horse.selectFirst("span.FastResultRide__RideOdds-sc-bf4bc8c6-4");

                String position = (posEl != null) ? posEl.text().trim() : "";
                String number = (numEl != null) ? numEl.text().trim() : "";
                String horseName = (nameEl != null) ? nameEl.text().trim() : "";
                String odds = (oddsEl != null) ? oddsEl.text().trim() : "";

                // Only add if we managed to parse at least a horse name
                if (!horseName.isEmpty()) {
                    positionsList.add(new Position(position, number, horseName, odds));
                }
            }

            // 2. FIXED: Parse Extra Metadata pairing the Label with its corresponding Value
            List<String> extraDetailsList = new ArrayList<>();
            Elements detailLabels = container.select("td.FastResultDetailItem__ItemLabel-sc-55408ced-0");

            for (Element labelEl : detailLabels) {
                String labelText = labelEl.text().trim();

                // Find the parent table row to get the matching value element cell next to it
                Element parentRow = labelEl.parent();
                String valueText = "";

                if (parentRow != null) {
                    Element valueEl = parentRow.selectFirst("td.FastResultDetailItem__ItemValue-sc-55408ced-1");
                    if (valueEl != null) {
                        valueText = valueEl.text().trim();
                    }
                }

                // Combine them cleanly if a value cell exists
                if (!labelText.isEmpty()) {
                    if (!valueText.isEmpty()) {
                        extraDetailsList.add(labelText + " " + valueText);
                    } else {
                        extraDetailsList.add(labelText);
                    }
                }
            }

            // Instantiate the bundled metadata block
            ResultDetails details = new ResultDetails(positionsList, extraDetailsList);

            // 3. Parse Header Time and Place info
            Elements placeAndTimes = container.select("span.FastResultCard__HeaderTimeCourseText-sc-4ab88fff-3");

            for (Element placeAndTime : placeAndTimes) {
                Element timeElement = placeAndTime.selectFirst("span.time-short");
                String time = (timeElement != null) ? timeElement.text().trim() : "";

                Element placeElementCopy = placeAndTime.clone();
                Element timeInCopy = placeElementCopy.selectFirst("span.time-short");
                if (timeInCopy != null) {
                    timeInCopy.remove();
                }
                String place = placeElementCopy.text().trim();

                if (!place.isEmpty() && !time.isEmpty()) {
                    results.add(new RaceResult(place, time, details));
                }
            }
        }
        return results;
    }
}
