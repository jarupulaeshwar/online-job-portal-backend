package com.jobportal.service;

import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.Company;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.entity.RecruiterProfile;
import com.jobportal.entity.User;
import com.jobportal.exception.ApiException;
import com.jobportal.repository.CompanyRepository;
import com.jobportal.repository.JobSeekerProfileRepository;
import com.jobportal.repository.RecruiterProfileRepository;
import com.jobportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final RecruiterProfileRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorage;

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static SeekerProfileResponse toSeekerResponse(JobSeekerProfile p) {
        List<String> missing = new ArrayList<>();
        if (blank(p.getPhone())) missing.add("phone");
        if (blank(p.getLocation())) missing.add("location");
        if (blank(p.getSummary())) missing.add("summary");
        if (blank(p.getSkills())) missing.add("skills");
        if (!p.hasResume()) missing.add("resume");
        User u = p.getUser();
        return new SeekerProfileResponse(u.getId(), u.getFullName(), u.getEmail(), p.getPhone(), p.getLocation(),
                p.getSummary(), p.getSkills(), p.getEducation(), p.getExperience(), p.hasResume(),
                p.getResumeOriginalName(), p.getResumeSize(), p.getResumeUploadedAt(), missing.isEmpty(), missing);
    }

    private JobSeekerProfile seekerProfile(Long userId) {
        return seekerRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Job seeker profile not found"));
    }

    @Transactional(readOnly = true)
    public SeekerProfileResponse getSeeker(Long userId) {
        return toSeekerResponse(seekerProfile(userId));
    }

    @Transactional
    public SeekerProfileResponse updateSeeker(Long userId, SeekerProfileRequest req) {
        JobSeekerProfile p = seekerProfile(userId);
        p.getUser().setFullName(req.fullName().trim());
        p.setPhone(clean(req.phone()));
        p.setLocation(clean(req.location()));
        p.setSummary(clean(req.summary()));
        p.setSkills(clean(req.skills()));
        p.setEducation(clean(req.education()));
        p.setExperience(clean(req.experience()));
        userRepository.save(p.getUser());
        return toSeekerResponse(seekerRepository.save(p));
    }

    @Transactional
    public SeekerProfileResponse uploadResume(Long userId, MultipartFile file) {
        JobSeekerProfile p = seekerProfile(userId);
        FileStorageService.StoredFile stored = fileStorage.storeResume(file);
        // Older files are kept because past applications still point to them.
        p.setResumeStoredName(stored.storedName());
        p.setResumeOriginalName(stored.originalName());
        p.setResumeSize(stored.size());
        p.setResumeUploadedAt(LocalDateTime.now());
        return toSeekerResponse(seekerRepository.save(p));
    }

    @Transactional(readOnly = true)
    public ResumeFile myResume(Long userId) {
        JobSeekerProfile p = seekerProfile(userId);
        if (!p.hasResume()) {
            throw ApiException.notFound("You have not uploaded a resume yet.");
        }
        return new ResumeFile(p.getResumeOriginalName(), fileStorage.read(p.getResumeStoredName()));
    }

    // ---------- Recruiter ----------
    private RecruiterProfile recruiterProfile(Long userId) {
        return recruiterRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Recruiter profile not found"));
    }

    private static RecruiterProfileResponse toRecruiterResponse(RecruiterProfile p) {
        User u = p.getUser();
        Company c = p.getCompany();
        return new RecruiterProfileResponse(u.getId(), u.getFullName(), u.getEmail(), p.getPhone(), p.getDesignation(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), c == null ? null : c.getDescription(),
                c == null ? null : c.getIndustry(), c == null ? null : c.getWebsite(),
                c == null ? null : c.getLocation(), c == null ? null : c.getCompanySize());
    }

    @Transactional(readOnly = true)
    public RecruiterProfileResponse getRecruiter(Long userId) {
        return toRecruiterResponse(recruiterProfile(userId));
    }

    @Transactional
    public RecruiterProfileResponse updateRecruiter(Long userId, RecruiterProfileRequest req) {
        RecruiterProfile p = recruiterProfile(userId);
        p.getUser().setFullName(req.fullName().trim());
        p.setPhone(clean(req.phone()));
        p.setDesignation(clean(req.designation()));
        Company c = p.getCompany();
        if (c == null) {
            c = new Company();
        }
        c.setName(req.companyName().trim());
        c.setDescription(clean(req.companyDescription()));
        c.setIndustry(clean(req.industry()));
        c.setWebsite(clean(req.website()));
        c.setLocation(clean(req.companyLocation()));
        c.setCompanySize(clean(req.companySize()));
        p.setCompany(companyRepository.save(c));
        userRepository.save(p.getUser());
        return toRecruiterResponse(recruiterRepository.save(p));
    }
}
