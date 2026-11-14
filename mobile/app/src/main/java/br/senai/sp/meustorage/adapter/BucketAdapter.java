package br.senai.sp.meustorage.adapter;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.ResumoBucket;
import br.senai.sp.meustorage.util.Formatos;

// Um só adapter para os 3 modos. Cada modo tem o seu layout de item,
// mas todos usam os mesmos ids, então o ViewHolder é o mesmo.
public class BucketAdapter extends RecyclerView.Adapter<BucketAdapter.BucketViewHolder> {

    public static final int MODO_ROTATIVO = 0;
    public static final int MODO_GRADE = 1;
    public static final int MODO_LISTA = 2;

    public interface Acao {
        void aoClicar(Bucket bucket, int posicao);

        void aoExcluir(Bucket bucket, int posicao);
    }

    private final List<Bucket> lista = new ArrayList<>();
    private final Map<String, ResumoBucket> resumos = new HashMap<>();
    private final Acao acao;
    private int modo = MODO_ROTATIVO;
    private int posicaoAtiva = 0; // centro do rotativo
    private String idDestaque = ""; // grade e lista: último bucket aberto

    public BucketAdapter(Acao acao) {
        this.acao = acao;
    }

    public void setLista(List<Bucket> novaLista) {
        lista.clear();
        if (novaLista != null) {
            lista.addAll(novaLista);
        }
        notifyDataSetChanged();
    }

    public void setModo(int novoModo) {
        modo = novoModo;
        notifyDataSetChanged();
    }

    public int getModo() {
        return modo;
    }

    public void setResumo(String bucketId, ResumoBucket resumo) {
        resumos.put(bucketId, resumo);
        for (int i = 0; i < lista.size(); i++) {
            if (bucketId.equals(lista.get(i).getId())) {
                notifyItemChanged(i);
            }
        }
    }

    public ResumoBucket getResumo(String bucketId) {
        return resumos.get(bucketId);
    }

    public Map<String, ResumoBucket> getResumos() {
        return resumos;
    }

    public void setPosicaoAtiva(int posicao) {
        if (posicao == posicaoAtiva) return;
        int anterior = posicaoAtiva;
        posicaoAtiva = posicao;
        // Só avisa posições que existem (a lista pode ter diminuído depois de uma exclusão)
        if (anterior >= 0 && anterior < lista.size()) notifyItemChanged(anterior);
        if (posicao >= 0 && posicao < lista.size()) notifyItemChanged(posicao);
    }

    public int getPosicaoAtiva() {
        return posicaoAtiva;
    }

    public void setIdDestaque(String id) {
        idDestaque = id != null ? id : "";
        notifyDataSetChanged();
    }

    public Bucket getItem(int posicao) {
        return lista.get(posicao);
    }

    @Override
    public int getItemViewType(int position) {
        return modo;
    }

    @NonNull
    @Override
    public BucketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == MODO_ROTATIVO ? R.layout.item_bucket_rotativo
                : viewType == MODO_LISTA ? R.layout.item_bucket_lista
                : R.layout.item_bucket_grade;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new BucketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BucketViewHolder holder, int position) {
        Bucket bucket = lista.get(position);
        Context context = holder.itemView.getContext();
        ResumoBucket resumo = resumos.get(bucket.getId());

        holder.txtNome.setText(bucket.getNome());

        // Ícone: tipo de arquivo que mais aparece (vazio ou carregando = pasta verde)
        Formatos.aplicarIcone(holder.imgTipo, holder.fundoIcone, resumo != null ? resumo.getTipoPrincipal() : null);

        // Números: Imagens · Docs · Outros
        holder.txtImagens.setText(numero(context, resumo, resumo != null ? resumo.getImagens() : 0));
        holder.txtDocs.setText(numero(context, resumo, resumo != null ? resumo.getDocs() : 0));
        holder.txtOutros.setText(numero(context, resumo, resumo != null ? resumo.getOutros() : 0));

        // Sem efeito de escala fora do rotativo (o RecyclerView reaproveita as views)
        if (modo != MODO_ROTATIVO) {
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.itemView.setAlpha(1f);
        }

        boolean ativo = modo == MODO_ROTATIVO ? position == posicaoAtiva : bucket.getId().equals(idDestaque);
        aplicarDestaque(holder.card, ativo);

        if (bucket.isExcluindo()) {
            // Sendo apagado no servidor: esmaecido e sem clique
            holder.txtData.setText(R.string.bucket_excluindo);
            holder.txtData.setTextColor(ContextCompat.getColor(context, R.color.acento));
            holder.card.setAlpha(0.4f);
            holder.btnExcluir.setVisibility(View.GONE);
            holder.card.setOnClickListener(null);
            holder.card.setClickable(false);
        } else {
            holder.txtData.setText(context.getString(R.string.criado_em, Formatos.data(bucket.getCriadoEm())));
            holder.txtData.setTextColor(ContextCompat.getColor(context, R.color.texto_secundario));
            holder.card.setAlpha(1f);
            holder.btnExcluir.setVisibility(View.VISIBLE);
            holder.card.setOnClickListener(v -> acao.aoClicar(bucket, holder.getBindingAdapterPosition()));
            holder.btnExcluir.setOnClickListener(v -> acao.aoExcluir(bucket, holder.getBindingAdapterPosition()));
        }

        holder.card.setContentDescription(context.getString(R.string.cd_bucket_posicao,
                position + 1, lista.size(), bucket.getNome()));
    }

    // Borda verde e brilho no cartão em destaque
    private void aplicarDestaque(MaterialCardView card, boolean ativo) {
        Context context = card.getContext();
        float densidade = context.getResources().getDisplayMetrics().density;
        int verde = ContextCompat.getColor(context, R.color.acento);
        if (ativo) {
            card.setStrokeColor(verde);
            card.setStrokeWidth(Math.round(2 * densidade));
            card.setCardElevation(12 * densidade);
            // Sombra verde (brilho) a partir do Android 9
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                card.setOutlineSpotShadowColor(verde);
                card.setOutlineAmbientShadowColor(verde);
            }
        } else {
            card.setStrokeColor(ContextCompat.getColor(context, R.color.borda_suave));
            card.setStrokeWidth(Math.round(densidade));
            card.setCardElevation(0f);
        }
    }

    private static String numero(Context context, ResumoBucket resumo, int valor) {
        return resumo == null ? context.getString(R.string.sem_numero) : context.getString(R.string.numero, valor);
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class BucketViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View fundoIcone;
        final ImageView imgTipo;
        final TextView txtNome;
        final TextView txtData;
        final TextView txtImagens;
        final TextView txtDocs;
        final TextView txtOutros;
        final TextView btnExcluir;

        BucketViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            fundoIcone = itemView.findViewById(R.id.fundoIcone);
            imgTipo = itemView.findViewById(R.id.imgTipo);
            txtNome = itemView.findViewById(R.id.txtNomeBucket);
            txtData = itemView.findViewById(R.id.txtDataBucket);
            txtImagens = itemView.findViewById(R.id.txtImagens);
            txtDocs = itemView.findViewById(R.id.txtDocs);
            txtOutros = itemView.findViewById(R.id.txtOutros);
            btnExcluir = itemView.findViewById(R.id.btnExcluir);
        }
    }
}
