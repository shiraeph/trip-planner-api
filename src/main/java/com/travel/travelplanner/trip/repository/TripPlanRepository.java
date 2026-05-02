package com.travel.travelplanner.trip.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.enums.TripStatus;

public interface TripPlanRepository extends MongoRepository<TripPlan, String>, TripPlanRepositoryCustom {
    List<TripPlan> findByUserIdOrderByCreatedAtDesc(String userId);

    List<TripPlan> findByUserIdAndTripStatusNotOrderByCreatedAtDesc(String userId, TripStatus tripStatus);

    Optional<TripPlan> findByUserIdAndId(String userId, String id);
}
