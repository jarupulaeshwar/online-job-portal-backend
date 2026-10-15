package com.jobportal.repository;

import com.jobportal.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {
    boolean existsByJobIdAndSeekerId(Long jobId, Long seekerId);

    List<Application> findBySeekerIdOrderByAppliedAtDesc(Long seekerId);

    long countByJobId(Long jobId);

    long countBySeekerId(Long seekerId);

    @Query("select a.status, count(a) from Application a where a.seeker.id = :uid group by a.status")
    List<Object[]> countBySeekerGroupedByStatus(@Param("uid") Long uid);

    @Query("select a.status, count(a) from Application a where a.job.recruiter.id = :rid group by a.status")
    List<Object[]> countByRecruiterGroupedByStatus(@Param("rid") Long rid);
}
