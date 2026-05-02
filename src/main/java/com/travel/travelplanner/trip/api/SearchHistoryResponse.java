package com.travel.travelplanner.trip.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.travel.travelplanner.trip.api.enums.BudgetLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SearchHistoryResponse {
    private String id;
    private String queryText;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BudgetLevel budgetLevel;
    private List<String> interests;
    private List<String> constraints;
    private Instant createdAt;
}
