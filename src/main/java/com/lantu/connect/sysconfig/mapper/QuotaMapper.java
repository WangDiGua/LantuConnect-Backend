package com.lantu.connect.sysconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.sysconfig.entity.Quota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaMapper extends BaseMapper<Quota> {

    @Update("UPDATE t_quota SET daily_used = 0")
    int resetDailyUsed();

    @Update("UPDATE t_quota SET monthly_used = 0")
    int resetMonthlyUsed();

    @Update("UPDATE t_quota SET daily_used = COALESCE(daily_used, 0) + #{tokens} "
            + "WHERE target_type = 'user' AND target_id = #{userId} AND enabled = 1 "
            + "AND resource_category = #{resourceCategory}")
    int incrementDailyUsed(@Param("userId") Long userId, @Param("tokens") int tokens,
            @Param("resourceCategory") String resourceCategory);

    @Update("UPDATE t_quota SET daily_used = COALESCE(daily_used, 0) + #{tokens} "
            + "WHERE target_type = 'user' AND target_id = #{userId} AND enabled = 1 "
            + "AND resource_category = #{resourceCategory} "
            + "AND (daily_limit IS NULL OR daily_limit <= 0 OR COALESCE(daily_used, 0) + #{tokens} <= daily_limit)")
    int incrementDailyUsedIfWithinLimit(@Param("userId") Long userId, @Param("tokens") int tokens,
            @Param("resourceCategory") String resourceCategory);

    @Update("UPDATE t_quota SET monthly_used = COALESCE(monthly_used, 0) + #{tokens} "
            + "WHERE target_type = 'user' AND target_id = #{userId} AND enabled = 1 "
            + "AND resource_category = #{resourceCategory}")
    int incrementMonthlyUsed(@Param("userId") Long userId, @Param("tokens") int tokens,
            @Param("resourceCategory") String resourceCategory);

    @Update("UPDATE t_quota SET monthly_used = COALESCE(monthly_used, 0) + #{tokens} "
            + "WHERE target_type = 'user' AND target_id = #{userId} AND enabled = 1 "
            + "AND resource_category = #{resourceCategory} "
            + "AND (monthly_limit IS NULL OR monthly_limit <= 0 OR COALESCE(monthly_used, 0) + #{tokens} <= monthly_limit)")
    int incrementMonthlyUsedIfWithinLimit(@Param("userId") Long userId, @Param("tokens") int tokens,
            @Param("resourceCategory") String resourceCategory);
}
