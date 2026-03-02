package com.hmdp.service.impl;

import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private IUserService userService;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        // 项目逻辑：通过手机号登录
        User user = userService.query().eq("phone", phone).one();
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        // 返回 Security 标准用户对象（暂时不加角色权限，给予空集合）
        // 注意：这里的密码在实际重构后应该是加密存储的
        return new org.springframework.security.core.userdetails.User(
                phone,
                user.getPassword(),
                Collections.emptyList());
    }
}
