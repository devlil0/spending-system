package com.devlil0.spending_system.dto;

import lombok.Data;

@Data
public class MessageData {

    private MessageContent message;
    private MessageKey key;
    private String messageType;

}
