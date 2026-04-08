package com.lantu.connect.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserDisplayNameResolver#resolveDisplayNames} must tolerate {@code get(null)} on the returned map
 * when all input ids were null (e.g. listing rows with {@code created_by} NULL).
 */
class UserDisplayNameResolverTest {

    @Test
    void emptyAfterNormalizingAllNullIds_returnsMapSafeForNullKeyLookup() {
        UserDisplayNameResolver resolver = new UserDisplayNameResolver(null);

        Map<Long, String> names = resolver.resolveDisplayNames(Arrays.asList(null, null));

        assertTrue(names.isEmpty());
        assertDoesNotThrow(() -> names.get(null));
        assertNull(names.get(null));
    }
}
