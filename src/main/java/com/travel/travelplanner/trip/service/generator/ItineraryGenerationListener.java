package com.travel.travelplanner.trip.service.generator;

import com.travel.travelplanner.trip.domain.GenerationProgress;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;

/**
 * Called during generation so partial itineraries and progress can be persisted for polling clients.
 */
@FunctionalInterface
public interface ItineraryGenerationListener {

    /**
     * @param partialHe null while only the English pass for the current chunk has finished
     */
    void onProgress(GenerationProgress progress, Itinerary partialEn, Itinerary partialHe);
}
