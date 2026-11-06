package com.codeassess.websocket.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketEvent {
    @Builder.Default
    private String eventId = "evt_" + UUID.randomUUID().toString();
    
    @Builder.Default
    private String version = "1.0";
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    private String channel;
    private String event;
    private Map<String, Object> payload;
}

