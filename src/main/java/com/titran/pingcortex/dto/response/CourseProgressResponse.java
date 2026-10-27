package com.titran.pingcortex.dto.response;

import com.titran.pingcortex.model.AnalysisStatus;

import java.util.UUID;

public record CourseProgressResponse(
        UUID courseId,
        String title,
        boolean isActive,
        AnalysisStatus analysisStatus,
        int totalConcepts,
        int coveredConcepts,
        int inProgressConcepts,
        int masteredConcepts,
        int averageMastery,
        int dueReviewCount
) {
}
