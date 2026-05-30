package com.travel.travelplanner.trip.domain;

import com.travel.travelplanner.trip.domain.enums.GenerationStage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationProgress {
    private GenerationStage stage;
    private int chunksCompleted;
    private int totalChunks;
    private int daysCompleted;
    private int totalDays;
}
