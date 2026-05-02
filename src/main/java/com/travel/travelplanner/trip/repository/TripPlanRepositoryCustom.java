package com.travel.travelplanner.trip.repository;

import java.time.LocalDate;
import java.util.List;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.enums.BudgetLevel;

public interface TripPlanRepositoryCustom {
    List<TripPlan> searchByUserAndFilters(
            String userId,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            BudgetLevel budgetLevel,
            List<String> interests,
            List<String> constraints);
}
