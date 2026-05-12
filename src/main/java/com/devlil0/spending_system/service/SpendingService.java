package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.SpendingEntity;
import com.devlil0.spending_system.parser.MessageParser;
import com.devlil0.spending_system.repository.SpendingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private final SpendingRepository spendingRepository;
    private final MessageParser messageParser;

    public String processMessage(String phone, String text) {
        if (text.equalsIgnoreCase("/resumo")) {
            return buildSummary(phone);
        }

        return messageParser.parse(text)
                .map(req -> saveSpending(phone, req))
                .orElse("Não entendi. Envie no formato: *Descrição Valor* (ex: Pizza 50)");
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
        var gastos = spendingRepository.findByPhone(phone);

        if (gastos.isEmpty()) return "Nenhum gasto registrado ainda!";

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return String.format("📊 *Resumo de gastos*\nTotal: R$ %.2f\nRegistros: %d",
                total, gastos.size());

    }
}
