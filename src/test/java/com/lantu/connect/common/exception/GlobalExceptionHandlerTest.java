package com.lantu.connect.common.exception;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldReturnGenericReadableErrorWithoutSkillPackHint() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/resource-center/resources");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad body");

        R<Void> response = handler.handleMessageNotReadable(ex, request);

        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("请求体无法解析"));
        assertFalse(response.getMessage().contains("技能包"));
    }

    @Test
    void shouldReturnGenericUnsupportedMediaTypeWithoutSkillPackHint() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/resource-center/resources");
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<R<Void>> response = handler.handleUnsupportedMediaType(ex, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Content-Type"));
        assertFalse(response.getBody().getMessage().contains("技能包"));
    }
}
