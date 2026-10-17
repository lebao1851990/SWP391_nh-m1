package com.codeassess.exam.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSummaryDto {
    private String id;
    private String name;
    private String code;
    private String semester;
}
