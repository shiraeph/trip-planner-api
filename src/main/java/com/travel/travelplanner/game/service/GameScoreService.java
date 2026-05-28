package com.travel.travelplanner.game.service;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.travel.travelplanner.config.CurrentUserProvider;
import com.travel.travelplanner.game.api.GameBestScoresResponse;
import com.travel.travelplanner.game.domain.GameScore;
import com.travel.travelplanner.game.repository.GameScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameScoreService {
    private final GameScoreRepository repo;
    private final CurrentUserProvider currentUserProvider;

    private String requireUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
        }
        return userId;
    }

    public GameBestScoresResponse getBestScores() {
        String userId = requireUserId();
        Integer myBest = repo.findByUserId(userId).map(GameScore::getBestScore).orElse(null);
        Integer globalBest = repo.findTopByOrderByBestScoreDesc().map(GameScore::getBestScore).orElse(null);
        return new GameBestScoresResponse(myBest, globalBest);
    }

    public GameBestScoresResponse submitScore(int score) {
        if (score < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be >= 0");
        }
        String userId = requireUserId();

        GameScore gs = repo.findByUserId(userId).orElseGet(() -> GameScore.builder()
                .userId(userId)
                .bestScore(null)
                .createdAt(Instant.now())
                .build());

        Integer current = gs.getBestScore();
        if (current == null || score > current) {
            gs.setBestScore(score);
        }
        repo.save(gs);

        Integer globalBest = repo.findTopByOrderByBestScoreDesc().map(GameScore::getBestScore).orElse(null);
        return new GameBestScoresResponse(gs.getBestScore(), globalBest);
    }
}

