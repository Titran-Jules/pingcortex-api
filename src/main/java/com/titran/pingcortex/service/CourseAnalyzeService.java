package com.titran.pingcortex.service;

import com.titran.pingcortex.ai.AiClient;
import com.titran.pingcortex.ai.AiClientResolver;
import com.titran.pingcortex.dto.response.CourseMaterialResponse;
import com.titran.pingcortex.dto.response.CourseResponse;
import com.titran.pingcortex.exception.CourseNotFoundException;
import com.titran.pingcortex.exception.NoActiveKeyException;
import com.titran.pingcortex.model.AiProvider;
import com.titran.pingcortex.model.AnalysisStatus;
import com.titran.pingcortex.model.UserApiKey;
import com.titran.pingcortex.repository.ConceptRepository;
import com.titran.pingcortex.repository.CourseMaterialRepository;
import com.titran.pingcortex.repository.CourseRepository;
import com.titran.pingcortex.repository.UserRepository;
import com.titran.pingcortex.security.EncryptionService;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.titran.pingcortex.ai.AiResults.ConceptSuggestion;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class CourseAnalyzeService {
    private final CourseRepository courseRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final ConceptRepository conceptRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final AiClientResolver aiClientResolver;

    @Async("taskExecutor")
    @Transactional
    public void analyzeCourse(UUID userId, UUID courseId) {
        try {
            CourseResponse course = courseRepository.findById(userId, courseId)
                    .orElseThrow(CourseNotFoundException::new);
            UserApiKey activeKey = userRepository.findActiveApiKey(userId)
                    .orElseThrow(NoActiveKeyException::new);

            String decryptedKey = encryptionService.decrypt(activeKey.encryptedKey());
            AiProvider provider = AiProvider.valueOf(activeKey.provider().toUpperCase());
            AiClient aiClient = aiClientResolver.resolve(provider);

            List<String> materialContents = courseMaterialRepository.findAllByCourseId(courseId).stream()
                    .map(CourseMaterialResponse::content)
                    .toList();
            conceptRepository.deleteAllByCourseId(courseId);
            courseRepository.updateAnalysisStatus(courseId, AnalysisStatus.ANALYZING);

            List<ConceptSuggestion> suggestions = aiClient.analyzeCourse(decryptedKey, course.title(), course.description(), materialContents);

            int position = 1;
            for (ConceptSuggestion suggestion : suggestions) {
                conceptRepository.create(courseId, suggestion.name(), suggestion.description(), position++);
            }
        } catch (Exception e) {
            courseRepository.updateAnalysisStatus(courseId, AnalysisStatus.FAILED);
        }
    }
}
