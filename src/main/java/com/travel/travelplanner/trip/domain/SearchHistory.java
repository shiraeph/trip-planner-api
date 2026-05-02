package com.travel.travelplanner.trip.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.travel.travelplanner.trip.domain.enums.BudgetLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "search_history")
public class SearchHistory {
    @Id
    private String id;
    private String userId;
    private String queryText;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BudgetLevel budgetLevel;
    private List<String> interests;
    private List<String> constraints;
    private Instant createdAt;
}
