package com.travel.travelplanner.trip.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.enums.BudgetLevel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TripPlanRepositoryCustomImpl implements TripPlanRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<TripPlan> searchByUserAndFilters(
            String userId,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            BudgetLevel budgetLevel,
            List<String> interests,
            List<String> constraints) {
        Criteria c = Criteria.where("userId").is(userId);

        if (destination != null && !destination.isBlank()) {
            c.and("destination").regex(".*" + escapeRegex(destination.trim()) + ".*", "i");
        }
        if (startDate != null) {
            c.and("startDate").is(startDate);
        }
        if (endDate != null) {
            c.and("endDate").is(endDate);
        }
        if (budgetLevel != null) {
            c.and("tripPreferences.budgetLevel").is(budgetLevel);
        }
        if (interests != null && !interests.isEmpty()) {
            c.and("tripPreferences.interests").in(interests);
        }
        if (constraints != null && !constraints.isEmpty()) {
            c.and("tripPreferences.constraints").in(constraints);
        }

        Query q = new Query(c).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(q, TripPlan.class);
    }

    private static String escapeRegex(String s) {
        return s.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }
}
