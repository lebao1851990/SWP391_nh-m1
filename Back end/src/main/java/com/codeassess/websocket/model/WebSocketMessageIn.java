package com.codeassess.websocket.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessageIn {
    private String action;   // SUBSCRIBE, UNSUBSCRIBE, PING
    private String channel;  // e.g. "submission:123", "user:456", "exam:789"
}

