package com.jobportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.Role;
import com.jobportal.entity.User;
import com.jobportal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests (controller -> service -> database) using an in-memory H2 database.
 * Every test creates users with random e-mail addresses, so tests do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalApiTests {
    private static final String PASSWORD = "Passw0rd1";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    // ---------------------------------------------------------------- helpers
    private String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String email(String prefix) {
        return prefix + uniq() + "@test.com";
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String registerSeeker(String email) throws Exception {
        ResultActions r = mvc.perform(post("/api/auth/register/seeker").contentType(JSON)
                .content(json(Map.of("fullName", "Test Seeker", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return body(r).get("token").asText();
    }

    private String registerRecruiter(String email) throws Exception {
        ResultActions r = mvc.perform(post("/api/auth/register/recruiter").contentType(JSON)
                .content(json(Map.of("fullName", "Test Recruiter", "email", email, "password", PASSWORD,
                        "companyName", "Test Company " + uniq()))))
                .andExpect(status().isCreated());
        return body(r).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        ResultActions r = mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(json(Map.of("email", email, "password", password)))).andExpect(status().isOk());
        return body(r).get("token").asText();
    }

    private String adminToken() throws Exception {
        String email = email("admin");
        User u = new User();
        u.setEmail(email);
        u.setFullName("Test Admin");
        u.setRole(Role.ADMIN);
        u.setPasswordHash(encoder.encode(PASSWORD));
        users.save(u);
        return login(email, PASSWORD);
    }

    private Map<String, Object> jobBody(String title, String location, String type, String mode, long min, long max) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("description", "A great role for testing purposes. " + title);
        m.put("responsibilities", "Build things\nTest things");
        m.put("requiredSkills", "Java, SQL");
        m.put("qualifications", "Degree");
        m.put("location", location);
        m.put("salaryMin", min);
        m.put("salaryMax", max);
        m.put("jobType", type);
        m.put("experienceLevel", "MID");
        m.put("workMode", mode);
        m.put("deadline", LocalDate.now().plusDays(30).toString());
        return m;
    }

    private long createDraft(String token, Map<String, Object> body) throws Exception {
        ResultActions r = mvc.perform(post("/api/recruiter/jobs").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body))).andExpect(status().isCreated());
        return body(r).get("id").asLong();
    }

    private long createAndPublish(String token, Map<String, Object> body) throws Exception {
        long id = createDraft(token, body);
        mvc.perform(post("/api/recruiter/jobs/" + id + "/publish").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-1.4 sample resume".getBytes());
    }

    /** Fills in the profile fields and uploads a valid PDF so the seeker may apply. */
    private void completeProfile(String token) throws Exception {
        mvc.perform(put("/api/seeker/profile").header("Authorization", bearer(token)).contentType(JSON)
                .content(json(Map.of("fullName", "Test Seeker", "phone", "9876543210", "location", "Hyderabad",
                        "summary", "Motivated developer", "skills", "Java, SQL"))))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/seeker/resume").file(pdf()).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private long apply(String seekerToken, long jobId) throws Exception {
        ResultActions r = mvc.perform(post("/api/applications").header("Authorization", bearer(seekerToken))
                .contentType(JSON).content(json(Map.of("jobId", jobId, "coverLetter", "Please consider me"))))
                .andExpect(status().isCreated());
        return body(r).get("id").asLong();
    }

    // ---------------------------------------------------------------- 1. registration and login
    @Test
    void registrationAndLogin() throws Exception {
        String email = email("seeker");
        registerSeeker(email);

        // duplicate e-mail (also different letter case) is rejected
        mvc.perform(post("/api/auth/register/seeker").contentType(JSON)
                .content(json(Map.of("fullName", "Other", "email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict());

        // validation errors
        mvc.perform(post("/api/auth/register/seeker").contentType(JSON)
                .content(json(Map.of("fullName", "X", "email", "not-an-email", "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register/seeker").contentType(JSON)
                .content(json(Map.of("fullName", "X", "email", email("weak"), "password", "short"))))
                .andExpect(status().isBadRequest());

        // wrong password and unknown user give the same 401
        mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(json(Map.of("email", email, "password", "WrongPass1")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(json(Map.of("email", email("ghost"), "password", PASSWORD)))).andExpect(status().isUnauthorized());

        // successful login and /me
        String token = login(email, PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());

        // the stored hash is BCrypt, never the plain password
        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("$2"));
    }

    // ---------------------------------------------------------------- 2. role authorization
    @Test
    void roleBasedAuthorization() throws Exception {
        String seeker = registerSeeker(email("seeker"));
        String recruiter = registerRecruiter(email("rec"));
        String admin = adminToken();

        mvc.perform(get("/api/seeker/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/seeker/profile").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/recruiter/jobs").header("Authorization", bearer(seeker))).andExpect(status().isForbidden());
        mvc.perform(get("/api/seeker/profile").header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(seeker))).andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/recruiter/jobs").header("Authorization", bearer(recruiter))).andExpect(status().isOk());
        mvc.perform(get("/api/seeker/profile").header("Authorization", bearer(seeker))).andExpect(status().isOk());

        // public endpoints need no token
        mvc.perform(get("/api/jobs")).andExpect(status().isOk());
        mvc.perform(get("/api/companies")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 3. search and filters
    @Test
    void jobSearchFiltersSortAndPagination() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String kw = "zq" + uniq();

        createAndPublish(recruiter, jobBody("Alpha " + kw + " Engineer", "Hyderabad, Telangana", "FULL_TIME", "ONSITE", 500000, 900000));
        createAndPublish(recruiter, jobBody("Beta " + kw + " Developer", "Bengaluru, Karnataka", "CONTRACT", "REMOTE", 1500000, 2500000));
        Map<String, Object> descOnly = jobBody("Gamma Role", "Pune, Maharashtra", "FULL_TIME", "HYBRID", 800000, 1200000);
        descOnly.put("description", "This description mentions " + kw + " only once");
        createAndPublish(recruiter, descOnly);
        createDraft(recruiter, jobBody("Hidden " + kw + " Draft", "Pune, Maharashtra", "FULL_TIME", "HYBRID", 1, 2));

        // keyword search finds titles and descriptions, never drafts
        assertEquals(3, body(mvc.perform(get("/api/jobs").param("q", kw)).andExpect(status().isOk())).get("totalElements").asInt());

        // filters
        assertEquals(1, body(mvc.perform(get("/api/jobs").param("q", kw).param("location", "bengaluru"))).get("totalElements").asInt());
        assertEquals(1, body(mvc.perform(get("/api/jobs").param("q", kw).param("workMode", "REMOTE"))).get("totalElements").asInt());
        assertEquals(1, body(mvc.perform(get("/api/jobs").param("q", kw).param("jobType", "CONTRACT"))).get("totalElements").asInt());
        assertEquals(3, body(mvc.perform(get("/api/jobs").param("q", kw).param("experienceLevel", "MID"))).get("totalElements").asInt());
        assertEquals(1, body(mvc.perform(get("/api/jobs").param("q", kw).param("minSalary", "2000000"))).get("totalElements").asInt());
        assertEquals(1, body(mvc.perform(get("/api/jobs").param("q", kw).param("maxSalary", "600000"))).get("totalElements").asInt());

        // pagination
        JsonNode page = body(mvc.perform(get("/api/jobs").param("q", kw).param("size", "2").param("page", "0")));
        assertEquals(2, page.get("content").size());
        assertEquals(3, page.get("totalElements").asInt());
        assertEquals(2, page.get("totalPages").asInt());

        // relevance: title matches come before description-only matches
        JsonNode relevant = body(mvc.perform(get("/api/jobs").param("q", kw).param("sort", "relevant")));
        assertEquals("Gamma Role", relevant.get("content").get(2).get("title").asText());

        // salary sort: highest salary first
        JsonNode bySalary = body(mvc.perform(get("/api/jobs").param("q", kw).param("sort", "salary")));
        assertEquals(2500000L, bySalary.get("content").get(0).get("salaryMax").asLong());
    }

    // ---------------------------------------------------------------- 4. create and edit jobs
    @Test
    void recruiterCreatesEditsPublishesAndClosesJobs() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String other = registerRecruiter(email("rec2"));
        Map<String, Object> body = jobBody("Test Engineer " + uniq(), "Chennai, Tamil Nadu", "FULL_TIME", "HYBRID", 600000, 900000);

        long id = createDraft(recruiter, body);

        // drafts are invisible to the public but visible to the owner
        mvc.perform(get("/api/jobs/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/recruiter/jobs/" + id).header("Authorization", bearer(recruiter))).andExpect(status().isOk());

        // edit
        body.put("title", "Renamed Engineer");
        ResultActions updated = mvc.perform(put("/api/recruiter/jobs/" + id).header("Authorization", bearer(recruiter))
                .contentType(JSON).content(json(body))).andExpect(status().isOk());
        assertEquals("Renamed Engineer", body(updated).get("title").asText());

        // another recruiter cannot edit, publish, close or delete it
        mvc.perform(put("/api/recruiter/jobs/" + id).header("Authorization", bearer(other)).contentType(JSON)
                .content(json(body))).andExpect(status().isForbidden());
        mvc.perform(post("/api/recruiter/jobs/" + id + "/publish").header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/recruiter/jobs/" + id).header("Authorization", bearer(other))).andExpect(status().isForbidden());

        // validation
        Map<String, Object> badSalary = new HashMap<>(body);
        badSalary.put("salaryMin", 900000);
        badSalary.put("salaryMax", 100000);
        mvc.perform(post("/api/recruiter/jobs").header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(badSalary))).andExpect(status().isBadRequest());
        Map<String, Object> pastDeadline = new HashMap<>(body);
        pastDeadline.put("deadline", LocalDate.now().minusDays(2).toString());
        mvc.perform(post("/api/recruiter/jobs").header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(pastDeadline))).andExpect(status().isBadRequest());
        Map<String, Object> noTitle = new HashMap<>(body);
        noTitle.remove("title");
        mvc.perform(post("/api/recruiter/jobs").header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(noTitle))).andExpect(status().isBadRequest());

        // publish -> public; publishing twice is a conflict
        mvc.perform(post("/api/recruiter/jobs/" + id + "/publish").header("Authorization", bearer(recruiter))).andExpect(status().isOk());
        mvc.perform(post("/api/recruiter/jobs/" + id + "/publish").header("Authorization", bearer(recruiter))).andExpect(status().isConflict());
        JsonNode pub = body(mvc.perform(get("/api/jobs/" + id)).andExpect(status().isOk()));
        assertTrue(pub.get("open").asBoolean());

        // close -> still viewable but not open
        mvc.perform(post("/api/recruiter/jobs/" + id + "/close").header("Authorization", bearer(recruiter))).andExpect(status().isOk());
        JsonNode closed = body(mvc.perform(get("/api/jobs/" + id)).andExpect(status().isOk()));
        assertFalse(closed.get("open").asBoolean());
        assertEquals("CLOSED", closed.get("status").asText());

        // a job without applications can be deleted
        mvc.perform(delete("/api/recruiter/jobs/" + id).header("Authorization", bearer(recruiter))).andExpect(status().isNoContent());
        mvc.perform(get("/api/jobs/" + id)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 5. resume upload validation
    @Test
    void resumeUploadValidationAndDownload() throws Exception {
        String seeker = registerSeeker(email("seeker"));
        String recruiter = registerRecruiter(email("rec"));

        // no resume yet
        mvc.perform(get("/api/seeker/resume").header("Authorization", bearer(seeker))).andExpect(status().isNotFound());

        // wrong extension
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "resume.txt", "text/plain", "hello".getBytes()))
                .header("Authorization", bearer(seeker))).andExpect(status().isBadRequest());
        // right extension, wrong content type
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "resume.pdf", "text/plain", "%PDF-1.4".getBytes()))
                .header("Authorization", bearer(seeker))).andExpect(status().isBadRequest());
        // looks like a PDF by name and type, but the content is not a PDF
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", "this is not a pdf".getBytes()))
                .header("Authorization", bearer(seeker))).andExpect(status().isBadRequest());
        // empty file
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]))
                .header("Authorization", bearer(seeker))).andExpect(status().isBadRequest());
        // too large (limit is 5 MB)
        byte[] big = new byte[5 * 1024 * 1024 + 10];
        System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);
        mvc.perform(multipart("/api/seeker/resume")
                .file(new MockMultipartFile("file", "big.pdf", "application/pdf", big))
                .header("Authorization", bearer(seeker))).andExpect(status().is(413));

        // valid upload
        JsonNode profile = body(mvc.perform(multipart("/api/seeker/resume").file(pdf())
                .header("Authorization", bearer(seeker))).andExpect(status().isOk()));
        assertTrue(profile.get("hasResume").asBoolean());
        assertEquals("resume.pdf", profile.get("resumeOriginalName").asText());

        // the owner can download it, a recruiter cannot use the seeker endpoint
        byte[] bytes = mvc.perform(get("/api/seeker/resume").header("Authorization", bearer(seeker)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(bytes).startsWith("%PDF-"));
        mvc.perform(get("/api/seeker/resume").header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/seeker/resume")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 6. applying
    @Test
    void applicationRulesAndDuplicatePrevention() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String seeker = registerSeeker(email("seeker"));
        long jobId = createAndPublish(recruiter, jobBody("Applyable " + uniq(), "Mumbai, Maharashtra", "FULL_TIME", "HYBRID", 500000, 800000));

        // incomplete profile / no resume -> 400
        mvc.perform(post("/api/applications").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("jobId", jobId)))).andExpect(status().isBadRequest());

        completeProfile(seeker);

        // missing job id and unknown job
        mvc.perform(post("/api/applications").header("Authorization", bearer(seeker)).contentType(JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("jobId", 99999999L)))).andExpect(status().isNotFound());

        // successful application
        long appId = apply(seeker, jobId);

        // duplicate -> 409
        mvc.perform(post("/api/applications").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("jobId", jobId)))).andExpect(status().isConflict());

        // appears in my applications with APPLIED status and a resume name
        JsonNode mine = body(mvc.perform(get("/api/applications/mine").header("Authorization", bearer(seeker))).andExpect(status().isOk()));
        assertEquals(1, mine.size());
        assertEquals("APPLIED", mine.get(0).get("status").asText());
        assertEquals("resume.pdf", mine.get(0).get("resumeOriginalName").asText());
        assertTrue(mine.get(0).get("canWithdraw").asBoolean());

        // job flags for the viewing seeker
        assertTrue(body(mvc.perform(get("/api/jobs/" + jobId).header("Authorization", bearer(seeker)))).get("applied").asBoolean());

        // seeker can open the resume attached to the application; another seeker cannot
        mvc.perform(get("/api/applications/" + appId + "/resume").header("Authorization", bearer(seeker))).andExpect(status().isOk());
        String stranger = registerSeeker(email("seeker"));
        mvc.perform(get("/api/applications/" + appId + "/resume").header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications/" + appId + "/withdraw").header("Authorization", bearer(stranger))).andExpect(status().isForbidden());

        // withdraw once; second time is a conflict
        mvc.perform(post("/api/applications/" + appId + "/withdraw").header("Authorization", bearer(seeker))).andExpect(status().isOk());
        mvc.perform(post("/api/applications/" + appId + "/withdraw").header("Authorization", bearer(seeker))).andExpect(status().isConflict());

        // a closed job no longer accepts applications
        long closedJob = createAndPublish(recruiter, jobBody("Soon closed " + uniq(), "Mumbai, Maharashtra", "FULL_TIME", "HYBRID", 1, 2));
        mvc.perform(post("/api/recruiter/jobs/" + closedJob + "/close").header("Authorization", bearer(recruiter))).andExpect(status().isOk());
        mvc.perform(post("/api/applications").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("jobId", closedJob)))).andExpect(status().isConflict());

        // recruiters cannot apply
        mvc.perform(post("/api/applications").header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("jobId", jobId)))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 7. recruiter restrictions
    @Test
    void recruitersCannotSeeOtherRecruitersApplications() throws Exception {
        String recruiterA = registerRecruiter(email("recA"));
        String recruiterB = registerRecruiter(email("recB"));
        String seeker = registerSeeker(email("seeker"));
        completeProfile(seeker);
        long jobId = createAndPublish(recruiterA, jobBody("Owned by A " + uniq(), "Delhi NCR", "FULL_TIME", "ONSITE", 400000, 700000));
        long appId = apply(seeker, jobId);

        // owner sees it
        JsonNode listA = body(mvc.perform(get("/api/recruiter/applications").header("Authorization", bearer(recruiterA))).andExpect(status().isOk()));
        assertEquals(1, listA.get("totalElements").asInt());
        mvc.perform(get("/api/recruiter/applications/" + appId).header("Authorization", bearer(recruiterA))).andExpect(status().isOk());
        mvc.perform(get("/api/recruiter/applications/" + appId + "/resume").header("Authorization", bearer(recruiterA))).andExpect(status().isOk());

        // other recruiter sees nothing and is blocked everywhere
        JsonNode listB = body(mvc.perform(get("/api/recruiter/applications").header("Authorization", bearer(recruiterB))).andExpect(status().isOk()));
        assertEquals(0, listB.get("totalElements").asInt());
        mvc.perform(get("/api/recruiter/applications/" + appId).header("Authorization", bearer(recruiterB))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/applications/" + appId + "/resume").header("Authorization", bearer(recruiterB))).andExpect(status().isForbidden());
        mvc.perform(put("/api/recruiter/applications/" + appId + "/status").header("Authorization", bearer(recruiterB))
                .contentType(JSON).content(json(Map.of("status", "UNDER_REVIEW")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/recruiter/applications/" + appId + "/notes").header("Authorization", bearer(recruiterB))
                .contentType(JSON).content(json(Map.of("note", "sneaky")))).andExpect(status().isForbidden());

        // a job seeker cannot use recruiter endpoints
        mvc.perform(get("/api/recruiter/applications").header("Authorization", bearer(seeker))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 8. status workflow and notes
    @Test
    void applicationStatusWorkflowAndNotes() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String seeker = registerSeeker(email("seeker"));
        completeProfile(seeker);
        long jobId = createAndPublish(recruiter, jobBody("Workflow " + uniq(), "Pune, Maharashtra", "FULL_TIME", "HYBRID", 700000, 1100000));
        long appId = apply(seeker, jobId);
        String statusUrl = "/api/recruiter/applications/" + appId + "/status";

        // cannot skip steps
        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("status", "HIRED")))).andExpect(status().isConflict());
        // unknown status value -> 400
        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content("{\"status\":\"FLYING\"}")).andExpect(status().isBadRequest());

        // private note
        mvc.perform(post("/api/recruiter/applications/" + appId + "/notes").header("Authorization", bearer(recruiter))
                .contentType(JSON).content(json(Map.of("note", "Strong Java skills")))).andExpect(status().isCreated());
        mvc.perform(post("/api/recruiter/applications/" + appId + "/notes").header("Authorization", bearer(recruiter))
                .contentType(JSON).content(json(Map.of("note", "  ")))).andExpect(status().isBadRequest());

        // normal path
        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("status", "UNDER_REVIEW")))).andExpect(status().isOk());
        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("status", "SHORTLISTED")))).andExpect(status().isOk());

        // seeker sees the new status and can no longer withdraw
        JsonNode mine = body(mvc.perform(get("/api/applications/mine").header("Authorization", bearer(seeker))));
        assertEquals("SHORTLISTED", mine.get(0).get("status").asText());
        assertFalse(mine.get(0).get("canWithdraw").asBoolean());
        mvc.perform(post("/api/applications/" + appId + "/withdraw").header("Authorization", bearer(seeker))).andExpect(status().isConflict());

        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("status", "HIRED")))).andExpect(status().isOk());
        // HIRED is final
        mvc.perform(put(statusUrl).header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(Map.of("status", "REJECTED")))).andExpect(status().isConflict());

        // detail view contains the note, the profile and filters work
        JsonNode detail = body(mvc.perform(get("/api/recruiter/applications/" + appId).header("Authorization", bearer(recruiter))).andExpect(status().isOk()));
        assertEquals(1, detail.get("notes").size());
        assertEquals("Strong Java skills", detail.get("notes").get(0).get("note").asText());
        assertEquals("HIRED", detail.get("application").get("status").asText());
        assertEquals(1, body(mvc.perform(get("/api/recruiter/applications").param("status", "HIRED")
                .param("jobId", String.valueOf(jobId)).header("Authorization", bearer(recruiter)))).get("totalElements").asInt());
        assertEquals(0, body(mvc.perform(get("/api/recruiter/applications").param("status", "REJECTED")
                .header("Authorization", bearer(recruiter)))).get("totalElements").asInt());

        // statistics reflect the hire
        JsonNode stats = body(mvc.perform(get("/api/recruiter/stats").header("Authorization", bearer(recruiter))).andExpect(status().isOk()));
        assertEquals(1, stats.get("applicationsByStatus").get("HIRED").asInt());
    }

    // ---------------------------------------------------------------- 9. invalid requests and missing records
    @Test
    void invalidRequestsAndMissingRecords() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String seeker = registerSeeker(email("seeker"));

        mvc.perform(get("/api/jobs/99999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/companies/99999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs").param("jobType", "NOT_A_TYPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{ this is not json")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/recruiter/jobs/99999999").header("Authorization", bearer(recruiter)).contentType(JSON)
                .content(json(jobBody("X", "Y", "FULL_TIME", "ONSITE", 1, 2)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/applications/99999999/withdraw").header("Authorization", bearer(seeker))).andExpect(status().isNotFound());
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/saved-jobs/99999999").header("Authorization", bearer(seeker))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 10. saved jobs
    @Test
    void savedJobsCanBeAddedAndRemoved() throws Exception {
        String recruiter = registerRecruiter(email("rec"));
        String seeker = registerSeeker(email("seeker"));
        long jobId = createAndPublish(recruiter, jobBody("Saveable " + uniq(), "Chennai, Tamil Nadu", "PART_TIME", "REMOTE", 100000, 200000));

        mvc.perform(put("/api/saved-jobs/" + jobId).header("Authorization", bearer(seeker))).andExpect(status().isOk());
        mvc.perform(put("/api/saved-jobs/" + jobId).header("Authorization", bearer(seeker))).andExpect(status().isOk()); // idempotent
        assertEquals(1, body(mvc.perform(get("/api/saved-jobs").header("Authorization", bearer(seeker)))).size());
        assertTrue(body(mvc.perform(get("/api/jobs/" + jobId).header("Authorization", bearer(seeker)))).get("saved").asBoolean());

        mvc.perform(delete("/api/saved-jobs/" + jobId).header("Authorization", bearer(seeker))).andExpect(status().isOk());
        assertEquals(0, body(mvc.perform(get("/api/saved-jobs").header("Authorization", bearer(seeker)))).size());
    }

    // ---------------------------------------------------------------- 11. administrator
    @Test
    void adminCanDeactivateUsersAndModerateJobs() throws Exception {
        String admin = adminToken();
        String recruiter = registerRecruiter(email("rec"));
        String seekerEmail = email("seeker");
        String seeker = registerSeeker(seekerEmail);
        long jobId = createAndPublish(recruiter, jobBody("Moderated " + uniq(), "Pune, Maharashtra", "FULL_TIME", "ONSITE", 1, 2));

        // report -> admin resolves and removes the job
        mvc.perform(post("/api/jobs/" + jobId + "/report").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("reason", "Misleading", "details", "Looks fake")))).andExpect(status().isOk());
        mvc.perform(post("/api/jobs/" + jobId + "/report").header("Authorization", bearer(seeker)).contentType(JSON)
                .content(json(Map.of("reason", "Misleading")))).andExpect(status().isConflict());
        JsonNode reports = body(mvc.perform(get("/api/admin/reports").param("status", "OPEN").param("size", "100")
                .header("Authorization", bearer(admin))).andExpect(status().isOk()));
        long reportId = -1;
        for (JsonNode r : reports.get("content")) {
            if (r.get("jobId").asLong() == jobId) reportId = r.get("id").asLong();
        }
        assertTrue(reportId > 0);
        mvc.perform(put("/api/admin/reports/" + reportId).header("Authorization", bearer(admin)).contentType(JSON)
                .content(json(Map.of("status", "RESOLVED", "removeJob", true)))).andExpect(status().isOk());
        mvc.perform(get("/api/jobs/" + jobId)).andExpect(status().isNotFound());
        // the recruiter cannot bring a removed job back
        mvc.perform(post("/api/recruiter/jobs/" + jobId + "/publish").header("Authorization", bearer(recruiter))).andExpect(status().isForbidden());

        // deactivate the seeker: login fails and the old token stops working
        long seekerId = users.findByEmailIgnoreCase(seekerEmail).orElseThrow().getId();
        mvc.perform(put("/api/admin/users/" + seekerId + "/active").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(Map.of("active", false)))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(json(Map.of("email", seekerEmail, "password", PASSWORD)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/seeker/profile").header("Authorization", bearer(seeker))).andExpect(status().isUnauthorized());

        // reactivate
        mvc.perform(put("/api/admin/users/" + seekerId + "/active").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(Map.of("active", true)))).andExpect(status().isOk());
        login(seekerEmail, PASSWORD);

        // an admin cannot deactivate themselves
        long adminId = users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElseThrow().getId();
        String selfToken = adminToken();
        JsonNode me = body(mvc.perform(get("/api/auth/me").header("Authorization", bearer(selfToken))));
        mvc.perform(put("/api/admin/users/" + me.get("id").asLong() + "/active").header("Authorization", bearer(selfToken))
                .contentType(JSON).content(json(Map.of("active", false)))).andExpect(status().isBadRequest());
        assertTrue(adminId > 0);
    }
}
