package com.lantu.connect.gateway.service.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicSkillPackValidatorTest {

    @Test
    void acceptsZipWithRootSkillMdAndFrontmatter() throws Exception {
        String md = """
                ---
                name: Demo Skill
                description: Hello
                ---
                # Body
                """;
        byte[] zip = zipOneEntry("SKILL.md", md);
        AnthropicSkillPackValidator.PackValidationOutcome out = AnthropicSkillPackValidator.validate(zip);
        assertTrue(out.valid(), out.message());
        assertEquals("Demo Skill", String.valueOf(out.manifest().get("name")));
        assertEquals("Hello", String.valueOf(out.manifest().get("description")));
    }

    @Test
    void rejectsZipWithoutSkillMd() throws Exception {
        byte[] zip = zipOneEntry("README.md", "x");
        AnthropicSkillPackValidator.PackValidationOutcome out = AnthropicSkillPackValidator.validate(zip);
        assertFalse(out.valid());
    }

    @Test
    void rejectsPathTraversalEntry() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry e = new ZipEntry("../evil/SKILL.md");
            zos.putNextEntry(e);
            zos.write("---\nname: X\n---\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        AnthropicSkillPackValidator.PackValidationOutcome out = AnthropicSkillPackValidator.validate(bos.toByteArray());
        assertFalse(out.valid());
    }

    private static byte[] zipOneEntry(String name, String content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
