package com.devlil0.spending_system.parser;

import com.devlil0.spending_system.dto.SpendingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessageParser {

    // Formato esperado: "<descrição> <valor>", com categoria e data opcionais no fim.
    private static final Pattern SPENDING_PATTERN =
            Pattern.compile("^(.+?)\\s+(\\d+(?:[.,]\\d{1,2})?)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}/\\d{2}");

    public Optional<SpendingRequest> parse(String text) {
        Matcher matcher = SPENDING_PATTERN.matcher(text.trim());
        if (!matcher.matches()) return Optional.empty();

        String description = matcher.group(1).trim();
        BigDecimal amount = new BigDecimal(matcher.group(2).replace(",", "."));
        String categoryAndDate = matcher.group(3) != null ? matcher.group(3).trim() : "";
        LocalDateTime date = LocalDateTime.now();
        String category = categoryAndDate.isBlank() ? "Outros" : categoryAndDate;

        String[] parts = categoryAndDate.split("\\s+");
        if (parts.length > 0 && DATE_PATTERN.matcher(parts[parts.length - 1]).matches()) {
            try {
                String[] dateParts = parts[parts.length - 1].split("/");
                date = LocalDate.of(
                        Year.now().getValue(),
                        Integer.parseInt(dateParts[1]),
                        Integer.parseInt(dateParts[0])).atStartOfDay();
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            } catch (RuntimeException ex) {
                return Optional.empty();
            }

            category = categoryAndDate.substring(0, categoryAndDate.length() - parts[parts.length - 1].length()).trim();
            if (category.isBlank()) {
                category = "Outros";
            }
        }

        return Optional.of(new SpendingRequest(description, amount, category, date));
    }
}
