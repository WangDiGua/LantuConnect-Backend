package com.lantu.connect.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserDisplayNameResolver {

    private final UserMapper userMapper;

    public Map<Long, String> resolveDisplayNames(Collection<Long> userIds) {
        Set<Long> ids = normalizeIds(userIds);
        if (ids.isEmpty()) {
            // Must not return Map.of() / immutable empty map: callers may still call get(null)
            // (e.g. SensitiveWord rows with created_by NULL).
            return new LinkedHashMap<>();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getUserId, ids)
                        .select(User::getUserId, User::getRealName, User::getUsername))
                .forEach(user -> names.put(user.getUserId(), chooseName(user)));
        ids.forEach(id -> names.putIfAbsent(id, fallbackName(id)));
        return names;
    }

    public String resolveDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        return resolveDisplayNames(Set.of(userId)).get(userId);
    }

    private static Set<Long> normalizeIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> out = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static String chooseName(User user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return fallbackName(user.getUserId());
    }

    private static String fallbackName(Long userId) {
        return "user-" + userId;
    }
}
