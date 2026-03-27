package com.lantu.connect.auth.service;

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
import java.util.Map;

/**
 * 认证Auth服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    void logout(String accessToken);

    TokenResponse refresh(RefreshTokenRequest request);

    UserInfoVO me(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    void updateProfile(Long userId, ProfileUpdateRequest request);

    void sendSms(Map<String, String> body);

    void bindPhone(Long userId, Map<String, String> body);

    PageResult<LoginHistory> loginHistory(Long userId, int page, int pageSize);

    List<SessionItem> listSessions(Long userId, String currentSessionId);

    void killSession(Long userId, String sessionId);
}
