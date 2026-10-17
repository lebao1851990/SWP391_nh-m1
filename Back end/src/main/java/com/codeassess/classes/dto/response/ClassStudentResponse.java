package com.codeassess.classes.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassStudentResponse {
    private String id;
    private String studentId;
    private String email;
    private String fullName;
}
