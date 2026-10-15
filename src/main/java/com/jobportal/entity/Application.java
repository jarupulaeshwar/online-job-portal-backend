package com.jobportal.entity;

import com.jobportal.domain.ApplicationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_job_seeker", columnNames = {"job_id", "seeker_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seeker_id", nullable = false)
    private User seeker;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    /** Snapshot of the resume submitted with this application. */
    @Column(name = "resume_original_name", length = 255)
    private String resumeOriginalName;

    @Column(name = "resume_stored_name", length = 100)
    private String resumeStoredName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
