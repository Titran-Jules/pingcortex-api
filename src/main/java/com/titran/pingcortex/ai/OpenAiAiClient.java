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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("openai")
public class OpenAiAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OpenAiAiClient(
            @Value("${ai.openai.base-url}") String baseUrl,
            @Value("${ai.openai.model}") String model,
            @Value("${ai.openai.max-tokens}") int maxTokens,
            @Value("${ai.openai.temperature:0.2}") double temperature,
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
            List<Map<String, String>> openAiMessages = new ArrayList<>();
            openAiMessages.add(Map.of("role", "system", "content", systemPrompt));

            for (ChatTurn turn : messages) {
                openAiMessages.add(Map.of(
                        "role", turn.role().toLowerCase(),
                        "content", turn.content()
                ));
            }
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("messages", openAiMessages);
            if (jsonMode != JsonMode.TEXT) {
                requestBody.put("temperature", temperature);
                requestBody.put("response_format", Map.of("type", "json_object"));
            }
            OpenAiResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiProviderException("OpenAI returned an empty response", null);
            }
            OpenAiChoice choice = response.choices().get(0);
            if ("length".equals(choice.finishReason()) && jsonMode != JsonMode.TEXT) {
                throw new AiProviderException("OpenAI response truncated (finish_reason: length), increase ai.openai.max-tokens", null);
            }
            if ("content_filter".equals(choice.finishReason()) && jsonMode != JsonMode.TEXT) {
                throw new AiProviderException("OpenAI blocked the response (finish_reason: content_filter)", null);
            }
            if (choice.message() == null || choice.message().content() == null) {
                throw new AiProviderException("OpenAI response message content is null", null);
            }
            return choice.message().content();
        } catch (RestClientResponseException e) {
            throw translateError(e);
        }
    }

    private AiProviderException translateError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detail = bodyExcerpt(e);
        if (status == 401) {
            return new AiProviderException("Invalid or revoked OpenAI API key (HTTP 401) " + detail, e);
        }
        if (status == 404) {
            return new AiProviderException("OpenAI model not found or unavailable (HTTP 404) " + detail, e);
        }
        if (status == 429) {
            return new AiProviderException("OpenAI rate limit exceeded or insufficient quota (HTTP 429) " + detail, e, true, retryAfterMillis(e));
        }
        if (status == 500 || status == 502 || status == 503 || status == 504) {
            return new AiProviderException("OpenAI API temporarily unavailable (HTTP " + status + ") " + detail, e, true, retryAfterMillis(e));
        }
        return new AiProviderException("OpenAI API error (HTTP " + status + ") " + detail, e);
    }

    private String bodyExcerpt(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        return "| " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
    }

    private record OpenAiMessage(String role, String content) {}
    private record OpenAiChoice(OpenAiMessage message, String finishReason) {}
    private record OpenAiResponse(List<OpenAiChoice> choices) {}
}
