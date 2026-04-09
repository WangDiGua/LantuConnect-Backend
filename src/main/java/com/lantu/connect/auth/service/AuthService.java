package com.lantu.connect.auth.service;

import com.lantu.connect.auth.dto.AccountInsightsVO;
import com.lantu.connect.auth.dto.ChangePasswordRequest;
import com.lantu.connect.auth.dto.LoginRequest;
import com.lantu.connect.auth.dto.LoginResponse;
import com.lantu.connect.auth.dto.ProfileUpdateRequest;
import com.lantu.connect.auth.dto.RefreshTokenRequest;
import com.lantu.connect.auth.dto.RegisterRequest;
import com.lantu.connect.auth.dto.SessionItem;
import com.lantu.connect.auth.dto.TokenResponse;
import com.lantu.connect.auth.dto.UserInfoVO;
import com.lantu.connect.auth.entity.LoginHistory;
import com.lantu.connect.common.result.PageResult;

import java.util.List;

/**
 * 认证Auth服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp);

    LoginResponse register(RegisterRequest request, String clientIp);

    void logout(String accessToken);

    TokenResponse refresh(RefreshTokenRequest request);

    UserInfoVO me(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    void updateProfile(Long userId, ProfileUpdateRequest request);

    PageResult<LoginHistory> loginHistory(Long userId, int page, int pageSize);

    AccountInsightsVO accountInsights(Long userId);

    List<SessionItem> listSessions(Long userId, String currentSessionId);

    void killSession(Long userId, String sessionId);
}
