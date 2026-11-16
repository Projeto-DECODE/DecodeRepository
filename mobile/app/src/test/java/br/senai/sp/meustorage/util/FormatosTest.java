package br.senai.sp.meustorage.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import br.senai.sp.meustorage.model.Arquivo;

// Testes das contas e textos que aparecem na tela (não precisam do celular)
public class FormatosTest {

    private static Arquivo arquivo(String nome, String tipo) {
        return new Arquivo("id", "b1", nome, 10, tipo, "sha", "2026-10-18T12:00:00Z");
    }

    // ---------- Tamanho ----------

    @Test
    public void tamanhoEmBytesKbEMb() {
        assertEquals("0 B", Formatos.tamanho(0));
        assertEquals("1023 B", Formatos.tamanho(1023));
        assertEquals("1,0 KB", Formatos.tamanho(1024));
        assertEquals("2,5 KB", Formatos.tamanho(2560));
        assertEquals("3,0 MB", Formatos.tamanho(3_145_728));
    }

    // ---------- Data ----------

    @Test
    public void dataNoFormatoBrasileiro() {
        // Meio-dia em UTC: cai no mesmo dia em qualquer fuso do Brasil
        assertEquals("18/10/2026", Formatos.data("2026-10-18T12:00:00Z"));
    }

    @Test
    public void dataSemZTambemFunciona() {
        assertEquals("18/10/2026", Formatos.data("2026-10-18T12:00:00"));
    }

    @Test
    public void dataVaziaOuInvalida() {
        assertEquals("", Formatos.data(null));
        assertEquals("", Formatos.data(""));
        assertEquals("ontem", Formatos.data("ontem")); // devolve como veio
    }

    // ---------- SHA-256 abreviado ----------

    @Test
    public void shaAbreviadoMostraComecoEFim() {
        assertEquals("9f2c1a7b…e4d3c2", Formatos.shaAbreviado("9f2c1a7b0011223344556677e4d3c2"));
    }

    @Test
    public void shaCurtoOuVazio() {
        assertEquals("abc", Formatos.shaAbreviado("abc"));
        assertEquals("—", Formatos.shaAbreviado(null));
        assertEquals("—", Formatos.shaAbreviado(""));
    }

    // ---------- Nome do bucket ----------

    @Test
    public void nomesDeBucketValidos() {
        assertTrue(Formatos.nomeBucketValido("abc"));
        assertTrue(Formatos.nomeBucketValido("fotos-obra"));
        assertTrue(Formatos.nomeBucketValido("backup2026"));
        assertTrue(Formatos.nomeBucketValido(repetir('a', 63)));
    }

    @Test
    public void nomesDeBucketInvalidos() {
        assertFalse(Formatos.nomeBucketValido(null));
        assertFalse(Formatos.nomeBucketValido("ab"));                // curto demais
        assertFalse(Formatos.nomeBucketValido(repetir('a', 64)));    // longo demais
        assertFalse(Formatos.nomeBucketValido("Fotos"));             // maiúscula
        assertFalse(Formatos.nomeBucketValido("fotos obra"));        // espaço
        assertFalse(Formatos.nomeBucketValido("-fotos"));            // começa com hífen
        assertFalse(Formatos.nomeBucketValido("fotos-"));            // termina com hífen
        assertFalse(Formatos.nomeBucketValido("fotos_obra"));        // sublinhado
        assertFalse(Formatos.nomeBucketValido("relatório"));         // acento
    }

    // ---------- Tipo e categoria (ícone e números do cartão) ----------

    @Test
    public void tipoPeloContentType() {
        assertEquals(Formatos.TIPO_IMAGEM, Formatos.tipo(arquivo("x", "image/png")));
        assertEquals(Formatos.TIPO_PDF, Formatos.tipo(arquivo("x", "application/pdf")));
        assertEquals(Formatos.TIPO_PLANILHA, Formatos.tipo(arquivo("x",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        assertEquals(Formatos.TIPO_PLANILHA, Formatos.tipo(arquivo("x", "application/vnd.ms-excel")));
        assertEquals(Formatos.TIPO_OUTRO, Formatos.tipo(arquivo("x", "video/mp4")));
    }

    @Test
    public void tipoPelaExtensaoQuandoOContentTypeEhGenerico() {
        assertEquals(Formatos.TIPO_PDF, Formatos.tipo(arquivo("ata.PDF", "application/octet-stream")));
        assertEquals(Formatos.TIPO_PLANILHA, Formatos.tipo(arquivo("dados.csv", null)));
        assertEquals(Formatos.TIPO_OUTRO, Formatos.tipo(arquivo("sem-extensao", null)));
        assertEquals(Formatos.TIPO_OUTRO, Formatos.tipo(arquivo(null, null)));
    }

    @Test
    public void categoriaImagensDocsOutros() {
        assertEquals(Formatos.CATEGORIA_IMAGEM, Formatos.categoria(arquivo("a.jpg", "image/jpeg")));
        assertEquals(Formatos.CATEGORIA_DOC, Formatos.categoria(arquivo("a.pdf", "application/pdf")));
        assertEquals(Formatos.CATEGORIA_DOC, Formatos.categoria(arquivo("a.xlsx", null)));
        assertEquals(Formatos.CATEGORIA_DOC, Formatos.categoria(arquivo("nota.txt", "text/plain")));
        assertEquals(Formatos.CATEGORIA_DOC, Formatos.categoria(arquivo("x",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
        assertEquals(Formatos.CATEGORIA_DOC, Formatos.categoria(arquivo("slides.pptx", "application/octet-stream")));
        assertEquals(Formatos.CATEGORIA_OUTRO, Formatos.categoria(arquivo("aula.mp4", "video/mp4")));
        assertEquals(Formatos.CATEGORIA_OUTRO, Formatos.categoria(arquivo("backup.zip", "application/zip")));
    }

    private static String repetir(char c, int vezes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vezes; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
