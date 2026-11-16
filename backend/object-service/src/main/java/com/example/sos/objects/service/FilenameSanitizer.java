package com.example.sos.objects.service;

import java.text.Normalizer;

/**
 * O nome original é só metadado de exibição. Esta limpeza serve ao banco e ao cabeçalho de download;
 * ela NÃO é o que protege o disco (o caminho físico nunca usa esse nome).
 */
public final class FilenameSanitizer {

    private static final int MAX_LENGTH = 255;
    private static final String FALLBACK = "file";

    private FilenameSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        // Remove qualquer componente de diretório, em estilo Unix ou Windows.
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        // Remove caracteres de controle e os que quebram cabeçalhos ou sistemas de arquivos.
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        // Sem pontos ou espaços no início/fim (".", ".." e nomes ocultos viram nomes comuns).
        name = name.replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
        if (name.isEmpty()) {
            return FALLBACK;
        }
        return name.length() > MAX_LENGTH ? truncate(name) : name;
    }

    /** Corta em MAX_LENGTH preservando a extensão, quando houver. */
    private static String truncate(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 16) {
            String extension = name.substring(dot);
            return name.substring(0, MAX_LENGTH - extension.length()) + extension;
        }
        return name.substring(0, MAX_LENGTH);
    }
}
