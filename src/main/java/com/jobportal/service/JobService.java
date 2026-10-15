package com.jobportal.service;

import com.jobportal.domain.*;
import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.*;
import com.jobportal.exception.ApiException;
import com.jobportal.repository.*;
import com.jobportal.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationRepository applicationRepository;
    private final JobReportRepository reportRepository;
    private final UserRepository userRepository;

    public record SearchParams(String q, String location, JobType jobType, ExperienceLevel experienceLevel,
                               WorkMode workMode, Long minSalary, Long maxSalary, Long companyId,
                               String sort, int page, int size) {}

    // ---------- mapping ----------
    public static CompanyDto toCompanyDto(Company c, long openJobs) {
        return new CompanyDto(c.getId(), c.getName(), c.getDescription(), c.getIndustry(), c.getWebsite(),
                c.getLocation(), c.getCompanySize(), c.isDemo(), openJobs);
    }

    static List<String> splitSkills(String skills) {
        if (skills == null) return List.of();
        return Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public JobDto toDto(Job j, Long applicationCount, Boolean saved, Boolean applied) {
        return new JobDto(j.getId(), j.getTitle(), j.getDescription(), j.getResponsibilities(), j.getRequiredSkills(),
                splitSkills(j.getRequiredSkills()), j.getQualifications(), j.getLocation(), j.getSalaryMin(),
                j.getSalaryMax(), j.getJobType(), j.getExperienceLevel(), j.getWorkMode(), j.getDeadline(),
                j.getStatus(), j.isOpen() && j.getRecruiter().isActive(), j.isDemo(), j.getCreatedAt(),
                toCompanyDto(j.getCompany(), 0), applicationCount, saved, applied);
    }

    private Set<Long> savedIds(AuthUser viewer) {
        if (viewer == null || viewer.role() != Role.JOB_SEEKER) return Set.of();
        return savedJobRepository.findByUserIdOrderBySavedAtDesc(viewer.id()).stream()
                .map(s -> s.getJob().getId()).collect(Collectors.toSet());
    }

    private Set<Long> appliedIds(AuthUser viewer) {
        if (viewer == null || viewer.role() != Role.JOB_SEEKER) return Set.of();
        return applicationRepository.findBySeekerIdOrderByAppliedAtDesc(viewer.id()).stream()
                .map(a -> a.getJob().getId()).collect(Collectors.toSet());
    }

    private static <T> PageResponse<T> toPage(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    // ---------- public ----------
    @Transactional(readOnly = true)
    public PageResponse<JobDto> search(SearchParams p, AuthUser viewer) {
        int size = Math.min(Math.max(p.size(), 1), 50);
        int page = Math.max(p.page(), 0);
        boolean relevant = "relevant".equalsIgnoreCase(p.sort()) && p.q() != null && !p.q().isBlank();
        Pageable pageable;
        if ("salary".equalsIgnoreCase(p.sort())) {
            pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("salaryMax"), Sort.Order.desc("createdAt")));
        } else if (relevant) {
            pageable = PageRequest.of(page, size); // ordering is added by JobSpecs
        } else {
            pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        }
        Page<Job> result = jobRepository.findAll(JobSpecs.publicSearch(p), pageable);
        Set<Long> saved = savedIds(viewer);
        Set<Long> applied = appliedIds(viewer);
        boolean seeker = viewer != null && viewer.role() == Role.JOB_SEEKER;
        return toPage(result.map(j -> toDto(j, null, seeker ? saved.contains(j.getId()) : null,
                seeker ? applied.contains(j.getId()) : null)));
    }

    @Transactional(readOnly = true)
    public JobDto getPublic(Long id, AuthUser viewer) {
        Job job = jobRepository.findById(id).orElseThrow(() -> ApiException.notFound("Job not found."));
        boolean owner = viewer != null && job.getRecruiter().getId().equals(viewer.id());
        boolean admin = viewer != null && viewer.role() == Role.ADMIN;
        boolean visible = (job.getStatus() == JobStatus.PUBLISHED || job.getStatus() == JobStatus.CLOSED)
                && job.getRecruiter().isActive();
        if (!visible && !owner && !admin) {
            throw ApiException.notFound("Job not found.");
        }
        boolean seeker = viewer != null && viewer.role() == Role.JOB_SEEKER;
        Boolean saved = seeker ? savedJobRepository.existsByUserIdAndJobId(viewer.id(), id) : null;
        Boolean applied = seeker ? applicationRepository.existsByJobIdAndSeekerId(viewer.id(), id) : null;
        return toDto(job, null, saved, applied);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyDto> companies(String q, int page, int size) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : jobRepository.countPublishedByCompany()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Page<Company> result = companyRepository.search(q == null ? "" : q.trim(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Order.asc("name"))));
        return toPage(result.map(c -> toCompanyDto(c, counts.getOrDefault(c.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public CompanyDto company(Long id) {
        Company c = companyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company not found."));
        return toCompanyDto(c, jobRepository.countByCompanyIdAndStatus(id, JobStatus.PUBLISHED));
    }

    // ---------- saved jobs ----------
    @Transactional(readOnly = true)
    public List<JobDto> savedJobs(Long userId) {
        Set<Long> applied = applicationRepository.findBySeekerIdOrderByAppliedAtDesc(userId).stream()
                .map(a -> a.getJob().getId()).collect(Collectors.toSet());
        return savedJobRepository.findByUserIdOrderBySavedAtDesc(userId).stream()
                .map(s -> toDto(s.getJob(), null, true, applied.contains(s.getJob().getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveJob(Long userId, Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.REMOVED) {
            throw ApiException.notFound("Job not found.");
        }
        if (savedJobRepository.existsByUserIdAndJobId(userId, jobId)) {
            return;
        }
        SavedJob saved = new SavedJob();
        saved.setUser(userRepository.getReferenceById(userId));
        saved.setJob(job);
        savedJobRepository.save(saved);
    }

    @Transactional
    public void unsaveJob(Long userId, Long jobId) {
        savedJobRepository.findByUserIdAndJobId(userId, jobId).ifPresent(savedJobRepository::delete);
    }

    // ---------- reports ----------
    @Transactional
    public void report(Long reporterId, Long jobId, ReportRequest req) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (reportRepository.existsByJobIdAndReporterIdAndStatus(jobId, reporterId, ReportStatus.OPEN)) {
            throw ApiException.conflict("You have already reported this job. Our team will review it.");
        }
        JobReport r = new JobReport();
        r.setJob(job);
        r.setReporter(userRepository.getReferenceById(reporterId));
        r.setReason(req.reason().trim());
        r.setDetails(req.details() == null || req.details().isBlank() ? null : req.details().trim());
        reportRepository.save(r);
    }

    // ---------- recruiter ----------
    private Job ownedJob(Long jobId, Long recruiterId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (!job.getRecruiter().getId().equals(recruiterId)) {
            throw ApiException.forbidden("You can only manage your own job listings.");
        }
        return job;
    }

    private static void validateSalary(JobRequest req) {
        if (req.salaryMin() != null && req.salaryMax() != null && req.salaryMin() > req.salaryMax()) {
            throw ApiException.badRequest("Minimum salary cannot be greater than maximum salary.");
        }
    }

    private static void requireFutureDeadline(LocalDate deadline) {
        if (deadline.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("The application deadline cannot be in the past.");
        }
    }

    private static void apply(Job job, JobRequest req) {
        job.setTitle(req.title().trim());
        job.setDescription(req.description().trim());
        job.setResponsibilities(req.responsibilities() == null ? null : req.responsibilities().trim());
        job.setRequiredSkills(req.requiredSkills().trim());
        job.setQualifications(req.qualifications() == null ? null : req.qualifications().trim());
        job.setLocation(req.location().trim());
        job.setSalaryMin(req.salaryMin());
        job.setSalaryMax(req.salaryMax());
        job.setJobType(req.jobType());
        job.setExperienceLevel(req.experienceLevel());
        job.setWorkMode(req.workMode());
        job.setDeadline(req.deadline());
        job.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<JobDto> myJobs(Long recruiterId) {
        return jobRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
                .map(j -> toDto(j, applicationRepository.countByJobId(j.getId()), null, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobDto myJob(Long recruiterId, Long jobId) {
        Job job = ownedJob(jobId, recruiterId);
        return toDto(job, applicationRepository.countByJobId(jobId), null, null);
    }

    @Transactional
    public JobDto create(Long recruiterId, JobRequest req) {
        RecruiterProfile profile = recruiterProfileRepository.findByUserId(recruiterId)
                .orElseThrow(() -> ApiException.notFound("Recruiter profile not found."));
        if (profile.getCompany() == null) {
            throw ApiException.badRequest("Please complete your company profile before posting a job.");
        }
        validateSalary(req);
        requireFutureDeadline(req.deadline());
        Job job = new Job();
        job.setCompany(profile.getCompany());
        job.setRecruiter(profile.getUser());
        job.setStatus(JobStatus.DRAFT);
        apply(job, req);
        return toDto(jobRepository.save(job), 0L, null, null);
    }

    @Transactional
    public JobDto update(Long recruiterId, Long jobId, JobRequest req) {
        Job job = ownedJob(jobId, recruiterId);
        if (job.getStatus() == JobStatus.REMOVED) {
            throw ApiException.forbidden("This listing was removed by an administrator and cannot be edited.");
        }
        validateSalary(req);
        if (job.getStatus() == JobStatus.PUBLISHED || !req.deadline().equals(job.getDeadline())) {
            requireFutureDeadline(req.deadline());
        }
        apply(job, req);
        return toDto(jobRepository.save(job), applicationRepository.countByJobId(jobId), null, null);
    }

    @Transactional
    public JobDto publish(Long recruiterId, Long jobId) {
        Job job = ownedJob(jobId, recruiterId);
        if (job.getStatus() == JobStatus.REMOVED) {
            throw ApiException.forbidden("This listing was removed by an administrator and cannot be published.");
        }
        if (job.getStatus() == JobStatus.PUBLISHED) {
            throw ApiException.conflict("This job is already published.");
        }
        requireFutureDeadline(job.getDeadline());
        job.setStatus(JobStatus.PUBLISHED);
        job.setUpdatedAt(LocalDateTime.now());
        return toDto(jobRepository.save(job), applicationRepository.countByJobId(jobId), null, null);
    }

    @Transactional
    public JobDto close(Long recruiterId, Long jobId) {
        Job job = ownedJob(jobId, recruiterId);
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw ApiException.conflict("Only published jobs can be closed.");
        }
        job.setStatus(JobStatus.CLOSED);
        job.setUpdatedAt(LocalDateTime.now());
        return toDto(jobRepository.save(job), applicationRepository.countByJobId(jobId), null, null);
    }

    @Transactional
    public void delete(Long recruiterId, Long jobId) {
        Job job = ownedJob(jobId, recruiterId);
        if (applicationRepository.countByJobId(jobId) > 0) {
            throw ApiException.conflict("This job already has applications, so it cannot be deleted. Close it instead.");
        }
        savedJobRepository.deleteByJobId(jobId);
        reportRepository.deleteByJobId(jobId);
        jobRepository.delete(job);
    }
}
