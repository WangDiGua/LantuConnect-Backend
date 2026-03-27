package com.lantu.connect.common.exception;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapRateLimitBusinessExceptionTo429() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        BusinessException ex = new BusinessException(ResultCode.RATE_LIMITED, "too many requests");

        ResponseEntity<R<Void>> response = handler.handleBusinessException(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(ResultCode.RATE_LIMITED.getCode(), response.getBody().getCode());
        assertEquals("too many requests", response.getBody().getMessage());
    }
}
