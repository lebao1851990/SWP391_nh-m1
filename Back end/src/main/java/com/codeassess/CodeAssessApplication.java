package com.codeassess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@EnableAsync
public class CodeAssessApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(CodeAssessApplication.class, args);
    }

    private static void loadDotenvIfPresent() {
        File[] candidateFiles = new File[] {
                new File(".env"),
                new File("Back end/.env"),
                new File("../.env")
        };

        for (File envFile : candidateFiles) {
            if (envFile.exists() && envFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int sep = line.indexOf('=');
                        if (sep > 0) {
                            String key = line.substring(0, sep).trim();
                            String val = line.substring(sep + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Non-fatal, fallback to standard environment
                }
                break;
            }
        }
    }
}
