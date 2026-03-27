package com.lantu.connect.useractivity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 用户活动 UserStatsVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsVO {

    private Map<String, Object> counters;

    private Map<String, Object> trends;
}
