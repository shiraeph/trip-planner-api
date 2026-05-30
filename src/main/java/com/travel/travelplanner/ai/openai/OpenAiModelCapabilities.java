package com.travel.travelplanner.ai.openai;

public final class OpenAiModelCapabilities {

    private OpenAiModelCapabilities() {
    }

    public static boolean usesMaxCompletionTokens(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.toLowerCase();
        return normalized.startsWith("gpt-5")
                || normalized.startsWith("gpt-4o")
                || normalized.startsWith("o1")
                || normalized.startsWith("o3");
    }

    public static boolean supportsTemperature(String model) {
        if (model == null || model.isBlank()) {
            return true;
        }
        String normalized = model.toLowerCase();
        if (normalized.contains("-chat")) {
            return true;
        }
        return !(normalized.startsWith("gpt-5")
                || normalized.startsWith("o1")
                || normalized.startsWith("o3"));
    }
}
