package br.senai.sp.meustorage.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.Pagina;
import br.senai.sp.meustorage.model.ResumoBucket;
import br.senai.sp.meustorage.model.Usuario;
import br.senai.sp.meustorage.util.MensagensErro;

// Dados de exemplo em memória, no mesmo formato do back-end.
// Somem quando o app é fechado.
//
// Contas de teste (senha 12345678):
//   teste@teste.com   -> conta normal
//   existe@teste.com  -> cadastrar com ele dá "e-mail já cadastrado"
//   expira@teste.com  -> sessão de 60 segundos (simula token expirado)
public final class MockDados {

    public static final String EMAIL_EXISTENTE = "existe@teste.com";
    public static final String EMAIL_EXPIRA = "expira@teste.com";

    private static final String TIPO_PDF = "application/pdf";
    private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TIPO_JPG = "image/jpeg";
    private static final String TIPO_PNG = "image/png";

    // e-mail -> { nome, senha }
    private static final Map<String, String[]> usuarios = new HashMap<>();
    private static final List<Bucket> buckets = new ArrayList<>();
    // bucketId -> arquivos (mais recente primeiro)
    private static final Map<String, List<Arquivo>> arquivos = new HashMap<>();
    // arquivoId -> bytes
    private static final Map<String, byte[]> conteudos = new HashMap<>();
    private static boolean iniciado = false;

    private MockDados() {
    }

    public static synchronized void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;

        usuarios.put("teste@teste.com", new String[]{"Usuário Teste", "12345678"});
        usuarios.put(EMAIL_EXISTENTE, new String[]{"Conta Existente", "12345678"});
        usuarios.put(EMAIL_EXPIRA, new String[]{"Sessão Curta", "12345678"});

        Bucket documentos = novoBucket("documentos", 40);
        exemplo(documentos, "ata-reuniao.pdf", TIPO_PDF, 120_400, 2);
        exemplo(documentos, "orcamento-2026.xlsx", TIPO_XLSX, 52_480, 10);
        exemplo(documentos, "relatorio-mensal-com-um-nome-bem-comprido.pdf", TIPO_PDF, 845_312, 30);

        // 26 arquivos: dá para testar a paginação (20 por página)
        Bucket fotos = novoBucket("fotos-obra", 30);
        exemplo(fotos, "panorama-obra.jpg", TIPO_JPG, 14_680_064, 0); // acima de 10 MB: sem prévia
        for (int i = 25; i >= 1; i--) {
            boolean png = i % 3 == 0;
            String nome = String.format(Locale.ROOT, "foto-%03d.%s", i, png ? "png" : "jpg");
            exemplo(fotos, nome, png ? TIPO_PNG : TIPO_JPG, 800_000L + i * 37_000L, 26 - i);
        }

        Bucket contratos = novoBucket("contratos", 20);
        exemplo(contratos, "contrato-fornecedor.pdf", TIPO_PDF, 310_000, 15);

        Bucket relatorios = novoBucket("relatorios-mensais", 12);
        String[] meses = {"janeiro", "fevereiro", "marco", "abril", "maio", "junho"};
        for (String mes : meses) {
            exemplo(relatorios, "relatorio-" + mes + ".xlsx", TIPO_XLSX, 48_000, 8);
        }
        exemplo(relatorios, "resumo-semestre.pdf", TIPO_PDF, 220_000, 7);

        Bucket videos = novoBucket("videos-treinamento", 9);
        exemplo(videos, "integracao-nr18.mp4", "video/mp4", 8_400_000, 6);
        exemplo(videos, "uso-de-epi.mp4", "video/mp4", 6_100_000, 6);
        exemplo(videos, "capa.png", TIPO_PNG, 320_000, 6);

