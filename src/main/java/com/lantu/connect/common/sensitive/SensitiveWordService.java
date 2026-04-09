package com.lantu.connect.common.sensitive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 敏感词服务
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    /** 与表 t_sensitive_word.word varchar(128) 一致 */
    private static final int MAX_WORD_LENGTH = 128;
    private static final Pattern DEFAULT_MASK_GROUP = Pattern.compile("\\*+");

    private final SensitiveWordMapper sensitiveWordMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final AtomicReference<SensitiveWordBs> engineRef = new AtomicReference<>();

    private static final class TxtParseOutcome {
        final List<String> words = new ArrayList<>();
        int skippedBlankOrComment;
        int skippedTooLong;
    }

    @PostConstruct
    public void init() {
        refreshWordBank();
    }

    @Scheduled(fixedRate = 3600000)
    public void scheduledRefresh() {
        refreshWordBank();
    }

    public void refreshWordBank() {
        try {
            List<String> dbWords = normalizedDbWords();
            SensitiveWordBs newEngine = buildEngine(dbWords);
            engineRef.set(newEngine);
            log.info("敏感词库刷新完成（开源引擎 + DB词库 + 内置词库），DB有效词数: {}", dbWords.size());
        } catch (RuntimeException e) {
            engineRef.set(buildEngine(List.of()));
            log.warn("敏感词库刷新失败，已回退为仅开源内置词库: {}", e.getMessage());
        }
    }

    public boolean contains(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return engine().contains(text);
    }

    public Set<String> findSensitiveWords(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        return new LinkedHashSet<>(engine().findAll(text));
    }

    public String filter(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return engine().replace(text);
    }

    public String filter(String text, String replacement) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String masked = engine().replace(text);
        if (!StringUtils.hasText(replacement) || "*".equals(replacement)) {
            return masked;
        }
        return DEFAULT_MASK_GROUP.matcher(masked).replaceAll(replacement);
    }

    public Page<SensitiveWord> list(int page, int pageSize, String category, Boolean enabled, String keyword) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(SensitiveWord::getCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(SensitiveWord::getEnabled, enabled);
        }
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            wrapper.like(SensitiveWord::getWord, kw);
        }
        wrapper.orderByDesc(SensitiveWord::getCreateTime);
        Page<SensitiveWord> result = sensitiveWordMapper.selectPage(new Page<>(page, pageSize), wrapper);
        enrichNames(result.getRecords());
        return result;
    }

    public List<SensitiveWordCategoryStat> listCategories() {
        List<SensitiveWordCategoryStat> rows = sensitiveWordMapper.selectCategoryCounts();
        Map<String, Integer> map = new LinkedHashMap<>();
        for (SensitiveWordCategoryStat r : rows) {
            if (r.getCategory() != null) {
                map.put(r.getCategory(), r.getCount() != null ? r.getCount() : 0);
            }
        }
        List<SensitiveWordCategoryStat> out = new ArrayList<>();
        for (String p : SensitiveWordFixedCategories.CODES) {
            out.add(new SensitiveWordCategoryStat(p, map.getOrDefault(p, 0)));
        }
        List<String> extras = map.keySet().stream()
                .filter(k -> !SensitiveWordFixedCategories.CODES.contains(k))
                .sorted()
                .toList();
        for (String e : extras) {
            out.add(new SensitiveWordCategoryStat(e, map.get(e)));
        }
        return out;
    }

    /**
     * 插入新词；空串/白空格返回 null；已存在返回 null。
     */
    private SensitiveWord tryInsertWord(String word, String category, Integer severity, String source, Long userId) {
        if (!StringUtils.hasText(word)) {
            return null;
        }
        word = normalizeWord(word);
        if (word.length() > MAX_WORD_LENGTH) {
            log.warn("敏感词超过 {} 字符已跳过: {}", MAX_WORD_LENGTH, word.substring(0, Math.min(word.length(), 32)));
            return null;
        }
        Long count = sensitiveWordMapper.selectCount(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, word));
        if (count > 0) {
            return null;
        }
        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setCategory(StringUtils.hasText(category) ? category : "default");
        entity.setSeverity(severity != null ? severity : 1);
        entity.setSource(StringUtils.hasText(source) ? source : "manual");
        entity.setEnabled(true);
        entity.setCreatedBy(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        sensitiveWordMapper.insert(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public SensitiveWord add(String word, String category, Integer severity, String source, Long userId) {
        if (!StringUtils.hasText(word)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "敏感词不能为空");
        }
        String normalized = word.trim().toLowerCase();
        if (normalized.length() > MAX_WORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "敏感词长度不能超过 " + MAX_WORD_LENGTH + " 个字符");
        }
        SensitiveWord entity = tryInsertWord(word, category, severity, source, userId);
        if (entity == null) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "该敏感词已存在");
        }
        refreshWordBank();
        entity.setCreatedByName(userDisplayNameResolver.resolveDisplayName(userId));
        return entity;
    }

    /**
     * 从 TXT 正文导入：每行一词，UTF-8；去掉 BOM；空行忽略；{@code #} 开头为注释；单条最长 128 字符（与表字段一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveWordTxtImportResult importFromTxt(String rawText, String category, Integer severity,
                                                        String source, Long userId) {
        if (!StringUtils.hasText(rawText)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容为空");
        }
        TxtParseOutcome parse = parseTxtLines(rawText);
        if (parse.words.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "文件中未解析到有效词条。规则：每行一词，UTF-8，# 开头为注释，单条最长 "
                            + MAX_WORD_LENGTH + " 字符。");
        }
        String src = StringUtils.hasText(source) ? source : "txt_upload";
        String cat = StringUtils.hasText(category) ? category : "default";
        int sev = severity != null ? severity : 1;
        int added = 0;
        for (String w : parse.words) {
            if (tryInsertWord(w, cat, sev, src, userId) != null) {
                added++;
            }
        }
        if (added > 0) {
            log.info("TXT 导入敏感词完成，新增 {} / 候选 {} 条", added, parse.words.size());
            refreshWordBank();
        }
        int candidates = parse.words.size();
        return new SensitiveWordTxtImportResult(added, candidates, parse.skippedBlankOrComment,
                parse.skippedTooLong, candidates - added);
    }

    @Transactional(rollbackFor = Exception.class)
    public SensitiveWordTxtImportResult importFromFile(byte[] fileBytes, String filename,
                                                       String category, Integer severity, String source, Long userId) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件内容为空");
        }
        String ext = extension(filename);
        return switch (ext) {
            case "txt" -> importFromTxt(new String(fileBytes, StandardCharsets.UTF_8), category, severity, source, userId);
            case "csv" -> importByWords(parseCsvFirstColumn(fileBytes), category, severity,
                    StringUtils.hasText(source) ? source : "csv_upload", userId);
            case "xlsx" -> importByWords(parseXlsxFirstColumn(fileBytes), category, severity,
                    StringUtils.hasText(source) ? source : "xlsx_upload", userId);
            default -> throw new BusinessException(ResultCode.UNSUPPORTED_FILE_TYPE, "仅支持 txt/csv/xlsx");
        };
    }

    private SensitiveWordTxtImportResult importByWords(List<String> words, String category, Integer severity,
                                                       String source, Long userId) {
        TxtParseOutcome parse = normalizeWords(words);
        if (parse.words.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件中未解析到有效词条");
        }
        String cat = StringUtils.hasText(category) ? category : "default";
        int sev = severity != null ? severity : 1;
        int added = 0;
        for (String w : parse.words) {
            if (tryInsertWord(w, cat, sev, source, userId) != null) {
                added++;
            }
        }
        if (added > 0) {
            refreshWordBank();
        }
        int candidates = parse.words.size();
        return new SensitiveWordTxtImportResult(added, candidates, parse.skippedBlankOrComment,
                parse.skippedTooLong, candidates - added);
    }

    private TxtParseOutcome normalizeWords(List<String> rawWords) {
        TxtParseOutcome out = new TxtParseOutcome();
        if (rawWords == null || rawWords.isEmpty()) {
            return out;
        }
        for (String line : rawWords) {
            String t = line == null ? "" : line.trim();
            if (!StringUtils.hasText(t) || t.startsWith("#")) {
                out.skippedBlankOrComment++;
                continue;
            }
            if (t.length() > MAX_WORD_LENGTH) {
                out.skippedTooLong++;
                continue;
            }
            out.words.add(t);
        }
        return out;
    }

    private List<String> parseCsvFirstColumn(byte[] bytes) {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    out.add(line);
                    continue;
                }
                String first = line.split(",", 2)[0];
                out.add(first);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "CSV 读取失败");
        }
        return out;
    }

    private List<String> parseXlsxFirstColumn(byte[] bytes) {
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(new ByteArrayInputStream(bytes))
                    .sheet(0)
                    .headRowNumber(0)
                    .doReadSync();
            if (rows == null) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>(rows.size());
            for (Map<Integer, String> row : rows) {
                Map<Integer, String> safe = row == null ? new LinkedHashMap<>() : row;
                out.add(safe.getOrDefault(0, ""));
            }
            return out;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "XLSX 读取失败");
        }
    }

    private static String extension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static TxtParseOutcome parseTxtLines(String rawText) {
        String text = rawText;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        TxtParseOutcome out = new TxtParseOutcome();
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (!StringUtils.hasText(t)) {
                    out.skippedBlankOrComment++;
                    continue;
                }
                if (t.startsWith("#")) {
                    out.skippedBlankOrComment++;
                    continue;
                }
                if (t.length() > MAX_WORD_LENGTH) {
                    out.skippedTooLong++;
                    continue;
                }
                out.words.add(t);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取文本失败", e);
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchAdd(List<String> words, String category, Integer severity, String source, Long userId) {
        if (words == null || words.isEmpty()) {
            return;
        }
        int added = 0;
        for (String word : words) {
            if (tryInsertWord(word, category, severity, source, userId) != null) {
                added++;
            }
        }
        if (added > 0) {
            log.info("批量导入敏感词完成，本次新增 {} 条（列表共 {} 行，含空行/重复已跳过）", added, words.size());
            refreshWordBank();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String word, String category, Integer severity, Boolean enabled, String source) {
        SensitiveWord entity = sensitiveWordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "敏感词不存在");
        }
        if (StringUtils.hasText(word)) {
            String nw = normalizeWord(word);
            if (nw.length() > MAX_WORD_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "敏感词长度不能超过 " + MAX_WORD_LENGTH);
            }
            if (!nw.equals(entity.getWord())) {
                Long dup = sensitiveWordMapper.selectCount(
                        new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, nw).ne(SensitiveWord::getId, id));
                if (dup != null && dup > 0) {
                    throw new BusinessException(ResultCode.CONFLICT, "敏感词已存在");
                }
                entity.setWord(nw);
            }
        }
        if (StringUtils.hasText(category)) {
            entity.setCategory(category);
        }
        if (severity != null) {
            entity.setSeverity(severity);
        }
        if (StringUtils.hasText(source)) {
            entity.setSource(source.trim());
        }
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        entity.setUpdateTime(LocalDateTime.now());
        sensitiveWordMapper.updateById(entity);
        refreshWordBank();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        sensitiveWordMapper.deleteById(id);
        refreshWordBank();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            sensitiveWordMapper.deleteById(id);
        }
        refreshWordBank();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchSetEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            SensitiveWord entity = sensitiveWordMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "敏感词不存在: " + id);
            }
            entity.setEnabled(enabled);
            entity.setUpdateTime(LocalDateTime.now());
            sensitiveWordMapper.updateById(entity);
        }
        refreshWordBank();
    }

    public int getWordCount() {
        return normalizedDbWords().size();
    }

    private SensitiveWordBs engine() {
        SensitiveWordBs bs = engineRef.get();
        if (bs != null) {
            return bs;
        }
        synchronized (engineRef) {
            bs = engineRef.get();
            if (bs == null) {
                refreshWordBank();
                bs = engineRef.get();
            }
            return bs;
        }
    }

    private List<String> normalizedDbWords() {
        return sensitiveWordMapper.selectAllEnabledWords().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeWord)
                .filter(w -> w.length() <= MAX_WORD_LENGTH)
                .distinct()
                .toList();
    }

    private String normalizeWord(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }

    private SensitiveWordBs buildEngine(List<String> dbWords) {
        IWordDeny dbWordDeny = () -> dbWords;
        return SensitiveWordBs.newInstance()
                .wordAllow(WordAllows.defaults())
                .wordDeny(WordDenys.chains(WordDenys.defaults(), dbWordDeny))
                .init();
    }

    private void enrichNames(List<SensitiveWord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(
                records.stream().map(SensitiveWord::getCreatedBy).toList());
        records.forEach(item -> item.setCreatedByName(names.get(item.getCreatedBy())));
    }
}
