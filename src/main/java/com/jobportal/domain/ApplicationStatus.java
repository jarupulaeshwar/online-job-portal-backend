package com.jobportal.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Application life cycle.
 *
 * APPLIED -> UNDER_REVIEW -> SHORTLISTED -> HIRED
 * Recruiters can reject an application at any stage before it is hired.
 * Job seekers can withdraw while the application is APPLIED or UNDER_REVIEW.
 * HIRED, REJECTED and WITHDRAWN are final states.
 */
public enum ApplicationStatus {
    APPLIED, UNDER_REVIEW, SHORTLISTED, REJECTED, HIRED, WITHDRAWN;

    /** Statuses a recruiter may move to from this status. */
    public Set<ApplicationStatus> recruiterTransitions() {
        return switch (this) {
            case APPLIED -> EnumSet.of(UNDER_REVIEW, REJECTED);
            case UNDER_REVIEW -> EnumSet.of(SHORTLISTED, REJECTED);
            case SHORTLISTED -> EnumSet.of(HIRED, REJECTED);
            default -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canRecruiterMoveTo(ApplicationStatus next) {
        return recruiterTransitions().contains(next);
    }

    public boolean canBeWithdrawn() {
        return this == APPLIED || this == UNDER_REVIEW;
    }
}
