package com.travel.travelplanner.ai.prompt;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps stored interest keys to human-readable labels for GPT prompts. */
final class InterestLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("food", "Food"),
            Map.entry("history", "History"),
            Map.entry("museums", "Museums"),
            Map.entry("architecture", "Architecture"),
            Map.entry("shopping", "Shopping"),
            Map.entry("nature", "Nature"),
            Map.entry("nightlife", "Nightlife"),
            Map.entry("beaches", "Beaches"),
            Map.entry("art", "Art"),
            Map.entry("localMarkets", "Local markets"),
            Map.entry("coffee", "Coffee"),
            Map.entry("photography", "Photography"),
            Map.entry("music", "Music"),
            Map.entry("sports", "Sports"),
            Map.entry("hiking", "Hiking (legacy — treat as moderate trekking)"),
            Map.entry("trekkingEasy", "Easy Hiking & Trekking"),
            Map.entry("trekkingModerate", "Moderate Hiking & Trekking"),
            Map.entry("trekkingDifficult", "Difficult Hiking & Trekking"),
            Map.entry("wellness", "Wellness"),
            Map.entry("technology", "Technology"),
            Map.entry("dayTrips", "Day trips"));

    private InterestLabels() {
    }

    static String format(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return "none";
        }
        return interests.stream()
                .map(i -> LABELS.getOrDefault(i, humanizeKey(i)))
                .collect(Collectors.joining(", "));
    }

    private static String humanizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        return key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }
}
