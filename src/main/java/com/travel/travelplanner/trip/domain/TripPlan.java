package com.travel.travelplanner.trip.domain;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import com.travel.travelplanner.trip.domain.enums.DisplayLanguage;
import com.travel.travelplanner.trip.domain.enums.TripStatus;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;

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
@Document(collection = "trip_plans")
public class TripPlan {
    @Id
    private String id;
    private String userId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String destination;
    private TripGroup tripGroup;
    private TripPreferences tripPreferences;
    private Itinerary itinerary;
    private Itinerary itineraryEn;
    private Itinerary itineraryHe;
    private DisplayLanguage displayLanguage;
    private TripStatus tripStatus;
    private String aiRawResponse;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private String errorMessage;
}
