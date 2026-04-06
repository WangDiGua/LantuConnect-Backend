package com.lantu.connect.common.session;

import com.lantu.connect.common.geo.GeoIpLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录后异步写入会话 meta 中的地理位置，避免阻塞登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionGeoEnrichmentService {

    private final GeoIpLookupService geoIpLookupService;
    private final SessionTrackerService sessionTrackerService;

    @Async
    public void enqueueLocationLookup(String sessionId, String ip) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            String loc = geoIpLookupService.lookup(ip);
            if (StringUtils.hasText(loc)) {
                sessionTrackerService.updateSessionMetaField(sessionId, "location", loc);
            }
        } catch (RuntimeException e) {
            log.debug("Session geo enrichment failed sessionId={}: {}", sessionId, e.getMessage());
        }
    }
}
