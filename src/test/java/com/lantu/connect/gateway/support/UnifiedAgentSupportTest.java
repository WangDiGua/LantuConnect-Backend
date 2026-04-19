package com.lantu.connect.gateway.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedAgentSupportTest {

    @Test
    void shouldRecognizeUnifiedAgentExposure() {
        assertTrue(UnifiedAgentSupport.isUnifiedAgentExposure("unified_agent"));
        assertTrue(UnifiedAgentSupport.isUnifiedAgentExposure("  UNIFIED_AGENT  "));
        assertFalse(UnifiedAgentSupport.isUnifiedAgentExposure("app"));
        assertFalse(UnifiedAgentSupport.isUnifiedAgentExposure(null));
    }

    @Test
    void shouldIncludePageAppsInsideAgentView() {
        assertTrue(UnifiedAgentSupport.shouldAppearInAgentView("agent", null));
        assertTrue(UnifiedAgentSupport.shouldAppearInAgentView("app", "unified_agent"));
        assertFalse(UnifiedAgentSupport.shouldAppearInAgentView("app", null));
        assertFalse(UnifiedAgentSupport.shouldAppearInAgentView("dataset", "unified_agent"));
    }

    @Test
    void shouldResolveDeliveryModeForUnifiedAgentResources() {
        assertEquals("api", UnifiedAgentSupport.resolveDeliveryMode("agent", null));
        assertEquals("page", UnifiedAgentSupport.resolveDeliveryMode("app", "unified_agent"));
        assertEquals("page", UnifiedAgentSupport.resolveDeliveryMode("app", "  unified_agent "));
        assertEquals("api", UnifiedAgentSupport.resolveDeliveryMode("app", null));
    }

    @Test
    void shouldMatchRequestedTypeAcrossAgentAndAppViews() {
        assertTrue(UnifiedAgentSupport.matchesRequestedType("agent", "agent", null));
        assertTrue(UnifiedAgentSupport.matchesRequestedType("agent", "app", "unified_agent"));
        assertFalse(UnifiedAgentSupport.matchesRequestedType("app", "app", "unified_agent"));
        assertTrue(UnifiedAgentSupport.matchesRequestedType("app", "app", null));
    }

    @Test
    void shouldResolveViewTypeForUnifiedAgentAliases() {
        assertEquals("agent", UnifiedAgentSupport.resolveViewType("agent", "app", "unified_agent"));
        assertEquals("app", UnifiedAgentSupport.resolveViewType("app", "app", "unified_agent"));
        assertEquals("skill", UnifiedAgentSupport.resolveViewType("skill", "skill", null));
    }
}
