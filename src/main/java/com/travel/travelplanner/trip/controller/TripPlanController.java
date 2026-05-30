package com.travel.travelplanner.trip.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.travelplanner.trip.api.Itinerary;
import com.travel.travelplanner.trip.api.PlanTripRequest;
import com.travel.travelplanner.trip.api.RegenerateTripRequest;
import com.travel.travelplanner.trip.api.TripPlanResponse;
import com.travel.travelplanner.trip.domain.enums.BudgetLevel;
import com.travel.travelplanner.trip.domain.enums.DisplayLanguage;
import com.travel.travelplanner.trip.service.TripPlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripPlanController {
    private final TripPlanService service;

    @PostMapping("/plan")
    public TripPlanResponse plan(@Valid @RequestBody PlanTripRequest request) {
        return service.createPlan(request);
    }

    @GetMapping("/search")
    public List<TripPlanResponse> searchTrips(
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String budgetLevel,
            @RequestParam(required = false) List<String> interests,
            @RequestParam(required = false) List<String> constraints) {
        BudgetLevel bl = parseBudgetLevel(budgetLevel);
        return service.searchTrips(destination, startDate, endDate, bl, interests, constraints);
    }

    @GetMapping("/{id}")
    public TripPlanResponse getPlan(
            @PathVariable String id,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String view) {
        DisplayLanguage lang = parseLanguage(language);
        return service.getPlan(id, lang, view);
    }

    @PostMapping("/{id}/regenerate")
    public TripPlanResponse regeneratePlan(
            @PathVariable String id,
            @RequestBody(required = false) RegenerateTripRequest request) {
        return service.regenerate(id, request);
    }

    @PutMapping("/{id}/itinerary")
    public TripPlanResponse updateItinerary(
            @PathVariable String id,
            @RequestParam(required = false) String language,
            @RequestBody Itinerary itinerary) {
        DisplayLanguage lang = parseLanguage(language);
        return service.updateItinerary(id, lang, itinerary);
    }

    private static DisplayLanguage parseLanguage(String language) {
        if (language == null || language.isBlank()) return null;
        return "he".equalsIgnoreCase(language) || "hebrew".equalsIgnoreCase(language)
                ? DisplayLanguage.HEBREW
                : "en".equalsIgnoreCase(language) || "english".equalsIgnoreCase(language)
                        ? DisplayLanguage.ENGLISH
                        : null;
    }

    private static BudgetLevel parseBudgetLevel(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return BudgetLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
