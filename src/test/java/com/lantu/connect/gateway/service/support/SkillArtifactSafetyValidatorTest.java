package com.lantu.connect.gateway.service.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lantu.connect.common.exception.BusinessException;

class SkillArtifactSafetyValidatorTest {

    @Test
    void emptyZipRejected() {
        assertNotNull(SkillArtifactSafetyValidator.validate(null));
        assertNotNull(SkillArtifactSafetyValidator.validate(new byte[0]));
    }

    @Test
    void noSkillMdStillPasses() throws Exception {
        byte[] zip = zipWithEntries(java.util.Map.of("readme.txt", "x".getBytes(StandardCharsets.UTF_8)));
        assertNull(SkillArtifactSafetyValidator.validate(zip));
    }

    @Test
    void multipleSkillMdPassesSafety() throws Exception {
        byte[] zip = zipWithEntries(java.util.Map.of(
                "a/SKILL.md", "---\nname: A\n---\n".getBytes(StandardCharsets.UTF_8),
                "b/SKILL.md", "---\nname: B\n---\n".getBytes(StandardCharsets.UTF_8)));
        assertNull(SkillArtifactSafetyValidator.validate(zip));
    }

    @Test
    void pathTraversalThrows() throws Exception {
        byte[] zip = zipWithEntries(java.util.Map.of("../evil.txt", "x".getBytes(StandardCharsets.UTF_8)));
        assertThrows(BusinessException.class, () -> SkillArtifactSafetyValidator.validateOrThrow(zip));
    }

    @Test
    void validateOrThrowPasses() throws Exception {
        byte[] zip = zipWithEntries(java.util.Map.of("x.txt", "ok".getBytes(StandardCharsets.UTF_8)));
        SkillArtifactSafetyValidator.validateOrThrow(zip);
    }

    private static byte[] zipWithEntries(java.util.Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (var e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
