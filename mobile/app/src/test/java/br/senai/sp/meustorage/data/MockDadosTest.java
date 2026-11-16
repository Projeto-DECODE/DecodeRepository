package br.senai.sp.meustorage.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.Pagina;
import br.senai.sp.meustorage.model.ResumoBucket;
import br.senai.sp.meustorage.model.Usuario;
import br.senai.sp.meustorage.util.Formatos;
import br.senai.sp.meustorage.util.MensagensErro;

// Testes do modo demonstração (dados de exemplo, sem back-end).
// Os dados ficam em memória e são compartilhados, então cada teste usa nomes únicos.
public class MockDadosTest {

    @BeforeClass
    public static void preparar() {
        MockDados.iniciar();
        MockDados.iniciar(); // chamar de novo não duplica nada
    }

    private static String nomeUnico() {
        return "teste-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Bucket buscar(String nome) {
        for (Bucket b : MockDados.listarBuckets()) {
            if (b.getNome().equals(nome)) {
                return b;
            }
        }
        throw new AssertionError("Bucket não encontrado: " + nome);
    }

    // ---------- Login e cadastro ----------

    @Test
    public void loginCertoErradoEMaiusculas() {
        assertNull(MockDados.validarLogin("teste@teste.com", "12345678"));
        assertNull(MockDados.validarLogin("TESTE@teste.com", "12345678"));
        assertEquals(MensagensErro.INVALID_CREDENTIALS, MockDados.validarLogin("teste@teste.com", "errada"));
        assertEquals(MensagensErro.INVALID_CREDENTIALS, MockDados.validarLogin("ninguem@teste.com", "12345678"));
    }

    @Test
    public void contaDeTesteExpiraEmUmMinuto() {
        assertEquals(60, MockDados.expiracaoSegundos(MockDados.EMAIL_EXPIRA));
        assertEquals(900, MockDados.expiracaoSegundos("teste@teste.com"));
    }

    @Test
    public void cadastroNovoEEmailRepetido() {
        String email = nomeUnico() + "@teste.com";
        Usuario usuario = MockDados.cadastrar("Ana", email, "12345678");

        assertNotNull(usuario);
        assertNull(MockDados.validarLogin(email, "12345678")); // já consegue entrar
        assertNull(MockDados.cadastrar("Outra", email.toUpperCase(), "12345678")); // repetido
        assertNull(MockDados.cadastrar("X", MockDados.EMAIL_EXISTENTE, "12345678"));
    }

    // ---------- Buckets ----------

    @Test
    public void bucketsDeExemploExistem() {
        assertTrue(MockDados.existeBucketComNome("documentos"));
        assertTrue(MockDados.existeBucketComNome("fotos-obra"));
        assertTrue(MockDados.existeBucketComNome("backups"));
        assertFalse(MockDados.existeBucketComNome("nao-existe"));
    }

    @Test
    public void criarEExcluirBucketVazio() {
        String nome = nomeUnico();
        Bucket bucket = MockDados.criarBucket(nome);

        assertEquals(Bucket.STATUS_ATIVO, bucket.getStatus());
        assertTrue(MockDados.existeBucket(bucket.getId()));
        assertTrue(MockDados.existeBucketComNome(nome));

        assertNull(MockDados.excluirBucket(bucket.getId()));
        assertFalse(MockDados.existeBucket(bucket.getId()));
        assertFalse(MockDados.existeBucketComNome(nome));
    }

    @Test
    public void naoExcluiBucketComArquivos() {
        Bucket documentos = buscar("documentos");
        assertEquals(MensagensErro.BUCKET_NOT_EMPTY, MockDados.excluirBucket(documentos.getId()));
        assertTrue(MockDados.existeBucket(documentos.getId()));
    }

    @Test
    public void excluirBucketQueNaoExiste() {
        assertEquals(MensagensErro.BUCKET_NOT_FOUND, MockDados.excluirBucket("id-inventado"));
    }

    // ---------- Arquivos e paginação ----------

