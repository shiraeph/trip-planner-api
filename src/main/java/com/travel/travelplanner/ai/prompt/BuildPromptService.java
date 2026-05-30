package com.travel.travelplanner.ai.prompt;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;

@Service
public class BuildPromptService {

    /**
     * Stable instruction prefix for bilingual single-pass (legacy / split-languages=false).
     */
    public String buildSystemMessage(TripPlan tripPlan) {
        return buildSystemMessageBilingual(tripPlan);
    }

    /** Stable English planning prefix — identical across chunks for prompt caching. */
    public String buildSystemMessageEnglish(TripPlan tripPlan) {
        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        TripPromptContext ctx = TripPromptContext.from(tripPlan, dateList, tripDays);
        String destination = ctx.destination() != null ? ctx.destination() : "the destination";

        return """
                You are an expert professional travel planner for %s.
                Trip dates: %s to %s (%d days). Every day must reflect season, holidays, and local events — not a generic template.
                Use real venue names and concrete details — never generic filler.

                OUTPUT: Return ONLY valid JSON (no markdown, no commentary). Match the schema exactly.
                Generate the itinerary in English only.

                """
                .formatted(destination, ctx.start(), ctx.end(), tripDays)
                + preferencesBlock(ctx)
                + trekkingGuidanceBlock(tripPlan)
                + qualityBarBlock(ctx)
                + dateAwarenessRulesBlock()
                + hardRulesBaseBlockEnglish(ctx)
                + transportRulesBlock(ctx)
                + englishJsonSchemaBlock();
    }

    /** Stable Hebrew translation prefix. */
    public String buildSystemMessageHebrewTranslation(TripPlan tripPlan) {
        String destination = tripPlan.getDestination() != null ? tripPlan.getDestination() : "the destination";
        return """
                You are a professional Hebrew translator for travel itineraries in %s.
                You receive a complete English itinerary JSON and translate it to Hebrew.

                OUTPUT: Return ONLY valid JSON (no markdown, no commentary). Match the schema exactly.
                Preserve IDENTICAL structure: same number of dayPlans, blocks, and items in the same order.
                Preserve every date, timeBlock, item type, transit.mode, and estimatedMinutes exactly.
                Translate to Hebrew: day titles, item names (use Hebrew for generic places; keep well-known proper names recognizable),
                notes, openingHours text, price strings, averagePricePerDish strings, location.name, transit.from, transit.directions.
                Do NOT add, remove, or reorder days, blocks, or items.

                """
                .formatted(destination)
                + hebrewJsonSchemaBlock();
    }

    private String buildSystemMessageBilingual(TripPlan tripPlan) {
        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        TripPromptContext ctx = TripPromptContext.from(tripPlan, dateList, tripDays);
        String destination = ctx.destination() != null ? ctx.destination() : "the destination";

        return """
                You are an expert professional travel planner for %s.
                Trip dates: %s to %s (%d days). Every day must reflect season, holidays, and local events — not a generic template.
                Use real venue names and concrete details — never generic filler.

                OUTPUT: Return ONLY valid JSON (no markdown, no commentary). Match the schema exactly.
                Generate the itinerary in TWO languages: English ("en") and Hebrew ("he") with identical structure.
                All content (titles, names, notes, openingHours, price fields, directions) must be in the respective language.

                """
                .formatted(destination, ctx.start(), ctx.end(), tripDays)
                + preferencesBlock(ctx)
                + trekkingGuidanceBlock(tripPlan)
                + qualityBarBlock(ctx)
                + dateAwarenessRulesBlock()
                + hardRulesBaseBlock(ctx)
                + transportRulesBlock(ctx)
                + jsonSchemaBlock();
    }

    /** User message for English single-pass generation. */
    public String buildPromptEnglish(TripPlan tripPlan) {
        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        TripPromptContext ctx = TripPromptContext.from(tripPlan, dateList, tripDays);

        return tripInputBlock(ctx, tripDays, tripDays, dateList)
                + dateAwarenessCalendarBlock(tripPlan, calendar, ctx)
                + hardRulesScopeBlockEnglish(ctx, tripDays, ctx.start(), ctx.end(), false, 0, 0, 0)
                + "\nGenerate the English itinerary for all dates above.\n";
    }

