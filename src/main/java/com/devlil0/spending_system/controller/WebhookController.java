package com.devlil0.spending_system.controller;

import com.devlil0.spending_system.dto.EvolutionWebhookPayload;
import com.devlil0.spending_system.dto.MessageData;
import com.devlil0.spending_system.service.SpendingService;
import com.devlil0.spending_system.service.WhatsappSendMsgService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final SpendingService spendingService;
    private final WhatsappSendMsgService whatsappSendMsgService;

    @PostMapping({"/whatsapp", "/whatsapp/messages-upsert"})
    public ResponseEntity<Void> receive(@RequestBody EvolutionWebhookPayload payload) {
        if (payload == null || !"messages.upsert".equals(payload.getEvent())) {
            return ResponseEntity.ok().build();
        }

        MessageData data = payload.getData();
        if (data == null || data.getKey() == null || data.getMessage() == null) {
            return ResponseEntity.ok().build();
        }

        String remoteJid = data.getKey().getRemoteJid();
        String text = data.getMessage().getText();
        if (remoteJid == null || text == null || text.isBlank()) {
            return ResponseEntity.ok().build();
        }

        if (isBotReply(text)) {
            return ResponseEntity.ok().build();
        }

        String phone = extractPhone(resolveSenderJid(payload, remoteJid));
        String reply = spendingService.processMessage(phone, text.trim());
        try {
            whatsappSendMsgService.sendText(phone, reply);
        } catch (RuntimeException ex) {
            log.warn("Failed to send WhatsApp reply to {}", phone, ex);
        }

        return ResponseEntity.ok().build();
    }

    private String extractPhone(String remoteJid) {
        return remoteJid.split("@")[0];
    }

    private String resolveSenderJid(EvolutionWebhookPayload payload, String remoteJid) {
        if (remoteJid.endsWith("@g.us") && payload.getSender() != null && !payload.getSender().isBlank()) {
            return payload.getSender();
        }

        return remoteJid;
    }

    private boolean isBotReply(String text) {
        return text.startsWith("Gasto registrado!")
                || text.startsWith("Não entendi.")
                || text.startsWith("Nenhum gasto registrado")
                || text.startsWith("📊");
    }

}
