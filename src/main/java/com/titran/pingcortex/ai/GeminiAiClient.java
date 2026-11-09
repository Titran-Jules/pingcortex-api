package com.titran.pingcortex.ai;

import com.titran.pingcortex.ai.AiResults.*;
import com.titran.pingcortex.exception.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service("gemini")
public class GeminiAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public GeminiAiClient(
            @Value("${ai.gemini.base-url}") String baseUrl,
            @Value("${ai.gemini.model}") String model,
            @Value("${ai.gemini.max-tokens}") int maxTokens,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    protected String callProviderApi(String apiKey, String systemPrompt, List<ChatTurn> messages, boolean jsonOutput) {
        try {
            List<GeminiContent> contents = messages.stream()
                    .map(turn -> {
                        String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
                        return new GeminiContent(role, List.of(new GeminiPart(turn.content())));
                    })
                    .toList();
            var systemInstruction = new GeminiContent("user", List.of(new GeminiPart(systemPrompt)));
            Map<String, Object> generationConfig = jsonOutput
                    ? Map.of("maxOutputTokens", maxTokens, "responseMimeType", "application/json")
                    : Map.of("maxOutputTokens", maxTokens);
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
            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new AiProviderException("Gemini returned an empty response", null);
            }
            GeminiCandidate candidate = response.candidates().get(0);
            if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                throw new AiProviderException("Gemini response contains no parts", null);
            }
            return candidate.content().parts().get(0).text();
        } catch (RestClientResponseException e) {
            throw translateError(e);
        }
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
            return new AiProviderException("Gemini rate limit exceeded (HTTP 429) " + detail, e);
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
    private record GeminiCandidate(GeminiContent content) {}
    private record GeminiResponse(List<GeminiCandidate> candidates) {}
}
