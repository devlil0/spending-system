package com.devlil0.spending_system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoryCommandParser {

    private final SpendingPeriodParser periodParser;

    public CategoryCommand parse(String value) {
        String[] tokens = value.split("\\s+");
        if (tokens.length > 2) {
            String possibleRange = tokens[tokens.length - 2] + " " + tokens[tokens.length - 1];
            SpendingPeriod period = periodParser.resolve(possibleRange);
            if (period != null) {
                String category = value.substring(0, value.length() - possibleRange.length()).trim();
                return new CategoryCommand(category, period);
            }
        }

        if (tokens.length > 1) {
            String lastToken = tokens[tokens.length - 1];
            SpendingPeriod period = periodParser.resolve(lastToken);
            if (period != null) {
                String category = value.substring(0, value.length() - lastToken.length()).trim();
                return new CategoryCommand(category, period);
            }
        }

        return new CategoryCommand(value, null);
    }

}
