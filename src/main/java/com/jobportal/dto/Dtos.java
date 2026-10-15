package com.jobportal.dto;

import com.jobportal.domain.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** All request/response objects in one place so beginners can find them easily. */
public final class Dtos {
    private Dtos() {}

    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain at least one letter and one number";

    // ---------- Authentication ----------
    public record RegisterSeekerRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 190) String email,
            @NotBlank(message = "Password is required") @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password) {}

    public record RegisterRecruiterRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 190) String email,
            @NotBlank(message = "Password is required") @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password,
            @NotBlank(message = "Company name is required") @Size(max = 150) String companyName) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required") @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String newPassword) {}

    public record UserDto(Long id, String email, String fullName, Role role, boolean active, LocalDateTime createdAt) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---------- Profiles ----------
    public record SeekerProfileRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @Size(max = 30) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,30}$", message = "Enter a valid phone number") String phone,
            @Size(max = 120) String location,
            @Size(max = 3000) String summary,
            @Size(max = 2000) String skills,
            @Size(max = 3000) String education,
            @Size(max = 3000) String experience) {}

    public record SeekerProfileResponse(
            Long userId, String fullName, String email, String phone, String location, String summary,
            String skills, String education, String experience,
            boolean hasResume, String resumeOriginalName, Long resumeSize, LocalDateTime resumeUploadedAt,
            boolean complete, List<String> missingFields) {}

    public record RecruiterProfileRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @Size(max = 30) String phone,
            @Size(max = 100) String designation,
            @NotBlank(message = "Company name is required") @Size(max = 150) String companyName,
            @Size(max = 3000) String companyDescription,
            @Size(max = 80) String industry,
            @Size(max = 255) String website,
            @Size(max = 120) String companyLocation,
            @Size(max = 40) String companySize) {}

    public record RecruiterProfileResponse(
            Long userId, String fullName, String email, String phone, String designation,
            Long companyId, String companyName, String companyDescription, String industry,
            String website, String companyLocation, String companySize) {}

    public record CompanyDto(Long id, String name, String description, String industry, String website,
                             String location, String companySize, boolean demo, long openJobs) {}

    // ---------- Jobs ----------
    public record JobRequest(
            @NotBlank(message = "Job title is required") @Size(max = 150) String title,
            @NotBlank(message = "Description is required") @Size(max = 8000) String description,
            @Size(max = 6000) String responsibilities,
            @NotBlank(message = "Required skills are required (comma separated)") @Size(max = 2000) String requiredSkills,
            @Size(max = 4000) String qualifications,
            @NotBlank(message = "Location is required") @Size(max = 120) String location,
            @PositiveOrZero(message = "Minimum salary cannot be negative") Long salaryMin,
            @PositiveOrZero(message = "Maximum salary cannot be negative") Long salaryMax,
            @NotNull(message = "Job type is required") JobType jobType,
            @NotNull(message = "Experience level is required") ExperienceLevel experienceLevel,
            @NotNull(message = "Work mode is required") WorkMode workMode,
            @NotNull(message = "Application deadline is required") LocalDate deadline) {}

    public record JobDto(
            Long id, String title, String description, String responsibilities, String requiredSkills,
            List<String> skills, String qualifications, String location, Long salaryMin, Long salaryMax,
            JobType jobType, ExperienceLevel experienceLevel, WorkMode workMode, LocalDate deadline,
            JobStatus status, boolean open, boolean demo, LocalDateTime createdAt, CompanyDto company,
            Long applicationCount, Boolean saved, Boolean applied) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record ReportRequest(
            @NotBlank(message = "Please choose a reason") @Size(max = 100) String reason,
            @Size(max = 2000) String details) {}

    // ---------- Applications ----------
    public record ApplyRequest(
            @NotNull(message = "Job id is required") Long jobId,
            @Size(max = 3000, message = "Cover letter is too long") String coverLetter) {}

    public record ApplicationDto(
            Long id, JobDto job, ApplicationStatus status, String coverLetter, String resumeOriginalName,
            LocalDateTime appliedAt, LocalDateTime updatedAt, boolean canWithdraw) {}

    public record RecruiterApplicationDto(
            Long id, Long jobId, String jobTitle, Long applicantId, String applicantName, String applicantEmail,
            ApplicationStatus status, LocalDateTime appliedAt, List<ApplicationStatus> allowedNext) {}

    public record NoteDto(Long id, String note, String recruiterName, LocalDateTime createdAt) {}

    public record ApplicantDetailDto(
            RecruiterApplicationDto application, String coverLetter, String resumeOriginalName,
            SeekerProfileResponse profile, List<NoteDto> notes) {}

    public record StatusUpdateRequest(@NotNull(message = "Status is required") ApplicationStatus status) {}

    public record NoteRequest(@NotBlank(message = "Note cannot be empty") @Size(max = 2000) String note) {}

    // ---------- Admin ----------
    public record ActiveRequest(@NotNull(message = "active is required") Boolean active) {}

    public record JobStatusRequest(@NotNull(message = "Status is required") JobStatus status) {}

    public record ReportDto(Long id, Long jobId, String jobTitle, String companyName, JobStatus jobStatus,
                            String reporterEmail, String reason, String details, ReportStatus status,
                            LocalDateTime createdAt) {}

    public record ReportResolveRequest(@NotNull(message = "Status is required") ReportStatus status, boolean removeJob) {}

    public record AdminApplicationDto(Long id, String jobTitle, String companyName, String applicantName,
                                      String applicantEmail, ApplicationStatus status, LocalDateTime appliedAt) {}
}
