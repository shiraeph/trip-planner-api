package com.travel.travelplanner.trip.api;

import java.time.Instant;
import java.time.LocalDate;

import com.travel.travelplanner.trip.api.enums.BudgetLevel;
import com.travel.travelplanner.trip.api.enums.DisplayLanguage;
import com.travel.travelplanner.trip.api.enums.TravelStyle;
import com.travel.travelplanner.trip.api.enums.TripStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TripPlanResponse {
    private String id;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripGroup tripGroup;
    private TravelStyle travelStyle;
    private BudgetLevel budgetLevel;
    private Itinerary itinerary;
    private DisplayLanguage displayLanguage;
    private TripStatus tripStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
