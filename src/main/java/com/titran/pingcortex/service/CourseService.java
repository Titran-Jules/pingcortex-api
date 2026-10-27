package com.titran.pingcortex.service;

import com.titran.pingcortex.dto.request.CourseRequest;
import com.titran.pingcortex.dto.request.CourseUpdate;
import com.titran.pingcortex.dto.request.CourseUpdateActive;
import com.titran.pingcortex.dto.response.CourseResponse;
import com.titran.pingcortex.exception.AnalysisNotRetryableException;
import com.titran.pingcortex.exception.CourseNotFoundException;
import com.titran.pingcortex.model.AnalysisStatus;
import com.titran.pingcortex.repository.CourseRepository;
import com.titran.pingcortex.util.TransactionUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseAnalyzeService courseAnalyzeService;

    public List<CourseResponse> findAllCourses(UUID userId, Boolean isActive) {
        return courseRepository.findAll(userId, isActive);
    }

    public Optional<CourseResponse> findCourseById(UUID userId, UUID courseId) {
        return courseRepository.findById(userId, courseId);
    }

    @Transactional
    public CourseResponse createCourse(UUID userId, CourseRequest courseRequest) {
        var createdCourse = courseRepository.createCourse(userId, courseRequest);
        TransactionUtils.afterCommit(() -> courseAnalyzeService.analyzeCourse(userId, createdCourse.id()));
        return createdCourse;
    }

    @Transactional
    public CourseResponse retryAnalysis(UUID userId, UUID courseId) {
        CourseResponse course = courseRepository.findById(userId, courseId)
                .orElseThrow(CourseNotFoundException::new);
        if (course.analysisStatus() != AnalysisStatus.FAILED) {
            throw new AnalysisNotRetryableException();
        }
        courseRepository.updateAnalysisStatus(courseId, AnalysisStatus.PENDING);
        TransactionUtils.afterCommit(() -> courseAnalyzeService.analyzeCourse(userId, courseId));
        return courseRepository.findById(userId, courseId).orElse(course);
    }

    public Optional<CourseResponse> updateCourse(UUID userId, UUID courseId, CourseUpdate  courseUpdate) {
        return courseRepository.updateCourse(userId, courseId, courseUpdate);
    }

    public Optional<CourseResponse> updateCourseActive(UUID userId, UUID courseId, CourseUpdateActive courseUpdateActive) {
        return courseRepository.updateCourseActive(userId, courseId, courseUpdateActive);
    }
}
