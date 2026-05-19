package com.travel.travelplanner.trip.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.travel.travelplanner.trip.domain.TripPlan;

public final class TripDates {

    private TripDates() {}

    /** Inclusive calendar days (e.g. Mon–Wed = 3 days). */
    public static int inclusiveDayCount(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0;
        }
        if (end.isBefore(start)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    public static int inclusiveDayCount(TripPlan tripPlan) {
        if (tripPlan == null) {
            return 0;
        }
        return inclusiveDayCount(tripPlan.getStartDate(), tripPlan.getEndDate());
    }

    public static List<LocalDate> eachDay(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        if (start == null || end == null || end.isBefore(start)) {
            return dates;
        }
        LocalDate d = start;
        while (!d.isAfter(end)) {
            dates.add(d);
            d = d.plusDays(1);
        }
        return dates;
    }

    public static List<LocalDate> eachDay(TripPlan tripPlan) {
        if (tripPlan == null) {
            return List.of();
        }
        return eachDay(tripPlan.getStartDate(), tripPlan.getEndDate());
    }
}
