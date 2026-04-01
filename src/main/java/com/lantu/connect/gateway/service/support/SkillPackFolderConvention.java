package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Anthropic 技能包要求存在 {@code SKILL.md}；对「纯文件夹」制品（仅有脚本/资源而无入口文档）在 zip 根目录补全最小 {@code SKILL.md}，
 * 以便平台校验与展示；用户可在后续版本中替换为正式说明。
 */
public final class SkillPackFolderConvention {

    private static final int MAX_LISTED_PATHS = 80;

    private SkillPackFolderConvention() {
    }

    public static byte[] ensureRootSkillMd(byte[] zipBytes, String nameHint) {
        if (zipBytes == null || zipBytes.length == 0) {
            return zipBytes;
        }
        try {
            Map<String, byte[]> files = new LinkedHashMap<>();
            boolean hasSkill = false;
            long uncompressedTotal = 0;
            int entryCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    entryCount++;
                    if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "zip 内条目过多");
                    }
                    String name = normalizeEntryName(entry.getName());
                    if (name == null) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "非法 zip 路径: " + entry.getName());
                    }
                    if (isSkillMdEntry(name)) {
                        hasSkill = true;
                    }
                    long size = entry.getSize();
                    byte[] body = readFullyCapped(zis, size, AnthropicSkillPackValidator.MAX_ENTRY_BYTES);
                    uncompressedTotal += body.length;
                    if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "解压 zip 总量超限");
                    }
                    files.put(name, body);
                    zis.closeEntry();
                }
            }
            if (hasSkill) {
                return zipBytes;
            }
            if (files.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "zip 中无文件，无法按纯文件夹规范补全 SKILL.md");
            }
            String packName = derivePackName(nameHint);
            byte[] synthetic = buildSyntheticSkillMd(packName, files.keySet());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry("SKILL.md"));
                zos.write(synthetic);
                zos.closeEntry();
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "处理技能包 zip 失败: " + e.getMessage());
        }
    }

    private static String derivePackName(String nameHint) {
        if (nameHint == null || nameHint.isBlank()) {
            return "imported-skill";
        }
        String n = nameHint.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0 && slash < n.length() - 1) {
            n = n.substring(slash + 1);
        }
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            n = n.substring(0, n.length() - 4);
        }
        if (n.isBlank()) {
            return "imported-skill";
        }
        if (n.length() > 120) {
            n = n.substring(0, 120);
        }
        return n;
    }

    private static byte[] buildSyntheticSkillMd(String packName, Iterable<String> paths) {
        List<String> list = new ArrayList<>();
        for (String p : paths) {
            list.add(p);
        }
        list.sort(String::compareTo);
        StringBuilder lines = new StringBuilder();
        lines.append("---\n");
        lines.append("name: ").append(yamlEscapeSimple(packName)).append("\n");
        lines.append("description: ")
                .append("由纯文件夹/无 SKILL.md 制品自动生成的入口说明（请替换为正式文档）\n");
        lines.append("---\n\n");
        lines.append("本 **SKILL.md** 由平台自动添加：原包不含 `SKILL.md`，但含有其它文件。\n\n");
        lines.append("## 包内文件（节选）\n\n");
        int n = 0;
        for (String p : list) {
            if (n >= MAX_LISTED_PATHS) {
                int rest = list.size() - MAX_LISTED_PATHS;
                lines.append("- … 另有 ").append(Math.max(0, rest)).append(" 个路径\n");
                break;
            }
            lines.append("- `").append(p.replace("`", "'")).append("`\n");
            n++;
        }
        return lines.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String yamlEscapeSimple(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\\\"");
        if (t.contains(":") || t.contains("\n") || t.contains("\\")) {
            return "\"" + t.replace("\n", " ") + "\"";
        }
        return t;
    }

    private static boolean isSkillMdEntry(String normalizedName) {
        String lower = normalizedName.toLowerCase(Locale.ROOT);
        return lower.endsWith("skill.md") && (lower.equals("skill.md") || lower.endsWith("/skill.md"));
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
}
