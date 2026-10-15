package com.jobportal.config;

import com.jobportal.domain.Role;
import com.jobportal.entity.Company;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.entity.RecruiterProfile;
import com.jobportal.entity.User;
import com.jobportal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * Runs at startup:
 *  1. creates the administrator from ADMIN_EMAIL / ADMIN_PASSWORD (only if both are set and the account is missing)
 *  2. if app.seed.demo-data=true: creates the demo job seeker and demo recruiter (passwords are BCrypt hashed
 *     here, never stored in plain text) and loads the demo companies and jobs from seed/demo-data.sql.
 * Demo jobs are loaded only when no demo job exists yet, so restarting never duplicates data.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String DEMO_SEEKER_EMAIL = "seeker@demo.jobportal.local";
    public static final String DEMO_RECRUITER_EMAIL = "recruiter@demo.jobportal.local";

    private final AppProperties props;
    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public DataSeeder(AppProperties props, UserRepository userRepository,
                      JobSeekerProfileRepository seekerProfileRepository,
                      RecruiterProfileRepository recruiterProfileRepository, CompanyRepository companyRepository,
                      JobRepository jobRepository, PasswordEncoder passwordEncoder, DataSource dataSource) {
        this.props = props;
        this.userRepository = userRepository;
        this.seekerProfileRepository = seekerProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        createAdmin();
        if (props.seed().demoData()) {
            seedDemoData();
        }
    }

    private void createAdmin() {
        String email = props.admin().email();
        String password = props.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD are not set, so no administrator account was created.");
            return;
        }
        if (password.length() < 8) {
            log.warn("ADMIN_PASSWORD must be at least 8 characters. Administrator was not created.");
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        User admin = new User();
        admin.setEmail(normalized);
        admin.setFullName(props.admin().name() == null || props.admin().name().isBlank()
                ? "Platform Administrator" : props.admin().name());
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setCreatedAt(LocalDateTime.now());
        userRepository.save(admin);
        log.info("Administrator account created for {}", normalized);
    }

    private void seedDemoData() {
        String demoPassword = props.demo().password();
        User seeker = userRepository.findByEmailIgnoreCase(DEMO_SEEKER_EMAIL).orElseGet(() -> {
            User u = newUser("Demo Job Seeker", DEMO_SEEKER_EMAIL, Role.JOB_SEEKER, demoPassword);
            JobSeekerProfile profile = new JobSeekerProfile();
            profile.setUser(u);
            profile.setPhone("+91 98765 43210");
            profile.setLocation("Hyderabad, Telangana");
            profile.setSummary("Demo account: a fresher with interest in software development. Edit this profile and upload your own PDF resume before applying.");
            profile.setSkills("Java, SQL, React");
            profile.setEducation("B.Tech in Computer Science (demo data)");
            seekerProfileRepository.save(profile);
            return u;
        });
        User recruiter = userRepository.findByEmailIgnoreCase(DEMO_RECRUITER_EMAIL).orElseGet(() -> {
            User u = newUser("Demo Recruiter", DEMO_RECRUITER_EMAIL, Role.RECRUITER, demoPassword);
            RecruiterProfile profile = new RecruiterProfile();
            profile.setUser(u);
            profile.setDesignation("Talent Acquisition Lead (demo)");
            recruiterProfileRepository.save(profile);
            return u;
        });
        log.info("Demo accounts ready: {} and {}", seeker.getEmail(), recruiter.getEmail());

        if (jobRepository.countByDemoTrue() == 0) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("seed/demo-data.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(dataSource);
            log.info("Demo companies and jobs loaded.");
        }
        Company home = companyRepository.findFirstByName("Nimbus Cloudworks").orElse(null);
        RecruiterProfile rp = recruiterProfileRepository.findByUserId(recruiter.getId()).orElse(null);
        if (home != null && rp != null && rp.getCompany() == null) {
            rp.setCompany(home);
            recruiterProfileRepository.save(rp);
        }
    }

    private User newUser(String name, String email, Role role, String rawPassword) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(u);
    }
}
