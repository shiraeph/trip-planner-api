package com.travel.travelplanner.trip.service.validation;

import java.util.List;

public class ItineraryValidationException extends RuntimeException {
    private final List<String> violations;

    public ItineraryValidationException(List<String> violations) {
        super("Itinerary validation failed" + String.join( " | ", violations));
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
