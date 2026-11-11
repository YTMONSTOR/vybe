package com.vybe.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * View helpers exposed to Thymeleaf as {@code @fmt}.
 */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

    public String views(long n) {
        return compact(n) + " " + plural(n, "просмотр", "просмотра", "просмотров");
    }

    public String subscribers(long n) {
        return compact(n) + " " + plural(n, "подписчик", "подписчика", "подписчиков");
    }

    public String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trim(n / 1000.0) + " тыс.";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0) + " млн";
        return trim(n / 1_000_000_000.0) + " млрд";
    }

    private String trim(double v) {
        if (v >= 100) return String.valueOf((long) v);
        String s = String.format(Locale.US, "%.1f", Math.floor(v * 10) / 10.0);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', ',');
    }

    public String number(long n) {
        return String.format(new Locale("ru"), "%,d", n);
    }

    public String duration(int seconds) {
        if (seconds <= 0) return "";
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** Like {@link #duration(int)} but renders zero as 0:00 (for chapters). */
    public String timestamp(int seconds) {
        return seconds <= 0 ? "0:00" : duration(seconds);
    }

    public String date(LocalDateTime t) {
        return t == null ? "" : t.format(DATE);
    }

    public String ago(LocalDateTime t) {
        if (t == null) return "";
        Duration d = Duration.between(t, LocalDateTime.now());
        long sec = Math.max(0, d.getSeconds());
        if (sec < 60) return "только что";
        long min = sec / 60;
        if (min < 60) return min + " " + plural(min, "минуту", "минуты", "минут") + " назад";
        long h = min / 60;
        if (h < 24) return h + " " + plural(h, "час", "часа", "часов") + " назад";
        long days = h / 24;
        if (days < 7) return days + " " + plural(days, "день", "дня", "дней") + " назад";
        if (days < 30) { long w = days / 7; return w + " " + plural(w, "неделю", "недели", "недель") + " назад"; }
        if (days < 365) { long mo = days / 30; return mo + " " + plural(mo, "месяц", "месяца", "месяцев") + " назад"; }
        long y = days / 365;
        return y + " " + plural(y, "год", "года", "лет") + " назад";
    }

    public String bytes(long b) {
        if (b < 1024) return b + " Б";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f КБ", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f МБ", mb);
        return String.format(Locale.US, "%.2f ГБ", mb / 1024.0);
    }

    public String plural(long n, String one, String few, String many) {
        long a = Math.abs(n) % 100, b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b > 1 && b < 5) return few;
        if (b == 1) return one;
        return many;
    }
}
