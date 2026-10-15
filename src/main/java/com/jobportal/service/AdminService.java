package com.jobportal.service;

import com.jobportal.domain.JobStatus;
import com.jobportal.domain.ReportStatus;
import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.Application;
import com.jobportal.entity.Job;
import com.jobportal.entity.JobReport;
import com.jobportal.entity.User;
import com.jobportal.exception.ApiException;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobReportRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final JobReportRepository reportRepository;
    private final JobService jobService;

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }

    private static <T> PageResponse<T> toPage(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> users(String q, int page, int size) {
        return toPage(userRepository.search(q == null ? "" : q.trim(),
                pageable(page, size, Sort.by(Sort.Order.desc("createdAt")))).map(AuthService::toDto));
    }

    @Transactional
    public UserDto setUserActive(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId) && !active) {
            throw ApiException.badRequest("You cannot deactivate your own account.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        user.setActive(active);
        return AuthService.toDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDto> jobs(JobStatus status, String q, int page, int size) {
        Page<Job> result = jobRepository.findAll(JobSpecs.adminSearch(status, q),
                pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return toPage(result.map(j -> jobService.toDto(j, applicationRepository.countByJobId(j.getId()), null, null)));
    }

    @Transactional
    public JobDto setJobStatus(Long jobId, JobStatus status) {
        if (status == JobStatus.DRAFT) {
            throw ApiException.badRequest("Administrators can publish, close or remove a job.");
        }
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        return jobService.toDto(jobRepository.save(job), applicationRepository.countByJobId(jobId), null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminApplicationDto> applications(int page, int size) {
        Page<Application> result = applicationRepository.findAll(
                pageable(page, size, Sort.by(Sort.Order.desc("appliedAt"), Sort.Order.desc("id"))));
        return toPage(result.map(a -> new AdminApplicationDto(a.getId(), a.getJob().getTitle(),
                a.getJob().getCompany().getName(), a.getSeeker().getFullName(), a.getSeeker().getEmail(),
                a.getStatus(), a.getAppliedAt())));
    }

    private static ReportDto toDto(JobReport r) {
        return new ReportDto(r.getId(), r.getJob().getId(), r.getJob().getTitle(), r.getJob().getCompany().getName(),
                r.getJob().getStatus(), r.getReporter().getEmail(), r.getReason(), r.getDetails(), r.getStatus(),
                r.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportDto> reports(ReportStatus status, int page, int size) {
        Pageable p = pageable(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<JobReport> result = status == null ? reportRepository.findAll(p) : reportRepository.findByStatus(status, p);
        return toPage(result.map(AdminService::toDto));
    }

    @Transactional
    public ReportDto resolveReport(Long reportId, ReportResolveRequest req) {
        JobReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Report not found."));
        if (req.status() == ReportStatus.OPEN) {
            throw ApiException.badRequest("Choose RESOLVED or DISMISSED.");
        }
        report.setStatus(req.status());
        if (req.removeJob()) {
            Job job = report.getJob();
            job.setStatus(JobStatus.REMOVED);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
        return toDto(reportRepository.save(report));
    }
}
