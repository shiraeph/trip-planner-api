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
    /** Typical opening hours for the venue (e.g. "09:00 - 18:00"). */
    private String openingHours;
    /** Ticket or entry price for attractions (e.g. "€15-20 per person"). */
    private String price;
    /** Average price per dish for restaurants (e.g. "€12-18"). */
    private String averagePricePerDish;
    private String timeHint;
    private Integer durationMinutes;
    private TransitInfo transit;
}
