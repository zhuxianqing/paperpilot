package com.paperpilot.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperpilot.entity.User;
import com.paperpilot.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        boolean isAdmin = "admin@paperpilot.com".equals(user.getEmail());

        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()),
                user.getPasswordHash(),
                user.getStatus() == 1,
                true, true, true,
                isAdmin
                        ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    public UserDetails loadUserByEmail(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }

        boolean isAdmin = "admin@paperpilot.com".equals(user.getEmail());

        return new org.springframework.security.core.userdetails.User(
                String.valueOf(user.getId()),
                user.getPasswordHash(),
                user.getStatus() == 1,
                true, true, true,
                isAdmin
                        ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
