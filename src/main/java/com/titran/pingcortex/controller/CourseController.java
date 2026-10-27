package com.titran.pingcortex.controller;

import com.titran.pingcortex.dto.request.CourseMaterialRequest;
import com.titran.pingcortex.dto.request.CourseRequest;
import com.titran.pingcortex.dto.request.CourseUpdate;
import com.titran.pingcortex.dto.request.CourseUpdateActive;
import com.titran.pingcortex.dto.response.CourseMaterialResponse;
import com.titran.pingcortex.dto.response.CourseResponse;
import com.titran.pingcortex.exception.CourseNotFoundException;
import com.titran.pingcortex.security.CurrentUser;
import com.titran.pingcortex.service.CourseMaterialService;
import com.titran.pingcortex.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class CourseController {
    private final CourseService courseService;
    private final CourseMaterialService courseMaterialService;

    @GetMapping("/courses")
    public ResponseEntity<List<CourseResponse>> getAllCourses(@RequestParam(required = false) Boolean isActive, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(courseService.findAllCourses(userId, isActive));
    }

    @PostMapping("/courses")
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseRequest courseRequest, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(userId, courseRequest));
    }

    @GetMapping("/courses/{courseId}")
    public ResponseEntity<CourseResponse> getCourse(@PathVariable UUID courseId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(courseService.findCourseById(userId, courseId)
                .orElseThrow(CourseNotFoundException::new));
    }

    @PatchMapping("/courses/{courseId}")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable UUID courseId, @RequestBody CourseUpdate courseUpdate, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(courseService.updateCourse(userId, courseId, courseUpdate)
                .orElseThrow(CourseNotFoundException::new));
    }

    @PatchMapping("/courses/{courseId}/active")
    public ResponseEntity<CourseResponse> updateCourseActive(@PathVariable UUID courseId, @RequestBody CourseUpdateActive courseUpdateActive, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(courseService.updateCourseActive(userId, courseId, courseUpdateActive)
                .orElseThrow(CourseNotFoundException::new));
    }

    @PostMapping("/courses/{courseId}/analyze")
    public ResponseEntity<CourseResponse> retryCourseAnalysis(@PathVariable UUID courseId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.accepted().body(courseService.retryAnalysis(userId, courseId));
    }

    @GetMapping("/courses/{courseId}/materials")
    public ResponseEntity<List<CourseMaterialResponse>> getAllCourseMaterials(@PathVariable UUID courseId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(courseMaterialService.findAll(userId, courseId));
    }

    @PostMapping("/courses/{courseId}/materials")
    public ResponseEntity<CourseMaterialResponse> createCourseMaterial(@PathVariable UUID courseId, @RequestBody CourseMaterialRequest courseMaterialRequest, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseMaterialService.create(userId, courseId, courseMaterialRequest));
    }
}
