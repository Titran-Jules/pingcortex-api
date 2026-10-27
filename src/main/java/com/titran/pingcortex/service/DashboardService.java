package com.titran.pingcortex.service;

import com.titran.pingcortex.dto.response.CourseProgressResponse;
import com.titran.pingcortex.repository.DashboardRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class DashboardService {
    private final DashboardRepository dashboardRepository;

    public List<CourseProgressResponse> findCourseProgress(UUID userId, Boolean isActive) {
        return dashboardRepository.findCourseProgress(userId, isActive);
    }
}
