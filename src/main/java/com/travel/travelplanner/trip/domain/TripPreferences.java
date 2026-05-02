package com.travel.travelplanner.trip.domain;

import java.util.List;

import com.travel.travelplanner.trip.domain.enums.BudgetLevel;
import com.travel.travelplanner.trip.domain.enums.TransportPreferences;
import com.travel.travelplanner.trip.domain.enums.TravelStyle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripPreferences {
    private TravelStyle travelStyle;
    private BudgetLevel budgetLevel;
    private List<String> interests;
    private List<String> constraints;
    private String hotelName;
    private String hotelAddressOrArea;
    private TransportPreferences transportPreferences;
    private Boolean includeDirections;
    private String freeText;
}
