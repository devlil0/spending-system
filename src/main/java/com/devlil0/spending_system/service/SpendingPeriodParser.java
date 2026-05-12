package com.devlil0.spending_system.service;

import com.devlil0.spending_system.model.SpendingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SpendingPeriodParser {

    private static final Pattern PERIOD_RANGE_PATTERN =
            Pattern.compile("^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CategoryNormalizer categoryNormalizer;

    public SpendingPeriod resolve(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = categoryNormalizer.normalize(value);
        LocalDate today = LocalDate.now();
        if (normalized.equals("HOJE")) {
            return new SpendingPeriod(today.atStartOfDay(), today.atTime(LocalTime.MAX), "HOJE");
        }
        if (normalized.equals("SEMANA")) {
            LocalDate start = today.minusDays(6);
            return new SpendingPeriod(start.atStartOfDay(), today.atTime(LocalTime.MAX), label(start, today));
        }
        if (normalized.equals("MES")) {
            LocalDate start = today.withDayOfMonth(1);
            return new SpendingPeriod(start.atStartOfDay(), today.atTime(LocalTime.MAX), label(start, today));
        }

        Matcher rangeMatcher = PERIOD_RANGE_PATTERN.matcher(value);
        if (rangeMatcher.matches()) {
            LocalDate start = parseDayMonth(rangeMatcher.group(1));
            LocalDate end = parseDayMonth(rangeMatcher.group(2));
            if (start != null && end != null) {
                return new SpendingPeriod(start.atStartOfDay(), end.atTime(LocalTime.MAX), label(start, end));
            }
        }

        return null;
    }

    public List<SpendingEntity> filter(List<SpendingEntity> spendings, SpendingPeriod period) {
        if (period == null) {
            return spendings;
        }

        return spendings.stream()
                .filter(spending -> !spending.getCreatedAt().isBefore(period.start())
                        && !spending.getCreatedAt().isAfter(period.end()))
                .toList();
    }

    public LocalDate parseDayMonth(String value) {
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

    private String label(LocalDate start, LocalDate end) {
        return start.format(DATE_FORMATTER) + " A " + end.format(DATE_FORMATTER);
    }

}
