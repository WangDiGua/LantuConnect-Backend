package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.SkillPackImportProperties;
import com.lantu.connect.gateway.service.support.AnthropicSkillPackValidator;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 通过 JGit 浅克隆将远程 Git 仓库打成 zip，供 {@link SkillPackArchiveNormalizer} 与入库流程使用。
 */
@Service
@RequiredArgsConstructor
public class GitSkillPackCloner {

    private final RuntimeAppConfigService runtimeAppConfigService;

    private SkillPackImportProperties p() {
        return runtimeAppConfigService.skillPackImport();
    }

    public boolean shouldGitClone(URI uri) {
        if (!p().isGitCloneEnabled()) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String sc = scheme.toLowerCase(Locale.ROOT);
        if (!"https".equals(sc) && !"http".equals(sc)) {
            return false;
        }
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            return false;
        }
        if (path.toLowerCase(Locale.ROOT).endsWith(".git")) {
            return true;
        }
        if (!p().isGitCloneAllowBareRepoPaths()) {
            return false;
        }
        return isBareRepoPathOnAllowedGitHost(uri.getHost(), path);
    }

    public SkillPackUrlFetcher.FetchedPack cloneShallowToFetchedPack(String cloneUrl) {
        String url = cloneUrl.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Git 地址无效");
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("lantu-skill-git-");
            Path finalWorkDir = workDir;
            Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(finalWorkDir.toFile())
                    .setDepth(1)
                    .setCloneSubmodules(false)
                    .setTimeout(p().getGitCloneTimeoutSeconds())
                    .call()
                    .close();
            Path dotGit = finalWorkDir.resolve(".git");
            if (Files.exists(dotGit)) {
                FileUtils.delete(dotGit.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
            }
            long unpacked = measureUnpackedBytes(finalWorkDir);
            long cap = Math.max(1024L, p().getGitCloneMaxUnpackedBytes());
            if (unpacked > cap) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "克隆产物超过大小上限 " + (cap / 1024 / 1024) + "MB，请缩小仓库或使用制品 zip");
            }
            byte[] zipBytes = directoryToZip(finalWorkDir);
            return new SkillPackUrlFetcher.FetchedPack(zipBytes, filenameForGitUri(uri));
        } catch (BusinessException e) {
            throw e;
        } catch (GitAPIException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Git 克隆失败: " + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Git 克隆 I/O 错误: " + e.getMessage());
        } finally {
            if (workDir != null) {
                try {
                    FileUtils.delete(workDir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String filenameForGitUri(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "git-repo.zip";
        }
        String p = path;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = p.lastIndexOf('/');
        String last = slash >= 0 ? p.substring(slash + 1) : p;
        if (last.toLowerCase(Locale.ROOT).endsWith(".git")) {
            last = last.substring(0, last.length() - 4);
        }
        if (!StringUtils.hasText(last)) {
            last = "git-repo";
        }
        return last + ".zip";
    }

    private boolean isBareRepoPathOnAllowedGitHost(String host, String pathRaw) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        List<String> suffixes = p().getGitBareRepoHostSuffixes();
        if (suffixes == null || suffixes.isEmpty()) {
            return false;
        }
        boolean hostOk = false;
        for (String s : suffixes) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String suf = s.trim().toLowerCase(Locale.ROOT);
            if (h.equals(suf) || h.endsWith("." + suf)) {
                hostOk = true;
                break;
            }
        }
        if (!hostOk) {
            return false;
        }
        String p = pathRaw.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (!StringUtils.hasText(p)) {
            return false;
        }
        String[] seg = p.split("/");
        if (seg.length != 2) {
            return false;
        }
        if (!StringUtils.hasText(seg[0]) || !StringUtils.hasText(seg[1])) {
            return false;
        }
        String last = seg[1].toLowerCase(Locale.ROOT);
        if (last.endsWith(".zip") || last.endsWith(".tar") || last.endsWith(".gz") || last.endsWith(".tgz")
                || last.endsWith(".rar") || last.endsWith(".7z") || last.endsWith(".md")) {
            return false;
        }
        return true;
    }

    private static long measureUnpackedBytes(Path root) throws IOException {
        long[] total = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    total[0] += attrs.size();
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private static byte[] directoryToZip(Path root) throws IOException {
        ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBos);
             Stream<Path> walk = Files.walk(root)) {
            int entryCount = 0;
            long uncompressedTotal = 0;
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                entryCount++;
                if (entryCount > AnthropicSkillPackValidator.MAX_ENTRIES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "克隆仓库内文件过多（上限 " + AnthropicSkillPackValidator.MAX_ENTRIES + "）");
                }
                String rel = root.relativize(file).toString().replace('\\', '/');
                String normalized = normalizeEntryName(rel);
                if (normalized == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "非法仓库路径: " + rel);
                }
                long sz = attrs.size();
                if (sz > AnthropicSkillPackValidator.MAX_ENTRY_BYTES) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "仓库内单文件过大: " + normalized);
                }
                byte[] body = Files.readAllBytes(file);
                uncompressedTotal += body.length;
                if (uncompressedTotal > AnthropicSkillPackValidator.MAX_UNCOMPRESSED_TOTAL) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "打 zip 时解压总量超限");
                }
                zos.putNextEntry(new ZipEntry(normalized));
                zos.write(body);
                zos.closeEntry();
            }
        }
        return zipBos.toByteArray();
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
