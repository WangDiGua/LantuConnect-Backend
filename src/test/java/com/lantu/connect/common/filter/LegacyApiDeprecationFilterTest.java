package com.lantu.connect.common.filter;

import com.lantu.connect.common.config.LegacyApiDeprecationProperties;
import com.lantu.connect.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyApiDeprecationFilterTest {

    @Test
    void shouldReturnGoneForDeprecatedWriteApi() throws Exception {
        LegacyApiDeprecationProperties properties = new LegacyApiDeprecationProperties();
        LegacyApiDeprecationFilter filter = new LegacyApiDeprecationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/agents");
        request.setServletPath("/v1/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(410, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":" + ResultCode.NOT_FOUND.getCode()));
    }
}
