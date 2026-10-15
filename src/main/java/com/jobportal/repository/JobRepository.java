package com.jobportal.repository;

import com.jobportal.domain.JobStatus;
import com.jobportal.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
    List<Job> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    long countByStatus(JobStatus status);

    long countByRecruiterIdAndStatus(Long recruiterId, JobStatus status);

    long countByRecruiterId(Long recruiterId);

    long countByDemoTrue();

    long countByCompanyIdAndStatus(Long companyId, JobStatus status);

    @Query("select j.company.id, count(j) from Job j where j.status = com.jobportal.domain.JobStatus.PUBLISHED "
            + "group by j.company.id")
    List<Object[]> countPublishedByCompany();
}
