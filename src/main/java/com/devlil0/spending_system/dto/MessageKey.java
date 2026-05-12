package com.devlil0.spending_system.dto;

import lombok.Data;

@Data
public class MessageKey {

    private String remoteJid;
    private String participant;
    private boolean fromMe;

}
