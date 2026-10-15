package com.jobportal.controller;

import com.jobportal.domain.JobStatus;
import com.jobportal.domain.ReportStatus;
import com.jobportal.dto.Dtos.*;
import com.jobportal.security.AuthUser;
import com.jobportal.service.AdminService;
import com.jobportal.service.JobService;
import com.jobportal.service.StatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administrator-only endpoints (protected by hasRole("ADMIN") in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final JobService jobService;
    private final StatsService statsService;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return statsService.admin();
    }

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return adminService.users(q, page, size);
    }

    @PutMapping("/users/{id}/active")
    public UserDto setActive(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                             @Valid @RequestBody ActiveRequest req) {
        return adminService.setUserActive(admin.id(), id, req.active());
    }

    @GetMapping("/companies")
    public PageResponse<CompanyDto> companies(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return jobService.companies(q, page, size);
    }

    @GetMapping("/jobs")
    public PageResponse<JobDto> jobs(@RequestParam(required = false) JobStatus status,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return adminService.jobs(status, q, page, size);
    }

    @PutMapping("/jobs/{id}/status")
    public JobDto setJobStatus(@PathVariable Long id, @Valid @RequestBody JobStatusRequest req) {
        return adminService.setJobStatus(id, req.status());
    }

    @GetMapping("/applications")
    public PageResponse<AdminApplicationDto> applications(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return adminService.applications(page, size);
    }

    @GetMapping("/reports")
    public PageResponse<ReportDto> reports(@RequestParam(required = false) ReportStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return adminService.reports(status, page, size);
    }

    @PutMapping("/reports/{id}")
    public ReportDto resolveReport(@PathVariable Long id, @Valid @RequestBody ReportResolveRequest req) {
        return adminService.resolveReport(id, req);
    }
}
