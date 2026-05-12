package com.devlil0.spending_system.service;

import com.devlil0.spending_system.dto.SpendingRequest;
import com.devlil0.spending_system.model.SpendingEntity;
import com.devlil0.spending_system.parser.MessageParser;
import com.devlil0.spending_system.repository.SpendingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
public class SpendingMutationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SpendingRepository spendingRepository;
    private final MessageParser messageParser;
    private final CategoryNormalizer categoryNormalizer;
    private final SpendingPeriodParser periodParser;

    public String saveMultiple(String jid, String phone, String text) {
        List<SpendingEntity> saved = new ArrayList<>();
        List<String> invalidLines = new ArrayList<>();

        text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> messageParser.parse(line)
                        .ifPresentOrElse(
                                req -> saved.add(saveEntity(jid, phone, req)),
                                () -> invalidLines.add(line)));

        if (saved.isEmpty()) {
            return "Não entendi nenhum gasto. Envie uma linha por gasto no formato: *Descrição Valor Categoria Data*";
        }

        BigDecimal total = saved.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder reply = new StringBuilder();
        reply.append(String.format("%d gastos registrados!", saved.size()));
        reply.append(String.format("\nTOTAL: R$ %.2f", total));

        if (!invalidLines.isEmpty()) {
            reply.append("\n\nLinhas ignoradas:");
            invalidLines.forEach(line -> reply.append("\n- ").append(line));
        }

        return reply.toString();
    }

    public String saveSingle(String jid, String phone, SpendingRequest request) {
        saveEntity(jid, phone, request);

        return String.format("Gasto registrado!\n%s\nR$ %.2f\n%s\n%s",
                request.description(),
                request.amount(),
                request.category(),
                request.date().format(DATE_FORMATTER));
    }

    public String editById(String jid, Matcher editMatcher) {
        long id = Long.parseLong(editMatcher.group(1));
        String field = categoryNormalizer.normalize(editMatcher.group(2));
        String value = editMatcher.group(3).trim();
        var spending = spendingRepository.findById(id)
                .filter(item -> jid.equals(item.getJid()))
                .orElse(null);

        if (spending == null) {
            return String.format("NENHUM GASTO COM O ID %d", id);
        }

        return editEntity(spending, field, value);
    }

    public String editByName(String jid, Matcher editMatcher) {
        String description = editMatcher.group(1).trim();
        String field = categoryNormalizer.normalize(editMatcher.group(2));
        String value = editMatcher.group(3).trim();
        var spendings = spendingRepository.findByJidAndDescriptionIgnoreCase(jid, description);

        if (spendings.isEmpty()) {
            return String.format("NENHUM GASTO COM O NOME %s", description);
        }

        if (spendings.size() > 1) {
            return String.format("EXISTE MAIS DE UM GASTO COM O NOME %s. USE O ID.", description);
        }

        return editEntity(spendings.getFirst(), field, value);
    }

    public String remove(String jid, String description) {
        if (description.matches("\\d+")) {
            return removeById(jid, Long.parseLong(description));
        }

        var spendings = spendingRepository.findByJidAndDescriptionIgnoreCase(jid, description);
        if (spendings.isEmpty()) {
            return String.format("NENHUM GASTO COM O NOME %s", description);
        }

        spendingRepository.deleteAll(spendings);
        return "GASTO REMOVIDO!";
    }

    public String removeAll(String jid) {
        long count = spendingRepository.findByJid(jid).size();
        if (count == 0) {
            return "NENHUM GASTO REGISTRADO PARA SER REMOVIDO!.";
        }

        spendingRepository.deleteByJid(jid);
        return "TODOS OS GASTOS FORAM REMOVIDOS!";
    }

    private SpendingEntity saveEntity(String jid, String phone, SpendingRequest request) {
        var entity = SpendingEntity.builder()
                .jid(jid)
                .phone(phone)
                .description(request.description().toUpperCase(Locale.ROOT))
                .amount(request.amount())
                .category(request.category().toUpperCase(Locale.ROOT))
                .createdAt(request.date())
                .build();

        return spendingRepository.save(entity);
    }

    private String editEntity(SpendingEntity spending, String field, String value) {
        if (field.equals("VALOR")) {
            try {
                spending.setAmount(new BigDecimal(value.replace(",", ".")));
            } catch (NumberFormatException ex) {
                return "VALOR INVALIDO.";
            }
        } else if (field.equals("CATEGORIA")) {
            spending.setCategory(value.toUpperCase(Locale.ROOT));
        } else if (field.equals("DATA")) {
            LocalDate date = periodParser.parseDayMonth(value);
            if (date == null) {
                return "DATA INVALIDA. Use dd/MM.";
            }
            spending.setCreatedAt(date.atStartOfDay());
        } else {
            spending.setDescription(value.toUpperCase(Locale.ROOT));
        }

        spendingRepository.save(spending);
        return String.format("GASTO ID %d ATUALIZADO!\n%s | R$ %.2f | %s | %s",
                spending.getId(),
                spending.getDescription(),
                spending.getAmount(),
                spending.getCategory(),
                spending.getCreatedAt().format(DATE_FORMATTER));
    }

    private String removeById(String jid, long id) {
        var spending = spendingRepository.findById(id)
                .filter(item -> jid.equals(item.getJid()))
                .orElse(null);
        if (spending == null) {
            return String.format("NENHUM GASTO COM O ID %d", id);
        }

        spendingRepository.delete(spending);
        return String.format("GASTO ID %d REMOVIDO!", id);
    }

}
