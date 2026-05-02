package com.travel.travelplanner.trip.api;

import java.util.List;

import com.travel.travelplanner.trip.api.enums.TimeBLock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockPlan {
    private TimeBLock timeBlock;
    private List<ItineraryItem> items;
}
