package com.vybe.entity;

public enum ReportReason {
    SPAM("Спам или вводящий в заблуждение контент"),
    VIOLENCE("Насилие или жестокость"),
    HATE("Разжигание ненависти"),
    SEXUAL("Контент сексуального характера"),
    COPYRIGHT("Нарушение авторских прав"),
    OTHER("Другое");

    private final String label;
    ReportReason(String label) { this.label = label; }
    public String getLabel() { return label; }
}
