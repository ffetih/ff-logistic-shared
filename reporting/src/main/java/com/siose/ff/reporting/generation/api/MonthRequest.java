package com.siose.ff.reporting.generation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code month} is {@code yyyy-MM}. A month that is not on the calendar is refused by the service. */
public record MonthRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month) {
}
