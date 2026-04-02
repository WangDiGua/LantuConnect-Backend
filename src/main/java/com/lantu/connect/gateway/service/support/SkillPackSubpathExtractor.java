package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 从标准 zip 中提取某一前缀下的条目，路径去掉该前缀，得到用于 {@link AnthropicSkillPackValidator} 的子 zip。
 */
public final class SkillPackSubpathExtractor {

    private SkillPackSubpathExtractor() {
    }

    /**
     * @param zipBytes   已通过安全扫描的 zip
     * @param skillRoot  非 null 非空，规范化后的前缀（无首尾斜杠）
     * @return 仅含子树文件的新 zip
     */
    public static byte[] extractSubtree(byte[] zipBytes, String skillRoot) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "zip 为空");
        }
        if (skillRoot == null || skillRoot.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "skillRoot 不能为空");
        }
        String prefix = skillRoot.replace('\\', '/');
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        final String p = prefix.toLowerCase(Locale.ROOT);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int writtenFiles = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = normalizeEntryName(entry.getName());
                if (name == null) {
                    zis.closeEntry();
                    continue;
                }
                String nameLower = name.toLowerCase(Locale.ROOT);
                String relative;
                if (nameLower.equals(p)) {
                    int slash = name.lastIndexOf('/');
                    relative = slash < 0 ? name : name.substring(slash + 1);
                } else if (nameLower.startsWith(p + "/")) {
                    relative = name.substring(prefix.length() + 1);
                } else {
                    zis.closeEntry();
                    continue;
                }
                if (relative.isEmpty() || relative.endsWith("/")) {
                    zis.closeEntry();
                    continue;
                }
                if (normalizeEntryName(relative) == null) {
                    zis.closeEntry();
                    continue;
                }
                long size = entry.getSize();
                if (size > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    zis.closeEntry();
                    continue;
                }
                boolean isSkillMd = isSkillMdEntry(relative);
                int limit = isSkillMd ? AnthropicSkillPackValidator.MAX_SKILL_MD_BYTES : AnthropicSkillPackValidator.MAX_ENTRY_BYTES;
                byte[] body = readFullyCapped(zis, size, limit);
                zos.putNextEntry(new ZipEntry(relative.replace('\\', '/')));
                zos.write(body);
                zos.closeEntry();
                writtenFiles++;
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "提取技能子目录失败: " + e.getMessage());
        }
        if (writtenFiles == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "skillRoot 下无匹配文件，请确认 zip 内存在此前缀路径: " + skillRoot);
        }
        return bos.toByteArray();
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
