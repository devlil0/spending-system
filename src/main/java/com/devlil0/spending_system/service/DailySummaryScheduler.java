package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.BotSessionEntity;
import com.devlil0.spending_system.repository.BotSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailySummaryScheduler {

    private static final String MORNING_REMINDER_MESSAGE = "Bom dia %s, não se esqueça se anotar seus gastos!";

    private final SpendingService spendingService;
    private final BotSessionRepository botSessionRepository;
    private final WhatsappSendMsgService whatsappSendMsgService;

    @Value("${bot.allowed-jids:}")
    private String allowedJids;

    @Value("${bot.daily-summary-enabled:true}")
    private boolean enabled;

    @Value("${bot.morning-reminder-enabled:true}")
    private boolean morningReminderEnabled;

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

    @Scheduled(cron = "${bot.morning-reminder-cron}", zone = "America/Sao_Paulo")
    public void sendMorningReminders() {
        if (!morningReminderEnabled || allowedJids == null || allowedJids.isBlank()) {
            return;
        }

        allowedJids.lines()
                .flatMap(line -> Arrays.stream(line.split("[,;|]")))
                .map(String::trim)
                .filter(jid -> !jid.isBlank())
                .forEach(jid -> findSession(jid.toLowerCase())
                        .map(BotSessionEntity::getName)
                        .filter(name -> !name.isBlank())
                        .map(name -> String.format(MORNING_REMINDER_MESSAGE, name))
                        .ifPresent(message -> {
                            try {
                                whatsappSendMsgService.sendText(jid, message);
                            } catch (RuntimeException ignored) {
                            }
                        }));
    }

    private Optional<BotSessionEntity> findSession(String jid) {
        if (!jid.endsWith("@g.us")) {
            String phone = jid.split("@")[0];
            return botSessionRepository.findByPhone(phone);
        }

        return botSessionRepository.findByJidOrderByIdAsc(jid).stream()
                .filter(session -> session.getName() != null && !session.getName().isBlank())
                .findFirst();
    }

}
