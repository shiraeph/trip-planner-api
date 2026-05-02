package com.travel.travelplanner.ai.dto;

import com.travel.travelplanner.trip.domain.itinerary.Itinerary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BilingualItinerary {
    private Itinerary en;
    private Itinerary he;
}
