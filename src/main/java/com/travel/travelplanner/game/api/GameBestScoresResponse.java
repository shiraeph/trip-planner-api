package com.travel.travelplanner.game.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameBestScoresResponse {
    private Integer myBest;
    private Integer globalBest;
}

