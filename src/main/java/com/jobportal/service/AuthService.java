package com.jobportal.service;

import com.jobportal.domain.Role;
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
import com.jobportal.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }

    @Transactional
    public AuthResponse registerSeeker(RegisterSeekerRequest req) {
        User user = createUser(req.fullName(), req.email(), req.password(), Role.JOB_SEEKER);
        JobSeekerProfile profile = new JobSeekerProfile();
        profile.setUser(user);
        seekerProfileRepository.save(profile);
        return new AuthResponse(jwtService.createToken(user), toDto(user));
    }

    @Transactional
    public AuthResponse registerRecruiter(RegisterRecruiterRequest req) {
        User user = createUser(req.fullName(), req.email(), req.password(), Role.RECRUITER);
        Company company = new Company();
        company.setName(req.companyName().trim());
        companyRepository.save(company);
        RecruiterProfile profile = new RecruiterProfile();
        profile.setUser(user);
        profile.setCompany(company);
        recruiterProfileRepository.save(profile);
        return new AuthResponse(jwtService.createToken(user), toDto(user));
    }

    private User createUser(String fullName, String email, String password, Role role) {
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.conflict("An account with this email already exists. Try logging in instead.");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setFullName(fullName.trim());
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password."));
        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated. Please contact the site administrator.");
        }
        return new AuthResponse(jwtService.createToken(user), toDto(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return toDto(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }
}
