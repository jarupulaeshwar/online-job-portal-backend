package com.jobportal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_seeker_profiles")
@Getter
@Setter
@NoArgsConstructor
public class JobSeekerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(name = "resume_original_name", length = 255)
    private String resumeOriginalName;

    @Column(name = "resume_stored_name", length = 100)
    private String resumeStoredName;

    @Column(name = "resume_size")
    private Long resumeSize;

    @Column(name = "resume_uploaded_at")
    private LocalDateTime resumeUploadedAt;

    public boolean hasResume() {
        return resumeStoredName != null && !resumeStoredName.isBlank();
    }
}
