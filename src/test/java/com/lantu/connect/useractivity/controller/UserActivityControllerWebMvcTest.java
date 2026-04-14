package com.lantu.connect.useractivity.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.useractivity.dto.AuthorizedSkillVO;
import com.lantu.connect.useractivity.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.List;

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
