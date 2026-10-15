package com.jobportal.repository;

import com.jobportal.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    List<SavedJob> findByUserIdOrderBySavedAtDesc(Long userId);

    Optional<SavedJob> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    long countByUserId(Long userId);

    void deleteByJobId(Long jobId);
}
