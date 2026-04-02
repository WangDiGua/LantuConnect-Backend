package com.lantu.connect.gateway.service.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lantu.connect.common.exception.BusinessException;

class SkillPackSubpathExtractorTest {

    @Test
    void extractSingleSubtreeForAnthropicValidation() throws Exception {
        byte[] zip = multiSkillZip();
        AnthropicSkillPackValidator.PackValidationOutcome whole = AnthropicSkillPackValidator.validate(zip);
        assertFalse(whole.valid());

        byte[] kb = SkillPackSubpathExtractor.extractSubtree(zip, "knowledge-base");
        byte[] normalized = SkillPackFolderConvention.ensureRootSkillMd(kb, "x.zip");
        AnthropicSkillPackValidator.PackValidationOutcome sub = AnthropicSkillPackValidator.validate(normalized);
        assertTrue(sub.valid());
    }

    @Test
    void missingPrefixThrows() throws Exception {
        byte[] zip = multiSkillZip();
        assertThrows(BusinessException.class, () -> SkillPackSubpathExtractor.extractSubtree(zip, "nope"));
    }

    private static byte[] multiSkillZip() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write("---\nname: root\n---\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("knowledge-base/SKILL.md"));
            zos.write("---\nname: KB\n---\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("knowledge-base/refs/a.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("notes/SKILL.md"));
            zos.write("---\nname: Notes\n---\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
