package com.paperpilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperpilot.entity.AILog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AILogMapper extends BaseMapper<AILog> {

    @Select("SELECT * FROM ai_logs WHERE DATE(created_at) = #{date}")
    List<AILog> selectByDate(@Param("date") String date);
}
