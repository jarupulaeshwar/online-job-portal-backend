package com.jobportal.repository;

import com.jobportal.domain.ReportStatus;
import com.jobportal.entity.JobReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobReportRepository extends JpaRepository<JobReport, Long> {
    Page<JobReport> findByStatus(ReportStatus status, Pageable pageable);

    boolean existsByJobIdAndReporterIdAndStatus(Long jobId, Long reporterId, ReportStatus status);

    long countByStatus(ReportStatus status);

    void deleteByJobId(Long jobId);
}
