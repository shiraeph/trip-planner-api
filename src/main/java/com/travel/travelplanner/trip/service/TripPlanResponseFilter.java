package com.travel.travelplanner.trip.service;

import com.travel.travelplanner.trip.api.BlockPlan;
import com.travel.travelplanner.trip.api.DayPlan;
import com.travel.travelplanner.trip.api.Itinerary;
import com.travel.travelplanner.trip.api.ItineraryItem;
import com.travel.travelplanner.trip.api.TripPlanResponse;
import com.travel.travelplanner.trip.api.enums.TripStatus;

/**
 * Shrinks trip GET responses while generation is in progress so polling stays fast
 * and clients are less likely to abort mid-stream (broken pipe).
 */
public final class TripPlanResponseFilter {

    private TripPlanResponseFilter() {
    }

    /**
     * @param view optional; {@code progress} omits itinerary entirely (status + generationProgress only)
     */
    public static void applyGeneratingView(TripPlanResponse response, String view) {
        if (response == null || response.getTripStatus() != TripStatus.GENERATING) {
            return;
        }
        if ("progress".equalsIgnoreCase(view)) {
            response.setItinerary(null);
            return;
        }
        stripHeavyItineraryFields(response.getItinerary());
    }

    private static void stripHeavyItineraryFields(Itinerary itinerary) {
        if (itinerary == null || itinerary.getDayPlans() == null) {
            return;
        }
        for (DayPlan day : itinerary.getDayPlans()) {
            if (day == null || day.getBlocks() == null) {
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
                    item.setNotes(null);
                    if (item.getTransit() != null) {
                        item.getTransit().setDirections(null);
                    }
                }
            }
        }
    }
}
