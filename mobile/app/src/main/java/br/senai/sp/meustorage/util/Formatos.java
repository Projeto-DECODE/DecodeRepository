package br.senai.sp.meustorage.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.model.Arquivo;

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

    // Mesma regra do back-end: 3 a 63 letras minúsculas, números ou hífen,
    // começando e terminando com letra ou número
    private static final Pattern NOME_BUCKET = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    public static boolean nomeBucketValido(String nome) {
        return nome != null && nome.length() >= 3 && nome.length() <= 63 && NOME_BUCKET.matcher(nome).matches();
    }

    // ---------- Tipo do arquivo (define o ícone e a cor) ----------

    public static final String TIPO_IMAGEM = "imagem";
    public static final String TIPO_PDF = "pdf";
    public static final String TIPO_PLANILHA = "planilha";
    public static final String TIPO_OUTRO = "outro";

    // Grupos dos números do cartão do bucket: Imagens · Docs · Outros
    public static final String CATEGORIA_IMAGEM = "imagem";
    public static final String CATEGORIA_DOC = "doc";
    public static final String CATEGORIA_OUTRO = "outro";

    private static final List<String> EXTENSOES_PLANILHA = Arrays.asList("xlsx", "xls", "csv", "ods");
    private static final List<String> EXTENSOES_DOC = Arrays.asList(
            "doc", "docx", "odt", "txt", "rtf", "md", "ppt", "pptx", "odp");

    private static String extensao(String nome) {
        if (nome == null) return "";
        int ponto = nome.lastIndexOf('.');
        return ponto >= 0 ? nome.substring(ponto + 1).toLowerCase(Locale.ROOT) : "";
    }

    public static String tipo(Arquivo arquivo) {
        String contentType = arquivo.getTipoConteudo() != null ? arquivo.getTipoConteudo() : "";
        String ext = extensao(arquivo.getNomeOriginal());
        if (contentType.startsWith("image/")) return TIPO_IMAGEM;
        if (contentType.equals("application/pdf") || ext.equals("pdf")) return TIPO_PDF;
        if (contentType.contains("spreadsheet") || contentType.contains("excel") || EXTENSOES_PLANILHA.contains(ext)) {
            return TIPO_PLANILHA;
        }
        return TIPO_OUTRO;
    }

    public static String categoria(Arquivo arquivo) {
        String tipo = tipo(arquivo);
        if (TIPO_IMAGEM.equals(tipo)) return CATEGORIA_IMAGEM;
        if (TIPO_PDF.equals(tipo) || TIPO_PLANILHA.equals(tipo)) return CATEGORIA_DOC;
        String contentType = arquivo.getTipoConteudo() != null ? arquivo.getTipoConteudo() : "";
        if (contentType.startsWith("text/") || contentType.contains("word") || contentType.contains("document")
                || contentType.contains("presentation") || EXTENSOES_DOC.contains(extensao(arquivo.getNomeOriginal()))) {
            return CATEGORIA_DOC;
        }
        return CATEGORIA_OUTRO;
    }

    public static int iconeDoTipo(String tipo) {
        if (tipo == null) return R.drawable.ic_folder;
        switch (tipo) {
            case TIPO_IMAGEM: return R.drawable.ic_image;
            case TIPO_PDF: return R.drawable.ic_document;
            case TIPO_PLANILHA: return R.drawable.ic_table;
            default: return R.drawable.ic_file;
        }
    }

    public static int corDoTipo(Context context, String tipo) {
        int id;
        if (tipo == null) {
            id = R.color.acento;
        } else {
            switch (tipo) {
                case TIPO_IMAGEM: id = R.color.tipo_imagem; break;
                case TIPO_PDF: id = R.color.tipo_pdf; break;
                case TIPO_PLANILHA: id = R.color.tipo_planilha; break;
                default: id = R.color.tipo_outro;
            }
        }
        return ContextCompat.getColor(context, id);
    }

    // Pinta o ícone com a cor do tipo e o quadrado de fundo com a mesma cor, bem clarinha.
    // tipo null = pasta verde (bucket vazio)
    public static void aplicarIcone(ImageView icone, View fundo, String tipo) {
        int cor = corDoTipo(icone.getContext(), tipo);
        icone.setImageResource(iconeDoTipo(tipo));
        icone.setColorFilter(cor);
        ViewCompat.setBackgroundTintList(fundo, ColorStateList.valueOf(ColorUtils.setAlphaComponent(cor, 38)));
    }
}
