package com.jobportal.controller;

import com.jobportal.domain.ApplicationStatus;
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

import java.util.List;
import java.util.Map;

/** Everything a recruiter can do. Ownership of every job/application is checked in the services. */
@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class RecruiterController {
    private final ProfileService profileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final StatsService statsService;

    @GetMapping("/profile")
    public RecruiterProfileResponse profile(@AuthenticationPrincipal AuthUser user) {
        return profileService.getRecruiter(user.id());
    }

    @PutMapping("/profile")
    public RecruiterProfileResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody RecruiterProfileRequest req) {
        return profileService.updateRecruiter(user.id(), req);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal AuthUser user) {
        return statsService.recruiter(user.id());
    }

    // ----- jobs -----
    @GetMapping("/jobs")
    public List<JobDto> myJobs(@AuthenticationPrincipal AuthUser user) {
        return jobService.myJobs(user.id());
    }

    @GetMapping("/jobs/{id}")
    public JobDto myJob(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return jobService.myJob(user.id(), id);
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobDto> create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody JobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(user.id(), req));
    }

    @PutMapping("/jobs/{id}")
    public JobDto update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                         @Valid @RequestBody JobRequest req) {
        return jobService.update(user.id(), id, req);
    }

    @PostMapping("/jobs/{id}/publish")
    public JobDto publish(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return jobService.publish(user.id(), id);
    }

    @PostMapping("/jobs/{id}/close")
    public JobDto close(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return jobService.close(user.id(), id);
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        jobService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- applicants -----
    @GetMapping("/applications")
    public PageResponse<RecruiterApplicationDto> applications(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return applicationService.forRecruiter(user.id(), jobId, status, page, size);
    }

    @GetMapping("/applications/{id}")
    public ApplicantDetailDto applicationDetail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return applicationService.detail(user.id(), id);
    }

    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<byte[]> resume(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResumeResponses.pdf(applicationService.recruiterApplicationResume(user.id(), id));
    }

    @PutMapping("/applications/{id}/status")
    public RecruiterApplicationDto updateStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                @Valid @RequestBody StatusUpdateRequest req) {
        return applicationService.updateStatus(user.id(), id, req.status());
    }

    @PostMapping("/applications/{id}/notes")
    public ResponseEntity<NoteDto> addNote(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                           @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.addNote(user.id(), id, req));
    }
}
