package com.lantu.connect.gateway.service.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 对 zip 技能包做 Anthropic 兼容子集校验：防 zip 炸弹、路径穿越、必须含 SKILL.md、可选 YAML frontmatter。
 */
public final class AnthropicSkillPackValidator {

    public static final int MAX_ZIP_BYTES = 100 * 1024 * 1024;
    public static final int MAX_UNCOMPRESSED_TOTAL = 40 * 1024 * 1024;
    public static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    /** 归档内「文件」条数上限（目录占位项不计入），防止恶意超大清单拖死扫描。 */
    public static final int MAX_ENTRIES = 20_000;
    public static final int MAX_SKILL_MD_BYTES = 512 * 1024;

    public record PackValidationOutcome(boolean valid, String message, Map<String, Object> manifest, String entryDoc) {
        public static PackValidationOutcome invalid(String msg) {
            return new PackValidationOutcome(false, msg, Map.of(), "SKILL.md");
        }

        public static PackValidationOutcome ok(Map<String, Object> manifest, String entryDoc) {
            return new PackValidationOutcome(true, null, manifest, entryDoc);
        }
    }

    private AnthropicSkillPackValidator() {
    }

    public static PackValidationOutcome validate(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            return PackValidationOutcome.invalid("zip 为空");
        }
        if (zipBytes.length > MAX_ZIP_BYTES) {
            return PackValidationOutcome.invalid("zip 超过大小上限 " + (MAX_ZIP_BYTES / 1024 / 1024) + "MB");
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            long uncompressedTotal = 0;
            int entryCount = 0;
            byte[] skillMd = null;
            String skillEntryName = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    return PackValidationOutcome.invalid("zip 内文件过多（上限 " + MAX_ENTRIES + "）");
                }
                String name = normalizeEntryName(entry.getName());
                if (name == null) {
                    return PackValidationOutcome.invalid("非法路径: " + entry.getName());
                }
                long size = entry.getSize();
                if (size > MAX_ENTRY_BYTES) {
                    return PackValidationOutcome.invalid("单文件过大: " + name);
                }
                boolean isSkillMd = isSkillMdEntry(name);
                int limit = isSkillMd ? MAX_SKILL_MD_BYTES : MAX_ENTRY_BYTES;
                byte[] body;
                try {
                    body = readFullyCapped(zis, size, limit);
                } catch (IOException ex) {
                    return PackValidationOutcome.invalid("读取条目失败: " + name);
                }
                if (isSkillMd) {
                    if (skillMd != null) {
                        return PackValidationOutcome.invalid("zip 中包含多个 SKILL.md");
                    }
                    skillMd = body;
                    skillEntryName = name;
                }
                uncompressedTotal += body.length;
                if (uncompressedTotal > MAX_UNCOMPRESSED_TOTAL) {
                    return PackValidationOutcome.invalid("解压后总大小超限");
                }
                zis.closeEntry();
            }
            if (skillMd == null || skillMd.length == 0) {
                return PackValidationOutcome.invalid("zip 中未找到 SKILL.md");
            }
            String text = new String(skillMd, StandardCharsets.UTF_8);
            Map<String, Object> manifest = new LinkedHashMap<>();
            parseFrontmatter(text, manifest);
            if (skillEntryName != null) {
                manifest.put("skillEntryPath", skillEntryName.replace('\\', '/'));
            }
            return PackValidationOutcome.ok(Map.copyOf(manifest), "SKILL.md");
        } catch (IOException e) {
            return PackValidationOutcome.invalid("解析 zip 失败: " + e.getMessage());
        }
    }

    private static String normalizeEntryName(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.replace('\\', '/');
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        if (n.isEmpty()) {
            return null;
        }
        for (String part : n.split("/")) {
            if ("..".equals(part)) {
                return null;
            }
        }
        return n;
    }

    private static boolean isSkillMdEntry(String normalizedName) {
        String lower = normalizedName.toLowerCase(Locale.ROOT);
        return lower.endsWith("skill.md") && (lower.equals("skill.md") || lower.endsWith("/skill.md"));
    }

    private static byte[] readFullyCapped(InputStream in, long declaredSize, int hardMax) throws IOException {
        if (declaredSize >= 0 && declaredSize > hardMax) {
            throw new IOException("entry too large");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int total = 0;
        int read;
        while ((read = in.read(b)) != -1) {
            total += read;
            if (total > hardMax) {
                throw new IOException("entry exceeded cap");
            }
            buf.write(b, 0, read);
        }
        return buf.toByteArray();
    }

    /**
     * 极简 frontmatter：首开头的 --- 块直至单独一行的 ---。
     */
    static void parseFrontmatter(String md, Map<String, Object> out) {
        if (md == null || !md.startsWith("---")) {
            return;
        }
        int openEnd = md.indexOf('\n', 3);
        if (openEnd < 0) {
            return;
        }
        int close = md.indexOf("\n---", openEnd);
        if (close < 0) {
            return;
        }
        String block = md.substring(openEnd + 1, close);
        for (String line : block.split("\r?\n")) {
            int c = line.indexOf(':');
            if (c <= 0) {
                continue;
            }
            String key = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
            String val = line.substring(c + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            if ("name".equals(key)) {
                out.put("name", val);
            } else if ("description".equals(key)) {
                out.put("description", val);
            }
        }
    }
}
