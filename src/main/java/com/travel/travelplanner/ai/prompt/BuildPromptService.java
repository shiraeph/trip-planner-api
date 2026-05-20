package com.travel.travelplanner.ai.prompt;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;

@Service
public class BuildPromptService {

    public String buildSystemMessage(TripPlan tripPlan) {
        String destination = tripPlan.getDestination() != null ? tripPlan.getDestination() : "the destination";
        return """
                You are an expert local travel planner for %s.
                You know specific venues, neighborhoods, dishes, and realistic pacing.
                Every recommendation must use real place names and concrete details — never generic filler.
                Return ONLY valid JSON (no markdown, no commentary).
                """
                .formatted(destination);
    }

    public String buildPrompt(TripPlan tripPlan) {
        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        TripPromptContext ctx = TripPromptContext.from(tripPlan, dateList, tripDays);

        return header(ctx)
                + tripInputBlock(ctx, tripDays, tripDays, dateList)
                + preferencesBlock(ctx)
                + hardRulesBlock(ctx, tripDays, ctx.start(), ctx.end(), false, 0, 0, 0)
                + qualityBarBlock(ctx, false)
                + transportRulesBlock(ctx)
                + jsonSchemaBlock()
                + "\nNow generate the bilingual itinerary that satisfies all rules.\n";
    }

    public String buildFixPrompt(TripPlan tripPlan, List<String> violations) {
        String problems = String.join("\n - ", violations);
        return buildPrompt(tripPlan)
                + """

                IMPORTANT: Your previous attempt violated these rules. Fix them and regenerate the FULL bilingual itinerary JSON (both "en" and "he").
                Violations:
                - %s

                Return ONLY the corrected JSON, matching the schema exactly.
                """
                        .formatted(problems);
    }

    public String buildChunkPrompt(
            TripPlan tripPlan,
            List<LocalDate> chunkDates,
            int chunkIndex,
            int totalChunks,
            String continuityHint) {
        int totalTripDays = TripDates.inclusiveDayCount(tripPlan);
        int chunkDays = chunkDates.size();
        String chunkDateList = chunkDates.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        String chunkStart = chunkDates.get(0).toString();
        String chunkEnd = chunkDates.get(chunkDates.size() - 1).toString();
        int tripDayStart = dayIndex(tripPlan, chunkDates.get(0));
        int tripDayEnd = dayIndex(tripPlan, chunkDates.get(chunkDates.size() - 1));

        TripPromptContext ctx = TripPromptContext.from(tripPlan, chunkDateList, chunkDays);

        String continuity = (continuityHint == null || continuityHint.isBlank())
                ? ""
                : continuityHint + "\n";

        String chunkArc = chunkArcBlock(chunkIndex, totalChunks, tripDayStart, tripDayEnd, totalTripDays, ctx.destination());

        return header(ctx)
                + """
                FULL TRIP (context)
                - Destination: %s
                - Overall dates: %s to %s (%d days total)
                - Group: %s
                - Travel style: %s, Budget: %s
                - Interests: %s
                - Constraints: %s
                - Hotel: %s (%s), Transport: %s, Include directions: %s
                - Free text: %s

                THIS CHUNK ONLY (STRICT)
                - Chunk %d of %d — trip days %d–%d of %d.
                - Output dayPlans for exactly these %d dates: %s
                - Date range: %s to %s
                - en.dayPlans and he.dayPlans must each have exactly %d entries — no more, no less.
                - Do NOT include days outside this chunk.

                %s
                %s
                """
                        .formatted(
                                ctx.destination(), ctx.start(), ctx.end(), totalTripDays,
                                ctx.groupSummary(), ctx.travelStyle(), ctx.budget(),
                                ctx.interests(), ctx.constraints(),
                                ctx.hotelName(), ctx.hotelAddress(), ctx.transportPref(),
                                ctx.includeDirections(), ctx.freeText(),
                                chunkIndex, totalChunks, tripDayStart, tripDayEnd, totalTripDays,
                                chunkDays, chunkDateList, chunkStart, chunkEnd, chunkDays,
                                continuity, chunkArc)
                + hardRulesBlock(ctx, chunkDays, chunkStart, chunkEnd, true, chunkIndex, totalChunks, tripDayStart)
                + qualityBarBlock(ctx, true)
                + transportRulesBlock(ctx)
                + jsonSchemaBlock()
                + "\nGenerate the bilingual itinerary for this chunk only. Same quality as a 3-day trip — no shortcuts.\n";
    }

