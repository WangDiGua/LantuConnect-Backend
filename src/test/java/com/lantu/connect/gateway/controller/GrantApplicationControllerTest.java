package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.gateway.dto.GrantApplicationVO;
import com.lantu.connect.gateway.service.GrantApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantApplicationControllerTest {

    @Mock
    private GrantApplicationService grantApplicationService;

    @InjectMocks
    private GrantApplicationController controller;

    @Test
    void pendingUsesKeywordWhenBothKeywordAndQProvided() {
        PageResult<GrantApplicationVO> empty = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(grantApplicationService.pagePendingApplications(eq("pending"), eq("from-keyword"), eq(1), eq(20)))
                .thenReturn(empty);

        R<PageResult<GrantApplicationVO>> r = controller.pendingApplications("pending", "from-keyword", "ignored-q", 1, 20);
        assertThat(r.getCode()).isZero();
        verify(grantApplicationService).pagePendingApplications("pending", "from-keyword", 1, 20);
    }

    @Test
    void pendingFallsBackToQWhenKeywordBlank() {
        PageResult<GrantApplicationVO> empty = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(grantApplicationService.pagePendingApplications(eq("pending"), eq("from-q"), eq(1), eq(20)))
                .thenReturn(empty);

        R<PageResult<GrantApplicationVO>> r = controller.pendingApplications("pending", null, "from-q", 1, 20);
        assertThat(r.getCode()).isZero();
        verify(grantApplicationService).pagePendingApplications("pending", "from-q", 1, 20);
    }

    @Test
    void mineUsesKeywordWhenProvided() {
        PageResult<GrantApplicationVO> empty = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(grantApplicationService.pageMyApplications(eq(99L), eq(null), eq("kw"), eq(1), eq(20)))
                .thenReturn(empty);

        R<PageResult<GrantApplicationVO>> r = controller.myApplications(99L, null, "kw", null, 1, 20);
        assertThat(r.getCode()).isZero();
        verify(grantApplicationService).pageMyApplications(99L, null, "kw", 1, 20);
    }

    @Test
    void mineFallsBackToQ() {
        PageResult<GrantApplicationVO> empty = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(grantApplicationService.pageMyApplications(eq(1L), eq("pending"), eq("qq"), eq(1), eq(20)))
                .thenReturn(empty);

        R<PageResult<GrantApplicationVO>> r = controller.myApplications(1L, "pending", null, "qq", 1, 20);
        assertThat(r.getCode()).isZero();
        verify(grantApplicationService).pageMyApplications(1L, "pending", "qq", 1, 20);
    }
}
