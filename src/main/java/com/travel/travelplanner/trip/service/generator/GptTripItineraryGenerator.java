package com.travel.travelplanner.trip.service.generator;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.travel.travelplanner.ai.dto.BilingualItinerary;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatRequest;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatResponse;
import com.travel.travelplanner.ai.openai.dto.OpenAiMessage;
import com.travel.travelplanner.ai.prompt.BuildPromptService;
import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.service.TripDates;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GptTripItineraryGenerator implements TripItineraryGenerator {
    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final BuildPromptService buildPromptService;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens:16384}")
    private int configuredMaxTokens;

    @Override
    public BilingualItinerary generate(TripPlan tripPlan) {
        String prompt = buildPromptService.buildPrompt(tripPlan);
        String response = callGptAndParse(tripPlan, prompt);
        try {
            return objectMapper.readValue(response, BilingualItinerary.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse GPT response into BilingualItinerary. Raw:\n" + response, e);
        }
    }

    @Override
    public BilingualItinerary generateFix(TripPlan tripPlan, List<String> violations) {
        String fixPrompt = buildPromptService.buildFixPrompt(tripPlan, violations);
        String response = callGptAndParse(tripPlan, fixPrompt);
        try {
            return objectMapper.readValue(response, BilingualItinerary.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse GPT response into BilingualItinerary. Raw:\n" + response, e);
        }
    }

    private String callGptAndParse(TripPlan tripPlan, String prompt) {
        OpenAiChatRequest request = new OpenAiChatRequest();
        request.setModel(model);
        request.setTemperature(0.2);
        request.setMaxTokens(resolveMaxTokens(tripPlan));
        request.setMessages(List.of(
                new OpenAiMessage("system",
                        "You are a travel itinerary planner Return ONLY valid JSON (no markdown, no explanation)."),
                new OpenAiMessage("user", prompt)));
        OpenAiChatResponse response;
        try {
            response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .block();

        } catch (WebClientResponseException e) {
            System.out.println("OpenAI status: " + e.getStatusCode());
            System.out.println("OpenAI response body: " + e.getResponseBodyAsString());
            throw e;
        }
        String json = Objects.nonNull(response) ? response.firstContent() : null;
        if (Objects.isNull(json)) {
            throw new RuntimeException("GPT returned empty response");
        }
        return sanitizeToJsonObject(json);
    }

    /** Scale completion budget with trip length (bilingual JSON is large). */
    private int resolveMaxTokens(TripPlan tripPlan) {
        int days = TripDates.inclusiveDayCount(tripPlan);
        int estimated = 2500 + Math.max(days, 1) * 1300;
        return Math.min(configuredMaxTokens, Math.max(estimated, 4096));
    }

    private String sanitizeToJsonObject(String raw) {
        if (Objects.isNull(raw)) {
            return null;
        }
        String trimRaw = raw.trim();
        if (trimRaw.startsWith("```")) {
            trimRaw = trimRaw.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimRaw = trimRaw.replaceFirst("\\s*```\\s*$", "");
            trimRaw = trimRaw.trim();
        }

        int start = trimRaw.indexOf('{');
        int end = trimRaw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimRaw.substring(start, end + 1).trim();
        }

        return trimRaw.trim();
    }
}
