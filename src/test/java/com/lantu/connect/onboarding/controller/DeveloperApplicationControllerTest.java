package com.lantu.connect.onboarding.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationReviewRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;
import com.lantu.connect.onboarding.service.DeveloperApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperApplicationControllerTest {

    @Mock
    private DeveloperApplicationService developerApplicationService;

    @InjectMocks
    private DeveloperApplicationController controller;

    @Test
    void shouldSubmitDeveloperApplication() {
        DeveloperApplicationCreateRequest req = new DeveloperApplicationCreateRequest();
        req.setApplyReason("need sdk");
        DeveloperApplication entity = new DeveloperApplication();
        entity.setId(1L);
        when(developerApplicationService.submit(10L, req)).thenReturn(entity);

        R<DeveloperApplication> result = controller.submit(10L, req);
        assertEquals(0, result.getCode());
        assertEquals(1L, result.getData().getId());
    }

    @Test
    void shouldApproveWithOptionalComment() {
        DeveloperApplicationReviewRequest review = new DeveloperApplicationReviewRequest();
        review.setReviewComment("ok");
        R<Void> result = controller.approve(2L, 99L, review);
        assertEquals(0, result.getCode());
        verify(developerApplicationService).approve(2L, 99L, "ok");
    }
}
