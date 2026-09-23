package com.codeassess.classes.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ClassResponse {
    private String id;
    private String name;
    private String code;
    private String semester;
    private String teacherId;
    private LocalDateTime createdAt;
}
