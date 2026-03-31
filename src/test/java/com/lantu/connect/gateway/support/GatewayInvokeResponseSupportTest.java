package com.lantu.connect.gateway.support;

import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayInvokeResponseSupportTest {

    @Test
    void successMapsToOkAndZeroCode() {
        InvokeResponse ok = InvokeResponse.builder().status("success").statusCode(200).build();
        assertEquals(HttpStatus.OK, GatewayInvokeResponseSupport.toHttpStatus(ok));
        assertEquals(0, GatewayInvokeResponseSupport.wrap(ok).getCode());
    }

    @Test
    void errorUsesUpstreamStatusInRange() {
        InvokeResponse err = InvokeResponse.builder().status("error").statusCode(418).build();
        assertEquals(418, GatewayInvokeResponseSupport.toHttpStatus(err).value());
    }

    @Test
    void errorWithoutStatusFallsBackTo502() {
        InvokeResponse err = InvokeResponse.builder().status("error").statusCode(null).build();
        assertEquals(HttpStatus.BAD_GATEWAY, GatewayInvokeResponseSupport.toHttpStatus(err));
    }

    @Test
    void wrapFailureSetsExternalServiceCode() {
        InvokeResponse err = InvokeResponse.builder().status("error").statusCode(500).body("x").build();
        assertEquals(ResultCode.EXTERNAL_SERVICE_ERROR.getCode(), GatewayInvokeResponseSupport.wrap(err).getCode());
        assertTrue(GatewayInvokeResponseSupport.wrap(err).getMessage().contains("外部服务"));
    }
}