    /** User message for a single-pass (≤ threshold days) bilingual generation. */
    public String buildPrompt(TripPlan tripPlan) {
        int tripDays = TripDates.inclusiveDayCount(tripPlan);
        List<LocalDate> calendar = TripDates.eachDay(tripPlan);
        String dateList = calendar.stream().map(LocalDate::toString).collect(Collectors.joining(", "));
        TripPromptContext ctx = TripPromptContext.from(tripPlan, dateList, tripDays);

        return tripInputBlock(ctx, tripDays, tripDays, dateList)
                + dateAwarenessCalendarBlock(tripPlan, calendar, ctx)
                + hardRulesScopeBlock(ctx, tripDays, ctx.start(), ctx.end(), false, 0, 0, 0)
                + "\nGenerate the bilingual itinerary for all dates above.\n";
    }

    public String buildChunkPromptEnglish(
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

        return """
                THIS CHUNK ONLY (STRICT)
                - Chunk %d of %d — trip days %d–%d of %d.
                - Output dayPlans for exactly these %d dates: %s
                - Date range: %s to %s
                - dayPlans must have exactly %d entries — no more, no less.
                - Do NOT include days outside this chunk.
                - Same quality standard as chunk 1 — no shortcuts or vaguer descriptions.

                %s
                %s
                """
                        .formatted(
                                chunkIndex, totalChunks, tripDayStart, tripDayEnd, totalTripDays,
                                chunkDays, chunkDateList, chunkStart, chunkEnd, chunkDays,
                                continuity, chunkArc)
                + dateAwarenessCalendarBlock(tripPlan, chunkDates, ctx)
                + hardRulesScopeBlockEnglish(ctx, chunkDays, chunkStart, chunkEnd, true, chunkIndex, totalChunks, tripDayStart)
                + "\nGenerate the English itinerary for this chunk only.\n";
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

        return """
                THIS CHUNK ONLY (STRICT)
                - Chunk %d of %d — trip days %d–%d of %d.
                - Output dayPlans for exactly these %d dates: %s
                - Date range: %s to %s
                - en.dayPlans and he.dayPlans must each have exactly %d entries — no more, no less.
                - Do NOT include days outside this chunk.
                - Same quality standard as chunk 1 — no shortcuts or vaguer descriptions.

                %s
                %s
                """
                        .formatted(
                                chunkIndex, totalChunks, tripDayStart, tripDayEnd, totalTripDays,
                                chunkDays, chunkDateList, chunkStart, chunkEnd, chunkDays,
                                continuity, chunkArc)
                + dateAwarenessCalendarBlock(tripPlan, chunkDates, ctx)
                + hardRulesScopeBlock(ctx, chunkDays, chunkStart, chunkEnd, true, chunkIndex, totalChunks, tripDayStart)
                + "\nGenerate the bilingual itinerary for this chunk only.\n";
    }

