package com.lantu.connect.common.filter;

import com.lantu.connect.common.config.LegacyApiDeprecationProperties;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyApiDeprecationFilterTest {

    @Test
    void shouldReturnGoneForDeprecatedWriteApi() throws Exception {
        LegacyApiDeprecationProperties properties = new LegacyApiDeprecationProperties();
        RuntimeAppConfigService runtime = mock(RuntimeAppConfigService.class);
        when(runtime.apiDeprecation()).thenReturn(properties);

        LegacyApiDeprecationFilter filter = new LegacyApiDeprecationFilter(runtime);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/agents");
        request.setServletPath("/v1/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(410, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":" + ResultCode.NOT_FOUND.getCode()));
    }
}
