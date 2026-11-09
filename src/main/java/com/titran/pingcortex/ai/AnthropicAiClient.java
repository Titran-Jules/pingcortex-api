package com.titran.pingcortex.ai;

import com.titran.pingcortex.exception.AiProviderException;
import com.titran.pingcortex.model.Difficulty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.titran.pingcortex.ai.AiResults.*;

import java.util.List;
import java.util.Map;

@Service("anthropic")
public class AnthropicAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public AnthropicAiClient(
            @Value("${ai.anthropic.base-url}") String baseUrl,
            @Value("${ai.anthropic.version}") String anthropicVersion,
            @Value("${ai.anthropic.model}") String model,
            @Value("${ai.anthropic.max-tokens}") int maxTokens,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("anthropic-version", anthropicVersion)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    private AiProviderException translateError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detail = bodyExcerpt(e);
        if (status == 401) {
            return new AiProviderException("Invalid or revoked Anthropic API key (HTTP 401) " + detail, e);
        }
        if (status == 404) {
            return new AiProviderException("Anthropic model not found or unavailable (HTTP 404) " + detail, e);
        }
        if (status == 429) {
            return new AiProviderException("Anthropic rate limit exceeded (HTTP 429) " + detail, e);
        }
        return new AiProviderException("Anthropic API error (HTTP " + status + ") " + detail, e);
    }

    private String bodyExcerpt(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        return "| " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
    }

    @Override
    protected String callProviderApi(String apiKey, String systemPrompt, List<ChatTurn> messages, boolean jsonOutput) {
        try {
            List<Map<String, String>> anthropicMessages = messages.stream()
                    .map(turn -> Map.of("role", turn.role().toLowerCase(), "content", turn.content()))
                    .toList();
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", systemPrompt,
                    "messages", anthropicMessages
            );
            AnthropicResponse response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(AnthropicResponse.class);

            if (response == null || response.content().isEmpty()) {
                throw new AiProviderException("Anthropic returned an empty response", null);
            }
            return response.content().get(0).text();
        } catch (RestClientResponseException e) {
            throw translateError(e);
        }
    }

    private record AnthropicResponse(List<ContentBlock> content) {}
    private record ContentBlock(String type, String text) {}
}
