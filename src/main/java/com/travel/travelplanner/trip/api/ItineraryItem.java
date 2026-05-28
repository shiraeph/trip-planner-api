package com.travel.travelplanner.trip.api;

import com.travel.travelplanner.trip.api.enums.ItineraryItemType;

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
    private String openingHours;
    private String price;
    private String averagePricePerDish;
    private TransitInfo transit;
}
