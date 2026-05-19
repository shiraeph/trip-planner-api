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
import com.travel.travelplanner.trip.domain.itinerary.BlockPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.domain.itinerary.ItineraryItem;

/**
 * Repairs common GPT shape issues before validation (e.g. only one time block on the last day).
 */
@Component
public class ItineraryNormalizer {

    private static final TimeBlock[] ORDER = {
            TimeBlock.MORNING, TimeBlock.AFTERNOON, TimeBlock.EVENING
    };

    public void normalize(TripPlan tripPlan, Itinerary itinerary, boolean hebrew) {
        if (itinerary == null || itinerary.getDayPlans() == null) {
            return;
        }
        String destination = tripPlan != null && tripPlan.getDestination() != null
                ? tripPlan.getDestination()
                : (hebrew ? "היעד" : "your destination");

        ensureFullTripLength(tripPlan, itinerary, destination, hebrew);

        for (DayPlan day : itinerary.getDayPlans()) {
            if (day == null) {
                continue;
            }
            normalizeDay(day, destination, hebrew);
        }
    }

    /**
     * GPT often returns only the first 1-2 days when the completion is truncated. Rebuild the list
     * so every calendar day from the trip exists (placeholder days when the model skipped them).
     */
    private void ensureFullTripLength(TripPlan tripPlan, Itinerary itinerary, String destination, boolean hebrew) {
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
                day = placeholderDay(date, i + 1, destination, hebrew);
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

    private static DayPlan placeholderDay(LocalDate date, int dayNumber, String destination, boolean hebrew) {
        DayPlan day = new DayPlan();
        day.setDate(date);
        day.setTitle(defaultDayTitle(dayNumber, destination, hebrew));
        List<BlockPlan> blocks = new ArrayList<>();
        for (TimeBlock tb : ORDER) {
            blocks.add(fillerBlock(tb, destination, hebrew));
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

    private void normalizeDay(DayPlan day, String destination, boolean hebrew) {
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
                blocks.add(fillerBlock(required, destination, hebrew));
                used.add(required);
            }
        }

        blocks.sort((a, b) -> Integer.compare(indexOf(a.getTimeBlock()), indexOf(b.getTimeBlock())));
        day.setBlocks(blocks);
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

    private static BlockPlan fillerBlock(TimeBlock timeBlock, String destination, boolean hebrew) {
        BlockPlan block = new BlockPlan();
        block.setTimeBlock(timeBlock);

        ItineraryItem item = new ItineraryItem();
        item.setType(ItineraryItemType.NOTE);
        if (hebrew) {
            item.setName(switch (timeBlock) {
                case MORNING -> "בוקר גמיש";
                case AFTERNOON -> "אחר הצהריים גמיש";
                case EVENING -> "ערב גמיש";
            });
            item.setNotes("זמן פנוי לנוחות, קניות קלות או לגלות עוד corners ב" + destination
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
        block.setItems(new ArrayList<>(List.of(item)));
        return block;
    }
}
