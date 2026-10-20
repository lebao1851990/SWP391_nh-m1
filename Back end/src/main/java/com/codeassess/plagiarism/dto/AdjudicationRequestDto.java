package com.codeassess.plagiarism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationRequestDto {

    @NotBlank(message = "Adjudication status is required")
    private String status;

    @Size(max = 2000, message = "Adjudication notes must not exceed 2000 characters")
    private String notes;
}
