package com.travel.travelplanner.ai.prompt;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;

@Service
public class BuildPromptService {

    public String buildPrompt(TripPlan tripPlan) {
        String destination = tripPlan.getDestination();
        String start = String.valueOf(tripPlan.getStartDate());
        String end = String.valueOf(tripPlan.getEndDate());

        String travelStyle = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getTravelStyle() != null)
                        ? tripPlan.getTripPreferences().getTravelStyle().name()
                        : "BALANCED";

        String budget = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getBudgetLevel() != null)
                        ? tripPlan.getTripPreferences().getBudgetLevel().name()
                        : "MEDIUM";

        String group = (tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getComposition() != null)
                ? tripPlan.getTripGroup().getComposition().name()
                : "SOLO";

        String peopleCount = (tripPlan.getTripGroup() != null)
                ? String.valueOf(tripPlan.getTripGroup().getPeopleCount())
                : "1";

        String minAge = (tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getMinAge() != null)
                ? String.valueOf(tripPlan.getTripGroup().getMinAge())
                : "unknown";

        String maxAge = (tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getMaxAge() != null)
                ? String.valueOf(tripPlan.getTripGroup().getMaxAge())
                : "unknown";

        String genderMix = (tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getGenderMix() != null)
                ? tripPlan.getTripGroup().getGenderMix().name()
                : "UNKNOWN";

        String groupSummary = group + ", " + peopleCount + " people, ages " + minAge + "-" + maxAge
                + ", gender mix " + genderMix;

        String interests = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getInterests() != null
                && !tripPlan.getTripPreferences().getInterests().isEmpty())
                        ? String.join(", ", tripPlan.getTripPreferences().getInterests())
                        : "none";

        String constraints = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getConstraints() != null
                && !tripPlan.getTripPreferences().getConstraints().isEmpty())
                        ? tripPlan.getTripPreferences().getConstraints().stream().collect(Collectors.joining("; "))
                        : "none";

        String hotelName = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelName() != null)
                        ? tripPlan.getTripPreferences().getHotelName()
                        : "not provided";

        String hotelAddress = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelAddressOrArea() != null)
                        ? tripPlan.getTripPreferences().getHotelAddressOrArea()
                        : "not provided";

        String transportPref = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getTransportPreferences() != null)
                        ? tripPlan.getTripPreferences().getTransportPreferences().name()
                        : "not provided";

        // Match ItineraryValidator: only when explicitly true (do not default-on when null).
        boolean includeDirections = tripPlan.getTripPreferences() != null
                && Boolean.TRUE.equals(tripPlan.getTripPreferences().getIncludeDirections());

        String freeText = (tripPlan.getTripPreferences() != null && tripPlan.getTripPreferences().getFreeText() != null)
                ? tripPlan.getTripPreferences().getFreeText()
                : "not provided";

        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        String lengthRule = tripDays > 7
                ? "Keep ATTRACTION notes concise but at least 30 characters each (why visit + duration or practical tip)."
                : "Use detailed notes as specified below.";

        return """
                You are an expert travel planner.

                Return ONLY valid JSON (no markdown, no explanations, no extra keys).
                The output must match EXACTLY the schema below.

                IMPORTANT: Generate the itinerary in TWO languages: English and Hebrew.
                Provide the exact same structure under "en" and "he". All content (titles, names, notes, directions) must be in the respective language.

                TRIP INPUT
                - Destination: %s
                - Dates: %s to %s (inclusive)
                - Trip length: %d days — dayPlans MUST contain exactly %d entries (one per calendar day, no more, no less)
                - Required dates in order: %s
                - Group: %s
                - Travel style: %s
                - Budget: %s

                PREFERENCES
                - Interests (prioritize these): %s
                - Constraints (STRICT rules, must NOT be violated): %s

                OPTIONAL
                - Hotel name: %s
                - Hotel area/address: %s
                - Transport preference: %s
                - Include directions: %s
                - Free text notes: %s

                HARD RULES (must follow)
                1) Constraints are STRICT. Do not violate them. If a constraint says "avoid museums", then do NOT include museums of any kind (British Museum, National Gallery, Tate Modern, etc.). Replace with non-museum alternatives.
                2) Interests are HIGH PRIORITY. The plan must reflect them clearly throughout the trip.
                3) Output must be realistic and city-specific. Avoid generic placeholders like "Local Restaurant" unless you truly cannot propose a specific type/place.
                4) If Include directions is true, EVERY item MUST include a "transit" object (including TRANSIT items). transit.directions must be non-empty.
                5) Do NOT invent exact latitude/longitude. Use null for lat/lng.
                6) Trip length (STRICT — most important):
                   - The en.dayPlans and he.dayPlans arrays must each have exactly %d objects.
                   - Do not stop after day 1 or 2. Cover every date from %s through %s.
                   - Each day object "date" field must match the calendar date for that day (in order).
                7) Balance by day and time-block (STRICT):
                   - EVERY day must have exactly THREE blocks: MORNING, AFTERNOON, EVENING.
                   - Never output only one block for a day. Never label two blocks with the same timeBlock.
                   - Each block must contain 1-6 items (aim for 2-3 on long trips).
                8) Notes (%s):
                   - For ATTRACTION: 2–4 sentences including (a) why it’s worth it, (b) recommended duration, (c) one practical tip (tickets, best time, crowd, nearby area).
                   - For FOOD: mention the style of food + what to try or a quick ordering tip.
                   - For TRANSIT: name it clearly and include concise step-by-step directions in transit.directions.
                   - For NOTE: practical reminders.
                9) "en" = full itinerary in English. "he" = full itinerary in Hebrew (use proper Hebrew text).

                TRANSPORT RULES
                - Use transportPreference when choosing transit.mode:
                  - WALKING -> mostly WALK
                  - PUBLIC_TRANSPORT -> METRO/BUS/TRAM/TRAIN when possible
                  - TAXI -> mostly TAXI
                  - CAR -> mostly CAR
                  - MIXED -> choose best per route
                - If hotel is provided, treat it as the default base ("Hotel") for the first activity of the day and often the last.

                OUTPUT JSON SCHEMA (exact)
                {
                  "en": {
                    "dayPlans": [
                      {
                        "date": "yyyy-MM-dd",
                        "title": "string (English)",
                        "blocks": [
                          {
                            "timeBlock": "MORNING|AFTERNOON|EVENING",
                            "items": [
                              {
                                "type": "FOOD|ATTRACTION|TRANSIT|NOTE",
                                "name": "string (English)",
                                "location": { "name": "string", "lat": null, "lng": null },
                                "notes": "string|null (English)",
                                "transit": {
                                  "from": "string",
                                  "mode": "WALK|METRO|BUS|TRAM|TRAIN|TAXI|CAR|TRANSFER|MIXED",
                                  "estimatedMinutes": 0,
                                  "directions": "string (English)"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  "he": {
                    "dayPlans": [
                      {
                        "date": "yyyy-MM-dd",
                        "title": "string (Hebrew)",
                        "blocks": [
                          {
                            "timeBlock": "MORNING|AFTERNOON|EVENING",
                            "items": [
                              {
                                "type": "FOOD|ATTRACTION|TRANSIT|NOTE",
                                "name": "string (Hebrew)",
                                "location": { "name": "string (Hebrew)", "lat": null, "lng": null },
                                "notes": "string|null (Hebrew)",
                                "transit": {
                                  "from": "string",
                                  "mode": "WALK|METRO|BUS|TRAM|TRAIN|TAXI|CAR|TRANSFER|MIXED",
                                  "estimatedMinutes": 0,
                                  "directions": "string (Hebrew)"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }

                Now generate the bilingual itinerary that satisfies all rules.
                """
                .formatted(
                        destination, start, end, tripDays, tripDays, dateList,
                        groupSummary, travelStyle, budget,
                        interests, constraints,
                        hotelName, hotelAddress, transportPref, includeDirections, freeText,
                        tripDays, start, end, lengthRule);
    }

    public String buildFixPrompt(TripPlan tripPlan, List<String> violations) {
        String base = buildPrompt(tripPlan);
        String problems = String.join("\n -", violations);

        return base + """
        IMPORTANT: Your previous attempt violated these rules. Fix them and regenerate the FULL bilingual itinerary JSON (both "en" and "he").
    Violations:
    - %s

    Return ONLY the corrected JSON, matching the schema exactly.
        """.formatted(problems);
    }

    /**
     * Prompt for one segment of a long trip. Output must contain dayPlans only for {@code chunkDates}.
     */
    public String buildChunkPrompt(
            TripPlan tripPlan,
            List<LocalDate> chunkDates,
            int chunkIndex,
            int totalChunks,
            String continuityHint) {
        String destination = tripPlan.getDestination();
        String tripStart = String.valueOf(tripPlan.getStartDate());
        String tripEnd = String.valueOf(tripPlan.getEndDate());
        int totalTripDays = TripDates.inclusiveDayCount(tripPlan);
        int chunkDays = chunkDates.size();
        String chunkDateList = chunkDates.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        String chunkStart = chunkDates.get(0).toString();
        String chunkEnd = chunkDates.get(chunkDates.size() - 1).toString();

        String travelStyle = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getTravelStyle() != null)
                        ? tripPlan.getTripPreferences().getTravelStyle().name()
                        : "BALANCED";
        String budget = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getBudgetLevel() != null)
                        ? tripPlan.getTripPreferences().getBudgetLevel().name()
                        : "MEDIUM";
        String group = (tripPlan.getTripGroup() != null && tripPlan.getTripGroup().getComposition() != null)
                ? tripPlan.getTripGroup().getComposition().name()
                : "SOLO";
        String peopleCount = (tripPlan.getTripGroup() != null)
                ? String.valueOf(tripPlan.getTripGroup().getPeopleCount())
                : "1";
        String interests = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getInterests() != null
                && !tripPlan.getTripPreferences().getInterests().isEmpty())
                        ? String.join(", ", tripPlan.getTripPreferences().getInterests())
                        : "none";
        String constraints = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getConstraints() != null
                && !tripPlan.getTripPreferences().getConstraints().isEmpty())
                        ? tripPlan.getTripPreferences().getConstraints().stream().collect(Collectors.joining("; "))
                        : "none";
        String hotelName = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelName() != null)
                        ? tripPlan.getTripPreferences().getHotelName()
                        : "not provided";
        String hotelArea = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getHotelAddressOrArea() != null)
                        ? tripPlan.getTripPreferences().getHotelAddressOrArea()
                        : "not provided";
        String transportPref = (tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getTransportPreferences() != null)
                        ? tripPlan.getTripPreferences().getTransportPreferences().name()
                        : "not provided";
        boolean includeDirections = tripPlan.getTripPreferences() != null
                && Boolean.TRUE.equals(tripPlan.getTripPreferences().getIncludeDirections());
        String freeText = (tripPlan.getTripPreferences() != null && tripPlan.getTripPreferences().getFreeText() != null)
                ? tripPlan.getTripPreferences().getFreeText()
                : "not provided";

        String continuity = (continuityHint == null || continuityHint.isBlank())
                ? ""
                : "CONTINUITY FROM EARLIER DAYS:\n- " + continuityHint + "\n";

        return """
                You are an expert travel planner.

                Return ONLY valid JSON (no markdown, no explanations, no extra keys).
                The output must match EXACTLY the schema below.

                IMPORTANT: Generate the itinerary in TWO languages: English and Hebrew.
                Provide the exact same structure under "en" and "he".

                FULL TRIP (for context)
                - Destination: %s
                - Overall dates: %s to %s (%d days total)
                - Group: %s, %s people
                - Travel style: %s, Budget: %s
                - Interests: %s
                - Constraints: %s
                - Hotel: %s (%s), Transport: %s, Include directions: %s
                - Free text: %s

                THIS CHUNK ONLY (STRICT)
                - You are generating chunk %d of %d for this trip.
                - Output dayPlans for exactly %d days: %s
                - Date range for this chunk: %s to %s
                - en.dayPlans and he.dayPlans must each have exactly %d entries — no more, no less.
                - Do NOT include days before %s or after %s.
                %s
                HARD RULES
                1) Constraints are STRICT.
                2) Each day: exactly THREE blocks with field name "timeBlock" (NOT "title"): MORNING, AFTERNOON, EVENING.
                   2-3 items per block. NEVER return a block with an empty "items" array.
                3) Item "type" must be exactly one of: FOOD, ATTRACTION, TRANSIT, NOTE (never ACTIVITY or other values).
                4) Each ATTRACTION needs notes of at least 30 characters (why visit + duration or tip).
                5) If Include directions is true, every item needs transit with non-empty directions.
                6) Put coordinates on location.lat / location.lng (or null), not as top-level lat/lng on items.

                OUTPUT JSON SCHEMA (same as full trip — only populate dayPlans for this chunk's dates)
                {
                  "en": { "dayPlans": [ { "date": "yyyy-MM-dd", "title": "...", "blocks": [...] } ] },
                  "he": { "dayPlans": [ { "date": "yyyy-MM-dd", "title": "...", "blocks": [...] } ] }
                }

                Generate the bilingual itinerary for this chunk only.
                """
                .formatted(
                        destination, tripStart, tripEnd, totalTripDays,
                        group, peopleCount, travelStyle, budget,
                        interests, constraints,
                        hotelName, hotelArea, transportPref, includeDirections, freeText,
                        chunkIndex, totalChunks, chunkDays, chunkDateList, chunkStart, chunkEnd, chunkDays,
                        chunkStart, chunkEnd,
                        continuity);
    }
}
