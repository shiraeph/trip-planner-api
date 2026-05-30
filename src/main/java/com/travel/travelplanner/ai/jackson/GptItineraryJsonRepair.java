package com.travel.travelplanner.ai.jackson;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import com.travel.travelplanner.ai.dto.BilingualItinerary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Normalizes common GPT JSON shape differences before binding to domain types.
 */
public final class GptItineraryJsonRepair {

    private GptItineraryJsonRepair() {
    }

    public static BilingualItinerary parse(ObjectMapper mapper, String json) {
        String normalized = normalizeRawJson(json);
        Exception lastError = null;
        for (String candidate : new String[] { normalized, closeTruncatedJson(normalized) }) {
            try {
                JsonNode root = mapper.readTree(candidate);
                repair(root);
                return mapper.treeToValue(root, BilingualItinerary.class);
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new RuntimeException("Failed to parse GPT response into BilingualItinerary. Raw:\n" + normalized, lastError);
    }

    /**
     * Strips markdown fences and keeps the outermost JSON object from the model output.
     */
    public static String normalizeRawJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```\\s*$", "");
            trimmed = trimmed.trim();
        }
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return trimmed;
        }
        return trimmed.substring(start).trim();
    }

    /**
     * Closes any unbalanced strings/brackets when the model hits its output token limit mid-JSON.
     */
    static String closeTruncatedJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        boolean inString = false;
        boolean escape = false;
        Deque<Character> stack = new ArrayDeque<>();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' && !stack.isEmpty() && stack.peek() == '{') {
                stack.pop();
            } else if (c == ']' && !stack.isEmpty() && stack.peek() == '[') {
                stack.pop();
            }
        }
        StringBuilder repaired = new StringBuilder(json);
        if (inString) {
            repaired.append('"');
        }
        while (!stack.isEmpty()) {
            char open = stack.pop();
            repaired.append(open == '{' ? '}' : ']');
        }
        return repaired.toString();
    }

    static void repair(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        repairItinerary(root.get("en"));
        repairItinerary(root.get("he"));
    }

    private static void repairItinerary(JsonNode itinerary) {
        if (itinerary == null || !itinerary.isObject()) {
            return;
        }
        JsonNode dayPlans = itinerary.get("dayPlans");
        if (dayPlans == null || !dayPlans.isArray()) {
            return;
        }
        for (JsonNode day : dayPlans) {
            repairDay(day);
        }
    }

    private static void repairDay(JsonNode day) {
        if (day == null || !day.isObject()) {
            return;
        }
        JsonNode blocks = day.get("blocks");
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (JsonNode block : blocks) {
            repairBlock(block);
        }
    }

    private static void repairBlock(JsonNode block) {
        if (block == null || !block.isObject()) {
            return;
        }
        ObjectNode blockObj = (ObjectNode) block;
        if (!blockObj.hasNonNull("timeBlock") && blockObj.has("title")) {
            String mapped = mapTimeBlock(blockObj.get("title").asString());
            if (mapped != null) {
                blockObj.put("timeBlock", mapped);
            }
            blockObj.remove("title");
        }

        JsonNode items = block.get("items");
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            repairItem(item);
        }
    }

    private static void repairItem(JsonNode item) {
        if (item == null || !item.isObject()) {
            return;
        }
        ObjectNode itemObj = (ObjectNode) item;

        if (itemObj.has("type")) {
            itemObj.put("type", mapItemType(itemObj.get("type").asString()));
        }

        if (!itemObj.has("location") && (itemObj.has("lat") || itemObj.has("lng"))) {
            ObjectNode location = itemObj.objectNode();
            location.putNull("lat");
            location.putNull("lng");
            if (itemObj.has("name")) {
                location.put("name", itemObj.get("name").asString());
            }
            itemObj.set("location", location);
        }
        itemObj.remove("lat");
        itemObj.remove("lng");

        JsonNode transit = itemObj.get("transit");
        if (transit != null && transit.isObject()) {
            ObjectNode transitObj = (ObjectNode) transit;
            if (transitObj.has("mode")) {
                transitObj.put("mode", mapTransitMode(transitObj.get("mode").asString()));
            }
        }
    }

    /**
     * GPT often invents modes (BTS, BOAT, etc.). Map to {@link com.travel.travelplanner.trip.domain.enums.TransitMode}.
     */
    private static String mapTransitMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MIXED";
        }
        String n = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (n) {
            case "BTS", "SKYTRAIN", "SKY_TRAIN", "MRT", "SUBWAY", "UNDERGROUND", "U_BAHN", "S_BAHN", "LIGHT_RAIL" ->
                    "METRO";
            case "BOAT", "FERRY", "SHIP", "CRUISE", "SPEEDBOAT", "WATER_TAXI" -> "TRANSFER";
            case "TUKTUK", "TUK_TUK", "MOTORBIKE", "SCOOTER", "RICKSHAW" -> "TAXI";
            case "BICYCLE", "BIKE", "CYCLING" -> "WALK";
            case "RIDESHARE", "RIDE_SHARE", "UBER", "GRAB", "LYFT" -> "TAXI";
            case "SHUTTLE", "MINIBUS", "VAN", "COACH" -> "BUS";
            case "WALKING", "ON_FOOT", "FOOT" -> "WALK";
            case "WALK", "METRO", "BUS", "TRAM", "TRAIN", "TAXI", "CAR", "TRANSFER", "MIXED" -> n;
            default -> "MIXED";
        };
    }

    private static String mapTimeBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.contains("MORNING") || "בוקר".equals(trimmed)) {
            return "MORNING";
        }
        if (upper.contains("AFTERNOON") || upper.contains("MIDDAY")
                || "צהריים".equals(trimmed) || "אחר הצהריים".equals(trimmed)) {
            return "AFTERNOON";
        }
        if (upper.contains("EVENING") || upper.contains("NIGHT") || "ערב".equals(trimmed)) {
            return "EVENING";
        }
        if ("MORNING".equals(upper) || "AFTERNOON".equals(upper) || "EVENING".equals(upper)) {
            return upper;
        }
        return "MORNING";
    }

    private static String mapItemType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NOTE";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "ACTIVITY", "ACTIVITIES", "EXPERIENCE", "EXPERIENCES", "TOUR", "TOURS", "SIGHTSEEING" -> "ATTRACTION";
            case "RESTAURANT", "RESTAURANTS", "DINING", "MEAL", "MEALS", "CAFE", "COFFEE", "BAR" -> "FOOD";
            case "TRAVEL", "TRANSPORT", "TRANSPORTATION", "COMMUTE", "TRANSFER" -> "TRANSIT";
            case "REMINDER", "TIP", "TIPS", "FREE_TIME", "LEISURE", "RELAX" -> "NOTE";
            case "ATTRACTION", "FOOD", "TRANSIT", "NOTE" -> normalized;
            default -> "NOTE";
        };
    }
}
