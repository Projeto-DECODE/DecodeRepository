package br.senai.sp.meustorage.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.util.Formatos;

public class BucketAdapter extends RecyclerView.Adapter<BucketAdapter.BucketViewHolder> {

    public interface Acao {
        void aoClicar(Bucket bucket);
    }

    private final List<Bucket> lista = new ArrayList<>();
    private final Acao acao;

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

    public Bucket getItem(int posicao) {
        return lista.get(posicao);
    }

    public Bucket remover(int posicao) {
        Bucket bucket = lista.remove(posicao);
        notifyItemRemoved(posicao);
        return bucket;
    }

    public void inserir(int posicao, Bucket bucket) {
        int pos = Math.min(posicao, lista.size());
        lista.add(pos, bucket);
        notifyItemInserted(pos);
    }

    @NonNull
    @Override
    public BucketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bucket, parent, false);
        return new BucketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BucketViewHolder holder, int position) {
        Bucket bucket = lista.get(position);
        holder.txtNome.setText(bucket.getNome());

        if (bucket.isExcluindo()) {
            // Esmaecido e sem clique
            holder.txtData.setText(R.string.bucket_excluindo);
            holder.itemView.setAlpha(0.5f);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            String data = Formatos.data(bucket.getCriadoEm());
            holder.txtData.setText(holder.itemView.getContext().getString(R.string.criado_em, data));
            holder.itemView.setAlpha(1f);
            holder.itemView.setOnClickListener(v -> acao.aoClicar(bucket));
        }
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class BucketViewHolder extends RecyclerView.ViewHolder {
        final TextView txtNome;
        final TextView txtData;

        BucketViewHolder(@NonNull View itemView) {
            super(itemView);
            txtNome = itemView.findViewById(R.id.txtNomeBucket);
            txtData = itemView.findViewById(R.id.txtDataBucket);
        }
    }
}
