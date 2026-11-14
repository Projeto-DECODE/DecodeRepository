package br.senai.sp.meustorage.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.senai.sp.meustorage.R;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.util.Formatos;

public class ArquivoAdapter extends RecyclerView.Adapter<ArquivoAdapter.ArquivoViewHolder> {

    public interface Acao {
        void aoClicar(Arquivo arquivo);
    }

    // Todos os arquivos carregados e os que aparecem depois da pesquisa
    private final List<Arquivo> todos = new ArrayList<>();
    private final List<Arquivo> lista = new ArrayList<>();
    private final Acao acao;

    private String filtroTexto = "";
    private String filtroTipo = null; // null = todos os tipos
    private int indiceRemovido = -1;

    public ArquivoAdapter(Acao acao) {
        this.acao = acao;
    }

    // Primeira página: substitui a lista
    public void setLista(List<Arquivo> novaLista) {
        todos.clear();
        if (novaLista != null) {
            todos.addAll(novaLista);
        }
        filtrar();
    }

    // Próximas páginas: acrescenta no fim
    public void adicionar(List<Arquivo> maisItens) {
        if (maisItens == null || maisItens.isEmpty()) {
            return;
        }
        todos.addAll(maisItens);
        filtrar();
    }

    // Pesquisa por nome e tipo (nos arquivos já carregados)
    public void setFiltro(String texto, String tipo) {
        filtroTexto = texto != null ? texto.trim().toLowerCase(Locale.ROOT) : "";
        filtroTipo = tipo;
        filtrar();
    }

    public boolean isFiltrando() {
        return !filtroTexto.isEmpty() || filtroTipo != null;
    }

    private void filtrar() {
        lista.clear();
        for (Arquivo arquivo : todos) {
            String nome = arquivo.getNomeOriginal() != null ? arquivo.getNomeOriginal().toLowerCase(Locale.ROOT) : "";
            boolean passaNome = filtroTexto.isEmpty() || nome.contains(filtroTexto);
            boolean passaTipo = filtroTipo == null || filtroTipo.equals(Formatos.tipo(arquivo));
            if (passaNome && passaTipo) {
                lista.add(arquivo);
            }
        }
        notifyDataSetChanged();
    }

    public Arquivo remover(int posicao) {
        Arquivo arquivo = lista.remove(posicao);
        indiceRemovido = todos.indexOf(arquivo);
        todos.remove(arquivo);
        notifyItemRemoved(posicao);
        return arquivo;
    }

    // Desfazer: devolve o arquivo para o mesmo lugar
    public void inserir(Arquivo arquivo) {
        int indice = indiceRemovido >= 0 ? Math.min(indiceRemovido, todos.size()) : todos.size();
        todos.add(indice, arquivo);
        filtrar();
    }

    public int getTotalCarregados() {
        return todos.size();
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
        Formatos.aplicarIcone(holder.imgTipo, holder.fundoIcone, Formatos.tipo(arquivo));
        holder.txtNome.setText(arquivo.getNomeOriginal());
        holder.txtInfo.setText(holder.itemView.getContext().getString(R.string.arquivo_linha,
                Formatos.tamanho(arquivo.getTamanhoBytes()),
                Formatos.data(arquivo.getCriadoEm())));
        holder.card.setOnClickListener(v -> acao.aoClicar(arquivo));
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    static class ArquivoViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View fundoIcone;
        final ImageView imgTipo;
        final TextView txtNome;
        final TextView txtInfo;

        ArquivoViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            fundoIcone = itemView.findViewById(R.id.fundoIcone);
            imgTipo = itemView.findViewById(R.id.imgTipo);
            txtNome = itemView.findViewById(R.id.txtNomeArquivo);
            txtInfo = itemView.findViewById(R.id.txtInfoArquivo);
        }
    }
}