    @Test
    public void paginacaoDe20Em20() {
        Bucket fotos = buscar("fotos-obra"); // 26 arquivos

        Pagina<Arquivo> primeira = MockDados.listarArquivos(fotos.getId(), 0, 20);
        Pagina<Arquivo> segunda = MockDados.listarArquivos(fotos.getId(), 1, 20);
        Pagina<Arquivo> depois = MockDados.listarArquivos(fotos.getId(), 5, 20);

        assertEquals(20, primeira.getConteudo().size());
        assertEquals(26, primeira.getTotalElementos());
        assertEquals(2, primeira.getTotalPaginas());
        assertEquals(6, segunda.getConteudo().size());
        assertTrue(depois.getConteudo().isEmpty());
    }

    @Test
    public void listarBucketQueNaoExisteDevolvePaginaVazia() {
        Pagina<Arquivo> pagina = MockDados.listarArquivos("id-inventado", 0, 20);
        assertTrue(pagina.getConteudo().isEmpty());
        assertEquals(0, pagina.getTotalElementos());
    }

    @Test
    public void enviarBaixarEExcluirArquivo() {
        Bucket bucket = MockDados.criarBucket(nomeUnico());
        byte[] bytes = "olá".getBytes(StandardCharsets.UTF_8);

        Arquivo arquivo = MockDados.adicionarArquivo(bucket.getId(), "nota.txt", "text/plain", bytes);

        assertNotNull(arquivo);
        assertEquals(bytes.length, arquivo.getTamanhoBytes());
        assertEquals(64, arquivo.getSha256().length()); // SHA-256 em hexadecimal
        assertEquals(arquivo.getId(), MockDados.listarArquivos(bucket.getId(), 0, 20).getConteudo().get(0).getId());
        assertArrayEquals(bytes, MockDados.conteudo(arquivo));

        assertNull(MockDados.excluirArquivo(bucket.getId(), arquivo.getId()));
        assertNull(MockDados.conteudo(arquivo)); // não existe mais
        assertEquals(MensagensErro.OBJECT_NOT_FOUND, MockDados.excluirArquivo(bucket.getId(), arquivo.getId()));
        assertNull(MockDados.excluirBucket(bucket.getId())); // ficou vazio, pode excluir
    }

    @Test
    public void enviarParaBucketQueNaoExiste() {
        assertNull(MockDados.adicionarArquivo("id-inventado", "a.txt", "text/plain", new byte[]{1}));
        assertEquals(MensagensErro.BUCKET_NOT_FOUND, MockDados.excluirArquivo("id-inventado", "x"));
    }

    @Test
    public void arquivoDeExemploGeraConteudo() {
        Bucket documentos = buscar("documentos");
        Arquivo pdf = MockDados.listarArquivos(documentos.getId(), 0, 20).getConteudo().get(0);
        byte[] bytes = MockDados.conteudo(pdf);
        assertNotNull(bytes);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains(pdf.getNomeOriginal()));
    }

    // ---------- Resumo do cartão ----------

    @Test
    public void resumoDosBucketsDeExemplo() {
        ResumoBucket fotos = MockDados.resumir(buscar("fotos-obra").getId());
        assertEquals(26, fotos.getTotal());
        assertEquals(26, fotos.getImagens());
        assertEquals(Formatos.TIPO_IMAGEM, fotos.getTipoPrincipal());

        ResumoBucket relatorios = MockDados.resumir(buscar("relatorios-mensais").getId());
        assertEquals(7, relatorios.getDocs());
        assertEquals(Formatos.TIPO_PLANILHA, relatorios.getTipoPrincipal());

        ResumoBucket videos = MockDados.resumir(buscar("videos-treinamento").getId());
        assertEquals(1, videos.getImagens());
        assertEquals(2, videos.getOutros());
        assertEquals(Formatos.TIPO_OUTRO, videos.getTipoPrincipal());

        assertNull(MockDados.resumir(buscar("backups").getId()).getTipoPrincipal());
        assertNull(MockDados.resumir("id-inventado"));
    }
}
