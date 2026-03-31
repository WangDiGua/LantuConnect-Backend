package com.lantu.connect.common.mybatis;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 统一 SQL 审计日志拦截器：
 * - 输出 mapper 类/方法
 * - 输出完整 SQL + 参数
 * - 输出执行耗时与结果行数
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class SqlAuditInterceptor implements Interceptor {
    private static final Set<String> SENSITIVE_PARAM_KEYS = Set.of(
            "password", "pwd", "secret", "token", "authorization", "apiKey", "key", "credential", "phone", "email");

    /** 超过该耗时（毫秒）时在 WARN 打一行摘要，避免高流量下 INFO 刷盘；明细需打开 DEBUG。 */
    private static final long SLOW_SQL_WARN_MS = 1_000L;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql = extractBoundSql(ms, args, parameterObject);

        long startNs = System.nanoTime();
        Object result = null;
        Throwable thrown = null;
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long costMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
            String mapperMethod = ms.getId();
            String caller = resolveBusinessCaller();
            String sql = normalizeSql(boundSql.getSql());
            String params = buildParams(boundSql, parameterObject);
            String rows = resolveRowCount(result);

            if (thrown == null) {
                if (log.isDebugEnabled()) {
                    log.debug("mapper={} caller={} costMs={} rows={} sql=\"{}\" params=[{}]",
                            mapperMethod, caller, costMs, rows, sql, params);
                } else if (costMs >= SLOW_SQL_WARN_MS) {
                    log.warn("slowSql mapper={} caller={} costMs={} rows={} sql=\"{}\"",
                            mapperMethod, caller, costMs, rows, sql);
                }
            } else {
                log.error("mapper={} caller={} costMs={} rows={} sql=\"{}\" error={}",
                        mapperMethod, caller, costMs, rows, sql, thrown.getMessage());
            }
        }
    }

    private static BoundSql extractBoundSql(MappedStatement ms, Object[] args, Object parameterObject) {
        if (args.length == 6 && args[5] instanceof BoundSql bs) {
            return bs;
        }
        return ms.getBoundSql(parameterObject);
    }

    private static String resolveRowCount(Object result) {
        if (result == null) {
            return "0";
        }
        if (result instanceof List<?> list) {
            return String.valueOf(list.size());
        }
        if (result instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return "1";
    }

    private static String normalizeSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static String buildParams(BoundSql boundSql, Object parameterObject) {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            return "";
        }
        MetaObject metaObject = parameterObject == null ? null : SystemMetaObject.forObject(parameterObject);
        List<String> items = new ArrayList<>();
        for (ParameterMapping mapping : mappings) {
            String property = mapping.getProperty();
            Object value;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (metaObject != null && metaObject.hasGetter(property)) {
                value = metaObject.getValue(property);
            } else if (parameterObject != null && isSimpleType(parameterObject.getClass())) {
                value = parameterObject;
            } else {
                value = null;
            }
            items.add(property + "=" + stringifyMaskingSensitive(property, value));
        }
        return String.join(", ", items);
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || Number.class.isAssignableFrom(clazz)
                || CharSequence.class.isAssignableFrom(clazz)
                || Boolean.class.isAssignableFrom(clazz)
                || java.util.Date.class.isAssignableFrom(clazz)
                || java.time.temporal.Temporal.class.isAssignableFrom(clazz)
                || Enum.class.isAssignableFrom(clazz);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            if (value instanceof Object[] arr) {
                StringJoiner joiner = new StringJoiner(", ", "[", "]");
                for (Object v : arr) {
                    joiner.add(Objects.toString(v));
                }
                return joiner.toString();
            }
            return "[primitive-array]";
        }
        return Objects.toString(value);
    }

    private static String stringifyMaskingSensitive(String property, Object value) {
        if (isSensitiveProperty(property)) {
            return "***";
        }
        return stringify(value);
    }

    private static boolean isSensitiveProperty(String property) {
        if (!StringUtils.hasText(property)) {
            return false;
        }
        String normalized = property.toLowerCase(Locale.ROOT);
        for (String key : SENSITIVE_PARAM_KEYS) {
            if (normalized.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String resolveBusinessCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String cls = frame.getClassName();
            if (!cls.startsWith("com.lantu.connect")) {
                continue;
            }
            String lower = cls.toLowerCase(Locale.ROOT);
            if (lower.contains("sqlauditinterceptor") || lower.contains("org.apache.ibatis")) {
                continue;
            }
            return cls + "#" + frame.getMethodName();
        }
        return "unknown";
    }
}
