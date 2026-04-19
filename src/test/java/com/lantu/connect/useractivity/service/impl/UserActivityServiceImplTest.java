package com.lantu.connect.useractivity.service.impl;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.useractivity.dto.RecentUseVO;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.mapper.FavoriteMapper;
import com.lantu.connect.useractivity.mapper.UsageRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceImplTest {

    @Mock
    private UsageRecordMapper usageRecordMapper;

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserActivityServiceImpl service;

    @Test
    void pageRecentUseShouldCollapseDuplicateResourceRecordsAndPaginate() {
        when(usageRecordMapper.selectList(any())).thenReturn(List.of(
                usageRecord(101L, "agent", 71L, "dify-course-agent", "dify取名助手", "invoke", 28053, "success",
                        LocalDateTime.of(2026, 4, 19, 16, 38, 10)),
                usageRecord(100L, "agent", 71L, "dify-course-agent", "dify取名助手", "invoke", 54442, "success",
                        LocalDateTime.of(2026, 4, 19, 16, 37, 30)),
                usageRecord(99L, "mcp", 64L, "worryzyy", "HowToCook-MCP Server", "invoke", 268, "success",
                        LocalDateTime.of(2026, 4, 18, 13, 19, 12)),
                usageRecord(98L, "skill", 68L, "msg_skill_01", "消息技能", "invoke", 603, "success",
                        LocalDateTime.of(2026, 4, 18, 9, 10, 1))));

        PageResult<RecentUseVO> firstPage = service.pageRecentUse(1L, 1, 2, null);
        PageResult<RecentUseVO> secondPage = service.pageRecentUse(1L, 2, 2, null);

        assertEquals(3, firstPage.getTotal());
        assertEquals(2, firstPage.getList().size());
        assertEquals(71L, firstPage.getList().get(0).getTargetId());
        assertEquals(64L, firstPage.getList().get(1).getTargetId());

        assertEquals(3, secondPage.getTotal());
        assertEquals(1, secondPage.getList().size());
        assertEquals(68L, secondPage.getList().get(0).getTargetId());
    }

    private static UsageRecord usageRecord(Long id,
                                           String type,
                                           Long resourceId,
                                           String agentName,
                                           String displayName,
                                           String action,
                                           Integer latencyMs,
                                           String status,
                                           LocalDateTime createTime) {
        UsageRecord record = new UsageRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setType(type);
        record.setResourceId(resourceId);
        record.setAgentName(agentName);
        record.setDisplayName(displayName);
        record.setAction(action);
        record.setLatencyMs(latencyMs);
        record.setStatus(status);
        record.setCreateTime(createTime);
        return record;
    }
}
