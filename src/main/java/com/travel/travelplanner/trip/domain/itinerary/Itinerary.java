package com.travel.travelplanner.trip.domain.itinerary;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Itinerary {
    private String summary;
    private List<String> tips;
    private List<DayPlan> dayPlans;
}
