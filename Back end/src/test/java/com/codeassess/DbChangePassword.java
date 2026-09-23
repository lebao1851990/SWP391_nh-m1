package com.codeassess;

import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class DbChangePassword {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void changePassword() {
        System.out.println("====== CHANGING PASSWORD ======");
        try {
            User user = userRepository.findByEmail("stua@fpt.edu.vn").orElseThrow();
            user.setPasswordHash(passwordEncoder.encode("password123"));
            userRepository.save(user);
            System.out.println("====== CHANGED PASSWORD SUCCESS ======");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
