package br.senai.sp.meustorage.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class Formatos {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);

    private Formatos() {
    }

    // 3145728 -> "3,0 MB"
    public static String tamanho(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(PT_BR, "%.1f KB", kb);
        }
        return String.format(PT_BR, "%.1f MB", kb / 1024.0);
    }

    // "2026-10-18T12:00:00Z" -> "18/10/2026" (no fuso do celular)
    public static String data(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        try {
            return DATA.format(Instant.parse(iso).atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            try {
                // Caso o back-end mande sem o "Z"
                return DATA.format(LocalDateTime.parse(iso));
            } catch (DateTimeParseException e2) {
                return iso;
            }
        }
    }

    // 8 primeiros + … + 6 últimos
    public static String shaAbreviado(String sha) {
        if (sha == null || sha.isEmpty()) {
            return "—";
        }
        if (sha.length() <= 14) {
            return sha;
        }
        return sha.substring(0, 8) + "…" + sha.substring(sha.length() - 6);
    }
}
