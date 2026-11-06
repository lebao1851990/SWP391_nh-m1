package com.codeassess.websocket.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessageOut {
    private String type;         // CONNECTED, SUBSCRIBED, UNSUBSCRIBED, ERROR, EVENT, PONG
    private String code;         // Error code: UNAUTHORIZED, FORBIDDEN, BAD_REQUEST
    private String message;
    private String channel;
    private String userId;
    private String role;
    private WebSocketEvent event;
}

