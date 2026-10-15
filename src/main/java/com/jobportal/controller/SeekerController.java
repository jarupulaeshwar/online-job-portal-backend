package com.jobportal.controller;

import com.jobportal.dto.Dtos.*;
import com.jobportal.security.AuthUser;
import com.jobportal.service.ApplicationService;
import com.jobportal.service.JobService;
import com.jobportal.service.ProfileService;
import com.jobportal.service.StatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Everything a logged-in job seeker can do. The URL rules in SecurityConfig restrict these to JOB_SEEKER. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SeekerController {
    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final StatsService statsService;

    // ----- profile & resume -----
    @GetMapping("/seeker/profile")
    public SeekerProfileResponse profile(@AuthenticationPrincipal AuthUser user) {
        return profileService.getSeeker(user.id());
    }

    @PutMapping("/seeker/profile")
    public SeekerProfileResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody SeekerProfileRequest req) {
        return profileService.updateSeeker(user.id(), req);
    }

    @PostMapping(value = "/seeker/resume", consumes = "multipart/form-data")
    public SeekerProfileResponse uploadResume(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam("file") MultipartFile file) {
        return profileService.uploadResume(user.id(), file);
    }

    @GetMapping("/seeker/resume")
    public ResponseEntity<byte[]> myResume(@AuthenticationPrincipal AuthUser user) {
        return ResumeResponses.pdf(profileService.myResume(user.id()));
    }

    @GetMapping("/seeker/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal AuthUser user) {
        return statsService.seeker(user.id());
    }

    // ----- saved jobs -----
    @GetMapping("/saved-jobs")
    public List<JobDto> savedJobs(@AuthenticationPrincipal AuthUser user) {
        return jobService.savedJobs(user.id());
    }

    @PutMapping("/saved-jobs/{jobId}")
    public Map<String, Object> save(@AuthenticationPrincipal AuthUser user, @PathVariable Long jobId) {
        jobService.saveJob(user.id(), jobId);
        return Map.of("saved", true);
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    public Map<String, Object> unsave(@AuthenticationPrincipal AuthUser user, @PathVariable Long jobId) {
        jobService.unsaveJob(user.id(), jobId);
        return Map.of("saved", false);
    }

    // ----- applications -----
    @PostMapping("/applications")
    public ResponseEntity<ApplicationDto> apply(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody ApplyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(user.id(), req));
    }

    @GetMapping("/applications/mine")
    public List<ApplicationDto> myApplications(@AuthenticationPrincipal AuthUser user) {
        return applicationService.mine(user.id());
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApplicationDto withdraw(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return applicationService.withdraw(user.id(), id);
    }

    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<byte[]> applicationResume(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResumeResponses.pdf(applicationService.seekerApplicationResume(user.id(), id));
    }

    // ----- reporting a job -----
    @PostMapping("/jobs/{jobId}/report")
    public Map<String, String> report(@AuthenticationPrincipal AuthUser user, @PathVariable Long jobId,
                                      @Valid @RequestBody ReportRequest req) {
        jobService.report(user.id(), jobId, req);
        return Map.of("message", "Thank you. Our team will review this listing.");
    }
}
