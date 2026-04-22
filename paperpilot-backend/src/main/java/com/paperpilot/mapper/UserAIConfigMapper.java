package com.paperpilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperpilot.entity.UserAIConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserAIConfigMapper extends BaseMapper<UserAIConfig> {

    @Update("UPDATE user_ai_configs SET is_active = 0 WHERE user_id = #{userId} AND provider != #{excludeProvider}")
    void updateDefaultConfig(@Param("userId") Long userId, @Param("excludeProvider") String excludeProvider);
}
