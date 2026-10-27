package com.titran.pingcortex.controller;

import com.titran.pingcortex.dto.response.CourseProgressResponse;
import com.titran.pingcortex.security.CurrentUser;
import com.titran.pingcortex.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<List<CourseProgressResponse>> findCourseProgress(
            @RequestParam(required = false) Boolean isActive,
            HttpServletRequest request
    ) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(dashboardService.findCourseProgress(userId, isActive));
    }
}