    public String buildHebrewTranslationPrompt(String englishItineraryJson, int daysInPass) {
        return """
                Translate the following English itinerary JSON to Hebrew.
                Output exactly %d dayPlans — same dates and structure as the English input.

                ENGLISH ITINERARY:
                %s
                """
                .formatted(daysInPass, englishItineraryJson);
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

    private static String tripInputBlock(TripPromptContext ctx, int daysInScope, int totalDays, String dateList) {
        return """
                TRIP INPUT
                - Destination: %s
                - Dates: %s to %s (inclusive)
                - Trip length: %d days — dayPlans MUST contain exactly %d entries (one per calendar day)
                - Required dates in order: %s
                - Each dayPlan.date MUST match the assigned calendar date exactly (yyyy-MM-dd).
                - Group: %s
                - Travel style: %s
                - Budget: %s

                """
                .formatted(
                        ctx.destination(), ctx.start(), ctx.end(),
                        totalDays, daysInScope, dateList,
                        ctx.groupSummary(), ctx.travelStyle(), ctx.budget());
    }

    /** Static date-awareness rules — lives in system message for caching. */
    private static String dateAwarenessRulesBlock() {
        return """
                DATE-AWARE PLANNING (HIGHEST PRIORITY)
                The traveler chose exact dates on purpose. The itinerary must feel timely and local — not generic.

                Before choosing activities, consider for the destination and date range:
                1) Season (correct hemisphere), holidays, religious observances
                2) Festivals, markets, concerts, sporting events during this window
                3) Closures or reduced hours on specific weekdays or holidays
                4) Seasonal food, weather, and atmosphere for pacing and tips

                INTEGRATION RULES:
                - Prioritize experiences that only make sense (or are best) during these dates.
                - Spread time-sensitive events across the trip; schedule each on the correct dayPlan.date.
                - On public holidays: note closures and offer strong alternatives.
                - Day titles should reference date-aware themes when relevant.

                """;
    }

    /** Trip-specific calendar snapshot — varies per user message / chunk. */
    private static String dateAwarenessCalendarBlock(
            TripPlan tripPlan,
            List<LocalDate> datesInScope,
            TripPromptContext ctx) {
        String calendarSummary = buildCalendarSummary(tripPlan, datesInScope);
        return """
                CALENDAR FOR THIS REQUEST
                %s

                """
                .formatted(calendarSummary);
    }

    private static String buildCalendarSummary(TripPlan tripPlan, List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return "- Calendar: (dates unavailable — infer season from destination and stated range)";
        }
        LocalDate first = dates.get(0);
        LocalDate last = dates.get(dates.size() - 1);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);

        String months = dates.stream()
                .map(d -> d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .distinct()
                .collect(Collectors.joining(", "));

        String weekdays = dates.stream()
                .map(d -> d.getDayOfWeek().toString().substring(0, 3))
                .distinct()
                .collect(Collectors.joining(", "));

        String dateLines = dates.size() <= 14
                ? dates.stream().map(d -> "  • " + d + " (" + d.format(dayFmt) + ")").collect(Collectors.joining("\n"))
                : "  • " + first + " (" + first.format(dayFmt) + ") … " + last + " (" + last.format(dayFmt) + ")"
                        + " (" + dates.size() + " days total; plan each date individually)";

        String seasonNote = seasonHint(first, last, tripPlan.getDestination());

        return """
                - First day: %s (%s)
                - Last day: %s (%s)
                - Months covered: %s
                - Weekdays in trip: %s
                - Season context: %s
                - Every day in scope:
                %s
                """
                .formatted(
                        first,
                        first.format(dayFmt),
                        last,
                        last.format(dayFmt),
                        months,
                        weekdays,
                        seasonNote,
                        dateLines);
    }

    private static String seasonHint(LocalDate start, LocalDate end, String destination) {
        String dest = destination != null ? destination : "";
        String monthsSpan = monthRangeLabel(start.getMonth(), end.getMonth());
        return monthsSpan
                + " — infer correct season for "
                + dest
                + " (Northern vs Southern Hemisphere). Match activities to weather and daylight.";
    }

