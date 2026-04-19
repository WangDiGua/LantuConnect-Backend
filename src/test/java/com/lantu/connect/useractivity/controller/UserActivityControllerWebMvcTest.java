package com.lantu.connect.useractivity.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.useractivity.dto.AuthorizedSkillVO;
import com.lantu.connect.useractivity.dto.RecentUseVO;
import com.lantu.connect.useractivity.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserActivityControllerWebMvcTest {

    @Mock
    private UserActivityService userActivityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserActivityController(userActivityService)).build();
    }

    @Test
    void authorizedSkillsShouldNotExposePackFormat() throws Exception {
        AuthorizedSkillVO row = AuthorizedSkillVO.builder()
                .id(1L)
                .agentName("ctx-skill")
                .displayName("Context Skill")
                .description("desc")
                .agentType("context_skill")
                .status("published")
                .source("own")
                .build();
        setFieldIfPresent(row, "packFormat", "context_v1");
        when(userActivityService.pageAuthorizedSkills(7L, 1, 20))
                .thenReturn(PageResult.of(List.of(row), 1, 1, 20));

        mockMvc.perform(get("/user/authorized-skills").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].agentType").value("context_skill"))
                .andExpect(jsonPath("$.data.list[0].packFormat").doesNotExist());
    }

    @Test
    void usageRecordsShouldForwardRangeAndKeywordFilters() throws Exception {
        when(userActivityService.pageUsageRecords(7L, 2, 10, "30d", "agent", "dify"))
                .thenReturn(PageResult.empty(2, 10));

        mockMvc.perform(get("/user/usage-records")
                        .header("X-User-Id", "7")
                        .param("page", "2")
                        .param("pageSize", "10")
                        .param("range", "30d")
                        .param("type", "agent")
                        .param("keyword", "dify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10));

        verify(userActivityService).pageUsageRecords(7L, 2, 10, "30d", "agent", "dify");
    }

    @Test
    void recentUseShouldReturnPaginatedPayload() throws Exception {
        RecentUseVO row = RecentUseVO.builder()
                .recordId(101L)
                .targetId(71L)
                .type("agent")
                .targetCode("dify-course-agent")
                .targetName("dify取名助手")
                .action("invoke")
                .status("success")
                .latencyMs(28053)
                .createTime(LocalDateTime.of(2026, 4, 19, 16, 38, 10))
                .lastUsedTime(LocalDateTime.of(2026, 4, 19, 16, 38, 10))
                .build();
        when(userActivityService.pageRecentUse(7L, 1, 20, "agent"))
                .thenReturn(PageResult.of(List.of(row), 1, 1, 20));

        mockMvc.perform(get("/user/recent-use")
                        .header("X-User-Id", "7")
                        .param("type", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].targetId").value(71))
                .andExpect(jsonPath("$.data.list[0].targetCode").value("dify-course-agent"))
                .andExpect(jsonPath("$.data.list[0].targetName").value("dify取名助手"));

        verify(userActivityService).pageRecentUse(7L, 1, 20, "agent");
    }

    private static void setFieldIfPresent(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException ignored) {
            // field already removed
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
