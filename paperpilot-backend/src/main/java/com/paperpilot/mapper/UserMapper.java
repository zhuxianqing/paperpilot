package com.paperpilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperpilot.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET quota_balance = quota_balance - #{amount} WHERE id = #{userId} AND quota_balance >= #{amount}")
    int deductQuota(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE users SET quota_balance = quota_balance + #{amount} WHERE id = #{userId}")
    int addQuota(@Param("userId") Long userId, @Param("amount") int amount);
}
