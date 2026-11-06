package com.codeassess.common.exception;

import com.codeassess.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Handles MaxUploadSizeExceededException with HTTP 413 and default configured message")
    void handleMaxUploadSizeExceededException_defaultLimit_returns413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceededException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(response.getBody().getMessage()).contains("vượt quá giới hạn tối đa cho phép (10MB)");
    }

    @Test
    @DisplayName("Handles MaxUploadSizeExceededException dynamically when max-file-size is overridden")
    void handleMaxUploadSizeExceededException_customLimit_returnsDynamicMessage() {
        GlobalExceptionHandler customHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(customHandler, "maxFileSize", "25MB");
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(25 * 1024 * 1024);

        ResponseEntity<ErrorResponse> response = customHandler.handleMaxUploadSizeExceededException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(response.getBody().getMessage()).isEqualTo("Dung lượng file tải lên vượt quá giới hạn tối đa cho phép (25MB).");
    }
}
