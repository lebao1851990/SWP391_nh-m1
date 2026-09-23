package com.codeassess.submission.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubmissionCreatedEvent extends ApplicationEvent {

    private final String examId;
    private final String submissionId;

    public SubmissionCreatedEvent(Object source, String examId, String submissionId) {
        super(source);
        this.examId = examId;
        this.submissionId = submissionId;
    }
}
