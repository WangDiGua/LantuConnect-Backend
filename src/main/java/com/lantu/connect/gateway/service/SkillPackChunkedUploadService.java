package com.lantu.connect.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.storage.FileStorageSupport;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.SkillPackChunkInitRequest;
import com.lantu.connect.gateway.dto.SkillPackChunkInitResponse;
import com.lantu.connect.gateway.dto.SkillPackChunkStatusResponse;
import com.lantu.connect.gateway.service.support.AnthropicSkillPackValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 技能包分片上传与合并：支持断点续传（同一 uploadId 重复提交相同分片会覆盖）。
 * 会话目录位于 {@code {file.upload-dir}/.skill-chunk-sessions/{uploadId}/}，完成或中止后删除。
 */
@Service
@RequiredArgsConstructor
public class SkillPackChunkedUploadService {

    public static final int CHUNK_SIZE = 4 * 1024 * 1024;
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final String META_FILE = "meta.json";
    private static final String SESSION_ROOT = ".skill-chunk-sessions";

    private final FileStorageSupport fileStorageSupport;
    private final ObjectMapper objectMapper;
    private final SkillPackUploadService skillPackUploadService;

    private final ConcurrentHashMap<String, Object> uploadLocks = new ConcurrentHashMap<>();

    public SkillPackChunkInitResponse init(Long userId, SkillPackChunkInitRequest req) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        long size = req.getFileSize();
        if (size > AnthropicSkillPackValidator.MAX_ZIP_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "文件超过上限 " + (AnthropicSkillPackValidator.MAX_ZIP_BYTES / 1024 / 1024) + "MB");
        }
        String fileName = req.getFileName() == null ? "" : req.getFileName().trim();
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "fileName 无效");
        }
        if (fileName.indexOf('\0') >= 0 || fileName.contains("..")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "fileName 非法");
        }

        pruneExpiredSessions();

        int totalChunks = (int) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE);
        if (totalChunks <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分片数无效");
        }
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path dir = sessionDir(uploadId);
        try {
            Files.createDirectories(dir);
            SessionMeta meta = new SessionMeta();
            meta.userId = userId;
            meta.fileName = fileName.length() > 512 ? fileName.substring(0, 512) : fileName;
            meta.fileSize = size;
            meta.chunkSize = CHUNK_SIZE;
            meta.totalChunks = totalChunks;
            meta.resourceId = req.getResourceId();
            meta.skillRoot = req.getSkillRoot();
            meta.createdAt = Instant.now().toEpochMilli();
            meta.received = new LinkedHashSet<>();
            writeMeta(dir, meta);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建上传会话失败: " + e.getMessage());
        }

        return SkillPackChunkInitResponse.builder()
                .uploadId(uploadId)
                .chunkSize(CHUNK_SIZE)
                .totalChunks(totalChunks)
                .fileSize(size)
                .build();
    }

    public void putChunk(Long userId, String uploadId, int chunkIndex, byte[] data) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        if (data == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分片内容为空");
        }
        validateUploadId(uploadId);
        Path dir = sessionDir(uploadId);
        SessionMeta meta = readMeta(dir);
        if (meta == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话不存在或已过期");
        }
        if (!meta.userId.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该上传会话");
        }
        if (chunkIndex < 0 || chunkIndex >= meta.totalChunks) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分片下标越界");
        }
        long expectedLen = chunkIndex < meta.totalChunks - 1
                ? CHUNK_SIZE
                : meta.fileSize - (long) (meta.totalChunks - 1) * CHUNK_SIZE;
        if (expectedLen < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分片大小计算异常");
        }
        if (data.length != expectedLen) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "分片 " + chunkIndex + " 大小须为 " + expectedLen + " 字节，实际 " + data.length);
        }

        Object lock = uploadLocks.computeIfAbsent(uploadId, k -> new Object());
        synchronized (lock) {
            SessionMeta m = readMeta(dir);
            if (m == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话不存在或已过期");
            }
            try {
                Path part = dir.resolve("part-" + chunkIndex);
                Files.write(part, data);
                m.received.add(chunkIndex);
                writeMeta(dir, m);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "写入分片失败: " + e.getMessage());
            }
        }
    }

    public SkillPackChunkStatusResponse status(Long userId, String uploadId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        validateUploadId(uploadId);
        Path dir = sessionDir(uploadId);
        SessionMeta meta = readMeta(dir);
        if (meta == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话不存在或已过期");
        }
        if (!meta.userId.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该上传会话");
        }
        List<Integer> indices = new ArrayList<>(meta.received);
        indices.sort(Comparator.naturalOrder());
        return SkillPackChunkStatusResponse.builder()
                .totalChunks(meta.totalChunks)
                .fileSize(meta.fileSize)
                .receivedCount(meta.received.size())
                .receivedChunkIndices(indices)
                .build();
    }

    public ResourceManageVO complete(Long userId, String uploadId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        validateUploadId(uploadId);
        Path dir = sessionDir(uploadId);
        Object lock = uploadLocks.computeIfAbsent(uploadId, k -> new Object());
        synchronized (lock) {
            SessionMeta meta = readMeta(dir);
            if (meta == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "上传会话不存在或已过期");
            }
            if (!meta.userId.equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权完成该上传会话");
            }
            if (meta.received.size() != meta.totalChunks) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "分片未齐：已收到 " + meta.received.size() + " / " + meta.totalChunks);
            }
            for (int i = 0; i < meta.totalChunks; i++) {
                if (!meta.received.contains(i)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "缺少分片 " + i);
                }
            }

            byte[] merged;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream((int) meta.fileSize)) {
                long written = 0;
                for (int i = 0; i < meta.totalChunks; i++) {
                    Path p = dir.resolve("part-" + i);
                    if (!Files.isRegularFile(p)) {
                        throw new BusinessException(ResultCode.PARAM_ERROR, "缺少分片文件 " + i);
                    }
                    byte[] piece = Files.readAllBytes(p);
                    bos.write(piece);
                    written += piece.length;
                }
                if (written != meta.fileSize) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "合并大小与声明不一致");
                }
                merged = bos.toByteArray();
            } catch (IOException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "合并分片失败: " + e.getMessage());
            }

            try {
                ResourceManageVO vo = skillPackUploadService.uploadPackBytes(userId, merged, meta.fileName, meta.resourceId, meta.skillRoot);
                deleteSessionQuietly(dir);
                return vo;
            } finally {
                uploadLocks.remove(uploadId);
            }
        }
    }

    public void abort(Long userId, String uploadId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证");
        }
        validateUploadId(uploadId);
        Path dir = sessionDir(uploadId);
        Object lock = uploadLocks.computeIfAbsent(uploadId, k -> new Object());
        synchronized (lock) {
            SessionMeta meta = readMeta(dir);
            if (meta != null && !meta.userId.equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权取消该上传会话");
            }
            deleteSessionQuietly(dir);
            uploadLocks.remove(uploadId);
        }
    }

    private Path sessionDir(String uploadId) {
        Path base = Path.of(fileStorageSupport.getUploadDir()).toAbsolutePath().normalize();
        Path root = base.resolve(SESSION_ROOT).normalize();
        if (!root.startsWith(base)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "上传根路径无效");
        }
        Path dir = root.resolve(uploadId).normalize();
        if (!dir.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "uploadId 非法");
        }
        return dir;
    }

    private static void validateUploadId(String uploadId) {
        if (!StringUtils.hasText(uploadId) || uploadId.length() > 64 || !uploadId.matches("[a-f0-9]+")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "uploadId 非法");
        }
    }

    private void pruneExpiredSessions() {
        Path base = Path.of(fileStorageSupport.getUploadDir()).toAbsolutePath().normalize();
        Path root = base.resolve(SESSION_ROOT);
        if (!Files.isDirectory(root)) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(sub -> {
                try {
                    Path mf = sub.resolve(META_FILE);
                    if (!Files.isRegularFile(mf)) {
                        deleteSessionQuietly(sub);
                        return;
                    }
                    SessionMeta m = objectMapper.readValue(mf.toFile(), SessionMeta.class);
                    if (m != null && m.createdAt > 0 && now - m.createdAt > SESSION_TTL_MS) {
                        deleteSessionQuietly(sub);
                    }
                } catch (IOException e) {
                    deleteSessionQuietly(sub);
                }
            });
        } catch (IOException ignored) {
        }
    }

    private SessionMeta readMeta(Path dir) {
        Path mf = dir.resolve(META_FILE);
        if (!Files.isRegularFile(mf)) {
            return null;
        }
        try {
            SessionMeta m = objectMapper.readValue(mf.toFile(), SessionMeta.class);
            if (m.received == null) {
                m.received = new LinkedHashSet<>();
            }
            return m;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeMeta(Path dir, SessionMeta meta) throws IOException {
        Path mf = dir.resolve(META_FILE);
        objectMapper.writeValue(mf.toFile(), meta);
    }

    private static void deleteSessionQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /** JSON 持久化用 */
    @SuppressWarnings("unused")
    static class SessionMeta {
        public Long userId;
        public String fileName;
        public long fileSize;
        public int chunkSize;
        public int totalChunks;
        public Long resourceId;
        public String skillRoot;
        public long createdAt;
        public Set<Integer> received;
    }
}
