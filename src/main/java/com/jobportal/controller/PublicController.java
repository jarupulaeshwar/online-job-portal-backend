package com.jobportal.controller;

import com.jobportal.domain.ExperienceLevel;
import com.jobportal.domain.JobType;
import com.jobportal.domain.WorkMode;
import com.jobportal.dto.Dtos.*;
import com.jobportal.security.AuthUser;
import com.jobportal.service.JobService;
import com.jobportal.service.JobService.SearchParams;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Endpoints anyone can call without logging in (job browsing and companies). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicController {
    private final JobService jobService;

    @GetMapping("/jobs")
    public PageResponse<JobDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) ExperienceLevel experienceLevel,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) Long minSalary,
            @RequestParam(required = false) Long maxSalary,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal AuthUser viewer) {
        return jobService.search(new SearchParams(q, location, jobType, experienceLevel, workMode,
                minSalary, maxSalary, companyId, sort, page, size), viewer);
    }

    @GetMapping("/jobs/{id}")
    public JobDto job(@PathVariable Long id, @AuthenticationPrincipal AuthUser viewer) {
        return jobService.getPublic(id, viewer);
    }

    @GetMapping("/companies")
    public PageResponse<CompanyDto> companies(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        return jobService.companies(q, page, size);
    }

    @GetMapping("/companies/{id}")
    public CompanyDto company(@PathVariable Long id) {
        return jobService.company(id);
    }
}
