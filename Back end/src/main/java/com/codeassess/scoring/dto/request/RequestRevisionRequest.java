package com.codeassess.scoring.dto.request;

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
public class RequestRevisionRequest {

    @NotBlank(message = "Lý do yêu cầu làm lại/chỉnh sửa không được để trống")
    @Size(min = 10, max = 1000, message = "Lý do yêu cầu làm lại/chỉnh sửa phải từ 10 đến 1000 ký tự")
    private String reason;
}

