package com.travel.travelplanner.ai.prompt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.travel.travelplanner.trip.domain.enums.ItineraryItemType;
import com.travel.travelplanner.trip.domain.itinerary.BlockPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.domain.itinerary.ItineraryItem;

/**
 * Builds a rich handoff between chunked API calls so later segments avoid repetition and stay specific.
 */
public final class ChunkContinuityBuilder {

    private static final int MAX_VENUES_LISTED = 24;
    private static final int MAX_AREAS_LISTED = 12;

    private ChunkContinuityBuilder() {
    }

    public static String build(Itinerary en) {
        if (en == null || en.getDayPlans() == null || en.getDayPlans().isEmpty()) {
            return null;
        }

        List<DayPlan> days = en.getDayPlans();
        DayPlan lastDay = days.get(days.size() - 1);

        Set<String> venues = new LinkedHashSet<>();
        Set<String> areas = new LinkedHashSet<>();
        List<String> dayThemes = new ArrayList<>();

        for (DayPlan day : days) {
            if (day == null) {
                continue;
            }
            if (day.getTitle() != null && !day.getTitle().isBlank()) {
                dayThemes.add(day.getDate() + ": " + day.getTitle().trim());
            }
            if (day.getBlocks() == null) {
                continue;
            }
            for (BlockPlan block : day.getBlocks()) {
                if (block == null || block.getItems() == null) {
                    continue;
                }
                for (ItineraryItem item : block.getItems()) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getName() != null && !item.getName().isBlank()
                            && (item.getType() == ItineraryItemType.ATTRACTION
                                    || item.getType() == ItineraryItemType.FOOD)) {
                        venues.add(item.getName().trim());
                    }
                    if (item.getLocation() != null && item.getLocation().getName() != null
                            && !item.getLocation().getName().isBlank()) {
                        areas.add(item.getLocation().getName().trim());
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PREVIOUS SEGMENT SUMMARY (mandatory — read before planning this chunk):\n");
        if (lastDay.getDate() != null) {
            sb.append("- Last planned date: ").append(lastDay.getDate());
            if (lastDay.getTitle() != null) {
                sb.append(" (").append(lastDay.getTitle()).append(")");
            }
            sb.append("\n");
        }
        if (!dayThemes.isEmpty()) {
            sb.append("- Recent day themes: ")
                    .append(String.join(" | ", dayThemes.subList(Math.max(0, dayThemes.size() - 4), dayThemes.size())))
                    .append("\n");
        }
        if (!venues.isEmpty()) {
            sb.append("- Venues already used (do NOT repeat): ")
                    .append(limitJoin(venues, MAX_VENUES_LISTED))
                    .append("\n");
        }
        if (!areas.isEmpty()) {
            sb.append("- Areas/neighborhoods already covered (prefer NEW districts): ")
                    .append(limitJoin(areas, MAX_AREAS_LISTED))
                    .append("\n");
        }
        sb.append("- This chunk must introduce fresh neighborhoods, different cuisines, and new named venues.\n");
        sb.append("- Match the same depth and specificity as the first days of the trip — no generic filler.\n");
        return sb.toString();
    }

    private static String limitJoin(Set<String> values, int max) {
        List<String> list = new ArrayList<>(values);
        if (list.size() <= max) {
            return String.join(", ", list);
        }
        return String.join(", ", list.subList(0, max)) + ", …";
    }
}
