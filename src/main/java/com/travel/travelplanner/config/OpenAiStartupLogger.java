package com.travel.travelplanner.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OpenAiStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(OpenAiStartupLogger.class);

    private final Environment environment;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens:32768}")
    private int maxTokens;

    @Value("${trip.itinerary.chunk-threshold-days:6}")
    private int chunkThresholdDays;

    @Value("${trip.itinerary.chunk-size-days:3}")
    private int chunkSizeDays;

    @Value("${trip.itinerary.split-languages:true}")
    private boolean splitLanguages;

    @Value("${openai.translation.model:${openai.model}}")
    private String translationModel;

    public OpenAiStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logOpenAiSettingsOnLocalProfile() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("local")) {
            return;
        }
        log.info(
                """
                OpenAI (local profile):
                  model={}
                  translationModel={}
                  maxTokens={}
                  chunkThresholdDays={}
                  chunkSizeDays={}
                  splitLanguages={}
                """,
                model,
                translationModel,
                maxTokens,
                chunkThresholdDays,
                chunkSizeDays,
                splitLanguages);
    }
}
