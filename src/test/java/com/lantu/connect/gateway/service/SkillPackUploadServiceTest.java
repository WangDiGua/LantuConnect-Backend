package com.lantu.connect.gateway.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkillPackUploadServiceTest {

    @Test
    void sanitizeUrlForDescription_stripsQueryAndFragment() {
        String u = "https://cdn.example.com/p/a.zip?token=secret&x=1#frag";
        String s = SkillPackUploadService.sanitizeUrlForDescription(u);
        assertEquals("https://cdn.example.com/p/a.zip", s);
        assertFalse(s.contains("token"));
    }

    @Test
    void sanitizeUrlForDescription_preservesPort() {
        assertEquals("http://host.example:8080/path/pkg.zip",
                SkillPackUploadService.sanitizeUrlForDescription("http://host.example:8080/path/pkg.zip?q=1"));
    }

    @Test
    void decodeUploadBase64_acceptsDataUrlPrefix() {
        byte[] raw = new byte[]{1, 2, 3};
        String b64 = Base64.getEncoder().encodeToString(raw);
        assertArrayEquals(raw, SkillPackUploadService.decodeUploadBase64(b64));
        assertArrayEquals(raw, SkillPackUploadService.decodeUploadBase64("data:application/zip;base64," + b64));
    }
}
