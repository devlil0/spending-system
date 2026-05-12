package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.SpendingEntity;
import com.devlil0.spending_system.parser.MessageParser;
import com.devlil0.spending_system.repository.SpendingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private static final Pattern REMOVE_PATTERN =
            Pattern.compile("^remover\\s+\"?(.+?)\"?$", Pattern.CASE_INSENSITIVE);

    private final SpendingRepository spendingRepository;
    private final MessageParser messageParser;

    @Transactional
    public String processMessage(String phone, String text) {
        if (text.equalsIgnoreCase("/resumo")) {
            return buildSummary(phone);
        }

        if (text.equalsIgnoreCase("remover todos")) {
            return removeAllSpendings(phone);
        }

        Matcher removeMatcher = REMOVE_PATTERN.matcher(text.trim());
        if (removeMatcher.matches()) {
            return removeSpending(phone, removeMatcher.group(1).trim());
        }

        return messageParser.parse(text)
                .map(req -> saveSpending(phone, req))
                .orElse("Não entendi. Envie no formato: *Descrição Valor* (ex: Pizza 50) ou remover \"Descrição\"");
    }

    private String saveSpending(String phone, com.devlil0.spending_system.dto.SpendingRequest req) {
        var entity = SpendingEntity.builder()
                .phone(phone)
                .description(req.description())
                .amount(req.amount())
                .category(req.category())
                .build();

        spendingRepository.save(entity);

        return String.format("Gasto registrado!\n%s\nR$ %.2f\n%s",
                req.description(), req.amount(), req.category());
    }

    private String buildSummary(String phone) {
        var gastos = spendingRepository.findByPhoneOrderByCreatedAtAsc(phone);

        if (gastos.isEmpty()) return "Nenhum gasto registrado ainda!";

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder summary = new StringBuilder();
        summary.append("📊 *Resumo de gastos*\n");
        summary.append(String.format("Total: R$ %.2f\n", total));
        summary.append(String.format("Registros: %d\n\n", gastos.size()));

        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("%d. %s - R$ %.2f - %s",
                    i + 1,
                    gasto.getDescription(),
                    gasto.getAmount(),
                    gasto.getCategory()));

            if (i < gastos.size() - 1) {
                summary.append("\n");
            }
        }

        return summary.toString();

    }

    private String removeSpending(String phone, String description) {
        var gastos = spendingRepository.findByPhoneAndDescriptionIgnoreCase(phone, description);

        if (gastos.isEmpty()) {
            return String.format("Nenhum gasto encontrado com o nome: %s", description);
        }

        spendingRepository.deleteAll(gastos);

        return String.format("Gasto removido!\n%s\nRegistros removidos: %d", description, gastos.size());
    }

    private String removeAllSpendings(String phone) {
        long count = spendingRepository.findByPhone(phone).size();

        if (count == 0) {
            return "Nenhum gasto registrado para remover.";
        }

        spendingRepository.deleteByPhone(phone);

        return String.format("Todos os gastos foram removidos!\nRegistros removidos: %d", count);
    }
}
