package com.titran.pingcortex.ai;

import com.titran.pingcortex.ai.AiClient.JsonMode;
import com.titran.pingcortex.ai.AiResults.*;
import com.titran.pingcortex.exception.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("gemini")
public class GeminiAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public GeminiAiClient(
            @Value("${ai.gemini.base-url}") String baseUrl,
            @Value("${ai.gemini.model}") String model,
            @Value("${ai.gemini.max-tokens}") int maxTokens,
            @Value("${ai.gemini.temperature:0.2}") double temperature,
            ClientHttpRequestFactory aiClientRequestFactory,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(aiClientRequestFactory)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    @Override
    protected String callProviderApi(String apiKey, String systemPrompt, List<ChatTurn> messages, JsonMode jsonMode) {
        try {
            List<GeminiContent> contents = messages.stream()
                    .map(turn -> {
                        String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
                        return new GeminiContent(role, List.of(new GeminiPart(turn.content())));
                    })
                    .toList();
            var systemInstruction = new GeminiContent("user", List.of(new GeminiPart(systemPrompt)));
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("maxOutputTokens", maxTokens);
            if (jsonMode != JsonMode.TEXT) {
                generationConfig.put("responseMimeType", "application/json");
                generationConfig.put("temperature", temperature);
            }
            Map<String, Object> requestBody = Map.of(
                    "systemInstruction", systemInstruction,
                    "contents", contents,
                    "generationConfig", generationConfig
            );
            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);
            if (response == null) {
                throw new AiProviderException("Gemini returned an empty response", null);
            }
            if (response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
                throw new AiProviderException("Gemini blocked the request (safety filters: " + response.promptFeedback().blockReason() + ")", null);
            }
            if (response.candidates() == null || response.candidates().isEmpty()) {
                throw new AiProviderException("Gemini returned an empty response", null);
            }
            GeminiCandidate candidate = response.candidates().get(0);
            checkFinishReason(candidate.finishReason(), jsonMode);
            if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                throw new AiProviderException("Gemini response contains no parts", null);
            }
            return firstText(candidate);
        } catch (RestClientResponseException e) {
            throw translateError(e);
        }
    }

    private void checkFinishReason(String finishReason, JsonMode jsonMode) {
        if (finishReason == null || "STOP".equals(finishReason)) {
            return;
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            if (jsonMode != JsonMode.TEXT) {
                throw new AiProviderException("Gemini response truncated (finishReason: MAX_TOKENS), increase ai.gemini.max-tokens", null);
            }
            return;
        }
        if (jsonMode != JsonMode.TEXT) {
            throw new AiProviderException("Gemini stopped early (finishReason: " + finishReason + ")", null);
        }
    }

    private String firstText(GeminiCandidate candidate) {
        for (GeminiPart part : candidate.content().parts()) {
            if (part.text() != null && !part.text().isBlank()) {
                return part.text();
            }
        }
        throw new AiProviderException("Gemini response contains no text", null);
    }

    private AiProviderException translateError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detail = bodyExcerpt(e);
        if (status == 400 || status == 401) {
            return new AiProviderException("Invalid or revoked Gemini API key (HTTP " + status + ") " + detail, e);
        }
        if (status == 404) {
            return new AiProviderException("Gemini model not found or unavailable (HTTP 404) " + detail, e);
        }
        if (status == 429) {
            return new AiProviderException("Gemini rate limit exceeded (HTTP 429) " + detail, e, true, retryAfterMillis(e));
        }
        if (status == 500 || status == 502 || status == 503 || status == 504) {
            return new AiProviderException("Gemini API temporarily unavailable (HTTP " + status + ") " + detail, e, true, retryAfterMillis(e));
        }
        return new AiProviderException("Gemini API error (HTTP " + status + ") " + detail, e);
    }

    private String bodyExcerpt(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        return "| " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
    }

    private record GeminiPart(String text) {}
    private record GeminiContent(String role, List<GeminiPart> parts) {}
    private record GeminiCandidate(GeminiContent content, String finishReason) {}
    private record GeminiPromptFeedback(String blockReason) {}
    private record GeminiResponse(List<GeminiCandidate> candidates, GeminiPromptFeedback promptFeedback) {}
}
