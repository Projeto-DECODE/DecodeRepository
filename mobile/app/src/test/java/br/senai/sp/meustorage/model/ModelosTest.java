package br.senai.sp.meustorage.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import br.senai.sp.meustorage.util.Formatos;

// Testes do resumo do bucket (Imagens · Docs · Outros) e dos modelos
public class ModelosTest {

    private static Arquivo arquivo(String nome, String tipo) {
        return new Arquivo("id", "b1", nome, 10, tipo, "sha", "2026-10-18T12:00:00Z");
    }

    @Test
    public void resumoVazioNaoTemTipoPrincipal() {
        ResumoBucket resumo = new ResumoBucket();
        assertEquals(0, resumo.getImagens());
        assertEquals(0, resumo.getDocs());
        assertEquals(0, resumo.getOutros());
        assertNull(resumo.getTipoPrincipal()); // o cartão mostra a pasta verde
    }

    @Test
    public void resumoContaCadaGrupo() {
        ResumoBucket resumo = new ResumoBucket();
        resumo.adicionar(arquivo("a.png", "image/png"));
        resumo.adicionar(arquivo("b.pdf", "application/pdf"));
        resumo.adicionar(arquivo("c.xlsx", null));
        resumo.adicionar(arquivo("d.txt", "text/plain"));
        resumo.adicionar(arquivo("e.mp4", "video/mp4"));
        resumo.setTotal(5);

        assertEquals(5, resumo.getTotal());
        assertEquals(1, resumo.getImagens());
        assertEquals(3, resumo.getDocs());
        assertEquals(1, resumo.getOutros());
    }

    @Test
    public void tipoPrincipalEhOQueMaisAparece() {
        ResumoBucket resumo = new ResumoBucket();
        resumo.adicionar(arquivo("a.xlsx", null));
        resumo.adicionar(arquivo("b.xlsx", null));
        resumo.adicionar(arquivo("c.pdf", "application/pdf"));
        assertEquals(Formatos.TIPO_PLANILHA, resumo.getTipoPrincipal());

        ResumoBucket videos = new ResumoBucket();
        videos.adicionar(arquivo("a.mp4", "video/mp4"));
        assertEquals(Formatos.TIPO_OUTRO, videos.getTipoPrincipal());
    }

    @Test
    public void empateFicaComAImagem() {
        ResumoBucket resumo = new ResumoBucket();
        resumo.adicionar(arquivo("a.pdf", "application/pdf"));
        resumo.adicionar(arquivo("b.jpg", "image/jpeg"));
        assertEquals(Formatos.TIPO_IMAGEM, resumo.getTipoPrincipal());
    }

    @Test
    public void arquivoSabeSeEhImagem() {
        assertTrue(arquivo("a.png", "image/png").isImagem());
        assertFalse(arquivo("a.pdf", "application/pdf").isImagem());
        assertFalse(arquivo("a", null).isImagem());
    }

    @Test
    public void bucketSabeSeEstaSendoExcluido() {
        assertTrue(new Bucket("1", "x", Bucket.STATUS_EXCLUINDO, "").isExcluindo());
        assertFalse(new Bucket("1", "x", Bucket.STATUS_ATIVO, "").isExcluindo());
    }

    @Test
    public void paginaSemConteudoDevolveListaVazia() {
        Pagina<Arquivo> pagina = new Pagina<>();
        assertNotNull(pagina.getConteudo());
        assertTrue(pagina.getConteudo().isEmpty());
    }
}
