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

@Service("anthropic")
public class AnthropicAiClient extends AbstractAiClient {
    private final RestClient restClient;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public AnthropicAiClient(
            @Value("${ai.anthropic.base-url}") String baseUrl,
            @Value("${ai.anthropic.version}") String anthropicVersion,
            @Value("${ai.anthropic.model}") String model,
            @Value("${ai.anthropic.max-tokens}") int maxTokens,
            @Value("${ai.anthropic.temperature:0.2}") double temperature,
            ClientHttpRequestFactory aiClientRequestFactory,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(aiClientRequestFactory)
                .defaultHeader("anthropic-version", anthropicVersion)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
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
            return new AiProviderException("Anthropic rate limit exceeded (HTTP 429) " + detail, e, true, retryAfterMillis(e));
        }
        if (status == 529) {
            return new AiProviderException("Anthropic overloaded (HTTP 529) " + detail, e, true, retryAfterMillis(e));
        }
        if (status == 500 || status == 502 || status == 503 || status == 504) {
            return new AiProviderException("Anthropic API temporarily unavailable (HTTP " + status + ") " + detail, e, true, retryAfterMillis(e));
        }
        return new AiProviderException("Anthropic API error (HTTP " + status + ") " + detail, e);
    }

    @Override
    protected String callProviderApi(String apiKey, String systemPrompt, List<ChatTurn> messages, JsonMode jsonMode) {
        try {
            List<Map<String, String>> anthropicMessages = new ArrayList<>();
            for (ChatTurn turn : messages) {
                anthropicMessages.add(Map.of("role", turn.role().toLowerCase(), "content", turn.content()));
            }
            String prefill = null;
            if (jsonMode == JsonMode.ARRAY) {
                prefill = "[";
                anthropicMessages.add(Map.of("role", "assistant", "content", prefill));
            } else if (jsonMode == JsonMode.OBJECT) {
                prefill = "{";
                anthropicMessages.add(Map.of("role", "assistant", "content", prefill));
            }
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("system", systemPrompt);
            requestBody.put("messages", anthropicMessages);
            if (jsonMode != JsonMode.TEXT) {
                requestBody.put("temperature", temperature);
            }
            AnthropicResponse response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(AnthropicResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                throw new AiProviderException("Anthropic returned an empty response", null);
            }
            if ("max_tokens".equals(response.stopReason()) && jsonMode != JsonMode.TEXT) {
                throw new AiProviderException("Anthropic response truncated (stop_reason: max_tokens), increase ai.anthropic.max-tokens", null);
            }
            if ("refusal".equals(response.stopReason()) && jsonMode != JsonMode.TEXT) {
                throw new AiProviderException("Anthropic refused to answer (stop_reason: refusal)", null);
            }
            String text = firstText(response.content());
            return prefill == null ? text : prefill + text;
        } catch (RestClientResponseException e) {
            throw translateError(e);
        }
    }

    private String firstText(List<ContentBlock> blocks) {
        for (ContentBlock block : blocks) {
            if (block != null && "text".equals(block.type()) && block.text() != null && !block.text().isBlank()) {
                return block.text();
            }
        }
        throw new AiProviderException("Anthropic response contains no text", null);
    }

    private String bodyExcerpt(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        return "| " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
    }

    private record AnthropicResponse(List<ContentBlock> content, String stopReason) {}
    private record ContentBlock(String type, String text) {}
}
