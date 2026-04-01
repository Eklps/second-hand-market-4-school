package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JmeterTokenGeneratorTest {

    @Resource
    private IUserService userService;

    @Test
    public void generateTokens() throws Exception {
        int generateCount = 1000;
        
        // 查找是否已经有足够的用户，没有则插入
        long count = userService.count();
        List<User> users;
        if (count < generateCount) {
            users = new ArrayList<>(generateCount);
            for (int i = 0; i < generateCount; i++) {
                User user = new User();
                user.setPhone(String.format("138%08d", i)); // 生成138开头的手机号
                user.setNickName("jmeter_user_" + i);
                users.add(user);
            }
            userService.saveBatch(users);
        } else {
            // 取出 1000 个用户
            users = userService.list(new QueryWrapper<User>().last("limit " + generateCount));
        }

        // 为每个用户生成 Token 并写入文件
        String outputFilePath = System.getProperty("user.dir") + "/tokens.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (User user : users) {
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((k, v) -> v != null ? v.toString() : null));
                
                String token = JwtUtils.createToken(userMap);
                writer.write(token);
                writer.newLine();
            }
        }
        System.out.println("========== 1000 JWT Tokens generated successfully ==========");
        System.out.println("Output file: " + outputFilePath);
    }
}