    private static int dayIndex(TripPlan tripPlan, LocalDate date) {
        List<LocalDate> all = TripDates.eachDay(tripPlan);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).equals(date)) {
                return i + 1;
            }
        }
        return 1;
    }

    private static String header(TripPromptContext ctx) {
        return """
                You are an expert travel planner specializing in %s.

                Return ONLY valid JSON (no markdown, no explanations, no extra keys).
                The output must match EXACTLY the schema below.

                IMPORTANT: Generate the itinerary in TWO languages: English and Hebrew.
                Provide the exact same structure under "en" and "he".
                All content (titles, names, notes, directions) must be in the respective language.

                """
                .formatted(ctx.destination());
    }

    private static String tripInputBlock(TripPromptContext ctx, int daysInScope, int totalDays, String dateList) {
        return """
                TRIP INPUT
                - Destination: %s
                - Dates: %s to %s (inclusive)
                - Trip length: %d days — dayPlans MUST contain exactly %d entries (one per calendar day)
                - Required dates in order: %s
                - Group: %s
                - Travel style: %s
                - Budget: %s

                """
                .formatted(
                        ctx.destination(), ctx.start(), ctx.end(),
                        totalDays, daysInScope, dateList,
                        ctx.groupSummary(), ctx.travelStyle(), ctx.budget());
    }

    private static String preferencesBlock(TripPromptContext ctx) {
        return """
                PREFERENCES
                - Interests (prioritize across the whole trip): %s
                - Constraints (STRICT — never violate): %s

                OPTIONAL
                - Hotel name: %s
                - Hotel area/address: %s
                - Transport preference: %s
                - Include directions: %s
                - Free text notes: %s

                """
                .formatted(
                        ctx.interests(), ctx.constraints(),
                        ctx.hotelName(), ctx.hotelAddress(), ctx.transportPref(),
                        ctx.includeDirections(), ctx.freeText());
    }

    private static String qualityBarBlock(TripPromptContext ctx, boolean chunked) {
        String chunkNote = chunked
                ? """
                LONG-TRIP CHUNK RULE: Later chunks are NOT allowed to be vaguer than earlier ones.
                Each day in this chunk needs the same specificity as day 1 of a short trip.
                """
                : "";

        return """
                QUALITY BAR (critical — applies to EVERY day)
                - Write like a knowledgeable local friend, not a brochure.
                - Use REAL, specific names: restaurants, cafés, markets, museums (unless constraints forbid), viewpoints, streets, parks.
                - BANNED generic labels: "Local Restaurant", "City Walk", "Traditional Taverna", "Explore the area",
                  "Famous landmark", "Popular spot", "Nice viewpoint" without a proper name.
                - Spread activities across DIFFERENT neighborhoods of %s across the trip.
                - Rotate cuisine styles and venue types; avoid scheduling the same kind of meal twice in one day.
                - Reflect interests (%s) on most days with concrete choices, not vague mentions.
                - Match budget (%s) and travel style (%s): pace, price level, and energy.

                ITEM DETAIL REQUIREMENTS
                - ATTRACTION: 2–4 sentences in notes (min ~45 characters). Include:
                  (a) why this place is worth it for THIS traveler profile,
                  (b) suggested duration,
                  (c) one practical tip (best time, tickets, crowds, dress code, or what to see first).
                  location.name = neighborhood or address area in %s.
                - FOOD: Use a real venue name. Notes must include cuisine/style, 1–2 dishes or drinks to order,
                  and why it fits the area or day (e.g. post-hike lunch, sunset drinks).
                - TRANSIT: Clear route; directions name lines/stops/landmarks where possible.
                - Each block: 2–3 items (one anchor experience + food/transit as needed). Never empty items arrays.

                VARIETY & REALISM
                - Mix icons with hidden gems; include at least one everyday local ritual (market, bakery, promenade).
                - No duplicate venue names across the trip.
                - Day titles: evocative and specific (e.g. "Ancient Hills & Plaka Evenings"), not "Day 5 Sightseeing".
                %s
                """
                .formatted(
                        ctx.destination(),
                        ctx.interests(),
                        ctx.budget(),
                        ctx.travelStyle(),
                        ctx.destination(),
                        chunkNote);
    }

    private static String chunkArcBlock(
            int chunkIndex, int totalChunks, int tripDayStart, int tripDayEnd, int totalDays, String destination) {
        String phase = chunkIndex == 1
                ? "Opening — strongest highlights, orientation, and signature food."
                : chunkIndex == totalChunks
                        ? "Closing — memorable capstone experiences and a strong final evening."
                        : "Middle — deeper neighborhoods, day trips or themes not yet covered.";
        return """
                CHUNK NARRATIVE (days %d–%d of %d in %s)
                - Phase: %s
                - Plan a coherent mini-arc across these days (theme per day, logical geography).
                - If a day trip fits, make it explicit with realistic timing; otherwise stay city-specific.
                """
                .formatted(tripDayStart, tripDayEnd, totalDays, destination, phase);
    }

    private static String hardRulesBlock(
            TripPromptContext ctx,
            int daysInScope,
            String rangeStart,
            String rangeEnd,
            boolean chunked,
            int chunkIndex,
            int totalChunks,
            int tripDayStart) {
        String scopeRule = chunked
                ? "- Output exactly " + daysInScope + " day objects for dates " + rangeStart + " through " + rangeEnd + " only.\n"
                : "- en.dayPlans and he.dayPlans must each have exactly " + daysInScope + " objects.\n"
                        + "- Cover every date from " + rangeStart + " through " + rangeEnd + ".\n";

        String pacingHint = tripDayStart > 1
                ? "- Trip day " + tripDayStart + "+: travelers are settled — you can plan fuller days, but keep commutes realistic.\n"
                : "";

        return """
                HARD RULES
                1) Constraints are STRICT: %s
                2) Interests are HIGH PRIORITY throughout.
                3) Schema discipline:
                   - Each day: exactly THREE blocks with field "timeBlock" (NOT "title"): MORNING, AFTERNOON, EVENING.
                   - Item type must be exactly: FOOD, ATTRACTION, TRANSIT, or NOTE (never ACTIVITY).
                   - NEVER return a block with an empty "items" array.
                %s
                4) %s
                5) Do NOT invent latitude/longitude — use null on location.lat and location.lng.
                6) If include directions is true, every item needs transit with non-empty directions.
                7) "en" in English, "he" in proper Hebrew (not transliteration unless a proper name).
                %s
                """
                .formatted(
                        ctx.constraints(),
                        scopeRule,
                        chunked
                                ? "Chunk " + chunkIndex + "/" + totalChunks + " — same quality standard as chunk 1."
                                : "Complete all calendar days — do not stop early.",
                        pacingHint);
    }

    private static String transportRulesBlock(TripPromptContext ctx) {
        return """
                TRANSPORT
                - Preference: %s — WALKING→WALK, PUBLIC_TRANSPORT→METRO/BUS/TRAM/TRAIN, TAXI→TAXI, CAR→CAR, MIXED→best per leg.
                - transit.mode MUST be exactly one of: WALK, METRO, BUS, TRAM, TRAIN, TAXI, CAR, TRANSFER, MIXED
                  (never BTS, MRT, BOAT, FERRY, etc. — use METRO for rail/subway, TRANSFER for ferries/boats).
                - Hotel base: %s (%s) — use as default "from" for first activity when relevant.

                """
                .formatted(ctx.transportPref(), ctx.hotelName(), ctx.hotelAddress());
    }

    private static String jsonSchemaBlock() {
        return """
                OUTPUT JSON SCHEMA (exact)
                {
                  "en": {
                    "dayPlans": [
                      {
                        "date": "yyyy-MM-dd",
                        "title": "string (specific, English)",
                        "blocks": [
                          {
                            "timeBlock": "MORNING|AFTERNOON|EVENING",
                            "items": [
                              {
                                "type": "FOOD|ATTRACTION|TRANSIT|NOTE",
                                "name": "string (specific venue or place name, English)",
                                "location": { "name": "neighborhood or area", "lat": null, "lng": null },
                                "notes": "string (detailed, English)",
                                "transit": {
                                  "from": "string",
                                  "mode": "WALK|METRO|BUS|TRAM|TRAIN|TAXI|CAR|TRANSFER|MIXED",
                                  "estimatedMinutes": 0,
                                  "directions": "string"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  "he": { "dayPlans": [ same structure, Hebrew text ] }
                }

                """;
    }
}
