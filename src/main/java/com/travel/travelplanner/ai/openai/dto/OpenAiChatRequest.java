package com.travel.travelplanner.ai.openai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;
}
