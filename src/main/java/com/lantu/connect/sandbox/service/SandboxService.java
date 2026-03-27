package com.lantu.connect.sandbox.service;

import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.sandbox.dto.SandboxSessionCreateRequest;
import com.lantu.connect.sandbox.dto.SandboxSessionVO;

import java.util.List;

public interface SandboxService {

    SandboxSessionVO createSession(Long userId, String apiKeyRaw, SandboxSessionCreateRequest request);

    List<SandboxSessionVO> mySessions(Long userId);

    InvokeResponse sandboxInvoke(String sessionToken, String traceId, String ip, InvokeRequest request);
}
