package com.travel.travelplanner.trip.service.validation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;
import com.travel.travelplanner.trip.domain.enums.ItineraryItemType;
import com.travel.travelplanner.trip.domain.enums.TimeBlock;
import com.travel.travelplanner.trip.domain.enums.TransitMode;
import com.travel.travelplanner.trip.domain.itinerary.BlockPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.domain.itinerary.ItineraryItem;
import com.travel.travelplanner.trip.domain.itinerary.TransitInfo;

/**
 * Repairs common GPT shape issues before validation (e.g. only one time block on the last day).
 */
@Component
public class ItineraryNormalizer {

    private static final TimeBlock[] ORDER = {
            TimeBlock.MORNING, TimeBlock.AFTERNOON, TimeBlock.EVENING
    };

    /** Must match {@link ItineraryValidator} minimum for ATTRACTION description notes. */
    private static final int MIN_ATTRACTION_NOTES_LEN = 80;

    public void normalize(TripPlan tripPlan, Itinerary itinerary, boolean hebrew) {
        if (itinerary == null || itinerary.getDayPlans() == null) {
            return;
        }
        String destination = tripPlan != null && tripPlan.getDestination() != null
                ? tripPlan.getDestination()
                : (hebrew ? "היעד" : "your destination");

        boolean includeDirections = tripPlan != null
                && tripPlan.getTripPreferences() != null
                && Boolean.TRUE.equals(tripPlan.getTripPreferences().getIncludeDirections());

        ensureFullTripLength(tripPlan, itinerary, destination, hebrew, includeDirections);

        for (DayPlan day : itinerary.getDayPlans()) {
            if (day == null) {
                continue;
            }
            normalizeDay(day, destination, hebrew, includeDirections);
        }
    }

    /**
     * GPT often returns only the first 1-2 days when the completion is truncated. Rebuild the list
     * so every calendar day from the trip exists (placeholder days when the model skipped them).
     */
    private void ensureFullTripLength(
            TripPlan tripPlan, Itinerary itinerary, String destination, boolean hebrew, boolean includeDirections) {
        List<LocalDate> expectedDates = TripDates.eachDay(tripPlan);
        if (expectedDates.isEmpty()) {
            return;
        }

        List<DayPlan> fromAi = itinerary.getDayPlans() != null
                ? new ArrayList<>(itinerary.getDayPlans())
                : new ArrayList<>();

        Map<LocalDate, DayPlan> byDate = new HashMap<>();
        for (DayPlan day : fromAi) {
            if (day != null && day.getDate() != null) {
                byDate.putIfAbsent(day.getDate(), day);
            }
        }

        List<DayPlan> rebuilt = new ArrayList<>();
        for (int i = 0; i < expectedDates.size(); i++) {
            LocalDate date = expectedDates.get(i);
            DayPlan day = byDate.get(date);
            if (day == null && i < fromAi.size()) {
                day = fromAi.get(i);
            }
            if (day == null) {
                day = placeholderDay(date, i + 1, destination, hebrew, includeDirections);
            } else {
                day.setDate(date);
                if (day.getTitle() == null || day.getTitle().isBlank()) {
                    day.setTitle(defaultDayTitle(i + 1, destination, hebrew));
                }
            }
            rebuilt.add(day);
        }
        itinerary.setDayPlans(rebuilt);
    }

    private static DayPlan placeholderDay(
            LocalDate date, int dayNumber, String destination, boolean hebrew, boolean includeDirections) {
        DayPlan day = new DayPlan();
        day.setDate(date);
        day.setTitle(defaultDayTitle(dayNumber, destination, hebrew));
        List<BlockPlan> blocks = new ArrayList<>();
        for (TimeBlock tb : ORDER) {
            blocks.add(fillerBlock(tb, destination, hebrew, includeDirections));
        }
        day.setBlocks(blocks);
        return day;
    }

    private static String defaultDayTitle(int dayNumber, String destination, boolean hebrew) {
        if (hebrew) {
            return "יום " + dayNumber + " ב" + destination;
        }
        return "Day " + dayNumber + " in " + destination;
    }

    private void normalizeDay(DayPlan day, String destination, boolean hebrew, boolean includeDirections) {
        List<BlockPlan> blocks = day.getBlocks();
        if (blocks == null) {
            blocks = new ArrayList<>();
            day.setBlocks(blocks);
        }

        // Drop null entries; fix missing timeBlock labels
        List<BlockPlan> cleaned = new ArrayList<>();
        int slot = 0;
        for (BlockPlan block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.getTimeBlock() == null && slot < ORDER.length) {
                block.setTimeBlock(ORDER[slot]);
            }
            if (block.getItems() == null) {
                block.setItems(new ArrayList<>());
            }
            block.getItems().removeIf(item -> item == null);
            if (block.getItems().isEmpty()) {
                block.getItems().add(fillerItem(block.getTimeBlock(), destination, hebrew, includeDirections));
            }
            cleaned.add(block);
            slot++;
        }
        blocks = cleaned;
        day.setBlocks(blocks);

