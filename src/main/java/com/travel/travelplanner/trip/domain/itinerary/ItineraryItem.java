package com.travel.travelplanner.trip.domain.itinerary;

import com.travel.travelplanner.trip.domain.enums.ItineraryItemType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryItem {
    private ItineraryItemType type;
    private String name;
    private Location location;
    private String notes;
    private String timeHint;
    private Integer durationMinutes;
    private TransitInfo transit;
}
