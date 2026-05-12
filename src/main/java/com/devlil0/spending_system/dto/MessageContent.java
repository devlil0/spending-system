package com.devlil0.spending_system.dto;

import lombok.Data;

@Data
public class MessageContent {
    private String conversation;
    private ExtendedMessage extendedTextMessage;

    public String getText() {
        if (conversation != null) return conversation;
        if (extendedTextMessage != null) return extendedTextMessage.getText();
        return null;
    }
}
