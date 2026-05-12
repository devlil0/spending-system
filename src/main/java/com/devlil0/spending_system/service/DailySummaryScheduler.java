package com.devlil0.spending_system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class DailySummaryScheduler {

    private final SpendingService spendingService;
    private final WhatsappSendMsgService whatsappSendMsgService;

    @Value("${bot.allowed-jids:}")
    private String allowedJids;

    @Value("${bot.daily-summary-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${bot.daily-summary-cron}", zone = "America/Sao_Paulo")
    public void sendDailySummaries() {
        if (!enabled || allowedJids == null || allowedJids.isBlank()) {
            return;
        }

        allowedJids.lines()
                .flatMap(line -> Arrays.stream(line.split("[,;|]")))
                .map(String::trim)
                .filter(jid -> !jid.isBlank())
                .forEach(jid -> {
                    String summary = spendingService.buildDailySummary(jid.toLowerCase());
                    if (!summary.isBlank()) {
                        try {
                            whatsappSendMsgService.sendText(jid, summary);
                        } catch (RuntimeException ignored) {
                        }
                    }
                });
    }

}
