package com.travel.travelplanner.trip.domain;



import com.travel.travelplanner.trip.domain.enums.GroupComposition;
import com.travel.travelplanner.trip.domain.enums.GenderMix;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TripGroup {
    GroupComposition composition;
    int peopleCount;
    Integer minAge;
    Integer maxAge;
    GenderMix GenderMix;
}
