package com.travel.travelplanner.game.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.travel.travelplanner.game.domain.GameScore;

public interface GameScoreRepository extends MongoRepository<GameScore, String> {
    Optional<GameScore> findByUserId(String userId);

    Optional<GameScore> findTopByOrderByBestScoreDesc();
}

