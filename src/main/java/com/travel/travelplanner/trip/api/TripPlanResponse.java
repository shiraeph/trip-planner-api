package com.travel.travelplanner.trip.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.travel.travelplanner.trip.api.enums.BudgetLevel;
import com.travel.travelplanner.trip.api.enums.DisplayLanguage;
import com.travel.travelplanner.trip.api.enums.TransportPreferences;
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
    private List<String> interests;
    private List<String> constraints;
    private String hotelName;
    private String hotelAddressOrArea;
    private Boolean includeDirections;
    private TransportPreferences transportPreferences;
    private String freeText;
    private Itinerary itinerary;
    private DisplayLanguage displayLanguage;
    private TripStatus tripStatus;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
