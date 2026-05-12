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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private static final Pattern EDIT_PATTERN =
            Pattern.compile("^editar\\s+(\\d+)\\s+(nome|descricao|descrição|valor|categoria|data)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDIT_BY_NAME_PATTERN =
            Pattern.compile("^editar\\s+(.+?)\\s+(nome|descricao|descrição|valor|categoria|data)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIGGEST_PATTERN =
            Pattern.compile("^maiores(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_RANGE_PATTERN =
            Pattern.compile("^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})$", Pattern.CASE_INSENSITIVE);
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
        if (text.equalsIgnoreCase("ajuda")) {
            return buildHelp();
        }

        if (text.toLowerCase(Locale.ROOT).startsWith("gastos")) {
            return buildSummary(jid, sessionName, resolvePeriod(text.substring("gastos".length()).trim()));
        }

        if (text.equalsIgnoreCase("categorias")) {
            return buildCategoriesSummary(jid);
        }

        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(text.trim());
        if (categoryMatcher.matches()) {
            CategoryCommand categoryCommand = parseCategoryCommand(categoryMatcher.group(1).trim());
            return buildCategoryDetails(jid, categoryCommand.category(), categoryCommand.period());
        }

        Matcher biggestMatcher = BIGGEST_PATTERN.matcher(text.trim());
        if (biggestMatcher.matches()) {
            int limit = biggestMatcher.group(1) != null ? Integer.parseInt(biggestMatcher.group(1)) : 5;
            return buildBiggestSpendings(jid, limit);
        }

        Matcher editMatcher = EDIT_PATTERN.matcher(text.trim());
        if (editMatcher.matches()) {
            return editSpending(jid, editMatcher);
        }

        Matcher editByNameMatcher = EDIT_BY_NAME_PATTERN.matcher(text.trim());
        if (editByNameMatcher.matches()) {
            return editSpendingByName(jid, editByNameMatcher);
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

    public String buildDailySummary(String jid) {
        var period = new Period(LocalDate.now().atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX), "HOJE");
        var gastos = filterSpendings(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period);
        if (gastos.isEmpty()) {
            return "";
        }

        BigDecimal total = calculateTotal(gastos);
        return String.format("*RESUMO DE HOJE*\nTOTAL: R$ %.2f\nITEMS: %d", total, gastos.size());
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

    private String buildHelp() {
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
                editar 3 valor 45,90
                editar Pizza valor 45,90
                editar 3 categoria Mercado
                editar 3 data 12/05
                remover 3
                remover todos
                """.trim();
    }

    private String buildSummary(String jid, String sessionName, Period period) {
        var gastos = filterSpendings(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period);

        if (gastos.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        BigDecimal total = calculateTotal(gastos);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("*TOTAL DE GASTOS %s:* \uD83D\uDCC8\n", sessionName.toUpperCase(Locale.ROOT)));
        if (period != null) {
            summary.append(String.format("PERIODO: %s\n", period.label()));
        }
        summary.append("\n");

        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("ID %d | %s | R$ %.2f | %s | %s\n",

                    gasto.getId(),
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

    private String buildCategoryDetails(String jid, String category, Period period) {
        String displayCategory = category.toUpperCase(Locale.ROOT);
        String comparableCategory = normalizeCategory(category);
        var gastos = filterSpendings(spendingRepository.findByJidOrderByCreatedAtAsc(jid), period).stream()
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
        if (period != null) {
            summary.append(String.format("PERIODO: %s\n\n", period.label()));
        }

        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("ID %d | %s | R$ %.2f | %s\n",
                    gasto.getId(),
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

    private String buildBiggestSpendings(String jid, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 20));
        var gastos = spendingRepository.findByJidOrderByCreatedAtAsc(jid).stream()
                .sorted(Comparator.comparing(SpendingEntity::getAmount).reversed())
                .limit(normalizedLimit)
                .toList();

        if (gastos.isEmpty()) return "*NENHUM GASTO REGISTRADO!*";

        StringBuilder summary = new StringBuilder(String.format("*MAIORES GASTOS (%d)*\n\n", normalizedLimit));
        for (int i = 0; i < gastos.size(); i++) {
            SpendingEntity gasto = gastos.get(i);
            summary.append(String.format("ID %d | %s | R$ %.2f | %s | %s\n",
                    gasto.getId(),
                    gasto.getDescription(),
                    gasto.getAmount(),
                    gasto.getCategory(),
                    gasto.getCreatedAt().format(SUMMARY_DATE_FORMATTER)));

            if (i < gastos.size() - 1) {
                summary.append("\n");
            }
        }

        return summary.toString();
    }

    private String editSpending(String jid, Matcher editMatcher) {
        int index = Integer.parseInt(editMatcher.group(1));
        String field = normalizeCategory(editMatcher.group(2));
        String value = editMatcher.group(3).trim();
        var gasto = spendingRepository.findById((long) index)
                .filter(spending -> jid.equals(spending.getJid()))
                .orElse(null);

        if (gasto == null) {
            return String.format("NENHUM GASTO COM O ID %d", index);
        }

        return editSpendingEntity(gasto, field, value);
    }

    private String editSpendingByName(String jid, Matcher editMatcher) {
        String description = editMatcher.group(1).trim();
        String field = normalizeCategory(editMatcher.group(2));
        String value = editMatcher.group(3).trim();
        var gastos = spendingRepository.findByJidAndDescriptionIgnoreCase(jid, description);

        if (gastos.isEmpty()) {
            return String.format("NENHUM GASTO COM O NOME %s", description);
        }

        if (gastos.size() > 1) {
            return String.format("EXISTE MAIS DE UM GASTO COM O NOME %s. USE O ID.", description);
        }

        return editSpendingEntity(gastos.getFirst(), field, value);
    }

    private String editSpendingEntity(SpendingEntity gasto, String field, String value) {
        if (field.equals("VALOR")) {
            try {
                gasto.setAmount(new BigDecimal(value.replace(",", ".")));
            } catch (NumberFormatException ex) {
                return "VALOR INVALIDO.";
            }
        } else if (field.equals("CATEGORIA")) {
            gasto.setCategory(value.toUpperCase(Locale.ROOT));
        } else if (field.equals("DATA")) {
            LocalDate date = parseDayMonth(value);
            if (date == null) {
                return "DATA INVALIDA. Use dd/MM.";
            }
            gasto.setCreatedAt(date.atStartOfDay());
        } else {
            gasto.setDescription(value.toUpperCase(Locale.ROOT));
        }

        spendingRepository.save(gasto);
        return String.format("GASTO ID %d ATUALIZADO!\n%s | R$ %.2f | %s | %s",
                gasto.getId(),
                gasto.getDescription(),
                gasto.getAmount(),
                gasto.getCategory(),
                gasto.getCreatedAt().format(SUMMARY_DATE_FORMATTER));
    }

    private Period resolvePeriod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalizeCategory(value);
        LocalDate today = LocalDate.now();
        if (normalized.equals("HOJE")) {
            return new Period(today.atStartOfDay(), today.atTime(LocalTime.MAX), "HOJE");
        }
        if (normalized.equals("SEMANA")) {
            LocalDate start = today.minusDays(6);
            return new Period(start.atStartOfDay(), today.atTime(LocalTime.MAX), start.format(SUMMARY_DATE_FORMATTER) + " A " + today.format(SUMMARY_DATE_FORMATTER));
        }
        if (normalized.equals("MES")) {
            LocalDate start = today.withDayOfMonth(1);
            return new Period(start.atStartOfDay(), today.atTime(LocalTime.MAX), start.format(SUMMARY_DATE_FORMATTER) + " A " + today.format(SUMMARY_DATE_FORMATTER));
        }

        Matcher rangeMatcher = PERIOD_RANGE_PATTERN.matcher(value);
        if (rangeMatcher.matches()) {
            LocalDate start = parseDayMonth(rangeMatcher.group(1));
            LocalDate end = parseDayMonth(rangeMatcher.group(2));
            if (start != null && end != null) {
                return new Period(start.atStartOfDay(), end.atTime(LocalTime.MAX), start.format(SUMMARY_DATE_FORMATTER) + " A " + end.format(SUMMARY_DATE_FORMATTER));
            }
        }

        return null;
    }

    private CategoryCommand parseCategoryCommand(String value) {
        String[] tokens = value.split("\\s+");
        if (tokens.length > 2) {
            String possibleRange = tokens[tokens.length - 2] + " " + tokens[tokens.length - 1];
            Period period = resolvePeriod(possibleRange);
            if (period != null) {
                String category = value.substring(0, value.length() - possibleRange.length()).trim();
                return new CategoryCommand(category, period);
            }
        }

        if (tokens.length > 1) {
            String lastToken = tokens[tokens.length - 1];
            Period period = resolvePeriod(lastToken);
            if (period != null) {
                String category = value.substring(0, value.length() - lastToken.length()).trim();
                return new CategoryCommand(category, period);
            }
        }

        return new CategoryCommand(value, null);
    }

    private List<SpendingEntity> filterSpendings(List<SpendingEntity> gastos, Period period) {
        if (period == null) {
            return gastos;
        }

        return gastos.stream()
                .filter(gasto -> !gasto.getCreatedAt().isBefore(period.start()) && !gasto.getCreatedAt().isAfter(period.end()))
                .toList();
    }

    private BigDecimal calculateTotal(List<SpendingEntity> gastos) {
        return gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate parseDayMonth(String value) {
        try {
            String[] dateParts = value.split("/");
            if (dateParts.length != 2) {
                return null;
            }
            return LocalDate.of(
                    Year.now().getValue(),
                    Integer.parseInt(dateParts[1]),
                    Integer.parseInt(dateParts[0]));
        } catch (DateTimeParseException ex) {
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
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
        if (description.matches("\\d+")) {
            return removeSpendingById(jid, Long.parseLong(description));
        }

        var gastos = spendingRepository.findByJidAndDescriptionIgnoreCase(jid, description);

        if (gastos.isEmpty()) {
            return String.format("NENHUM GASTO COM O NOME %s", description);
        }

        spendingRepository.deleteAll(gastos);

        return String.format("GASTO REMOVIDO!");
    }

    private String removeSpendingById(String jid, long id) {
        var gasto = spendingRepository.findById(id)
                .filter(spending -> jid.equals(spending.getJid()))
                .orElse(null);
        if (gasto == null) {
            return String.format("NENHUM GASTO COM O ID %d", id);
        }

        spendingRepository.delete(gasto);
        return String.format("GASTO ID %d REMOVIDO!", id);
    }

    private String removeAllSpendings(String jid) {
        long count = spendingRepository.findByJid(jid).size();

        if (count == 0) {
            return "NENHUM GASTO REGISTRADO PARA SER REMOVIDO!.";
        }

        spendingRepository.deleteByJid(jid);

        return String.format("TODOS OS GASTOS FORAM REMOVIDOS!");
    }

    private record Period(LocalDateTime start, LocalDateTime end, String label) {
    }

    private record CategoryCommand(String category, Period period) {
    }
}
