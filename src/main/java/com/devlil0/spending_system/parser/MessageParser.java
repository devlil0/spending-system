package com.devlil0.spending_system.parser;

import com.devlil0.spending_system.dto.SpendingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessageParser {

    // Formato esperado: "<descrição> <valor>" ou "<descrição> <valor> <categoria>"
    private static final Pattern SPENDING_PATTERN =
            Pattern.compile("^(.+?)\\s+(\\d+(?:[.,]\\d{1,2})?)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

    public Optional<SpendingRequest> parse(String text) {
        Matcher matcher = SPENDING_PATTERN.matcher(text.trim());
        if (!matcher.matches()) return Optional.empty();

        String description = matcher.group(1).trim();
        BigDecimal amount = new BigDecimal(matcher.group(2).replace(",", "."));
        String category = matcher.group(3) != null ? matcher.group(3).trim() : "Outros";

        return Optional.of(new SpendingRequest(description, amount, category));
    }
}
