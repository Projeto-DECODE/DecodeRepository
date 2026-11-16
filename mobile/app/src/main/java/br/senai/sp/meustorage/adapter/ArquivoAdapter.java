package br.senai.sp.meustorage.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.util.Formatos;

public class ArquivoAdapter extends RecyclerView.Adapter<ArquivoAdapter.ArquivoViewHolder> {

    public interface Acao {
        void aoClicar(Arquivo arquivo);
    }

    private final List<Arquivo> lista = new ArrayList<>();
    private final Acao acao;

    public ArquivoAdapter(Acao acao) {
        this.acao = acao;
    }

    // Primeira página: substitui a lista
    public void setLista(List<Arquivo> novaLista) {
        lista.clear();
        if (novaLista != null) {
            lista.addAll(novaLista);
        }
        notifyDataSetChanged();
    }

    // Próximas páginas: acrescenta no fim
    public void adicionar(List<Arquivo> maisItens) {
        if (maisItens == null || maisItens.isEmpty()) {
            return;
        }
        int inicio = lista.size();
        lista.addAll(maisItens);
        notifyItemRangeInserted(inicio, maisItens.size());
    }

    public Arquivo remover(int posicao) {
        Arquivo arquivo = lista.remove(posicao);
        notifyItemRemoved(posicao);
        return arquivo;
    }

    public void inserir(int posicao, Arquivo arquivo) {
        int pos = Math.min(posicao, lista.size());
        lista.add(pos, arquivo);
        notifyItemInserted(pos);
    }

    @NonNull
    @Override
    public ArquivoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_arquivo, parent, false);
        return new ArquivoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArquivoViewHolder holder, int position) {
        Arquivo arquivo = lista.get(position);
        holder.imgTipo.setImageResource(arquivo.isImagem() ? R.drawable.ic_image : R.drawable.ic_document);
        holder.txtNome.setText(arquivo.getNomeOriginal());
        holder.txtInfo.setText(holder.itemView.getContext().getString(R.string.arquivo_linha,
                Formatos.tamanho(arquivo.getTamanhoBytes()),
                Formatos.data(arquivo.getCriadoEm())));
        holder.itemView.setOnClickListener(v -> acao.aoClicar(arquivo));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class ArquivoViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgTipo;
        final TextView txtNome;
        final TextView txtInfo;

        ArquivoViewHolder(@NonNull View itemView) {
            super(itemView);
            imgTipo = itemView.findViewById(R.id.imgTipo);
            txtNome = itemView.findViewById(R.id.txtNomeArquivo);
            txtInfo = itemView.findViewById(R.id.txtInfoArquivo);
        }
    }
}
