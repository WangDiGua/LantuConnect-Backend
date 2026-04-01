package com.lantu.connect.gateway.service.support;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackArchiveNormalizerTest {

    @Test
    void tarGzWithSkillMdNormalizesAndValidates() throws Exception {
        byte[] tgz = tarGzWithSkillMd("subdir/SKILL.md", """
                ---
                name: From Tar
                description: t
                ---
                # Body
                """);
        byte[] zip = SkillPackArchiveNormalizer.normalizeToSkillZip(tgz, "pkg.tgz");
        AnthropicSkillPackValidator.PackValidationOutcome out = AnthropicSkillPackValidator.validate(zip);
        assertTrue(out.valid(), out.message());
        assertEquals("From Tar", String.valueOf(out.manifest().get("name")));
    }

    @Test
    void singleMdWithFilenameWraps() {
        byte[] md = """
                ---
                name: Solo
                ---
                """.getBytes(StandardCharsets.UTF_8);
        byte[] zip = SkillPackArchiveNormalizer.normalizeToSkillZip(md, "SKILL.md");
        assertTrue(AnthropicSkillPackValidator.validate(zip).valid());
    }

    @Test
    void singleMdHeuristicWithoutExtension() {
        byte[] md = """
                ---
                name: NoExt
                ---
                """.getBytes(StandardCharsets.UTF_8);
        byte[] zip = SkillPackArchiveNormalizer.normalizeToSkillZip(md, "readme.txt");
        assertTrue(AnthropicSkillPackValidator.validate(zip).valid());
    }

    @Test
    void gzipSingleMarkdown() throws Exception {
        byte[] md = """
                ---
                name: GzMd
                ---
                """.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream gzbos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gos = new GzipCompressorOutputStream(gzbos)) {
            gos.write(md);
        }
        byte[] zip = SkillPackArchiveNormalizer.normalizeToSkillZip(gzbos.toByteArray(), "skill.md.gz");
        assertTrue(AnthropicSkillPackValidator.validate(zip).valid());
    }

    @Test
    void zipPassesThroughSameReference() throws Exception {
        byte[] zip = zipOneEntry("SKILL.md", """
                ---
                name: Z
                ---
                """);
        byte[] out = SkillPackArchiveNormalizer.normalizeToSkillZip(zip, "a.zip");
        assertSame(zip, out);
        assertTrue(AnthropicSkillPackValidator.validate(out).valid());
    }

    @Test
    void normalizeStorageFilenameTarGz() {
        assertEquals("foo.zip", SkillPackArchiveNormalizer.normalizeStorageFilename("foo.tar.gz"));
        assertEquals("bar.zip", SkillPackArchiveNormalizer.normalizeStorageFilename("bar.tgz"));
        assertEquals("x.zip", SkillPackArchiveNormalizer.normalizeStorageFilename("x.7z"));
        assertEquals("y.zip", SkillPackArchiveNormalizer.normalizeStorageFilename("y.rar"));
    }

    @Test
    void sevenZWithSkillMdNormalizesAndValidates() throws Exception {
        Path skillFile = createTempSkillFile();
        Path seven = Files.createTempFile("skill7z", ".7z");
        try {
            try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
                SevenZArchiveEntry e = out.createArchiveEntry(skillFile.toFile(), "nested/SKILL.md");
                out.putArchiveEntry(e);
                out.write(Files.readAllBytes(skillFile));
                out.closeArchiveEntry();
            }
            byte[] raw = Files.readAllBytes(seven);
            byte[] zip = SkillPackArchiveNormalizer.normalizeToSkillZip(raw, "pack.7z");
            AnthropicSkillPackValidator.PackValidationOutcome outV = AnthropicSkillPackValidator.validate(zip);
            assertTrue(outV.valid(), outV.message());
            assertEquals("SevenSkill", String.valueOf(outV.manifest().get("name")));
        } finally {
            Files.deleteIfExists(seven);
            Files.deleteIfExists(skillFile);
        }
    }

    @Test
    void folderOnlyZipGetsSyntheticSkillMd() throws Exception {
        byte[] zip = zipOneEntry("scripts/run.sh", "#!/bin/sh\necho x\n");
        byte[] merged = SkillPackFolderConvention.ensureRootSkillMd(zip, "my-tool.zip");
        AnthropicSkillPackValidator.PackValidationOutcome outV = AnthropicSkillPackValidator.validate(merged);
        assertTrue(outV.valid(), outV.message());
        assertEquals("my-tool", String.valueOf(outV.manifest().get("name")));
    }

    private static Path createTempSkillFile() throws Exception {
        Path p = Files.createTempFile("sk", ".md");
        Files.writeString(p, """
                ---
                name: SevenSkill
                description: from 7z
                ---
                # x
                """);
        return p;
    }

    private static byte[] tarGzWithSkillMd(String entryName, String content) throws Exception {
        byte[] utf8 = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gzOut)) {
            TarArchiveEntry e = new TarArchiveEntry(entryName);
            e.setSize(utf8.length);
            tos.putArchiveEntry(e);
            tos.write(utf8);
            tos.closeArchiveEntry();
            tos.finish();
        }
        return bos.toByteArray();
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
