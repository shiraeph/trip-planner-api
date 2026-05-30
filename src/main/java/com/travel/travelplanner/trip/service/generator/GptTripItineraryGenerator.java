package com.travel.travelplanner.trip.service.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.travel.travelplanner.ai.dto.BilingualItinerary;
import com.travel.travelplanner.ai.jackson.GptItineraryJsonRepair;
import com.travel.travelplanner.ai.openai.OpenAiModelCapabilities;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatRequest;
import com.travel.travelplanner.ai.openai.dto.OpenAiChatResponse;
import com.travel.travelplanner.ai.openai.dto.OpenAiMessage;
import com.travel.travelplanner.ai.prompt.BuildPromptService;
import com.travel.travelplanner.ai.prompt.ChunkContinuityBuilder;
import com.travel.travelplanner.trip.domain.GenerationProgress;
import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.enums.GenerationStage;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.service.TripDates;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
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

    @Value("${openai.translation.model:${openai.model}}")
    private String translationModel;

    @Value("${openai.max-tokens:32768}")
    private int configuredMaxTokens;

    @Value("${trip.itinerary.chunk-threshold-days:6}")
    private int chunkThresholdDays;

    @Value("${trip.itinerary.chunk-size-days:3}")
    private int chunkSizeDays;

    @Value("${trip.itinerary.split-languages:true}")
    private boolean splitLanguages;

    @Override
    public BilingualItinerary generate(TripPlan tripPlan) {
        return generate(tripPlan, null);
    }

    @Override
    public BilingualItinerary generate(TripPlan tripPlan, ItineraryGenerationListener listener) {
        if (splitLanguages) {
            return generateSplitLanguages(tripPlan, listener);
        }
        return generateBilingual(tripPlan, listener);
    }

    private boolean shouldChunk(TripPlan tripPlan) {
        return TripDates.inclusiveDayCount(tripPlan) > chunkThresholdDays;
    }

    private BilingualItinerary generateBilingual(TripPlan tripPlan, ItineraryGenerationListener listener) {
        if (shouldChunk(tripPlan)) {
            return generateBilingualChunked(tripPlan, listener);
        }
        return generateBilingualSinglePass(
                tripPlan, buildPromptService.buildPrompt(tripPlan), TripDates.inclusiveDayCount(tripPlan), listener);
    }

    private BilingualItinerary generateSplitLanguages(TripPlan tripPlan, ItineraryGenerationListener listener) {
        int totalDays = TripDates.inclusiveDayCount(tripPlan);
        int totalChunks = shouldChunk(tripPlan)
                ? TripDates.chunk(TripDates.eachDay(tripPlan), chunkSizeDays).size()
                : 1;
        notifyProgress(listener, progress(GenerationStage.ANALYZING, 0, totalChunks, 0, totalDays), null, null);

        if (shouldChunk(tripPlan)) {
            return generateSplitLanguagesChunked(tripPlan, listener, totalDays, totalChunks);
        }
        return generateSplitLanguagesSinglePass(
                tripPlan, buildPromptService.buildPromptEnglish(tripPlan), totalDays, listener);
    }

    private BilingualItinerary generateSplitLanguagesSinglePass(
            TripPlan tripPlan,
            String englishPrompt,
            int daysInPass,
            ItineraryGenerationListener listener) {
        notifyProgress(listener, progress(GenerationStage.PLANNING_DAYS, 0, 1, 0, daysInPass), null, null);

        Itinerary en = generateEnglishPass(tripPlan, englishPrompt, daysInPass);
        notifyProgress(listener, progress(GenerationStage.PLANNING_DAYS, 1, 1, en.getDayPlans().size(), daysInPass), copyItinerary(en), null);

        notifyProgress(listener, progress(GenerationStage.TRANSLATING, 0, 1, en.getDayPlans().size(), daysInPass), copyItinerary(en), null);
        Itinerary he = translateToHebrew(tripPlan, en, daysInPass);

        notifyProgress(listener, progress(GenerationStage.FINALIZING, 1, 1, daysInPass, daysInPass), copyItinerary(en), copyItinerary(he));
        return new BilingualItinerary(en, he);
    }

    private BilingualItinerary generateSplitLanguagesChunked(
            TripPlan tripPlan,
            ItineraryGenerationListener listener,
            int totalDays,
            int totalChunks) {
        List<LocalDate> allDates = TripDates.eachDay(tripPlan);
        List<List<LocalDate>> chunks = TripDates.chunk(allDates, chunkSizeDays);

        log.info("Generating {}-day trip in {} chunk(s) of up to {} days, EN-first + HE translate (model={}, translation={})",
                allDates.size(), totalChunks, chunkSizeDays, model, translationModel);

        Itinerary en = new Itinerary();
        en.setDayPlans(new ArrayList<>());
        Itinerary he = new Itinerary();
        he.setDayPlans(new ArrayList<>());

        String continuityHint = null;
        for (int i = 0; i < chunks.size(); i++) {
            List<LocalDate> chunkDates = chunks.get(i);
            int chunkNum = i + 1;
            int chunkDays = chunkDates.size();
            log.info("Trip chunk {}/{}: {} ({} days)", chunkNum, totalChunks,
                    chunkDates.get(0) + " → " + chunkDates.get(chunkDates.size() - 1), chunkDays);

            notifyProgress(listener,
                    progress(GenerationStage.PLANNING_DAYS, chunkNum - 1, totalChunks, en.getDayPlans().size(), totalDays),
                    copyItinerary(en), copyItinerary(he));

            String prompt = buildPromptService.buildChunkPromptEnglish(
                    tripPlan, chunkDates, chunkNum, totalChunks, continuityHint);
            Itinerary enChunk = generateEnglishPass(tripPlan, prompt, chunkDays);
            en.getDayPlans().addAll(enChunk.getDayPlans());

            notifyProgress(listener,
                    progress(GenerationStage.PLANNING_DAYS, chunkNum, totalChunks, en.getDayPlans().size(), totalDays),
                    copyItinerary(en), copyItinerary(he));

            notifyProgress(listener,
                    progress(GenerationStage.TRANSLATING, chunkNum - 1, totalChunks, en.getDayPlans().size(), totalDays),
                    copyItinerary(en), copyItinerary(he));

            Itinerary heChunk = translateToHebrew(tripPlan, enChunk, chunkDays);
            he.getDayPlans().addAll(heChunk.getDayPlans());

            notifyProgress(listener,
                    progress(GenerationStage.TRANSLATING, chunkNum, totalChunks, en.getDayPlans().size(), totalDays),
                    copyItinerary(en), copyItinerary(he));

            continuityHint = ChunkContinuityBuilder.build(en);
        }

        notifyProgress(listener,
                progress(GenerationStage.FINALIZING, totalChunks, totalChunks, totalDays, totalDays),
                copyItinerary(en), copyItinerary(he));
        return new BilingualItinerary(en, he);
    }

    private BilingualItinerary generateBilingualChunked(TripPlan tripPlan, ItineraryGenerationListener listener) {
        List<LocalDate> allDates = TripDates.eachDay(tripPlan);
        List<List<LocalDate>> chunks = TripDates.chunk(allDates, chunkSizeDays);
        int totalChunks = chunks.size();
        int totalDays = allDates.size();

        log.info("Generating {}-day trip in {} chunk(s) of up to {} days each (model={})",
                totalDays, totalChunks, chunkSizeDays, model);

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
            BilingualItinerary chunk = generateBilingualSinglePass(tripPlan, prompt, chunkDates.size(), null);

            if (chunk.getEn() != null && chunk.getEn().getDayPlans() != null) {
                en.getDayPlans().addAll(chunk.getEn().getDayPlans());
            }
            if (chunk.getHe() != null && chunk.getHe().getDayPlans() != null) {
                he.getDayPlans().addAll(chunk.getHe().getDayPlans());
            }

            notifyProgress(listener,
                    progress(GenerationStage.PLANNING_DAYS, chunkNum, totalChunks, en.getDayPlans().size(), totalDays),
                    copyItinerary(en), copyItinerary(he));

            continuityHint = ChunkContinuityBuilder.build(en);
        }

        return new BilingualItinerary(en, he);
    }

    private BilingualItinerary generateBilingualSinglePass(
            TripPlan tripPlan,
            String prompt,
            int daysInPass,
            ItineraryGenerationListener listener) {
        String response = callGpt(
                tripPlan,
                prompt,
                daysInPass,
                model,
                buildPromptService.buildSystemMessage(tripPlan),
                "bilingual");
        BilingualItinerary result = GptItineraryJsonRepair.parse(objectMapper, response);
        if (listener != null && result != null) {
            notifyProgress(listener,
                    progress(GenerationStage.FINALIZING, 1, 1, daysInPass, daysInPass),
                    copyItinerary(result.getEn()),
                    copyItinerary(result.getHe()));
        }
        return result;
    }

    private Itinerary generateEnglishPass(TripPlan tripPlan, String prompt, int daysInPass) {
        String response = callGpt(
                tripPlan,
                prompt,
                daysInPass,
                model,
                buildPromptService.buildSystemMessageEnglish(tripPlan),
                "english");
        return GptItineraryJsonRepair.parseEnglish(objectMapper, response);
    }

    private Itinerary translateToHebrew(TripPlan tripPlan, Itinerary english, int daysInPass) {
        String enJson = serializeItineraryJson(english);
        String prompt = buildPromptService.buildHebrewTranslationPrompt(enJson, daysInPass);
        String response = callGpt(
                tripPlan,
                prompt,
                daysInPass,
                translationModel,
                buildPromptService.buildSystemMessageHebrewTranslation(tripPlan),
                "hebrew-translate");
        return GptItineraryJsonRepair.parseHebrew(objectMapper, response);
    }

    private String serializeItineraryJson(Itinerary itinerary) {
        try {
            return objectMapper.writeValueAsString(itinerary);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize itinerary for Hebrew translation", e);
        }
    }

    private String callGpt(
            TripPlan tripPlan,
            String prompt,
            int daysInPass,
            String modelName,
            String systemMessage,
            String passLabel) {
        int tokenLimit = resolveMaxTokens(daysInPass, passLabel);
        OpenAiChatRequest request = new OpenAiChatRequest();
        request.setModel(modelName);
        if (OpenAiModelCapabilities.usesMaxCompletionTokens(modelName)) {
            request.setMaxCompletionTokens(tokenLimit);
        } else {
            request.setMaxTokens(tokenLimit);
        }
        if (OpenAiModelCapabilities.supportsTemperature(modelName)) {
            request.setTemperature("hebrew-translate".equals(passLabel) ? 0.2 : 0.35);
        }
        request.setResponseFormat(Map.of("type", "json_object"));
        request.setMessages(List.of(
                new OpenAiMessage("system", systemMessage),
                new OpenAiMessage("user", prompt)));

        long startedAt = System.currentTimeMillis();
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

        long elapsedMs = System.currentTimeMillis() - startedAt;
        if (Objects.nonNull(response) && !CollectionUtils.isEmpty(response.getChoices())) {
            String finishReason = response.getChoices().get(0).getFinishReason();
            if ("length".equals(finishReason)) {
                log.warn("OpenAI {} pass truncated (finish_reason=length) for {} days; tokenLimit={}",
                        passLabel, daysInPass, tokenLimit);
            }
        }
        log.info("OpenAI model={} {} pass ({} days, {} tokens cap) completed in {}ms",
                modelName, passLabel, daysInPass, tokenLimit, elapsedMs);

        String json = Objects.nonNull(response) ? response.firstContent() : null;
        if (Objects.isNull(json)) {
            throw new RuntimeException("GPT returned empty response");
        }
        return GptItineraryJsonRepair.normalizeRawJson(json);
    }

    private int resolveMaxTokens(int daysInPass, String passLabel) {
        int perDay = switch (passLabel) {
            case "hebrew-translate" -> 1800;
            case "english" -> 2000;
            default -> 3000;
        };
        int base = "hebrew-translate".equals(passLabel) ? 1500 : 3200;
        int estimated = base + Math.max(daysInPass, 1) * perDay;
        return Math.min(configuredMaxTokens, Math.max(estimated, 4096));
    }

    private static GenerationProgress progress(
            GenerationStage stage,
            int chunksCompleted,
            int totalChunks,
            int daysCompleted,
            int totalDays) {
        return GenerationProgress.builder()
                .stage(stage)
                .chunksCompleted(chunksCompleted)
                .totalChunks(totalChunks)
                .daysCompleted(daysCompleted)
                .totalDays(totalDays)
                .build();
    }

    private static void notifyProgress(
            ItineraryGenerationListener listener,
            GenerationProgress progress,
            Itinerary partialEn,
            Itinerary partialHe) {
        if (listener != null) {
            listener.onProgress(progress, partialEn, partialHe);
        }
    }

    private static Itinerary copyItinerary(Itinerary source) {
        if (source == null) {
            return null;
        }
        Itinerary copy = new Itinerary();
        if (source.getDayPlans() != null) {
            copy.setDayPlans(new ArrayList<>(source.getDayPlans()));
        }
        return copy;
    }
}
