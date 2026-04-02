package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * zip 内技能根目录相对路径（用于子树语义校验）；规范化后不含首尾 {@code /}，不含 {@code ..}。
 */
public final class SkillPackSkillRootPath {

    public static final int MAX_LEN = 512;

    private SkillPackSkillRootPath() {
    }

    /**
     * @return null 表示整包根目录
     */
    public static String normalizeOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        if (!StringUtils.hasText(s)) {
            return null;
        }
        if (s.length() > MAX_LEN) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "skillRoot 过长（上限 " + MAX_LEN + "）");
        }
        List<String> parts = new ArrayList<>();
        for (String part : s.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "skillRoot 不可包含 ..");
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("/", parts);
    }
}
