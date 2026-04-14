package com.lantu.connect.onboarding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;
import com.lantu.connect.onboarding.mapper.DeveloperApplicationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperApplicationServiceImplTest {

    @Mock
    private DeveloperApplicationMapper developerApplicationMapper;
    @Mock
    private PlatformRoleMapper platformRoleMapper;
    @Mock
    private UserRoleRelMapper userRoleRelMapper;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private SystemNotificationFacade systemNotificationFacade;

    @InjectMocks
    private DeveloperApplicationServiceImpl service;

    @Test
    void submitShouldTranslatePendingUniqueConstraintIntoConflict() {
        DeveloperApplicationCreateRequest request = new DeveloperApplicationCreateRequest();
        request.setContactEmail("dev@example.com");
        request.setApplyReason("need publish access");

        when(developerApplicationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(developerApplicationMapper.insert(any(DeveloperApplication.class)))
                .thenThrow(new DuplicateKeyException("uk_dev_apply_pending_user"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(8L, request));

        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        verify(systemNotificationFacade, never())
                .notifyOnboardingSubmitted(any(), any(), any(), any());
    }

    @Test
    void approveShouldFailWhenPendingRowWasHandledConcurrently() {
        DeveloperApplication row = new DeveloperApplication();
        row.setId(5L);
        row.setUserId(33L);
        row.setStatus("pending");

        PlatformRole developerRole = new PlatformRole();
        developerRole.setId(9L);
        developerRole.setRoleCode("developer");

        when(developerApplicationMapper.selectById(5L)).thenReturn(row);
        when(platformRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(developerRole);
        when(userRoleRelMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(developerApplicationMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(5L, 99L, "ok"));

        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        verify(systemNotificationFacade, never())
                .notifyOnboardingReviewed(any(), any(), anyBoolean(), any(), any());
    }
}
