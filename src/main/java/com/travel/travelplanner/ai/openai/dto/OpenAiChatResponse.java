package com.travel.travelplanner.ai.openai.dto;

import java.util.List;
import java.util.Objects;

import org.springframework.util.CollectionUtils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OpenAiChatResponse {
    private List<Choice> choices;
    
    public String firstContent() {
        if (CollectionUtils.isEmpty(choices) || Objects.isNull(choices.get(0).getMessage())) {
            return null;
        }
        return choices.get(0).getMessage().getContent();
    }
}
