package com.lantu.connect.gateway.service;

import org.junit.jupiter.api.Test;

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
}
