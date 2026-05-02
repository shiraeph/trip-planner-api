package com.travel.travelplanner.trip.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.travel.travelplanner.trip.api.Itinerary;
import com.travel.travelplanner.trip.api.PlanTripRequest;
import com.travel.travelplanner.trip.api.RegenerateTripRequest;
import com.travel.travelplanner.trip.api.SearchHistoryResponse;
import com.travel.travelplanner.trip.api.TripPlanResponse;
import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.enums.DisplayLanguage;
import com.travel.travelplanner.trip.domain.enums.TripStatus;
import com.travel.travelplanner.trip.mapper.TripPlanMapper;
import com.travel.travelplanner.config.CurrentUserProvider;
import com.travel.travelplanner.trip.domain.SearchHistory;
import com.travel.travelplanner.trip.domain.enums.BudgetLevel;
import com.travel.travelplanner.trip.repository.SearchHistoryRepository;
import com.travel.travelplanner.trip.repository.TripPlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripPlanService {
    private final TripPlanRepository tripPlanRepository;
    private final TripPlanMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final SearchHistoryRepository searchHistoryRepository;
    private final TripItineraryAsyncService tripItineraryAsyncService;

    private String requireUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
        }
        return userId;
    }

    private static DisplayLanguage toDomainDisplayLanguage(com.travel.travelplanner.trip.api.enums.DisplayLanguage api) {
        if (api == null) return null;
        return api == com.travel.travelplanner.trip.api.enums.DisplayLanguage.HEBREW
                ? DisplayLanguage.HEBREW : DisplayLanguage.ENGLISH;
    }

    public TripPlanResponse createPlan(PlanTripRequest planTripRequest) {
        TripPlan tripPlan = mapper.toTripPlanDMNFromRequest(planTripRequest);
        String userId = requireUserId();
        tripPlan.setUserId(userId);
        tripPlan.setTripStatus(TripStatus.GENERATING);
        DisplayLanguage preferred = planTripRequest.getDisplayLanguage() != null
                ? toDomainDisplayLanguage(planTripRequest.getDisplayLanguage())
                : DisplayLanguage.ENGLISH;
        tripPlan.setDisplayLanguage(preferred);

        // Save "search history" for planner submissions too (not only /api/trips/search).
        SearchHistory history = SearchHistory.builder()
                .userId(userId)
                .queryText(buildQueryText(
                        tripPlan.getDestination(),
                        tripPlan.getStartDate(),
                        tripPlan.getEndDate(),
                        safeBudgetLevel(tripPlan),
                        tripPlan.getTripPreferences() != null ? tripPlan.getTripPreferences().getInterests() : null,
                        tripPlan.getTripPreferences() != null ? tripPlan.getTripPreferences().getConstraints() : null))
                .destination(tripPlan.getDestination())
                .startDate(tripPlan.getStartDate())
                .endDate(tripPlan.getEndDate())
                .budgetLevel(safeBudgetLevel(tripPlan))
                .interests(tripPlan.getTripPreferences() != null ? tripPlan.getTripPreferences().getInterests() : null)
                .constraints(tripPlan.getTripPreferences() != null ? tripPlan.getTripPreferences().getConstraints() : null)
                .createdAt(Instant.now())
                .build();
        searchHistoryRepository.save(history);

        TripPlan saved = tripPlanRepository.save(tripPlan);
        tripItineraryAsyncService.generateItineraryAsync(saved.getId());
        return mapper.toTripPlanResponseFromTripPlan(saved, preferred);
    }

    private static BudgetLevel safeBudgetLevel(TripPlan plan) {
        if (plan == null || plan.getTripPreferences() == null) return null;
        return plan.getTripPreferences().getBudgetLevel();
    }

    public TripPlanResponse regenerate(String id, RegenerateTripRequest request) {
        String userId = requireUserId();
        TripPlan existing = tripPlanRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + id));

        // Keep a snapshot so we can merge locked blocks into the new itinerary.
        com.travel.travelplanner.trip.domain.itinerary.Itinerary oldEn = existing.getItineraryEn();
        com.travel.travelplanner.trip.domain.itinerary.Itinerary oldHe = existing.getItineraryHe();

        // Create a new TripPlan so "regenerate" produces a new ID (keeps old trip intact).
        TripPlan plan = TripPlan.builder()
                .userId(userId)
                .destination(existing.getDestination())
                .startDate(existing.getStartDate())
                .endDate(existing.getEndDate())
                .tripGroup(existing.getTripGroup())
                .tripPreferences(existing.getTripPreferences())
                .displayLanguage(existing.getDisplayLanguage())
                .tripStatus(TripStatus.GENERATING)
                .errorMessage(null)
                .aiRawResponse(null)
                .itinerary(null)
                .itineraryEn(null)
                .itineraryHe(null)
                .build();

        TripPlan saved = tripPlanRepository.save(plan);
        tripItineraryAsyncService.generateItineraryAsync(saved.getId(), oldEn, oldHe, request);
        DisplayLanguage lang = saved.getDisplayLanguage() != null ? saved.getDisplayLanguage() : DisplayLanguage.ENGLISH;
        return mapper.toTripPlanResponseFromTripPlan(saved, lang);
    }

    public TripPlanResponse getPlan(String id, DisplayLanguage language) {
        String userId = requireUserId();
        TripPlan plan = tripPlanRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new RuntimeException("plan not found: " + id));
        DisplayLanguage lang = language != null ? language : (plan.getDisplayLanguage() != null ? plan.getDisplayLanguage() : DisplayLanguage.ENGLISH);
        return mapper.toTripPlanResponseFromTripPlan(plan, lang);
    }

    public TripPlanResponse updateItinerary(String id, DisplayLanguage language, Itinerary body) {
        if (body == null || body.getDayPlans() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itinerary.dayPlans is required");
        }
        String userId = requireUserId();
        TripPlan plan = tripPlanRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (plan.getTripStatus() != TripStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Itinerary can only be saved when the trip is READY");
        }
        DisplayLanguage lang = language != null ? language
                : (plan.getDisplayLanguage() != null ? plan.getDisplayLanguage() : DisplayLanguage.ENGLISH);

        java.util.List<DayPlan> newDays = mapper.toDomainDayPlans(body.getDayPlans());
        applyDayPlansForLanguage(plan, lang, newDays);

        TripPlan saved = tripPlanRepository.save(plan);
        return mapper.toTripPlanResponseFromTripPlan(saved, lang);
    }

    private static void applyDayPlansForLanguage(TripPlan plan, DisplayLanguage lang, java.util.List<DayPlan> newDays) {
        if (lang == DisplayLanguage.HEBREW) {
            if (plan.getItineraryHe() != null) {
                plan.getItineraryHe().setDayPlans(newDays);
            } else {
                plan.setItineraryHe(new com.travel.travelplanner.trip.domain.itinerary.Itinerary(null, null, newDays));
            }
            return;
        }
        if (plan.getItineraryEn() != null) {
            plan.getItineraryEn().setDayPlans(newDays);
            return;
        }
        if (plan.getItineraryHe() == null && plan.getItinerary() != null) {
            plan.getItinerary().setDayPlans(newDays);
            return;
        }
        plan.setItineraryEn(new com.travel.travelplanner.trip.domain.itinerary.Itinerary(null, null, newDays));
    }

    public List<TripPlanResponse> getMyTrips() {
        String userId = requireUserId();
        List<TripPlan> plans = tripPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);
        DisplayLanguage lang = DisplayLanguage.ENGLISH;
        return plans.stream()
                .map(p -> mapper.toTripPlanResponseFromTripPlan(p, lang))
                .collect(Collectors.toList());
    }

    public List<TripPlanResponse> searchTrips(
            String destination,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            com.travel.travelplanner.trip.domain.enums.BudgetLevel budgetLevel,
            List<String> interests,
            List<String> constraints) {
        String userId = requireUserId();
        String queryText = buildQueryText(destination, startDate, endDate, budgetLevel, interests, constraints);
        SearchHistory history = SearchHistory.builder()
                .userId(userId)
                .queryText(queryText)
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .budgetLevel(budgetLevel)
                .interests(interests != null ? new ArrayList<>(interests) : null)
                .constraints(constraints != null ? new ArrayList<>(constraints) : null)
                .createdAt(Instant.now())
                .build();
        searchHistoryRepository.save(history);

        List<TripPlan> plans = tripPlanRepository.searchByUserAndFilters(
                userId, destination, startDate, endDate, budgetLevel, interests, constraints);
        DisplayLanguage lang = DisplayLanguage.ENGLISH;
        return plans.stream()
                .map(p -> mapper.toTripPlanResponseFromTripPlan(p, lang))
                .collect(Collectors.toList());
    }

    public List<SearchHistoryResponse> getMySearchHistory() {
        String userId = requireUserId();
        List<SearchHistory> list = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 20));
        return list.stream().map(this::toSearchHistoryResponse).collect(Collectors.toList());
    }

    private static String buildQueryText(
            String destination,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            com.travel.travelplanner.trip.domain.enums.BudgetLevel budgetLevel,
            List<String> interests,
            List<String> constraints) {
        List<String> parts = new ArrayList<>();
        if (destination != null && !destination.isBlank()) parts.add("destination=" + destination.trim());
        if (startDate != null) parts.add("startDate=" + startDate);
        if (endDate != null) parts.add("endDate=" + endDate);
        if (budgetLevel != null) parts.add("budgetLevel=" + budgetLevel);
        if (interests != null && !interests.isEmpty()) parts.add("interests=" + interests);
        if (constraints != null && !constraints.isEmpty()) parts.add("constraints=" + constraints);
        return parts.isEmpty() ? "(no filters)" : String.join(", ", parts);
    }

    private SearchHistoryResponse toSearchHistoryResponse(SearchHistory h) {
        SearchHistoryResponse r = new SearchHistoryResponse();
        r.setId(h.getId());
        r.setQueryText(h.getQueryText());
        r.setDestination(h.getDestination());
        r.setStartDate(h.getStartDate());
        r.setEndDate(h.getEndDate());
        r.setBudgetLevel(h.getBudgetLevel() != null
                ? com.travel.travelplanner.trip.api.enums.BudgetLevel.valueOf(h.getBudgetLevel().name())
                : null);
        r.setInterests(h.getInterests());
        r.setConstraints(h.getConstraints());
        r.setCreatedAt(h.getCreatedAt());
        return r;
    }
}
