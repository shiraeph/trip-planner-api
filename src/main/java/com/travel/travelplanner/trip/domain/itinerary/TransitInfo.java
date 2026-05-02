package com.travel.travelplanner.trip.domain.itinerary;

import com.travel.travelplanner.trip.domain.enums.TransitMode;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransitInfo {

    @Size(max = 80)
    private String from;

    @Size(max = 20)
    private TransitMode mode;

    private Integer estimatedMinutes;

    @Size(max = 300)
    private String directions;
}
