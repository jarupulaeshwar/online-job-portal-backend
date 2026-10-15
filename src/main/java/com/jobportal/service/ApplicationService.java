package com.jobportal.service;

import com.jobportal.domain.ApplicationStatus;
import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.*;
import com.jobportal.exception.ApiException;
import com.jobportal.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerProfileRepository;
    private final ApplicationNoteRepository noteRepository;
    private final FileStorageService fileStorage;
    private final JobService jobService;

    // ---------- mapping ----------
    private ApplicationDto toDto(Application a) {
        return new ApplicationDto(a.getId(), jobService.toDto(a.getJob(), null, null, true), a.getStatus(),
                a.getCoverLetter(), a.getResumeOriginalName(), a.getAppliedAt(), a.getUpdatedAt(),
                a.getStatus().canBeWithdrawn());
    }

    private static RecruiterApplicationDto toRecruiterDto(Application a) {
        return new RecruiterApplicationDto(a.getId(), a.getJob().getId(), a.getJob().getTitle(),
                a.getSeeker().getId(), a.getSeeker().getFullName(), a.getSeeker().getEmail(), a.getStatus(),
                a.getAppliedAt(), new ArrayList<>(a.getStatus().recruiterTransitions()));
    }

    private static NoteDto toNoteDto(ApplicationNote n) {
        return new NoteDto(n.getId(), n.getNote(), n.getRecruiter().getFullName(), n.getCreatedAt());
    }

    // ---------- job seeker ----------
    @Transactional
    public ApplicationDto apply(Long seekerId, ApplyRequest req) {
        User seeker = userRepository.findById(seekerId).orElseThrow(() -> ApiException.notFound("User not found."));
        JobSeekerProfile profile = seekerProfileRepository.findByUserId(seekerId)
                .orElseThrow(() -> ApiException.notFound("Job seeker profile not found."));
        SeekerProfileResponse view = ProfileService.toSeekerResponse(profile);
        if (!view.complete()) {
            throw ApiException.badRequest("Complete your profile and upload a resume before applying. Missing: "
                    + String.join(", ", view.missingFields()) + ".");
        }
        Job job = jobRepository.findById(req.jobId()).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (!job.isOpen() || !job.getRecruiter().isActive()) {
            throw ApiException.conflict("This job is closed and no longer accepts applications.");
        }
        if (applicationRepository.existsByJobIdAndSeekerId(job.getId(), seekerId)) {
            throw ApiException.conflict("You have already applied for this job.");
        }
        Application a = new Application();
        a.setJob(job);
        a.setSeeker(seeker);
        a.setCoverLetter(req.coverLetter() == null || req.coverLetter().isBlank() ? null : req.coverLetter().trim());
        a.setResumeOriginalName(profile.getResumeOriginalName());
        a.setResumeStoredName(profile.getResumeStoredName());
        a.setStatus(ApplicationStatus.APPLIED);
        return toDto(applicationRepository.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> mine(Long seekerId) {
        return applicationRepository.findBySeekerIdOrderByAppliedAtDesc(seekerId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ApplicationDto withdraw(Long seekerId, Long applicationId) {
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        if (!a.getSeeker().getId().equals(seekerId)) {
            throw ApiException.forbidden("You can only withdraw your own applications.");
        }
        if (!a.getStatus().canBeWithdrawn()) {
            throw ApiException.conflict("This application can no longer be withdrawn because it is already "
                    + a.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        a.setStatus(ApplicationStatus.WITHDRAWN);
        a.setUpdatedAt(LocalDateTime.now());
        return toDto(applicationRepository.save(a));
    }

    @Transactional(readOnly = true)
    public ResumeFile seekerApplicationResume(Long seekerId, Long applicationId) {
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        if (!a.getSeeker().getId().equals(seekerId)) {
            throw ApiException.forbidden("You can only view your own applications.");
        }
        return new ResumeFile(a.getResumeOriginalName(), fileStorage.read(a.getResumeStoredName()));
    }

    // ---------- recruiter ----------
    private Application ownedApplication(Long recruiterId, Long applicationId) {
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        if (!a.getJob().getRecruiter().getId().equals(recruiterId)) {
            throw ApiException.forbidden("This application belongs to another recruiter.");
        }
        return a;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecruiterApplicationDto> forRecruiter(Long recruiterId, Long jobId, ApplicationStatus status,
                                                              int page, int size) {
        Specification<Application> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("job").get("recruiter").get("id"), recruiterId));
            if (jobId != null) preds.add(cb.equal(root.get("job").get("id"), jobId));
            if (status != null) preds.add(cb.equal(root.get("status"), status));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<Application> result = applicationRepository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(result.getContent().stream().map(ApplicationService::toRecruiterDto).collect(Collectors.toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ApplicantDetailDto detail(Long recruiterId, Long applicationId) {
        Application a = ownedApplication(recruiterId, applicationId);
        JobSeekerProfile profile = seekerProfileRepository.findByUserId(a.getSeeker().getId())
                .orElseThrow(() -> ApiException.notFound("Applicant profile not found."));
        List<NoteDto> notes = noteRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(ApplicationService::toNoteDto).collect(Collectors.toList());
        return new ApplicantDetailDto(toRecruiterDto(a), a.getCoverLetter(), a.getResumeOriginalName(),
                ProfileService.toSeekerResponse(profile), notes);
    }

    @Transactional
    public RecruiterApplicationDto updateStatus(Long recruiterId, Long applicationId, ApplicationStatus next) {
        Application a = ownedApplication(recruiterId, applicationId);
        if (!a.getStatus().canRecruiterMoveTo(next)) {
            throw ApiException.conflict("An application that is " + a.getStatus().name().toLowerCase().replace('_', ' ')
                    + " cannot be changed to " + next.name().toLowerCase().replace('_', ' ') + ".");
        }
        a.setStatus(next);
        a.setUpdatedAt(LocalDateTime.now());
        return toRecruiterDto(applicationRepository.save(a));
    }

    @Transactional
    public NoteDto addNote(Long recruiterId, Long applicationId, NoteRequest req) {
        Application a = ownedApplication(recruiterId, applicationId);
        ApplicationNote n = new ApplicationNote();
        n.setApplication(a);
        n.setRecruiter(userRepository.findById(recruiterId).orElseThrow(() -> ApiException.notFound("User not found.")));
        n.setNote(req.note().trim());
        return toNoteDto(noteRepository.save(n));
    }

    @Transactional(readOnly = true)
    public ResumeFile recruiterApplicationResume(Long recruiterId, Long applicationId) {
        Application a = ownedApplication(recruiterId, applicationId);
        return new ResumeFile(a.getResumeOriginalName(), fileStorage.read(a.getResumeStoredName()));
    }
}
