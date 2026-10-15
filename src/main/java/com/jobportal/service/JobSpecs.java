package com.jobportal.service;

import com.jobportal.domain.JobStatus;
import com.jobportal.entity.Job;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds the dynamic WHERE clause for searching jobs. */
final class JobSpecs {
    private JobSpecs() {}

    private static String like(String text) {
        return "%" + text.trim().toLowerCase().replace("%", "").replace("_", "") + "%";
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    /** Public search: published jobs from active recruiters only. */
    static Specification<Job> publicSearch(JobService.SearchParams p) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
            preds.add(cb.isTrue(root.get("recruiter").get("active")));

            if (has(p.q())) {
                String pattern = like(p.q());
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("requiredSkills")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("company").get("name")), pattern)));
            }
            if (has(p.location())) {
                preds.add(cb.like(cb.lower(root.get("location")), like(p.location())));
            }
            if (p.jobType() != null) preds.add(cb.equal(root.get("jobType"), p.jobType()));
            if (p.experienceLevel() != null) preds.add(cb.equal(root.get("experienceLevel"), p.experienceLevel()));
            if (p.workMode() != null) preds.add(cb.equal(root.get("workMode"), p.workMode()));
            if (p.companyId() != null) preds.add(cb.equal(root.get("company").get("id"), p.companyId()));
            if (p.minSalary() != null) preds.add(cb.greaterThanOrEqualTo(root.<Long>get("salaryMax"), p.minSalary()));
            if (p.maxSalary() != null) preds.add(cb.lessThanOrEqualTo(root.<Long>get("salaryMin"), p.maxSalary()));

            boolean isCountQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            if (!isCountQuery && "relevant".equalsIgnoreCase(p.sort()) && has(p.q())) {
                String pattern = like(p.q());
                Expression<Integer> rank = cb.<Integer>selectCase()
                        .when(cb.like(cb.lower(root.get("title")), pattern), 0)
                        .when(cb.like(cb.lower(root.get("requiredSkills")), pattern), 1)
                        .otherwise(2);
                query.orderBy(cb.asc(rank), cb.desc(root.get("createdAt")));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    /** Admin search: any status, optional status and text filters. */
    static Specification<Job> adminSearch(JobStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) preds.add(cb.equal(root.get("status"), status));
            if (has(q)) {
                String pattern = like(q);
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("company").get("name")), pattern)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
