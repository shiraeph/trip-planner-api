package com.travel.travelplanner.trip.api;

import com.travel.travelplanner.trip.api.enums.GenerationStage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GenerationProgressResponse {
    private GenerationStage stage;
    private int chunksCompleted;
    private int totalChunks;
    private int daysCompleted;
    private int totalDays;
}
