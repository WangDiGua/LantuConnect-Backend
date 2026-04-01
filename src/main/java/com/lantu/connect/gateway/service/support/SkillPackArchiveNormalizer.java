package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将多种常见技能包形态归一为标准 zip，再走 {@link AnthropicSkillPackValidator}：
 * <ul>
 *     <li>.zip — 原样通过</li>
 *     <li>.tar.gz / .tgz — 解压后整包转 zip（保留相对路径，含 SKILL.md）</li>
 *     <li>.7z — 解压后整包转 zip</li>
 *     <li>.rar（常见 RAR4，由 junrar 解析）— 解压后整包转 zip</li>
 *     <li>裸 .tar — 同上</li>
 *     <li>gzip 压缩的单个文件 — 若为 Markdown 则包成根目录 SKILL.md</li>
 *     <li>单个 SKILL.md 文本 — 包成 zip</li>
 * </ul>
 */
public final class SkillPackArchiveNormalizer {

    private SkillPackArchiveNormalizer() {
    }

    public static byte[] normalizeToSkillZip(byte[] input, String filenameHint) {
        if (input == null || input.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "技能包内容为空");
        }
        if (input.length > AnthropicSkillPackValidator.MAX_ZIP_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "技能包超过大小上限 " + (AnthropicSkillPackValidator.MAX_ZIP_BYTES / 1024 / 1024) + "MB");
        }
        if (isZip(input)) {
            return input;
        }
        if (is7z(input)) {
            return sevenZBytesToZip(input);
        }
        if (isRar(input)) {
            return rarBytesToZip(input);
        }
        if (isGzip(input)) {
            byte[] inner = gunzipFully(input);
            if (looksLikeTar(inner)) {
                return tarBytesToZip(inner);
            }
            return singleMarkdownOrTextToZip(inner, filenameHint);
        }
        if (looksLikeTar(input)) {
            return tarBytesToZip(input);
        }
        if (looksLikeSingleSkillMarkdown(input, filenameHint)) {
            return wrapAsRootSkillMd(input);
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "不支持的技能包格式：请使用 .zip、.7z、.rar、.tar.gz/.tgz、裸 .tar，或单个 Markdown（SKILL.md / frontmatter）");
    }

    /**
     * 存储用文件名：归一化后制品一律为 .zip，便于后续下载与类型识别。
     */
    public static String normalizeStorageFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return "skill-pack.zip";
        }
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return original;
        }
        if (lower.endsWith(".tar.gz")) {
            return original.substring(0, original.length() - ".tar.gz".length()) + ".zip";
        }
        if (lower.endsWith(".tgz")) {
            return original.substring(0, original.length() - ".tgz".length()) + ".zip";
        }
        if (lower.endsWith(".tar")) {
            return original.substring(0, original.length() - ".tar".length()) + ".zip";
        }
        if (lower.endsWith(".7z")) {
            return original.substring(0, original.length() - ".7z".length()) + ".zip";
        }
        if (lower.endsWith(".rar")) {
            return original.substring(0, original.length() - ".rar".length()) + ".zip";
        }
        if (lower.endsWith(".md")) {
            int d = original.lastIndexOf('.');
            String base = d > 0 ? original.substring(0, d) : "skill";
            return base + ".zip";
        }
        if (lower.endsWith(".gz")) {
            int d = original.lastIndexOf('.');
            String base = d > 0 ? original.substring(0, d) : "skill-pack";
            return base + ".zip";
        }
        return original.endsWith(".zip") ? original : original + ".zip";
    }

    private static boolean isZip(byte[] b) {
        return b.length >= 4 && b[0] == 'P' && b[1] == 'K'
                && (b[2] == 3 || b[2] == 5 || b[2] == 7)
                && (b[3] == 4 || b[3] == 6 || b[3] == 8);
    }

    private static boolean isGzip(byte[] b) {
        return b.length >= 2 && (b[0] & 0xFF) == 0x1f && (b[1] & 0xFF) == 0x8b;
    }

    private static boolean is7z(byte[] b) {
        return b.length >= 6
                && (b[0] & 0xFF) == 0x37
                && (b[1] & 0xFF) == 0x7a
                && (b[2] & 0xFF) == 0xbc
                && (b[3] & 0xFF) == 0xaf
                && (b[4] & 0xFF) == 0x27
                && (b[5] & 0xFF) == 0x1c;
    }

    private static boolean isRar(byte[] b) {
        if (b.length < 7) {
            return false;
        }
        return b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!' && b[4] == 0x1a && b[5] == 0x07;
    }

    private static byte[] sevenZBytesToZip(byte[] sevenZBytes) {
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
        try (SeekableInMemoryByteChannel ch = new SeekableInMemoryByteChannel(sevenZBytes);
             SevenZFile sz = SevenZFile.builder().setSeekableByteChannel(ch).get();
             ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            int entryCount = 0;
            long uncompressedTotal = 0;
            Iterable<SevenZArchiveEntry> entries = sz.getEntries();
            for (SevenZArchiveEntry entry : entries) {
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "7z 内条目过多");
                }
                String normalized = normalizeEntryName(entry.getName());
                if (normalized == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "非法 7z 路径: " + entry.getName());
                }
                long declared = entry.getSize();
                byte[] body;
                try (InputStream in = sz.getInputStream(entry)) {
                    body = readStreamFullyCapped(in, declared, AnthropicSkillPackValidator.MAX_ENTRY_BYTES);
                }
                long add = body.length;
                if (add > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "7z 内单文件过大: " + normalized);
                }
                uncompressedTotal += add;
                if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "7z 解压后总大小超限");
                }
                zos.putNextEntry(new ZipEntry(normalized));
                zos.write(body);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "解析 7z 失败: " + e.getMessage());
        }
        byte[] zip = zipBos.toByteArray();
        if (zip.length < 22) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "7z 归档无有效文件或转 zip 失败");
        }
        return zip;
    }

    private static byte[] rarBytesToZip(byte[] rarBytes) {
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
        try (Archive archive = new Archive(new ByteArrayInputStream(rarBytes));
             ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            int entryCount = 0;
            long uncompressedTotal = 0;
            FileHeader fh;
            while ((fh = archive.nextFileHeader()) != null) {
                if (fh.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "rar 内条目过多");
                }
                String nameRaw = fh.getFileName().trim().replace('\\', '/');
                String normalized = normalizeEntryName(nameRaw);
                if (normalized == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "非法 rar 路径: " + nameRaw);
                }
                if (fh.isEncrypted()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "不支持加密 rar 技能包");
                }
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                archive.extractFile(fh, bout);
                byte[] body = bout.toByteArray();
                if (body.length > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "rar 内单文件过大: " + normalized);
                }
                uncompressedTotal += body.length;
                if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "rar 解压后总大小超限");
                }
                zos.putNextEntry(new ZipEntry(normalized));
                zos.write(body);
                zos.closeEntry();
            }
        } catch (RarException | IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "解析 rar 失败: " + e.getMessage());
        }
        byte[] zip = zipBos.toByteArray();
        if (zip.length < 22) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "rar 归档无有效文件或转 zip 失败");
        }
        return zip;
    }

    private static byte[] readStreamFullyCapped(InputStream in, long declaredSize, int hardMax) throws IOException {
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

    private static boolean looksLikeTar(byte[] raw) {
        return raw.length >= 264 && "ustar".equals(new String(raw, 257, 5, StandardCharsets.US_ASCII));
    }

    private static byte[] gunzipFully(byte[] gzipBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(gzipBytes.length * 3,
                AnthropicSkillPackValidator.MAX_ZIP_BYTES));
        try (GzipCompressorInputStream gis = new GzipCompressorInputStream(new ByteArrayInputStream(gzipBytes))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) >= 0) {
                if (out.size() + n > AnthropicSkillPackValidator.MAX_ZIP_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "gzip 解压后超过大小上限");
                }
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "gzip 解压失败: " + e.getMessage());
        }
        return out.toByteArray();
    }

    private static boolean looksLikeSingleSkillMarkdown(byte[] raw, String hint) {
        if (raw.length > AnthropicSkillPackValidator.MAX_SKILL_MD_BYTES) {
            return false;
        }
        if (StringUtils.hasText(hint) && hint.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return true;
        }
        int probe = Math.min(raw.length, 256);
        if (probe < 4) {
            return false;
        }
        try {
            String head = new String(raw, 0, probe, StandardCharsets.UTF_8);
            if (head.startsWith("---")) {
                return true;
            }
            return head.stripLeading().startsWith("#");
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] singleMarkdownOrTextToZip(byte[] content, String filenameHint) {
        if (looksLikeSingleSkillMarkdown(content, filenameHint)) {
            return wrapAsRootSkillMd(content);
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "gzip 内为单文件时须为 Markdown（建议 .md 文件名或以 --- frontmatter / # 标题开头）");
    }

    private static byte[] wrapAsRootSkillMd(byte[] utf8Content) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write(utf8Content);
            zos.closeEntry();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打包 SKILL.md 失败: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    private static byte[] tarBytesToZip(byte[] tarBytes) {
        if (tarBytes.length > AnthropicSkillPackValidator.MAX_ZIP_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "tar 超过大小上限");
        }
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
        try (TarArchiveInputStream tin = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes));
             ZipOutputStream zos = new ZipOutputStream(zipBos)) {
            TarArchiveEntry entry;
            int entryCount = 0;
            long uncompressedTotal = 0;
            while ((entry = tin.getNextEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                entryCount++;
                if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "tar 内条目过多");
                }
                String normalized = normalizeEntryName(entry.getName());
                if (normalized == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "非法 tar 路径: " + entry.getName());
                }
                long sz = entry.getSize();
                byte[] body = readTarEntryBody(tin, sz);
                long add = body.length;
                if (add > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "tar 内单文件过大: " + normalized);
                }
                uncompressedTotal += add;
                if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "tar 解压后总大小超限");
                }
                zos.putNextEntry(new ZipEntry(normalized));
                zos.write(body);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "解析 tar 失败: " + e.getMessage());
        }
        byte[] zip = zipBos.toByteArray();
        if (zip.length == 0 || zip.length < 22) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "tar 归档为空或无效");
        }
        return zip;
    }

    private static byte[] readTarEntryBody(TarArchiveInputStream tin, long declaredSize) throws IOException {
        int max = AnthropicSkillPackValidator.MAX_ENTRY_BYTES;
        if (declaredSize > max) {
            throw new IOException("entry too large");
        }
        byte[] buf = new byte[8192];
        if (declaredSize >= 0) {
            long rem = declaredSize;
            ByteArrayOutputStream bout = new ByteArrayOutputStream((int) Math.min(declaredSize, max));
            while (rem > 0) {
                int chunk = (int) Math.min(buf.length, rem);
                int read = tin.read(buf, 0, chunk);
                if (read < 0) {
                    throw new IOException("truncated tar entry");
                }
                bout.write(buf, 0, read);
                rem -= read;
            }
            return bout.toByteArray();
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int total = 0;
        int read;
        while ((read = tin.read(buf)) != -1) {
            total += read;
            if (total > max) {
                throw new IOException("entry exceeded cap");
            }
            bout.write(buf, 0, read);
        }
        return bout.toByteArray();
    }

    private static String normalizeEntryName(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.replace('\\', '/').trim();
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
}
