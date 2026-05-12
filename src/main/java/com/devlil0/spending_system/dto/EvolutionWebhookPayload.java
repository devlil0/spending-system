package com.devlil0.spending_system.dto;

import lombok.Data;

@Data
public class EvolutionWebhookPayload {

    private String event;
    private String instance;
    private String sender;
    private MessageData data;
}
