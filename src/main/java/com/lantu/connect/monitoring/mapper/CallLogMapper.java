package com.lantu.connect.monitoring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.monitoring.entity.CallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 监控 CallLog 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface CallLogMapper extends BaseMapper<CallLog> {

    @Select("SELECT COUNT(*) FROM t_call_log WHERE DATE(create_time) = CURDATE()")
    Long selectTodayCount();

    @Select("SELECT COALESCE(AVG(latency_ms), 0) FROM t_call_log WHERE DATE(create_time) = CURDATE()")
    Double selectTodayAvgLatencyMs();

    @Select("SELECT COUNT(*) FROM t_call_log WHERE DATE(create_time) = CURDATE() AND status = 'success'")
    Long selectTodaySuccessCount();

    @Select("SELECT COUNT(*) FROM t_call_log WHERE user_id = #{userId} AND create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)")
    Long countCallsLast24Hours(@Param("userId") String userId);
}
