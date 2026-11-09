package com.titran.pingcortex.ai;

import com.titran.pingcortex.exception.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import com.titran.pingcortex.ai.AiResults.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("openai")
public class OpenAiAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public OpenAiAiClient(
            @Value("${ai.openai.base-url}") String baseUrl,
            @Value("${ai.openai.model}") String model,
            @Value("${ai.openai.max-tokens}") int maxTokens,
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
            if (jsonOutput) {
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
            return new AiProviderException("OpenAI rate limit exceeded or insufficient quota (HTTP 429) " + detail, e);
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
    private record OpenAiChoice(OpenAiMessage message) {}
    private record OpenAiResponse(List<OpenAiChoice> choices) {}
}
