package com.titran.pingcortex.ai;

import com.titran.pingcortex.ai.AiResults.*;
import com.titran.pingcortex.model.Difficulty;

import java.util.List;
import java.util.UUID;

public interface AiClient {
    enum JsonMode { TEXT, OBJECT, ARRAY }

    List<ConceptSuggestion> analyzeCourse(
            String apiKey,
            String courseTitle,
            String courseDescription,
            List<String> materialContents
    );

    List<ConceptCoverageSuggestion> analyzeCoverage(
            String apiKey,
            List<ConceptSummary> concepts,
            String materialContent
    );

    QuizQuestionSuggestion generateQuiz(
            String apiKey,
            String conceptName,
            String conceptDescription,
            Difficulty difficulty
    );

    List<QuizQuestionSuggestion> generateQuizBatch(
            String apiKey,
            String conceptName,
            String conceptDescription,
            Difficulty difficulty,
            int count
    );

    FeynmanEvaluationResult evaluateFeynman(
            String apiKey,
            String conceptName,
            String conceptDescription,
            String explanationText
    );

    String chat(
            String apiKey,
            String courseContext,
            List<ChatTurn> history,
            String newMessage
    );
}
