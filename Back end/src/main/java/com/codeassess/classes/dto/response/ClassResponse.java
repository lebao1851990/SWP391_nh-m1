package com.codeassess.classes.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassResponse {
    private String id;
    private String name;
    private String code;
    private String semester;
    private String teacherId;
    private LocalDateTime createdAt;
    private Long studentCount;
}
