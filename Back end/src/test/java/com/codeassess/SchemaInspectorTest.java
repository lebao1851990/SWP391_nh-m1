package com.codeassess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class SchemaInspectorTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void printSchema() {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("SHOW CREATE TABLE plagiarism_appeals");
        for (Map<String, Object> row : result) {
            System.out.println("SCHEMA_OUTPUT_START");
            System.out.println(row.get("Create Table"));
            System.out.println("SCHEMA_OUTPUT_END");
        }
    }
}
