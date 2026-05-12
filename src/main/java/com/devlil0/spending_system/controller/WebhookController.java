package com.devlil0.spending_system.controller;

import com.devlil0.spending_system.dto.EvolutionWebhookPayload;
import com.devlil0.spending_system.dto.MessageData;
import com.devlil0.spending_system.service.SpendingService;
import com.devlil0.spending_system.service.WhatsappSendMsgService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${bot.allowed-jids:}")
    private String allowedJids;

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

        if (!isAllowedJid(remoteJid)) {
            return ResponseEntity.ok().build();
        }

        String senderJid = resolveSenderJid(payload, remoteJid);
        String phone = extractPhone(senderJid);

        if (isBotReply(text)) {
            return ResponseEntity.ok().build();
        }

        String replyTarget = resolveReplyTarget(remoteJid, phone);
        String reply = spendingService.processMessage(normalizeJid(remoteJid), text.trim());
        try {
            whatsappSendMsgService.sendText(replyTarget, reply);
        } catch (RuntimeException ex) {
            log.warn("Failed to send WhatsApp reply to {}", replyTarget, ex);
        }

        return ResponseEntity.ok().build();
    }

    private String extractPhone(String remoteJid) {
        return remoteJid.split("@")[0];
    }

    private boolean isAllowedJid(String remoteJid) {
        String normalizedRemoteJid = normalizeJid(remoteJid);
        if (allowedJids == null || allowedJids.isBlank()) {
            return true;
        }

        return allowedJids.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split("[,;|]")))
                .map(this::normalizeJid)
                .filter(allowedJid -> !allowedJid.isBlank())
                .anyMatch(normalizedRemoteJid::equals);
    }

    private String normalizeJid(String jid) {
        if (jid == null) {
            return "";
        }

        return jid.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }

        return phone.replaceAll("\\D", "");
    }

    private String resolveSenderJid(EvolutionWebhookPayload payload, String remoteJid) {
        if (remoteJid.endsWith("@g.us") && payload.getSender() != null && !payload.getSender().isBlank()) {
            return payload.getSender();
        }

        return remoteJid;
    }

    private String resolveReplyTarget(String remoteJid, String phone) {
        if (remoteJid.endsWith("@g.us")) {
            return remoteJid;
        }

        return phone;
    }

    private boolean isBotReply(String text) {
        return text.startsWith("Gasto registrado!")
                || text.startsWith("Digite o seu nome:")
                || text.startsWith("Olá ")
                || text.startsWith("Não entendi.")
                || text.startsWith("Nenhum gasto registrado")
                || text.startsWith("Nenhum gasto encontrado")
                || text.startsWith("Nenhum gasto registrado para remover")
                || text.startsWith("Gasto removido!")
                || text.startsWith("Todos os gastos foram removidos!")
                || text.startsWith("📊");
    }

}
