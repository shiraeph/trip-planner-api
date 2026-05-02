package com.travel.travelplanner.trip.service.generator;

import java.util.List;

import com.travel.travelplanner.ai.dto.BilingualItinerary;
import com.travel.travelplanner.trip.domain.TripPlan;

public interface TripItineraryGenerator {
    BilingualItinerary generate(TripPlan tripPlan);
    BilingualItinerary generateFix(TripPlan tripPlan, List<String> violations);
}
