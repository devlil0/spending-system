package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.BotSessionEntity;
import com.devlil0.spending_system.parser.MessageParser;
import com.devlil0.spending_system.repository.BotSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CHANGE_NAME_PATTERN =
            Pattern.compile("^alterar\\s+nome(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

    private final BotSessionRepository botSessionRepository;
    private final MessageParser messageParser;
    private final SpendingPeriodParser periodParser;
    private final CategoryCommandParser categoryCommandParser;
    private final SpendingReportService spendingReportService;
    private final SpendingMutationService spendingMutationService;

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

        return routeCommand(jid, phone, session, text);
    }

    public String buildDailySummary(String jid) {
        return spendingReportService.buildDailySummary(jid);
    }

    private String routeCommand(String jid, String phone, BotSessionEntity session, String text) {
        if (text.equalsIgnoreCase("ajuda")) {
            return spendingReportService.buildHelp();
        }

        Matcher changeNameMatcher = CHANGE_NAME_PATTERN.matcher(text.trim());
        if (changeNameMatcher.matches()) {
            String newName = changeNameMatcher.group(1);
            if (newName == null || newName.isBlank()) {
                session.setName(null);
                botSessionRepository.save(session);
                return "Digite o seu nome:";
            }

            String normalizedName = normalizeSessionName(newName);
            session.setName(normalizedName);
            botSessionRepository.save(session);
            return String.format("Olá %s", normalizedName);
        }

        if (text.toLowerCase(Locale.ROOT).startsWith("gastos")) {
            SpendingPeriod period = periodParser.resolve(text.substring("gastos".length()).trim());
            return spendingReportService.buildSummary(jid, session.getName(), period);
        }

        if (text.equalsIgnoreCase("categorias")) {
            return spendingReportService.buildCategoriesSummary(jid);
        }

        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(text.trim());
        if (categoryMatcher.matches()) {
            CategoryCommand command = categoryCommandParser.parse(categoryMatcher.group(1).trim());
            return spendingReportService.buildCategoryDetails(jid, command.category(), command.period());
        }

        Matcher biggestMatcher = BIGGEST_PATTERN.matcher(text.trim());
        if (biggestMatcher.matches()) {
            int limit = biggestMatcher.group(1) != null ? Integer.parseInt(biggestMatcher.group(1)) : 5;
            return spendingReportService.buildBiggestSpendings(jid, limit);
        }

        Matcher editMatcher = EDIT_PATTERN.matcher(text.trim());
        if (editMatcher.matches()) {
            return spendingMutationService.editById(jid, editMatcher);
        }

        Matcher editByNameMatcher = EDIT_BY_NAME_PATTERN.matcher(text.trim());
        if (editByNameMatcher.matches()) {
            return spendingMutationService.editByName(jid, editByNameMatcher);
        }

        if (text.equalsIgnoreCase("remover todos")) {
            return spendingMutationService.removeAll(jid);
        }

        Matcher removeMatcher = REMOVE_PATTERN.matcher(text.trim());
        if (removeMatcher.matches()) {
            return spendingMutationService.remove(jid, removeMatcher.group(1).trim());
        }

        if (text.lines().filter(line -> !line.isBlank()).count() > 1) {
            return spendingMutationService.saveMultiple(jid, phone, text);
        }

        return messageParser.parse(text)
                .map(req -> spendingMutationService.saveSingle(jid, phone, req))
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

}
