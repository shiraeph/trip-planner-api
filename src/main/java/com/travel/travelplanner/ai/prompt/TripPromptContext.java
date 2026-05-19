package com.travel.travelplanner.ai.prompt;

import java.util.stream.Collectors;

import com.travel.travelplanner.trip.domain.TripPlan;

/** Extracted trip fields used to build GPT prompts. */
record TripPromptContext(
        String destination,
        String start,
        String end,
        int tripDays,
        String dateList,
        String groupSummary,
        String travelStyle,
        String budget,
        String interests,
        String constraints,
        String hotelName,
        String hotelAddress,
        String transportPref,
        boolean includeDirections,
        String freeText) {

    static TripPromptContext from(TripPlan tripPlan, String dateList, int tripDays) {
        String travelStyle = prefEnum(tripPlan, "travelStyle", "BALANCED");
        String budget = prefEnum(tripPlan, "budgetLevel", "MEDIUM");
        String group = tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getComposition() != null
                ? tripPlan.getTripGroup().getComposition().name()
                : "SOLO";
        String peopleCount = tripPlan.getTripGroup() != null
                ? String.valueOf(tripPlan.getTripGroup().getPeopleCount())
                : "1";
        String minAge = tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getMinAge() != null
                ? String.valueOf(tripPlan.getTripGroup().getMinAge())
                : "unknown";
        String maxAge = tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getMaxAge() != null
                ? String.valueOf(tripPlan.getTripGroup().getMaxAge())
                : "unknown";
        String genderMix = tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getGenderMix() != null
                ? tripPlan.getTripGroup().getGenderMix().name()
                : "UNKNOWN";
        String groupSummary = group + ", " + peopleCount + " people, ages " + minAge + "-" + maxAge
                + ", gender mix " + genderMix;

        String interests = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getInterests() != null
                && !tripPlan.getTripPreferences().getInterests().isEmpty()
                        ? String.join(", ", tripPlan.getTripPreferences().getInterests())
                        : "none";

        String constraints = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getConstraints() != null
                && !tripPlan.getTripPreferences().getConstraints().isEmpty()
                        ? tripPlan.getTripPreferences().getConstraints().stream().collect(Collectors.joining("; "))
                        : "none";

        String hotelName = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelName() != null
                        ? tripPlan.getTripPreferences().getHotelName()
                        : "not provided";

        String hotelAddress = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelAddressOrArea() != null
                        ? tripPlan.getTripPreferences().getHotelAddressOrArea()
                        : "not provided";

        String transportPref = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getTransportPreferences() != null
                        ? tripPlan.getTripPreferences().getTransportPreferences().name()
                        : "not provided";

        boolean includeDirections = tripPlan.getTripPreferences() != null
                && Boolean.TRUE.equals(tripPlan.getTripPreferences().getIncludeDirections());

        String freeText = tripPlan.getTripPreferences() != null && tripPlan.getTripPreferences().getFreeText() != null
                ? tripPlan.getTripPreferences().getFreeText()
                : "not provided";

        return new TripPromptContext(
                tripPlan.getDestination(),
                String.valueOf(tripPlan.getStartDate()),
                String.valueOf(tripPlan.getEndDate()),
                tripDays,
                dateList,
                groupSummary,
                travelStyle,
                budget,
                interests,
                constraints,
                hotelName,
                hotelAddress,
                transportPref,
                includeDirections,
                freeText);
    }

    private static String prefEnum(TripPlan tripPlan, String field, String fallback) {
        if (tripPlan.getTripPreferences() == null) {
            return fallback;
        }
        return switch (field) {
            case "travelStyle" -> tripPlan.getTripPreferences().getTravelStyle() != null
                    ? tripPlan.getTripPreferences().getTravelStyle().name()
                    : fallback;
            case "budgetLevel" -> tripPlan.getTripPreferences().getBudgetLevel() != null
                    ? tripPlan.getTripPreferences().getBudgetLevel().name()
                    : fallback;
            default -> fallback;
        };
    }
}
