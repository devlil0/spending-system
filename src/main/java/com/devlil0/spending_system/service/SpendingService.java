package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.BotSessionEntity;
import com.devlil0.spending_system.model.SpendingEntity;
import com.devlil0.spending_system.parser.MessageParser;
import com.devlil0.spending_system.repository.BotSessionRepository;
import com.devlil0.spending_system.repository.SpendingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private static final Pattern REMOVE_PATTERN =
            Pattern.compile("^remover\\s+\"?(.+?)\"?$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter SUMMARY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SpendingRepository spendingRepository;
    private final BotSessionRepository botSessionRepository;
    private final MessageParser messageParser;

    @Transactional
    public String processMessage(String jid, String phone, String text) {
        var session = botSessionRepository.findByPhone(phone).orElse(null);
        if (session == null) {
            botSessionRepository.save(BotSessionEntity.builder()
                    .jid(jid)
                    .phone(phone)
                    .build());
            return "Digite o seu nome:";
        }

        if (session.getName() == null || session.getName().isBlank()) {
            String name = normalizeSessionName(text);
            session.setName(name);
            botSessionRepository.save(session);
            return String.format("Olá %s", name);
        }

        String sessionName = session.getName();
        if (text.equalsIgnoreCase("/resumo")) {
            return buildSummary(jid, sessionName);
        }

        if (text.equalsIgnoreCase("remover todos")) {
            return removeAllSpendings(jid);
        }

        Matcher removeMatcher = REMOVE_PATTERN.matcher(text.trim());
        if (removeMatcher.matches()) {
            return removeSpending(jid, removeMatcher.group(1).trim());
        }

        return messageParser.parse(text)
                .map(req -> saveSpending(jid, phone, req))
                .orElse("Não entendi. Envie no formato: *Descrição Valor* (ex: Pizza 50) ou *Descrição Valor Categoria Data* (ex: Pizza 50 Alimentacao 12/05)");
    }

    private String normalizeSessionName(String text) {
        String name = text.trim();
        if (name.toLowerCase(Locale.ROOT).startsWith("digite o seu nome:")) {
            name = name.substring(name.indexOf(":") + 1).trim();
        }

        if (name.isBlank()) {
            return "SEM NOME";
        }

        return name;
    }

    private String saveSpending(String jid, String phone, com.devlil0.spending_system.dto.SpendingRequest req) {
        var entity = SpendingEntity.builder()
                .jid(jid)
                .phone(phone)
                .description(req.description().toUpperCase(Locale.ROOT))
                .amount(req.amount())
                .category(req.category().toUpperCase(Locale.ROOT))
                .createdAt(req.date())
                .build();

        spendingRepository.save(entity);

        return String.format("Gasto registrado!\n%s\nR$ %.2f\n%s\n%s",
                req.description(), req.amount(), req.category(), req.date().format(SUMMARY_DATE_FORMATTER));
    }

    private String buildSummary(String jid, String sessionName) {
        var gastos = spendingRepository.findByJidOrderByCreatedAtAsc(jid);

        if (gastos.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*TOTAL DE GASTOS %s:* \uD83D\uDCC8\n", sessionName.toUpperCase(Locale.ROOT)));
        summary.append("NOME | VALOR | CATEGORIA | DATA\n");

        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("%d. %s | R$ %.2f | %s | %s\n",

                    i + 1,
                    gasto.getDescription(),
                    gasto.getAmount(),
                    gasto.getCategory(),
                    gasto.getCreatedAt().format(SUMMARY_DATE_FORMATTER)));

            if (i < gastos.size() - 1) {
                summary.append("\n");
            }
        }
        summary.append(String.format("TOTAL: R$ %.2f\n", total));
        summary.append(String.format("ITEMS: %d\n", gastos.size()));
        return summary.toString();

    }

    private String removeSpending(String jid, String description) {
        var gastos = spendingRepository.findByJidAndDescriptionIgnoreCase(jid, description);

        if (gastos.isEmpty()) {
            return String.format("NENHUM GASTO COM O NOME %s", description);
        }

        spendingRepository.deleteAll(gastos);

        return String.format("GASTO REMOVIDO!");
    }

    private String removeAllSpendings(String jid) {
        long count = spendingRepository.findByJid(jid).size();

        if (count == 0) {
            return "NENHUM GASTO REGISTRADO PARA SER REMOVIDO!.";
        }

        spendingRepository.deleteByJid(jid);

        return String.format("TODOS OS GASTOS FORAM REMOVIDOS!");
    }
}
