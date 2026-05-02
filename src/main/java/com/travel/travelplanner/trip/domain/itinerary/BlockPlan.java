package com.travel.travelplanner.trip.domain.itinerary;

import java.util.List;

import com.travel.travelplanner.trip.domain.enums.TimeBlock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockPlan {
    private TimeBlock timeBlock;
    private List<ItineraryItem> items;
}
