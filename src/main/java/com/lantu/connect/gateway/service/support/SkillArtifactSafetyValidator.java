package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能 zip 安全扫描：大小、条目数、解压总量、路径穿越、单条上限；不要求 SKILL.md。
 * 通过后方可入库；语义校验见 {@link AnthropicSkillPackValidator}。
 */
public final class SkillArtifactSafetyValidator {

    private SkillArtifactSafetyValidator() {
    }

    /**
     * @throws BusinessException PARAM_ERROR 当 zip 不符合安全策略
     */
    public static void validateOrThrow(byte[] zipBytes) {
        String msg = validate(zipBytes);
        if (msg != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, msg);
        }
    }

    /**
     * @return 错误信息；null 表示通过
     */
    public static String validate(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            return "zip 为空";
        }
        if (zipBytes.length > AnthropicSkillPackValidator.MAX_ZIP_BYTES) {
            return "zip 超过大小上限 " + (AnthropicSkillPackValidator.MAX_ZIP_BYTES / 1024 / 1024) + "MB";
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            long uncompressedTotal = 0;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                entryCount++;
                if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                    return "zip 内文件过多（上限 " + AnthropicSkillPackValidator.MAX_ENTRIES + "）";
                }
                String name = normalizeEntryName(entry.getName());
                if (name == null) {
                    return "非法路径: " + entry.getName();
                }
                long size = entry.getSize();
                if (size > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    return "单文件过大: " + name;
                }
                boolean isSkillMd = isSkillMdEntry(name);
                int limit = isSkillMd ? AnthropicSkillPackValidator.MAX_SKILL_MD_BYTES : AnthropicSkillPackValidator.MAX_ENTRY_BYTES;
                byte[] body;
                try {
                    body = readFullyCapped(zis, size, limit);
                } catch (IOException ex) {
                    return "读取条目失败: " + name;
                }
                uncompressedTotal += body.length;
                if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                    return "解压后总大小超限";
                }
                zis.closeEntry();
            }
            return null;
        } catch (IOException e) {
            return "解析 zip 失败: " + e.getMessage();
        }
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
