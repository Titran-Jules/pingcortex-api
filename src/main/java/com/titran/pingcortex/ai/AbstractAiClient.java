package com.titran.pingcortex.ai;

import com.titran.pingcortex.ai.AiClient.JsonMode;
import com.titran.pingcortex.ai.AiResults.*;
import com.titran.pingcortex.exception.AiProviderException;
import com.titran.pingcortex.model.Difficulty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractAiClient implements AiClient {
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MILLIS = 2000;
    private static final int QUIZ_OPTION_COUNT = 4;

    protected final ObjectMapper objectMapper;

    protected AbstractAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected abstract String callProviderApi(String apiKey, String systemPrompt, List<ChatTurn> messages, JsonMode jsonMode);

    protected String callProviderWithRetry(String apiKey, String systemPrompt, List<ChatTurn> messages, JsonMode jsonMode) {
        AiProviderException lastError = null;
        long backoffMillis = BASE_BACKOFF_MILLIS;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (lastError != null) {
                long waitMillis = Math.max(backoffMillis, lastError.retryAfterMillis());
                log.warn("AI provider call failed (retry {}/{}), waiting {} ms: {}",
                        attempt, MAX_RETRIES, waitMillis, lastError.getMessage());
                sleep(waitMillis);
                backoffMillis *= 3;
            }
            try {
                return callProviderApi(apiKey, systemPrompt, messages, jsonMode);
            } catch (AiProviderException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                lastError = e;
            }
        }
        throw lastError;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI call interrupted during retry backoff", e);
        }
    }

    protected static long retryAfterMillis(RestClientResponseException e) {
        if (e.getResponseHeaders() == null) {
            return 0;
        }
        String retryAfter = e.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(retryAfter.strip()) * 1000;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public List<ConceptSuggestion> analyzeCourse(String apiKey, String courseTitle, String courseDescription, List<String> materialContent) {
        String systemPrompt = """
            Tu es un assistant pedagogique. Decoupe un cours en concepts cles,
            ordonnes du plus fondamental au plus avance, adaptes au niveau indique.
            Reponds UNIQUEMENT avec un tableau JSON valide, sans aucun texte avant
            ou apres, au format exact :
            [{"name": "...", "description": "..."}]
        """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Titre du cours : ").append(courseTitle).append("\n");
        userPrompt.append("Description : ").append(courseDescription).append("\n");
        if (!materialContent.isEmpty()) {
            userPrompt.append("Support de cours fournis :\n");
            for (String content : materialContent) {
                userPrompt.append("---\n").append(content).append("\n");
            }
        }
        String rawResponse = callProviderWithRetry(apiKey, systemPrompt, List.of(new ChatTurn("user", userPrompt.toString())), JsonMode.ARRAY);
        List<ConceptSuggestion> parsed = parseJson(rawResponse, new TypeReference<List<ConceptSuggestion>>() {});
        if (parsed == null) {
            throw new AiProviderException("AI returned no concepts", null);
        }
        List<ConceptSuggestion> valid = new ArrayList<>();
        for (ConceptSuggestion suggestion : parsed) {
            if (suggestion == null || suggestion.name() == null || suggestion.name().isBlank()) {
                continue;
            }
            valid.add(new ConceptSuggestion(suggestion.name().strip(), suggestion.description()));
        }
        if (valid.isEmpty()) {
            throw new AiProviderException("AI returned no valid concepts", null);
        }
        return valid;
    }

    @Override
    public List<ConceptCoverageSuggestion> analyzeCoverage(String apiKey, List<ConceptSummary> concepts, String materialContent) {
        String systemPrompt = """
            Tu es un assistant pedagogique.
            Tu as élaboré ces CONCEPTS pour un cours.
            Tu va ensuite analyser ces concepts à partir du MATERIAL_CONTENT et proposer les concepts qui pourront être mis à COVERED
            Réponds UNIQUEMENT en JSON valide, sans aucun texte devant ou apres, au format exact :
            [{"conceptId": "...", "confidence": "HIGH/MEDIUM/LOW"}]
        """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("CONCEPTS : ").append("\n");
        for (ConceptSummary concept : concepts) {
            userPrompt.append("id : ").append(concept.id()).append("; name: ").append(concept.name()).append("; description: ").append(concept.description()).append(";\n");
        }
        userPrompt.append("\n");
        userPrompt.append("MATERIAL CONTENT : ").append("\n");
        userPrompt.append(materialContent).append("\n");
        String rawResponse = callProviderWithRetry(apiKey, systemPrompt, List.of(new ChatTurn("user", userPrompt.toString())), JsonMode.ARRAY);
        List<Map<String, Object>> entries = parseJson(rawResponse, new TypeReference<List<Map<String, Object>>>() {});
        if (entries == null) {
            throw new AiProviderException("AI returned no coverage suggestions", null);
        }
        List<ConceptCoverageSuggestion> valid = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            ConceptCoverageSuggestion suggestion = toCoverageSuggestion(entry);
            if (suggestion != null) {
                valid.add(suggestion);
            }
        }
        if (valid.isEmpty()) {
            throw new AiProviderException("AI returned no valid coverage suggestions", null);
        }
        return valid;
    }

    private ConceptCoverageSuggestion toCoverageSuggestion(Map<String, Object> entry) {
        if (entry == null || entry.get("conceptId") == null) {
            return null;
        }
        Object confidence = entry.get("confidence");
        try {
            return new ConceptCoverageSuggestion(
                    UUID.fromString(String.valueOf(entry.get("conceptId")).strip()),
                    confidence == null ? null : confidence.toString()
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public QuizQuestionSuggestion generateQuiz(String apiKey, String conceptName, String conceptDescription, Difficulty difficulty) {
        String systemPrompt = """
            Tu generes une question a choix multiples de difficulte %s sur le
            concept suivant : "%s" - %s.
            Exactement 4 options, une seule correcte. Reponds UNIQUEMENT en JSON,
            sans texte avant ou apres, au format exact :
            {"questionText": "...", "options": [{"text": "...", "correct": true|false}, ...]}
        """.formatted(difficulty, conceptName, conceptDescription);

        String rawResponse = callProviderWithRetry(apiKey, systemPrompt, List.of(new ChatTurn("user", "Genere la question suivante.")), JsonMode.OBJECT);
        QuizQuestionDraft draft = parseJson(rawResponse, new TypeReference<QuizQuestionDraft>() {});
        List<QuizQuestionSuggestion> valid = validQuizQuestions(draft == null ? List.of() : List.of(draft), difficulty);
        if (valid.isEmpty()) {
            throw new AiProviderException("AI returned an invalid quiz question", null);
        }
        return valid.get(0);
    }

    private record QuizQuestionDraft(String questionText, List<QuizOptionSuggestion> options) {}

    @Override
    public List<QuizQuestionSuggestion> generateQuizBatch(String apiKey, String conceptName, String conceptDescription, Difficulty difficulty, int count) {
        String systemPrompt = """
            Tu generes %d questions a choix multiples de difficulte %s sur le
            concept suivant : "%s" - %s.
            Chaque question doit etre distincte des autres (pas de reformulation
            d'une meme question), et rester independante (pas de reference a une
            autre question du lot).
            Chaque question a exactement 4 options, une seule correcte.
            Reponds UNIQUEMENT avec un tableau JSON valide, sans aucun texte avant
            ou apres, au format exact :
            [{"questionText": "...", "options": [{"text": "...", "correct": true|false}, ...]}]
        """.formatted(count, difficulty, conceptName, conceptDescription);

        String rawResponse = callProviderWithRetry(apiKey, systemPrompt, List.of(new ChatTurn("user", "Genere une sequence de quiz pour maitriser ce concept")), JsonMode.ARRAY);
        List<QuizQuestionDraft> drafts = parseJson(rawResponse, new TypeReference<List<QuizQuestionDraft>>() {});
        List<QuizQuestionSuggestion> valid = validQuizQuestions(drafts, difficulty);
        if (valid.isEmpty()) {
            throw new AiProviderException("AI returned no valid quiz questions", null);
        }
        return valid;
    }

    private List<QuizQuestionSuggestion> validQuizQuestions(List<QuizQuestionDraft> drafts, Difficulty difficulty) {
        List<QuizQuestionSuggestion> valid = new ArrayList<>();
        if (drafts == null) {
            return valid;
        }
        for (QuizQuestionDraft draft : drafts) {
            if (draft == null || draft.questionText() == null || draft.questionText().isBlank()) {
                continue;
            }
            if (draft.options() == null || draft.options().size() != QUIZ_OPTION_COUNT) {
                continue;
            }
            boolean allOptionsValid = draft.options().stream()
                    .allMatch(option -> option != null && option.text() != null && !option.text().isBlank());
            if (!allOptionsValid) {
                continue;
            }
            long correctCount = draft.options().stream().filter(QuizOptionSuggestion::correct).count();
            if (correctCount != 1) {
                continue;
            }
            valid.add(new QuizQuestionSuggestion(draft.questionText().strip(), difficulty, draft.options()));
        }
        return valid;
    }

    @Override
    public FeynmanEvaluationResult evaluateFeynman(String apiKey, String conceptName, String conceptDescription, String explanationText) {
        String systemPrompt = """
            Tu evalues l'explication d'un eleve sur un concept, selon la methode
            Feynman (l'eleve doit expliquer avec ses propres mots comme s'il
            enseignait a quelqu'un d'autre). Concept : "%s" - %s.
            Evalue sur plusieurs criteres (clarte, exactitude, completude).
            Reponds UNIQUEMENT en JSON, sans texte avant ou apres, au format exact :
            {"criteria": [{"name": "...", "score": 0-100, "comment": "..."}],
            "overallFeedback": "...", "misconceptions": ["..."], "score": 0-100}
        """.formatted(conceptName, conceptDescription);

        String rawResponse = callProviderWithRetry(apiKey, systemPrompt, List.of(new ChatTurn("user", explanationText)), JsonMode.OBJECT);
        FeynmanEvaluationResult result = parseJson(rawResponse, new TypeReference<FeynmanEvaluationResult>() {});
        if (result == null) {
            throw new AiProviderException("AI returned no Feynman evaluation", null);
        }
        List<CriterionResult> criteria = result.criteria() == null ? List.of() : result.criteria();
        List<String> misconceptions = result.misconceptions() == null ? List.of() : result.misconceptions();
        String feedback = result.overallFeedback() == null ? "" : result.overallFeedback();
        return new FeynmanEvaluationResult(criteria, feedback, misconceptions, clampScore(result.score()));
    }

    private double clampScore(double score) {
        if (Double.isNaN(score)) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    @Override
    public String chat(String apiKey, String courseContext, List<ChatTurn> history, String newMessage) {
        String systemPrompt = """
            Tu es un tuteur pedagogique pour ce cours : %s.
            Reponds de facon claire et encourageante, adaptee au niveau de l'eleve.
            Pas de format JSON ici - reponse en texte libre, conversationnel.
        """.formatted(courseContext);

        List<ChatTurn> messages = new ArrayList<>(history);
        messages.add(new ChatTurn("user", newMessage));
        return callProviderWithRetry(apiKey, systemPrompt, messages, JsonMode.TEXT);
    }

    protected <T> T parseJson(String rawText, TypeReference<T> typeRef) {
        String payload = extractJsonPayload(rawText);
        try {
            return objectMapper.readValue(payload, typeRef);
        } catch (JacksonException e) {
            throw new AiProviderException("Failed to parse AI response as JSON: " + excerpt(payload), e);
        }
    }

    private String excerpt(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private String extractJsonPayload(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new AiProviderException("AI returned an empty response", null);
        }
        String text = rawText.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.strip();
        }
        int start = -1;
        char open = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[' || c == '{') {
                start = i;
                open = c;
                break;
            }
        }
        if (start < 0) {
            throw new AiProviderException("AI response contains no JSON structure", null);
        }
        char close = open == '[' ? ']' : '}';
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new AiProviderException("AI response contains unterminated JSON", null);
    }
}
