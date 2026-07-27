package uk.co.pluckier.discordbot.model;

import java.util.List;

public record ResultDetails(List<Position> positions, List<String> details) {
};