    private static String monthRangeLabel(Month start, Month end) {
        if (start == end) {
            return start.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }
        return start.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " through "
                + end.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String preferencesBlock(TripPromptContext ctx) {
        return """
                TRAVELER PREFERENCES
                - Interests (prioritize across the whole trip): %s
                - Constraints (STRICT — never violate): %s
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

    private static String trekkingGuidanceBlock(TripPlan tripPlan) {
        List<String> keys = tripPlan.getTripPreferences() != null
                && tripPlan.getTripPreferences().getInterests() != null
                        ? tripPlan.getTripPreferences().getInterests()
                        : List.of();
        if (keys.contains("trekkingDifficult")) {
            return """
                    
                    TREKKING PREFERENCE — DIFFICULT (active; shape several days around this)
                    Include challenging outdoor hiking that matches the destination:
                    - Full-day or half-day mountain hikes, ridge trails, canyon routes, or long coastal cliff paths
                    - Typical range: 10–18 km, 500–1200m elevation gain, 4–8 hours including breaks
                    - Name specific trails, national parks, nature reserves, and summit viewpoints — never generic "hike"
                    - Schedule as the anchor MORNING or full-day block; lighter food/evening activities afterward
                    - Note fitness level, weather, gear (boots, water, layers), and seasonal trail conditions in notes
                    - Avoid flat city strolls as the main "hike"; prioritize strenuous outdoor routes
                    """;
        }
        if (keys.contains("trekkingModerate") || keys.contains("hiking")) {
            return """
                    
                    TREKKING PREFERENCE — MODERATE (active; include on multiple days)
                    Include balanced hiking and nature experiences:
                    - Forest, hill, and coastal trails typically 4–10 km, 150–500m elevation, 2–4 hours
                    - National parks, nature reserves, scenic viewpoints, and lake or valley loops
                    - Mix well-known trails with lesser-known local routes; name each trail or park explicitly
                    - Schedule in MORNING or AFTERNOON blocks with realistic travel time from the hotel area
                    - Combine with food stops or cultural sights nearby — but keep the hike as a clear anchor activity
                    """;
        }
        if (keys.contains("trekkingEasy")) {
            return """
                    
                    TREKKING PREFERENCE — EASY (active; include on multiple days)
                    Include gentle outdoor walking and nature experiences suitable for most fitness levels:
                    - Short marked trails, promenades, botanical gardens, lakeside paths, and scenic viewpoints
                    - Typical range: 1–4 km, minimal elevation (under ~150m), 1–2.5 hours including breaks
                    - Paved or well-maintained paths; avoid alpine routes, via ferrata, or backcountry treks
                    - Name specific parks, nature reserves, and easy trails near the destination
                    - Ideal for MORNING or AFTERNOON blocks; pair with nearby cafés or relaxed sightseeing
                    """;
        }
        return "";
    }

    private static String qualityBarBlock(TripPromptContext ctx) {
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
                - DATE-AWARE: Each day must reflect that calendar date's season, holidays, and local events.
                - In notes, briefly explain WHY this activity fits THIS date.

                ITEM DETAIL REQUIREMENTS (use separate JSON fields — NEVER put hours or price inside notes)
                - ATTRACTION:
                  - notes: DETAILED description only (3–5 sentences, min ~80 characters). Include:
                    why it is worth visiting, what to see/do, suggested duration, one practical tip, and why it fits THIS date.
                  - openingHours: time range only (e.g. "09:00 - 18:00" or "varies").
                  - price: ticket/entry cost only (e.g. "€15-20 per person" or "free").
                  - averagePricePerDish: null
                  - location.name = neighborhood or address area in %s.
                - FOOD (restaurants/cafés):
                  - notes: DETAILED description only (2–4 sentences, min ~60 characters). Cuisine, atmosphere,
                    1–2 specific dishes/drinks to order, and why it fits the day.
                  - openingHours: time range only (e.g. "12:00 - 23:00").
                  - averagePricePerDish: cost range only (e.g. "€12-18").
                  - price: null
                - TRANSIT / NOTE: openingHours, price, averagePricePerDish: null; notes brief if helpful.
                - TRANSIT: Clear route; directions name lines/stops/landmarks where possible.
                - Each block: 2–3 items (one anchor experience + food/transit as needed). Never empty items arrays.

                VARIETY & REALISM
                - Mix icons with hidden gems; include at least one everyday local ritual (market, bakery, promenade).
                - No duplicate venue names across the trip.
                - Day titles: evocative and specific, not "Day 5 Sightseeing".

                """
                .formatted(
                        ctx.destination(),
                        ctx.interests(),
                        ctx.budget(),
                        ctx.travelStyle(),
                        ctx.destination());
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

    /** Scope-specific rules that vary per request — user message only. */
    private static String hardRulesScopeBlock(
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
                ? "- Trip day " + tripDayStart + "+: travelers are settled — plan fuller days, keep commutes realistic.\n"
                : "";

        String completionRule = chunked
                ? "Chunk " + chunkIndex + "/" + totalChunks + " — complete all dates in this chunk."
                : "Complete all calendar days — do not stop early.";

        return """
                SCOPE FOR THIS REQUEST
                %s
                %s
                %s

                """
                .formatted(scopeRule, pacingHint, completionRule);
    }

    /** Static hard rules — system message. */
    private static String hardRulesScopeBlockEnglish(
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
                : "- dayPlans must have exactly " + daysInScope + " objects.\n"
                        + "- Cover every date from " + rangeStart + " through " + rangeEnd + ".\n";

        String pacingHint = tripDayStart > 1
                ? "- Trip day " + tripDayStart + "+: travelers are settled — plan fuller days, keep commutes realistic.\n"
                : "";

        String completionRule = chunked
                ? "Chunk " + chunkIndex + "/" + totalChunks + " — complete all dates in this chunk."
                : "Complete all calendar days — do not stop early.";

        return """
                SCOPE FOR THIS REQUEST
                %s
                %s
                %s

                """
                .formatted(scopeRule, pacingHint, completionRule);
    }

    /** Static hard rules for English generation — system message. */
    private static String hardRulesBaseBlockEnglish(TripPromptContext ctx) {
        return hardRulesBaseBlock(ctx).replace(
                "6) \"en\" in English, \"he\" in proper Hebrew (not transliteration unless a proper name).",
                "6) All text in English.");
    }

    private static String hardRulesBaseBlock(TripPromptContext ctx) {
        return """
                HARD RULES
                1) Constraints are STRICT: %s
                2) Interests are HIGH PRIORITY throughout.
                2b) DATE-AWARENESS is HIGH PRIORITY: anchor to the exact dates provided in each request.
                3) Schema discipline:
                   - Each day: exactly THREE blocks with field "timeBlock" (NOT "title"): MORNING, AFTERNOON, EVENING.
                   - Item type must be exactly: FOOD, ATTRACTION, TRANSIT, or NOTE (never ACTIVITY).
                   - NEVER return a block with an empty "items" array.
                   - ATTRACTION/FOOD: openingHours and price (or averagePricePerDish) MUST be separate JSON fields, not inside notes.
                4) Do NOT invent latitude/longitude — use null on location.lat and location.lng.
                5) If include directions is true, every item needs transit with non-empty directions.
                6) "en" in English, "he" in proper Hebrew (not transliteration unless a proper name).

                """
                .formatted(ctx.constraints());
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

    private static String englishJsonSchemaBlock() {
        return """
                OUTPUT JSON SCHEMA (exact)
                {
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
                              "notes": "string (detailed description ONLY — no hours/price here)",
                              "openingHours": "string or null (e.g. 09:00 - 18:00)",
                              "price": "string or null (ATTRACTION ticket price)",
                              "averagePricePerDish": "string or null (FOOD only)",
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
                }

                """;
    }

    private static String hebrewJsonSchemaBlock() {
        return """
                OUTPUT JSON SCHEMA (exact)
                {
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
                              "location": { "name": "neighborhood or area in Hebrew", "lat": null, "lng": null },
                              "notes": "string (Hebrew, detailed description ONLY)",
                              "openingHours": "string or null",
                              "price": "string or null",
                              "averagePricePerDish": "string or null",
                              "transit": {
                                "from": "string (Hebrew)",
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

                """;
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
                                "notes": "string (detailed description ONLY — no hours/price here)",
                                "openingHours": "string or null (e.g. 09:00 - 18:00)",
                                "price": "string or null (ATTRACTION ticket price)",
                                "averagePricePerDish": "string or null (FOOD only)",
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
