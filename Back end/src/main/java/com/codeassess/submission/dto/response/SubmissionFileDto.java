package com.codeassess.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionFileDto {
    private String filename;
    private String relativePath;
    private long sizeBytes;
    private String content;
}
