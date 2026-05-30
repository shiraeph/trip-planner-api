package com.travel.travelplanner.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Choice {
    private OpenAiMessage message;
    @JsonProperty("finish_reason")
    private String finishReason;
}
