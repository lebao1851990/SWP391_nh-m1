package com.codeassess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeAssessApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAssessApplication.class, args);
    }
}
