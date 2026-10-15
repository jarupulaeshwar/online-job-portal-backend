package com.jobportal.service;

import com.jobportal.domain.ApplicationStatus;
import com.jobportal.domain.JobStatus;
import com.jobportal.domain.ReportStatus;
import com.jobportal.domain.Role;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numbers shown on the three dashboards. */
@Service
@RequiredArgsConstructor
public class StatsService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final JobReportRepository reportRepository;
    private final JobSeekerProfileRepository seekerProfileRepository;

    private static Map<String, Long> statusMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            map.put(s.name(), 0L);
        }
        for (Object[] row : rows) {
            map.put(((ApplicationStatus) row[0]).name(), (Long) row[1]);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> seeker(Long userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("applicationsTotal", applicationRepository.countBySeekerId(userId));
        stats.put("applicationsByStatus", statusMap(applicationRepository.countBySeekerGroupedByStatus(userId)));
        stats.put("savedJobs", savedJobRepository.countByUserId(userId));
        JobSeekerProfile profile = seekerProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            var view = ProfileService.toSeekerResponse(profile);
            stats.put("profileComplete", view.complete());
            stats.put("missingFields", view.missingFields());
        }
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recruiter(Long recruiterId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("jobsTotal", jobRepository.countByRecruiterId(recruiterId));
        stats.put("jobsPublished", jobRepository.countByRecruiterIdAndStatus(recruiterId, JobStatus.PUBLISHED));
        stats.put("jobsDraft", jobRepository.countByRecruiterIdAndStatus(recruiterId, JobStatus.DRAFT));
        stats.put("jobsClosed", jobRepository.countByRecruiterIdAndStatus(recruiterId, JobStatus.CLOSED));
        Map<String, Long> byStatus = statusMap(applicationRepository.countByRecruiterGroupedByStatus(recruiterId));
        stats.put("applicationsByStatus", byStatus);
        stats.put("applicationsTotal", byStatus.values().stream().mapToLong(Long::longValue).sum());
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> admin() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", userRepository.count());
        stats.put("jobSeekers", userRepository.countByRole(Role.JOB_SEEKER));
        stats.put("recruiters", userRepository.countByRole(Role.RECRUITER));
        stats.put("companies", companyRepository.count());
        stats.put("jobsTotal", jobRepository.count());
        stats.put("jobsPublished", jobRepository.countByStatus(JobStatus.PUBLISHED));
        stats.put("jobsRemoved", jobRepository.countByStatus(JobStatus.REMOVED));
        stats.put("applications", applicationRepository.count());
        stats.put("openReports", reportRepository.countByStatus(ReportStatus.OPEN));
        return stats;
    }
}
