package com.travel.travelplanner.trip.domain.itinerary;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DayPlan {
    private LocalDate date;
    private String title;
    private List<BlockPlan> blocks;
}