        // Reassign duplicate timeBlock values to the next free slot
        Set<TimeBlock> used = EnumSet.noneOf(TimeBlock.class);
        for (BlockPlan block : blocks) {
            TimeBlock tb = block.getTimeBlock();
            if (tb == null || !used.add(tb)) {
                TimeBlock replacement = firstMissing(used);
                if (replacement != null) {
                    block.setTimeBlock(replacement);
                    used.add(replacement);
                }
            }
        }

        // Ensure at least MORNING + AFTERNOON (validator minimum); prefer all three
        used = EnumSet.noneOf(TimeBlock.class);
        for (BlockPlan block : blocks) {
            if (block.getTimeBlock() != null) {
                used.add(block.getTimeBlock());
            }
        }

        for (TimeBlock required : ORDER) {
            if (!used.contains(required)) {
                blocks.add(fillerBlock(required, destination, hebrew, includeDirections));
                used.add(required);
            }
        }

        for (BlockPlan block : blocks) {
            if (block.getItems() == null || block.getItems().isEmpty()) {
                TimeBlock tb = block.getTimeBlock() != null ? block.getTimeBlock() : TimeBlock.MORNING;
                block.setItems(new ArrayList<>(List.of(fillerItem(tb, destination, hebrew, includeDirections))));
            }
        }

        blocks.sort((a, b) -> Integer.compare(indexOf(a.getTimeBlock()), indexOf(b.getTimeBlock())));
        for (BlockPlan block : blocks) {
            if (block.getItems() == null) {
                continue;
            }
            for (ItineraryItem item : block.getItems()) {
                normalizeItem(item, destination, hebrew);
            }
        }
        day.setBlocks(blocks);
    }

    private void normalizeItem(ItineraryItem item, String destination, boolean hebrew) {
        if (item == null) {
            return;
        }
        if (item.getName() == null || item.getName().isBlank()) {
            item.setName(hebrew ? "פעילות ב" + destination : "Activity in " + destination);
        }
        if (item.getType() == ItineraryItemType.ATTRACTION) {
            item.setNotes(ensureAttractionNotes(item.getNotes(), item.getName(), destination, hebrew));
        }
    }

    private static String ensureAttractionNotes(String notes, String name, String destination, boolean hebrew) {
        String base = notes != null ? notes.trim() : "";
        if (base.length() >= MIN_ATTRACTION_NOTES_LEN) {
            return base;
        }
        String tip = hebrew
                ? " מומלץ להקדיש כשעה–שעתיים; בדקו שעות פתיחה וכרטיסים מראש."
                : " Allow 1–2 hours; check opening hours and book tickets ahead if needed.";
        if (base.isEmpty()) {
            String label = (name != null && !name.isBlank()) ? name.trim() : destination;
            return (hebrew ? "ביקור ב" + label + "." : "Visit " + label + ".") + tip;
        }
        return base + tip;
    }

    private static TimeBlock firstMissing(Set<TimeBlock> used) {
        for (TimeBlock tb : ORDER) {
            if (!used.contains(tb)) {
                return tb;
            }
        }
        return null;
    }

    private static int indexOf(TimeBlock tb) {
        if (tb == null) {
            return ORDER.length;
        }
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == tb) {
                return i;
            }
        }
        return ORDER.length;
    }

    private static BlockPlan fillerBlock(TimeBlock timeBlock, String destination, boolean hebrew, boolean includeDirections) {
        BlockPlan block = new BlockPlan();
        block.setTimeBlock(timeBlock);
        block.setItems(new ArrayList<>(List.of(fillerItem(timeBlock, destination, hebrew, includeDirections))));
        return block;
    }

    private static ItineraryItem fillerItem(TimeBlock timeBlock, String destination, boolean hebrew, boolean includeDirections) {
        ItineraryItem item = new ItineraryItem();
        item.setType(ItineraryItemType.NOTE);
        if (hebrew) {
            item.setName(switch (timeBlock) {
                case MORNING -> "בוקר גמיש";
                case AFTERNOON -> "אחר הצהריים גמיש";
                case EVENING -> "ערב גמיש";
            });
            item.setNotes("זמן פנוי לנוחות, קניות קלות או לגלות עוד פינות ב" + destination
                    + ". התאימו לפי האנרגיה שלכם.");
        } else {
            item.setName(switch (timeBlock) {
                case MORNING -> "Flexible morning";
                case AFTERNOON -> "Flexible afternoon";
                case EVENING -> "Flexible evening";
            });
            item.setNotes("Open time to rest, grab a casual bite, or explore more of " + destination
                    + " at your own pace.");
        }
        if (includeDirections) {
            item.setTransit(fillerTransit(hebrew));
        }
        return item;
    }

    private static TransitInfo fillerTransit(boolean hebrew) {
        TransitInfo transit = new TransitInfo();
        transit.setFrom(hebrew ? "מלון / מרכז העיר" : "Hotel / city center");
        transit.setMode(TransitMode.WALK);
        transit.setEstimatedMinutes(10);
        transit.setDirections(hebrew
                ? "התניידות קצרה ברגל לפי מיקום הלינה — התאימו את המסלול במפה."
                : "Short walk from your stay; adjust the route on a map as needed.");
        return transit;
    }
}
