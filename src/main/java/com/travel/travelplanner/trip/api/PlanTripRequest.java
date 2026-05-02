package com.travel.travelplanner.trip.api;

import java.time.LocalDate;
import java.util.List;

import com.travel.travelplanner.trip.api.enums.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlanTripRequest {
    private String userId;
    private String destination;
    private DisplayLanguage displayLanguage;
    private LocalDate startDate;
    private LocalDate endDate;
    private TravelStyle travelStyle;
    private BudgetLevel budgetLevel;
    private List<String> interests;
    private List<String> constraints;
    private TripGroup tripGroup;
    private String  hotelName;
    private String hotelAddressOrArea;
    private Boolean includeDirections;
    private TransportPreferences transportPreferences;
    private String freeText;

}
