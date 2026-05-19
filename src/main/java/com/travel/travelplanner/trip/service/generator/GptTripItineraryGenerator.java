package com.travel.travelplanner.trip.service.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.travel.travelplanner.ai.dto.BilingualItinerary;
import com.travel.travelplanner.ai.jackson.GptItineraryJsonRepair;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatRequest;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatResponse;
import com.travel.travelplanner.ai.openai.dto.OpenAiMessage;
import com.travel.travelplanner.ai.prompt.BuildPromptService;
import com.travel.travelplanner.ai.prompt.ChunkContinuityBuilder;
import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.service.TripDates;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GptTripItineraryGenerator implements TripItineraryGenerator {

    private static final Logger log = LoggerFactory.getLogger(GptTripItineraryGenerator.class);

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final BuildPromptService buildPromptService;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens:16384}")
    private int configuredMaxTokens;

    @Value("${trip.itinerary.chunk-threshold-days:9}")
    private int chunkThresholdDays;

    @Value("${trip.itinerary.chunk-size-days:7}")
    private int chunkSizeDays;

    @Override
    public BilingualItinerary generate(TripPlan tripPlan) {
        if (shouldChunk(tripPlan)) {
            return generateChunked(tripPlan);
        }
        return generateSinglePass(tripPlan, buildPromptService.buildPrompt(tripPlan));
    }

    @Override
    public BilingualItinerary generateFix(TripPlan tripPlan, List<String> violations) {
        if (shouldChunk(tripPlan)) {
            log.info("Long trip ({} days): regenerating all chunks after validation issues",
                    TripDates.inclusiveDayCount(tripPlan));
            return generateChunked(tripPlan);
        }
        String fixPrompt = buildPromptService.buildFixPrompt(tripPlan, violations);
        return generateSinglePass(tripPlan, fixPrompt);
    }

    private boolean shouldChunk(TripPlan tripPlan) {
        return TripDates.inclusiveDayCount(tripPlan) > chunkThresholdDays;
    }

    private BilingualItinerary generateChunked(TripPlan tripPlan) {
        List<LocalDate> allDates = TripDates.eachDay(tripPlan);
        List<List<LocalDate>> chunks = TripDates.chunk(allDates, chunkSizeDays);
        int totalChunks = chunks.size();

        log.info("Generating {}-day trip in {} chunk(s) of up to {} days each",
                allDates.size(), totalChunks, chunkSizeDays);

        Itinerary en = new Itinerary();
        en.setDayPlans(new ArrayList<>());
        Itinerary he = new Itinerary();
        he.setDayPlans(new ArrayList<>());

        String continuityHint = null;
        for (int i = 0; i < chunks.size(); i++) {
            List<LocalDate> chunkDates = chunks.get(i);
            int chunkNum = i + 1;
            log.info("Trip chunk {}/{}: {} ({} days)", chunkNum, totalChunks,
                    chunkDates.get(0) + " → " + chunkDates.get(chunkDates.size() - 1), chunkDates.size());

            String prompt = buildPromptService.buildChunkPrompt(
                    tripPlan, chunkDates, chunkNum, totalChunks, continuityHint);
            BilingualItinerary chunk = generateSinglePass(tripPlan, prompt, chunkDates.size());

            if (chunk.getEn() != null && chunk.getEn().getDayPlans() != null) {
                en.getDayPlans().addAll(chunk.getEn().getDayPlans());
            }
            if (chunk.getHe() != null && chunk.getHe().getDayPlans() != null) {
                he.getDayPlans().addAll(chunk.getHe().getDayPlans());
            }

            continuityHint = ChunkContinuityBuilder.build(chunk.getEn());
        }

        return new BilingualItinerary(en, he);
    }

    private BilingualItinerary generateSinglePass(TripPlan tripPlan, String prompt) {
        int days = TripDates.inclusiveDayCount(tripPlan);
        return generateSinglePass(tripPlan, prompt, days);
    }

    private BilingualItinerary generateSinglePass(TripPlan tripPlan, String prompt, int daysInPass) {
        String response = callGpt(tripPlan, prompt, daysInPass);
        return GptItineraryJsonRepair.parse(objectMapper, response);
    }

    private String callGpt(TripPlan tripPlan, String prompt, int daysInPass) {
        OpenAiChatRequest request = new OpenAiChatRequest();
        request.setModel(model);
        request.setTemperature(0.35);
        request.setMaxTokens(resolveMaxTokens(daysInPass));
        request.setMessages(List.of(
                new OpenAiMessage("system", buildPromptService.buildSystemMessage(tripPlan)),
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
            log.error("OpenAI status: {} body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        String json = Objects.nonNull(response) ? response.firstContent() : null;
        if (Objects.isNull(json)) {
            throw new RuntimeException("GPT returned empty response");
        }
        return sanitizeToJsonObject(json);
    }

    private int resolveMaxTokens(int daysInPass) {
        // ~1.8k tokens per day leaves room for detailed bilingual notes per item.
        int estimated = 2800 + Math.max(daysInPass, 1) * 1800;
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
