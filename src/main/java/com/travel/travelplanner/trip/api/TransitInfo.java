package com.travel.travelplanner.trip.api;

import com.travel.travelplanner.trip.api.enums.TransitMode;

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

    private TransitMode mode;

    private Integer estimatedMinutes;

    @Size(max = 300)
    private String directions;
}
