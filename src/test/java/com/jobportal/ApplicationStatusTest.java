package com.jobportal;

import com.jobportal.domain.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static com.jobportal.domain.ApplicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationStatusTest {

    @Test
    void recruiterCanFollowTheNormalPath() {
        assertTrue(APPLIED.canRecruiterMoveTo(UNDER_REVIEW));
        assertTrue(UNDER_REVIEW.canRecruiterMoveTo(SHORTLISTED));
        assertTrue(SHORTLISTED.canRecruiterMoveTo(HIRED));
    }

    @Test
    void recruiterCanRejectBeforeHiring() {
        assertTrue(APPLIED.canRecruiterMoveTo(REJECTED));
        assertTrue(UNDER_REVIEW.canRecruiterMoveTo(REJECTED));
        assertTrue(SHORTLISTED.canRecruiterMoveTo(REJECTED));
    }

    @Test
    void skippingStepsIsNotAllowed() {
        assertFalse(APPLIED.canRecruiterMoveTo(SHORTLISTED));
        assertFalse(APPLIED.canRecruiterMoveTo(HIRED));
        assertFalse(UNDER_REVIEW.canRecruiterMoveTo(HIRED));
    }

    @Test
    void finalStatusesCannotChange() {
        for (ApplicationStatus finalStatus : new ApplicationStatus[]{HIRED, REJECTED, WITHDRAWN}) {
            assertTrue(finalStatus.recruiterTransitions().isEmpty(), finalStatus + " should be final");
        }
    }

    @Test
    void withdrawalOnlyEarlyInTheProcess() {
        assertTrue(APPLIED.canBeWithdrawn());
        assertTrue(UNDER_REVIEW.canBeWithdrawn());
        assertFalse(SHORTLISTED.canBeWithdrawn());
        assertFalse(HIRED.canBeWithdrawn());
        assertFalse(REJECTED.canBeWithdrawn());
        assertFalse(WITHDRAWN.canBeWithdrawn());
    }
}
