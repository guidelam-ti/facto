package com.guidelam.facto.web.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ResetForm {

    private String mode = "all"; // "all" or "period"

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodEnd;

    public ResetForm() {
    }

    public ResetForm(String mode, LocalDate periodStart, LocalDate periodEnd) {
        this.mode = mode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }
}
