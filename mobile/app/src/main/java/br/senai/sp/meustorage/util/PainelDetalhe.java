package br.senai.sp.meustorage.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.api.Config;
import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;

// Preenche as views do layout conteudo_detalhe.xml.
// É usada pela DetalheArquivoActivity (celular) e pelo painel da direita
// da ArquivosActivity (tablet). Assim o mesmo código serve aos dois, sem Fragments.
public class PainelDetalhe {

    public interface Acoes {
        void aoBaixar(Arquivo arquivo);

        // Chamado depois que o usuário confirmou a exclusão
        void aoExcluir(Arquivo arquivo);
    }

    private final Activity activity;
    private final View raiz;
    private final StorageRepository repositorio;
    private final Acoes acoes;

    private final ImageView imgPrevia;
    private final ProgressBar progressPrevia;
    private final TextView txtNome;
    private final TextView txtTipo;
    private final TextView txtTamanho;
    private final TextView txtEnviadoEm;
    private final TextView txtBucket;
    private final TextView txtSha;
    private final MaterialButton btnBaixar;
    private final MaterialButton btnExcluir;

    private Arquivo arquivo;

    public PainelDetalhe(Activity activity, View raiz, StorageRepository repositorio, Acoes acoes) {
        this.activity = activity;
        this.raiz = raiz;
        this.repositorio = repositorio;
        this.acoes = acoes;

        imgPrevia = raiz.findViewById(R.id.imgPrevia);
        progressPrevia = raiz.findViewById(R.id.progressPrevia);
        txtNome = raiz.findViewById(R.id.txtNomeDetalhe);
        txtTipo = raiz.findViewById(R.id.txtTipo);
        txtTamanho = raiz.findViewById(R.id.txtTamanho);
        txtEnviadoEm = raiz.findViewById(R.id.txtEnviadoEm);
        txtBucket = raiz.findViewById(R.id.txtBucket);
        txtSha = raiz.findViewById(R.id.txtSha);
        btnBaixar = raiz.findViewById(R.id.btnBaixar);
        btnExcluir = raiz.findViewById(R.id.btnExcluir);

        btnBaixar.setOnClickListener(v -> {
            if (arquivo != null) {
                acoes.aoBaixar(arquivo);
            }
        });
        btnExcluir.setOnClickListener(v -> confirmarExclusao());
    }

    public void mostrar(Arquivo novoArquivo, Bucket bucket) {
        arquivo = novoArquivo;
        raiz.setVisibility(View.VISIBLE);
        setBotoesHabilitados(true);

        txtNome.setText(novoArquivo.getNomeOriginal());
        txtTipo.setText(novoArquivo.getTipoConteudo() != null ? novoArquivo.getTipoConteudo() : "—");
        txtTamanho.setText(Formatos.tamanho(novoArquivo.getTamanhoBytes()));
        txtEnviadoEm.setText(Formatos.data(novoArquivo.getCriadoEm()));
        txtBucket.setText(bucket != null ? bucket.getNome() : "—");
        txtSha.setText(Formatos.shaAbreviado(novoArquivo.getSha256()));

        carregarPrevia(novoArquivo);
    }

    public void esconder() {
        arquivo = null;
        raiz.setVisibility(View.GONE);
    }

    public Arquivo getArquivo() {
        return arquivo;
    }

    public void setBotoesHabilitados(boolean habilitados) {
        btnBaixar.setEnabled(habilitados);
        btnExcluir.setEnabled(habilitados);
    }

    // Baixa e grava no local escolhido pelo usuário
    public void salvar(Arquivo alvo, Uri destino) {
        btnBaixar.setEnabled(false);
        repositorio.salvarArquivo(alvo, destino, new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                btnBaixar.setEnabled(true);
                Toast.makeText(activity, R.string.download_concluido, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                btnBaixar.setEnabled(true);
                if (MensagensErro.sessaoExpirou(activity, codigo)) return;
                Toast.makeText(activity, MensagensErro.texto(activity, codigo, mensagem), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmarExclusao() {
        Arquivo alvo = arquivo;
        if (alvo == null) {
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.confirmar_exclusao_titulo)
                .setMessage(activity.getString(R.string.confirmar_exclusao_msg, alvo.getNomeOriginal()))
                .setNegativeButton(R.string.botao_cancelar, null)
                .setPositiveButton(R.string.botao_excluir, (dialogo, botao) -> acoes.aoExcluir(alvo))
                .show();
    }

    // Prévia só para imagens de até 10 MB; nos outros casos, fica o ícone
    private void carregarPrevia(Arquivo alvo) {
        mostrarIcone(alvo);
        progressPrevia.setVisibility(View.GONE);
        if (!alvo.isImagem() || alvo.getTamanhoBytes() > Config.LIMITE_PREVIA_BYTES) {
            return;
        }

        progressPrevia.setVisibility(View.VISIBLE);
        repositorio.carregarPrevia(alvo, new RepositorioCallback<byte[]>() {
            @Override
            public void onSucesso(byte[] bytes) {
                if (arquivo != alvo) return; // o usuário já escolheu outro arquivo
                progressPrevia.setVisibility(View.GONE);
                Bitmap bitmap = decodificar(bytes);
                if (bitmap != null) {
                    imgPrevia.setPadding(0, 0, 0, 0);
                    imgPrevia.clearColorFilter(); // a foto aparece com as cores reais
                    imgPrevia.setImageBitmap(bitmap);
                }
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (arquivo != alvo) return;
                progressPrevia.setVisibility(View.GONE);
                MensagensErro.sessaoExpirou(activity, codigo);
            }
        });
    }

    private void mostrarIcone(Arquivo alvo) {
        int espaco = Math.round(64 * activity.getResources().getDisplayMetrics().density);
        imgPrevia.setPadding(espaco, espaco, espaco, espaco);
        // Ícone grande na cor do tipo (imagem azul, PDF vermelho, planilha verde, outros laranja)
        String tipo = Formatos.tipo(alvo);
        imgPrevia.setImageResource(Formatos.iconeDoTipo(tipo));
        imgPrevia.setColorFilter(Formatos.corDoTipo(activity, tipo));
    }

    // Reduz a imagem (inSampleSize) para não estourar a memória com fotos grandes
    private static Bitmap decodificar(byte[] bytes) {
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opcoes);
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) {
            return null;
        }

        int amostra = 1;
        while (opcoes.outWidth / amostra > 1200 || opcoes.outHeight / amostra > 1200) {
            amostra *= 2;
        }

        BitmapFactory.Options reduzir = new BitmapFactory.Options();
        reduzir.inSampleSize = amostra;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, reduzir);
    }
}
