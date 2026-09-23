package com.codeassess.classes.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassDto {
    private String name;
    private String code;
    private String semester;
    private String teacherId;
}