        novoBucket("backups", 5); // vazio: pode ser excluído
    }

    // ---------- Autenticação ----------

    public static synchronized String validarLogin(String email, String senha) {
        String[] usuario = usuarios.get(email.toLowerCase(Locale.ROOT));
        if (usuario == null || !usuario[1].equals(senha)) {
            return MensagensErro.INVALID_CREDENTIALS;
        }
        return null;
    }

    public static long expiracaoSegundos(String email) {
        return EMAIL_EXPIRA.equalsIgnoreCase(email) ? 60 : 900;
    }

    // Retorna null se o e-mail já existir
    public static synchronized Usuario cadastrar(String nome, String email, String senha) {
        String chave = email.toLowerCase(Locale.ROOT);
        if (usuarios.containsKey(chave)) {
            return null;
        }
        usuarios.put(chave, new String[]{nome, senha});
        return new Usuario(UUID.randomUUID().toString(), nome, email, agora());
    }

    // ---------- Buckets ----------

    public static synchronized List<Bucket> listarBuckets() {
        return new ArrayList<>(buckets);
    }

    public static synchronized boolean existeBucketComNome(String nome) {
        for (Bucket b : buckets) {
            if (b.getNome().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean existeBucket(String bucketId) {
        return arquivos.containsKey(bucketId);
    }

    public static synchronized Bucket criarBucket(String nome) {
        return novoBucket(nome, 0);
    }

    // Retorna o código de erro, ou null se excluiu
    public static synchronized String excluirBucket(String bucketId) {
        List<Arquivo> lista = arquivos.get(bucketId);
        if (lista == null) {
            return MensagensErro.BUCKET_NOT_FOUND;
        }
        if (!lista.isEmpty()) {
            return MensagensErro.BUCKET_NOT_EMPTY;
        }
        arquivos.remove(bucketId);
        buckets.removeIf(b -> b.getId().equals(bucketId));
        return null;
    }

    // ---------- Arquivos ----------

    public static synchronized Pagina<Arquivo> listarArquivos(String bucketId, int pagina, int tamanho) {
        List<Arquivo> todos = arquivos.get(bucketId);
        if (todos == null) {
            todos = new ArrayList<>();
        }
        int total = todos.size();
        int inicio = Math.min(pagina * tamanho, total);
        int fim = Math.min(inicio + tamanho, total);
        int totalPaginas = (total + tamanho - 1) / tamanho;
        return new Pagina<>(new ArrayList<>(todos.subList(inicio, fim)), pagina, tamanho, total, totalPaginas);
    }

    // Imagens · Docs · Outros e o tipo mais comum. Retorna null se o bucket não existir
    public static synchronized ResumoBucket resumir(String bucketId) {
        List<Arquivo> lista = arquivos.get(bucketId);
        if (lista == null) {
            return null;
        }
        ResumoBucket resumo = new ResumoBucket();
        for (Arquivo arquivo : lista) {
            resumo.adicionar(arquivo);
        }
        resumo.setTotal(lista.size());
        return resumo;
    }

    // Retorna null se o bucket não existir
    public static synchronized Arquivo adicionarArquivo(String bucketId, String nome, String tipo, byte[] bytes) {
        List<Arquivo> lista = arquivos.get(bucketId);
        if (lista == null) {
            return null;
        }
        Arquivo arquivo = new Arquivo(UUID.randomUUID().toString(), bucketId, nome,
                bytes.length, tipo, sha256(bytes), agora());
        lista.add(0, arquivo);
        conteudos.put(arquivo.getId(), bytes);
        return arquivo;
    }

    // Retorna o código de erro, ou null se excluiu
    public static synchronized String excluirArquivo(String bucketId, String arquivoId) {
        List<Arquivo> lista = arquivos.get(bucketId);
        if (lista == null) {
            return MensagensErro.BUCKET_NOT_FOUND;
        }
        boolean removeu = lista.removeIf(a -> a.getId().equals(arquivoId));
        if (!removeu) {
            return MensagensErro.OBJECT_NOT_FOUND;
        }
        conteudos.remove(arquivoId);
        return null;
    }

    // Bytes do arquivo. Para os exemplos, gera o conteúdo na primeira vez.
    public static synchronized byte[] conteudo(Arquivo arquivo) {
        List<Arquivo> lista = arquivos.get(arquivo.getBucketId());
        boolean existe = false;
        if (lista != null) {
            for (Arquivo a : lista) {
                if (a.getId().equals(arquivo.getId())) {
                    existe = true;
                    break;
                }
            }
        }
        if (!existe) {
            return null;
        }
        byte[] bytes = conteudos.get(arquivo.getId());
        if (bytes == null) {
            if (arquivo.isImagem()) {
                bytes = gerarImagem(arquivo);
            } else {
                bytes = ("Arquivo de exemplo do MeuStorage: " + arquivo.getNomeOriginal())
                        .getBytes(StandardCharsets.UTF_8);
            }
            conteudos.put(arquivo.getId(), bytes);
        }
        return bytes;
    }

    // ---------- Auxiliares ----------

    private static Bucket novoBucket(String nome, int diasAtras) {
        Bucket bucket = new Bucket(UUID.randomUUID().toString(), nome, Bucket.STATUS_ATIVO, diasAtras(diasAtras));
        buckets.add(bucket);
        arquivos.put(bucket.getId(), new ArrayList<>());
        return bucket;
    }

    private static void exemplo(Bucket bucket, String nome, String tipo, long tamanho, int diasAtras) {
        Arquivo arquivo = new Arquivo(UUID.randomUUID().toString(), bucket.getId(), nome, tamanho, tipo,
                sha256(nome.getBytes(StandardCharsets.UTF_8)), diasAtras(diasAtras));
        List<Arquivo> lista = arquivos.get(bucket.getId());
        if (lista != null) {
            lista.add(arquivo);
        }
    }

    private static byte[] gerarImagem(Arquivo arquivo) {
        int[] cores = {0xFF1D4ED8, 0xFF047857, 0xFFB45309, 0xFF7C3AED, 0xFF0E7490};
        int cor = cores[Math.floorMod(arquivo.getNomeOriginal().hashCode(), cores.length)];

        Bitmap bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(cor);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(48f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(arquivo.getNomeOriginal(), 400f, 316f, paint);

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Bitmap.CompressFormat formato = TIPO_PNG.equals(arquivo.getTipoConteudo())
                ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        bitmap.compress(formato, 90, saida);
        bitmap.recycle();
        return saida.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String agora() {
        return Instant.now().toString();
    }

    private static String diasAtras(int dias) {
        return Instant.now().minus(dias, ChronoUnit.DAYS).toString();
    }
}
