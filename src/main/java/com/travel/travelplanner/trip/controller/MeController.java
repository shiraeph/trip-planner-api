package com.travel.travelplanner.trip.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travel.travelplanner.trip.api.SearchHistoryResponse;
import com.travel.travelplanner.trip.api.TripPlanResponse;
import com.travel.travelplanner.trip.service.TripPlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {
    private final TripPlanService tripPlanService;

    @GetMapping("/trips")
    public List<TripPlanResponse> getMyTrips() {
        return tripPlanService.getMyTrips();
    }

    @GetMapping("/search-history")
    public List<SearchHistoryResponse> getMySearchHistory() {
        return tripPlanService.getMySearchHistory();
    }
}
