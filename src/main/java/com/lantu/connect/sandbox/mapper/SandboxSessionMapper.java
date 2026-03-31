package com.lantu.connect.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.sandbox.entity.SandboxSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SandboxSessionMapper extends BaseMapper<SandboxSession> {

    @Update("UPDATE t_sandbox_session " +
            "SET used_calls = COALESCE(used_calls,0) + 1, last_invoke_at = NOW(), update_time = NOW() " +
            "WHERE id = #{sessionId} AND status = 'active' " +
            "AND (max_calls IS NULL OR max_calls <= 0 OR COALESCE(used_calls,0) < max_calls)")
    int incrementUsedCallsIfAllowed(@Param("sessionId") Long sessionId);

    @Update("UPDATE t_sandbox_session " +
            "SET used_calls = GREATEST(COALESCE(used_calls,0) - 1, 0), update_time = NOW() " +
            "WHERE id = #{sessionId}")
    int rollbackUsedCalls(@Param("sessionId") Long sessionId);
}
