package com.lantu.connect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.auth.entity.LoginHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginHistoryMapper extends BaseMapper<LoginHistory> {
}
