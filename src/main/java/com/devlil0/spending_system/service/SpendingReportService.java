package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.SpendingEntity;
import com.devlil0.spending_system.repository.SpendingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpendingReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SpendingRepository spendingRepository;
    private final SpendingPeriodParser periodParser;
    private final CategoryNormalizer categoryNormalizer;

    public String buildHelp() {
        return """
                *COMANDOS*
                gastos
                gastos hoje
                gastos semana
                gastos mes
                gastos 01/05 15/05
                categorias
                categoria mercado
                categoria mercado mes
                categoria mercado 01/05 15/05
                maiores
                maiores 10
                editar <id ou nome> <campo> <novo valor>
                editar <id> valor 45,90
                editar Pizza valor 45,90
                editar <id> categoria Mercado
                editar <id> data 12/05
                remover <id>
                remover todos 
                alterar nome <nome desejado>
                """.trim();
    }

    public String buildDailySummary(String jid) {
        var today = LocalDate.now();
        var period = new SpendingPeriod(today.atStartOfDay(), today.atTime(LocalTime.MAX), "HOJE");
        var spendings = periodParser.filter(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period);
        if (spendings.isEmpty()) {
            return "";
        }

        return String.format("*RESUMO DE HOJE*\nTOTAL: R$ %.2f\nITEMS: %d",
                calculateTotal(spendings),
                spendings.size());
    }

    public String buildSummary(String jid, String sessionName, SpendingPeriod period) {
        var spendings = periodParser.filter(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period);
        if (spendings.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*GASTOS DE %s \uD83D\uDCC8*\n", sessionName.toUpperCase()));
        if (period != null) {
            summary.append(String.format("PERIODO: %s\n", period.label()));
        }
        summary.append("\nO NÚMERO AO LADO DO NOME É O *ID* DO *GASTO*.\n");
        summary.append("\n");

        for (int i = 0; i < spendings.size(); i++) {
            SpendingEntity spending = spendings.get(i);
            summary.append(String.format("%s (%d) | R$ %.2f | %s | %s\n",
                    spending.getDescription(),
                    spending.getId(),
                    spending.getAmount(),
                    spending.getCategory(),
                    spending.getCreatedAt().format(DATE_FORMATTER)));

            if (i < spendings.size() - 1) {
                summary.append("\n");
            }
        }

        summary.append(String.format("\nTOTAL: R$ %.2f\n", calculateTotal(spendings)));
        summary.append(String.format("ITEMS: %d", spendings.size()));
        return summary.toString();
    }

    public String buildCategoriesSummary(String jid) {
        var spendings = spendingRepository.findByJidOrderByCreatedAtAsc(jid);
        if (spendings.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        Map<String, BigDecimal> totalsByCategory = spendings.stream()
                .collect(Collectors.groupingBy(
                        SpendingEntity::getCategory,
                        LinkedHashMap::new,
                        Collectors.mapping(SpendingEntity::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        StringBuilder summary = new StringBuilder("*CATEGORIAS*\n");
        totalsByCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> summary.append(String.format("%s: R$ %.2f\n", entry.getKey(), entry.getValue())));

        summary.append(String.format("TOTAL: R$ %.2f", calculateTotal(spendings)));
        return summary.toString();
    }

    public String buildCategoryDetails(String jid, String category, SpendingPeriod period) {
        String displayCategory = category.toUpperCase();
        String comparableCategory = categoryNormalizer.normalize(category);
        var spendings = periodParser.filter(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period).stream()
                .filter(spending -> categoryNormalizer.normalize(spending.getCategory()).equals(comparableCategory))
                .sorted(Comparator.comparing(SpendingEntity::getCreatedAt))
                .toList();

        if (spendings.isEmpty()) {
            return String.format("NENHUM GASTO NA CATEGORIA %s", displayCategory);
        }

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*GASTOS (%s):*\n\n", spendings.getFirst().getCategory()));
        if (period != null) {
            summary.append(String.format("PERIODO: %s\n\n", period.label()));
        }
        summary.append("O NÚMERO AO LADO DO NOME É O ID DO GASTO.\n\n");

        for (int i = 0; i < spendings.size(); i++) {
            SpendingEntity spending = spendings.get(i);
            summary.append(String.format("%s (%d) | R$ %.2f | %s\n",
                    spending.getDescription(),
                    spending.getId(),
                    spending.getAmount(),
                    spending.getCreatedAt().format(DATE_FORMATTER)));

            if (i < spendings.size() - 1) {
                summary.append("\n");
            }
        }

        summary.append(String.format("\nTOTAL: R$ %.2f\n", calculateTotal(spendings)));
        summary.append(String.format("ITEMS: %d\n", spendings.size()));
        return summary.toString();
    }

    public String buildBiggestSpendings(String jid, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 20));
        var spendings = spendingRepository.findByJidOrderByCreatedAtAsc(jid).stream()
                .sorted(Comparator.comparing(SpendingEntity::getAmount).reversed())
                .limit(normalizedLimit)
                .toList();

        if (spendings.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        StringBuilder summary = new StringBuilder(String.format("*MAIORES GASTOS (%d)*\n\n", normalizedLimit));
        summary.append("O NÚMERO AO LADO DO NOME É O ID DO GASTO.\n\n");
        for (int i = 0; i < spendings.size(); i++) {
            SpendingEntity spending = spendings.get(i);
            summary.append(String.format("%s (%d) | R$ %.2f | %s | %s\n",
                    spending.getDescription(),
                    spending.getId(),
                    spending.getAmount(),
                    spending.getCategory(),
                    spending.getCreatedAt().format(DATE_FORMATTER)));

            if (i < spendings.size() - 1) {
                summary.append("\n");
            }
        }

        return summary.toString();
    }

    private BigDecimal calculateTotal(java.util.List<SpendingEntity> spendings) {
        return spendings.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
