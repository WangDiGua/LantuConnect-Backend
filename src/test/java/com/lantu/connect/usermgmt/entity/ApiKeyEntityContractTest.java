package com.lantu.connect.usermgmt.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiKeyEntityContractTest {

    @Test
    void apiKeyEntityDeclaresEncryptedSecretField() throws Exception {
        assertNotNull(ApiKey.class.getDeclaredField("secretCiphertext"));
    }
}
