package com.travel.travelplanner.game.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.travelplanner.game.api.GameBestScoresResponse;
import com.travel.travelplanner.game.api.SubmitScoreRequest;
import com.travel.travelplanner.game.service.GameScoreService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/game/scores")
@RequiredArgsConstructor
public class GameScoreController {
    private final GameScoreService service;

    @GetMapping("/best")
    public GameBestScoresResponse best() {
        return service.getBestScores();
    }

    @PostMapping("/submit")
    public GameBestScoresResponse submit(@RequestBody(required = false) SubmitScoreRequest req) {
        int score = req != null && req.getScore() != null ? req.getScore() : 0;
        return service.submitScore(score);
    }
}

