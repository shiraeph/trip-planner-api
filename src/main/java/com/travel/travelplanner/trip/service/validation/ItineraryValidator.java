package com.travel.travelplanner.trip.service.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;
import com.travel.travelplanner.trip.domain.enums.TimeBlock; 
import com.travel.travelplanner.trip.domain.enums.ItineraryItemType; 
import com.travel.travelplanner.trip.domain.itinerary.BlockPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.domain.itinerary.ItineraryItem;

@Component
public class ItineraryValidator {

    public List<String> validate(TripPlan tripPlan, Itinerary itinerary) {
        List<String> violations = new ArrayList<>();

        if (itinerary == null) {
            violations.add("Itinerary is null.");
            return violations;
        }
        if (itinerary.getDayPlans() == null || itinerary.getDayPlans().isEmpty()) {
            violations.add("Itinerary has no dayPlans.");
            return violations;
        }

        int expectedDays = TripDates.inclusiveDayCount(tripPlan);
        if (expectedDays > 0 && itinerary.getDayPlans().size() != expectedDays) {
            violations.add("Expected " + expectedDays + " dayPlans for trip dates "
                    + tripPlan.getStartDate() + " to " + tripPlan.getEndDate()
                    + " but got " + itinerary.getDayPlans().size() + ".");
        }

        boolean includeDirections = includeDirections(tripPlan);
        boolean avoidMuseums = avoidMuseums(tripPlan);

        for (int d = 0; d < itinerary.getDayPlans().size(); d++) {
            DayPlan day = itinerary.getDayPlans().get(d);

            if (day.getBlocks() == null || day.getBlocks().isEmpty()) {
                violations.add(msg(d, null, null, "Day has no blocks."));
                continue;
            }

            if (!hasMinimumTimeBlocks(day)) {
                violations.add(msg(d, null, null,
                        "Day must include at least two of MORNING / AFTERNOON / EVENING."));
            }

            for (int b = 0; b < day.getBlocks().size(); b++) {
                BlockPlan block = day.getBlocks().get(b);

                if (block.getItems() == null || block.getItems().isEmpty()) {
                    violations.add(msg(d, block.getTimeBlock(), null, "Block has no items."));
                    continue;
                }

                // AI often omits or merges stops; keep bounds loose enough to pass useful itineraries.
                int size = block.getItems().size();
                if (size < 1 || size > 6) {
                    violations.add(msg(d, block.getTimeBlock(), null,
                            "Block must have 1–6 items, got " + size + "."));
                }

                for (int i = 0; i < block.getItems().size(); i++) {
                    ItineraryItem item = block.getItems().get(i);

                    // rule: transit required when includeDirections=true
                    if (includeDirections) {
                        if (item.getTransit() == null) {
                            violations.add(msg(d, block.getTimeBlock(), i,
                                    "Missing transit (includeDirections=true). Item: " + safe(item.getName())));
                        } else {
                            if (item.getTransit().getMode() == null) {
                                violations.add(msg(d, block.getTimeBlock(), i,
                                        "Missing transit.mode. Item: " + safe(item.getName())));
                            }
                            if (isBlank(item.getTransit().getDirections())) {
                                violations.add(msg(d, block.getTimeBlock(), i,
                                        "Missing transit.directions. Item: " + safe(item.getName())));
                            }
                        }
                    }

                    // rule: avoid museums
                    if (avoidMuseums && looksLikeMuseum(item)) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "Constraint violated (avoid museums). Item: " + safe(item.getName())));
                    }

                    // rule: attraction notes must be detailed enough (description only)
                    if (isAttraction(item) && notesTooShort(item.getNotes())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "ATTRACTION notes too short (need more detail). Item: " + safe(item.getName())));
                    }
                    if (isAttraction(item) && isBlank(item.getOpeningHours())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "ATTRACTION missing openingHours field. Item: " + safe(item.getName())));
                    }
                    if (isAttraction(item) && isBlank(item.getPrice())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "ATTRACTION missing price field. Item: " + safe(item.getName())));
                    }
                    if (isFood(item) && foodNotesTooShort(item.getNotes())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "FOOD notes too short (need more detail). Item: " + safe(item.getName())));
                    }
                    if (isFood(item) && isBlank(item.getOpeningHours())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "FOOD missing openingHours field. Item: " + safe(item.getName())));
                    }
                    if (isFood(item) && isBlank(item.getAveragePricePerDish())) {
                        violations.add(msg(d, block.getTimeBlock(), i,
                                "FOOD missing averagePricePerDish field. Item: " + safe(item.getName())));
                    }
                }
            }
        }

        return violations;
    }

    private boolean includeDirections(TripPlan tripPlan) {
        if (tripPlan.getTripPreferences() == null) return false;
        Boolean v = tripPlan.getTripPreferences().getIncludeDirections();
        return Boolean.TRUE.equals(v);
    }

    private boolean avoidMuseums(TripPlan tripPlan) {
        if (tripPlan.getTripPreferences() == null) return false;
        List<String> constraints = tripPlan.getTripPreferences().getConstraints();
        if (constraints == null) return false;

        return constraints.stream()
                .filter(s -> s != null)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(s -> s.contains("avoid museums")
                        || s.contains("no museums"));
    }

    /** GPT often skips one slot on travel/arrival days; requiring all three caused frequent FAILED trips. */
    private boolean hasMinimumTimeBlocks(DayPlan day) {
        Set<TimeBlock> blocks = new HashSet<>();
        for (BlockPlan b : day.getBlocks()) {
            if (b != null && b.getTimeBlock() != null) blocks.add(b.getTimeBlock());
        }
        int n = 0;
        if (blocks.contains(TimeBlock.MORNING)) n++;
        if (blocks.contains(TimeBlock.AFTERNOON)) n++;
        if (blocks.contains(TimeBlock.EVENING)) n++;
        return n >= 2;
    }

    private boolean looksLikeMuseum(ItineraryItem item) {
        String name = lower(item.getName());
        String notes = lower(item.getNotes());
        String loc = (item.getLocation() != null) ? lower(item.getLocation().getName()) : "";

        return containsAny(name, notes, loc,
                "museum", "art gallery", "exhibition", "art museum", "national gallery",
                "british museum", "tate modern", "tate britain");
    }

    private boolean isAttraction(ItineraryItem item) {
        return item.getType() == ItineraryItemType.ATTRACTION;
    }

    private boolean isFood(ItineraryItem item) {
        return item.getType() == ItineraryItemType.FOOD;
    }

    /** Description-only notes; hours/price live in separate fields. */
    private static final int MIN_ATTRACTION_NOTES_LEN = 80;
    private static final int MIN_FOOD_NOTES_LEN = 60;

    private boolean notesTooShort(String notes) {
        if (notes == null) return true;
        String t = notes.trim();
        return t.length() < MIN_ATTRACTION_NOTES_LEN;
    }

    private boolean foodNotesTooShort(String notes) {
        if (notes == null) return true;
        return notes.trim().length() < MIN_FOOD_NOTES_LEN;
    }


    private boolean containsAny(String t1, String t2, String t3, String... needles) {
        for (String n : needles) {
            if (t1.contains(n) || t2.contains(n) || t3.contains(n)) return true;
        }
        return false;
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String safe(String s) {
        return s == null ? "unknown" : s;
    }

    private String msg(int dayIndex, TimeBlock block, Integer itemIndex, String message) {
        String base = "Day " + (dayIndex + 1);
        if (block != null) base += " " + block.name();
        if (itemIndex != null) base += " item #" + (itemIndex + 1);
        return base + ": " + message;
    }
}
