package com.travel.travelplanner.trip.api;

import com.travel.travelplanner.trip.api.enums.GenderMix;
import com.travel.travelplanner.trip.api.enums.GroupComposition;

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
    GenderMix genderMix;
}
