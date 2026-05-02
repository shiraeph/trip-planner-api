package com.travel.travelplanner.trip.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.travel.travelplanner.trip.domain.SearchHistory;

public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {
    List<SearchHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
