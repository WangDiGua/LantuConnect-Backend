package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceLifecycleStateMachineTest {

    @Test
    void shouldAllowDraftToPendingReview() {
        assertDoesNotThrow(() ->
                ResourceLifecycleStateMachine.ensureTransitionAllowed("draft", "pending_review"));
    }

    @Test
    void shouldAllowPendingReviewToPublished() {
        assertDoesNotThrow(() ->
                ResourceLifecycleStateMachine.ensureTransitionAllowed("pending_review", "published"));
    }

    @Test
    void shouldRejectPendingReviewToDeprecated() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ResourceLifecycleStateMachine.ensureTransitionAllowed("pending_review", "deprecated"));
        assertEquals(ResultCode.ILLEGAL_STATE_TRANSITION.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectDeleteForPublished() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ResourceLifecycleStateMachine.ensureDeletable("published"));
        assertEquals(ResultCode.CANNOT_DELETE_PUBLISHED.getCode(), ex.getCode());
    }
}

