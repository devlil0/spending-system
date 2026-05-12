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
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpendingService {

    private static final Pattern REMOVE_PATTERN =
            Pattern.compile("^remover\\s+\"?(.+?)\"?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("^categoria\\s+(.+)$", Pattern.CASE_INSENSITIVE);
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
        if (text.equalsIgnoreCase("gastos")) {
            return buildSummary(jid, sessionName);
        }

        if (text.equalsIgnoreCase("categorias")) {
            return buildCategoriesSummary(jid);
        }

        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(text.trim());
        if (categoryMatcher.matches()) {
            return buildCategoryDetails(jid, categoryMatcher.group(1).trim());
        }

        if (text.equalsIgnoreCase("remover todos")) {
            return removeAllSpendings(jid);
        }

        Matcher removeMatcher = REMOVE_PATTERN.matcher(text.trim());
        if (removeMatcher.matches()) {
            return removeSpending(jid, removeMatcher.group(1).trim());
        }

        if (text.lines().filter(line -> !line.isBlank()).count() > 1) {
            return saveMultipleSpendings(jid, phone, text);
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

    private String saveMultipleSpendings(String jid, String phone, String text) {
        List<SpendingEntity> saved = new ArrayList<>();
        List<String> invalidLines = new ArrayList<>();

        text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> messageParser.parse(line)
                        .ifPresentOrElse(
                                req -> saved.add(saveSpendingEntity(jid, phone, req)),
                                () -> invalidLines.add(line)));

        if (saved.isEmpty()) {
            return "Não entendi nenhum gasto. Envie uma linha por gasto no formato: *Descrição Valor Categoria Data*";
        }

        StringBuilder reply = new StringBuilder();
        reply.append(String.format("%d gastos registrados!", saved.size()));

        BigDecimal total = saved.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        reply.append(String.format("\nTOTAL: R$ %.2f", total));

        if (!invalidLines.isEmpty()) {
            reply.append("\n\nLinhas ignoradas:");
            invalidLines.forEach(line -> reply.append("\n- ").append(line));
        }

        return reply.toString();
    }

    private String saveSpending(String jid, String phone, com.devlil0.spending_system.dto.SpendingRequest req) {
        saveSpendingEntity(jid, phone, req);

        return String.format("Gasto registrado!\n%s\nR$ %.2f\n%s\n%s",
                req.description(), req.amount(), req.category(), req.date().format(SUMMARY_DATE_FORMATTER));
    }

    private SpendingEntity saveSpendingEntity(String jid, String phone, com.devlil0.spending_system.dto.SpendingRequest req) {
        var entity = SpendingEntity.builder()
                .jid(jid)
                .phone(phone)
                .description(req.description().toUpperCase(Locale.ROOT))
                .amount(req.amount())
                .category(req.category().toUpperCase(Locale.ROOT))
                .createdAt(req.date())
                .build();

        return spendingRepository.save(entity);
    }

    private String buildSummary(String jid, String sessionName) {
        var gastos = spendingRepository.findByJidOrderByCreatedAtAsc(jid);

        if (gastos.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*TOTAL DE GASTOS %s:* \uD83D\uDCC8\n", sessionName.toUpperCase(Locale.ROOT)));
        summary.append("\n");

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
        summary.append(String.format("\nTOTAL: R$ %.2f\n", total));
        summary.append(String.format("ITEMS: %d\n", gastos.size()));
        return summary.toString();

    }

    private String buildCategoriesSummary(String jid) {
        var gastos = spendingRepository.findByJidOrderByCreatedAtAsc(jid);

        if (gastos.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        Map<String, BigDecimal> totalsByCategory = gastos.stream()
                .collect(Collectors.groupingBy(
                        SpendingEntity::getCategory,
                        LinkedHashMap::new,
                        Collectors.mapping(SpendingEntity::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        StringBuilder summary = new StringBuilder("*CATEGORIAS*\n");
        totalsByCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> summary.append(String.format("%s: R$ %.2f\n", entry.getKey(), entry.getValue())));

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.append(String.format("TOTAL: R$ %.2f", total));

        return summary.toString();
    }

    private String buildCategoryDetails(String jid, String category) {
        String displayCategory = category.toUpperCase(Locale.ROOT);
        String comparableCategory = normalizeCategory(category);
        var gastos = spendingRepository.findByJidOrderByCreatedAtAsc(jid).stream()
                .filter(gasto -> normalizeCategory(gasto.getCategory()).equals(comparableCategory))
                .sorted(Comparator.comparing(SpendingEntity::getCreatedAt))
                .toList();

        if (gastos.isEmpty()) {
            return String.format("NENHUM GASTO NA CATEGORIA %s", displayCategory);
        }

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*GASTOS (%s):*\n\n", gastos.getFirst().getCategory()));

        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("%d. %s | R$ %.2f | %s\n",
                    i + 1,
                    gasto.getDescription(),
                    gasto.getAmount(),
                    gasto.getCreatedAt().format(SUMMARY_DATE_FORMATTER)));

            if (i < gastos.size() - 1) {
                summary.append("\n");
            }
        }

        summary.append(String.format("\nTOTAL: R$ %.2f\n", total));
        summary.append(String.format("ITEMS: %d\n", gastos.size()));

        return summary.toString();
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }

        String noAccents = Normalizer.normalize(category, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.trim().toUpperCase(Locale.ROOT);
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
