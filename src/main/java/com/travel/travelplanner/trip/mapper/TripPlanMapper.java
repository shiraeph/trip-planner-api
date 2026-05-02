package com.travel.travelplanner.trip.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TripPlanMapper {
    @Mapping(source = "travelStyle", target = "tripPreferences.travelStyle")
    @Mapping(source = "budgetLevel", target = "tripPreferences.budgetLevel")
    @Mapping(source = "interests", target = "tripPreferences.interests")
    @Mapping(source = "constraints", target = "tripPreferences.constraints")
    @Mapping(source = "freeText", target = "tripPreferences.freeText")
    @Mapping(source = "hotelName", target = "tripPreferences.hotelName")
    @Mapping(source = "hotelAddressOrArea", target = "tripPreferences.hotelAddressOrArea")
    @Mapping(source = "transportPreferences", target = "tripPreferences.transportPreferences")
    @Mapping(source = "includeDirections", target = "tripPreferences.includeDirections")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "tripStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "itinerary", ignore = true)
    @Mapping(target = "itineraryEn", ignore = true)
    @Mapping(target = "itineraryHe", ignore = true)
    @Mapping(target = "displayLanguage", ignore = true)
    @Mapping(target = "aiRawResponse", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    com.travel.travelplanner.trip.domain.TripPlan toTripPlanDMNFromRequest(com.travel.travelplanner.trip.api.PlanTripRequest planTripRequest);

    @Mapping(source = "tripPreferences.travelStyle", target = "travelStyle")
    @Mapping(source = "tripPreferences.budgetLevel", target = "budgetLevel")
    @Mapping(target = "itinerary", expression = "java(mapItinerary(selectItineraryForLanguage(tripPlan, language)))")
    @Mapping(target = "displayLanguage", expression = "java(toApiDisplayLanguage(language))")
    com.travel.travelplanner.trip.api.TripPlanResponse toTripPlanResponseFromTripPlan(
            com.travel.travelplanner.trip.domain.TripPlan tripPlan,
            @Context com.travel.travelplanner.trip.domain.enums.DisplayLanguage language);

    com.travel.travelplanner.trip.api.Itinerary mapItinerary(com.travel.travelplanner.trip.domain.itinerary.Itinerary itinerary);

    java.util.List<com.travel.travelplanner.trip.domain.itinerary.DayPlan> toDomainDayPlans(
            java.util.List<com.travel.travelplanner.trip.api.DayPlan> dayPlans);

    com.travel.travelplanner.trip.domain.itinerary.DayPlan toDomainDayPlan(com.travel.travelplanner.trip.api.DayPlan dayPlan);

    com.travel.travelplanner.trip.domain.itinerary.BlockPlan toDomainBlockPlan(com.travel.travelplanner.trip.api.BlockPlan blockPlan);

    @Mapping(target = "timeHint", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    com.travel.travelplanner.trip.domain.itinerary.ItineraryItem toDomainItineraryItem(
            com.travel.travelplanner.trip.api.ItineraryItem item);

    com.travel.travelplanner.trip.domain.itinerary.Location toDomainLocation(com.travel.travelplanner.trip.api.Location location);

    default com.travel.travelplanner.trip.domain.itinerary.Itinerary selectItineraryForLanguage(
            com.travel.travelplanner.trip.domain.TripPlan plan,
            com.travel.travelplanner.trip.domain.enums.DisplayLanguage lang) {
        if (plan == null) return null;
        boolean useHebrew = lang == com.travel.travelplanner.trip.domain.enums.DisplayLanguage.HEBREW;
        com.travel.travelplanner.trip.domain.itinerary.Itinerary he = plan.getItineraryHe();
        com.travel.travelplanner.trip.domain.itinerary.Itinerary en = plan.getItineraryEn();
        if (useHebrew && he != null) return he;
        if (!useHebrew && en != null) return en;
        return plan.getItinerary();
    }

    default com.travel.travelplanner.trip.api.enums.DisplayLanguage toApiDisplayLanguage(
            com.travel.travelplanner.trip.domain.enums.DisplayLanguage lang) {
        if (lang == null) return null;
        return lang == com.travel.travelplanner.trip.domain.enums.DisplayLanguage.HEBREW
                ? com.travel.travelplanner.trip.api.enums.DisplayLanguage.HEBREW
                : com.travel.travelplanner.trip.api.enums.DisplayLanguage.ENGLISH;
    }

    com.travel.travelplanner.trip.api.TransitInfo toTransitInfoApi(
            com.travel.travelplanner.trip.domain.itinerary.TransitInfo transitInfo);

    com.travel.travelplanner.trip.domain.itinerary.TransitInfo toTransitInfoDMN(
            com.travel.travelplanner.trip.api.TransitInfo transitInfo);
}
