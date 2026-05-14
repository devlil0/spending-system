package com.devlil0.spending_system.parser;

import com.devlil0.spending_system.dto.SpendingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MessageParser {

    // Tudo antes do primeiro valor numerico e tratado como nome/descricao.
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+(?:[.,]\\d{1,2})?");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}/\\d{2}");

    private final Clock clock;

    public Optional<SpendingRequest> parse(String text) {
        String[] tokens = text.trim().split("\\s+");
        int amountIndex = findAmountIndex(tokens);
        if (amountIndex <= 0) return Optional.empty();

        String description = String.join(" ", Arrays.copyOfRange(tokens, 0, amountIndex)).trim();
        BigDecimal amount = new BigDecimal(tokens[amountIndex].replace(",", "."));
        String categoryAndDate = String.join(" ", Arrays.copyOfRange(tokens, amountIndex + 1, tokens.length)).trim();
        LocalDateTime date = LocalDateTime.now(clock);
        String category = categoryAndDate.isBlank() ? "Outros" : categoryAndDate;

        String[] parts = categoryAndDate.split("\\s+");
        if (parts.length > 0 && DATE_PATTERN.matcher(parts[parts.length - 1]).matches()) {
            try {
                String[] dateParts = parts[parts.length - 1].split("/");
                date = LocalDate.of(
                        Year.now(clock).getValue(),
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

    private int findAmountIndex(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            if (AMOUNT_PATTERN.matcher(tokens[i]).matches()) {
                return i;
            }
        }

        return -1;
    }
}
