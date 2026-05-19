package com.travel.travelplanner.ai.jackson;

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
        try {
            JsonNode root = mapper.readTree(json);
            repair(root);
            return mapper.treeToValue(root, BilingualItinerary.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GPT response into BilingualItinerary. Raw:\n" + json, e);
        }
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
