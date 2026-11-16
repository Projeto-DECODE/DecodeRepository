package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Pagina<T> {

    @SerializedName("content")
    private List<T> conteudo;

    @SerializedName("page")
    private int pagina;

    @SerializedName("size")
    private int tamanho;

    @SerializedName("totalElements")
    private long totalElementos;

    @SerializedName("totalPages")
    private int totalPaginas;

    public Pagina() {
    }

    public Pagina(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {
        this.conteudo = conteudo;
        this.pagina = pagina;
        this.tamanho = tamanho;
        this.totalElementos = totalElementos;
        this.totalPaginas = totalPaginas;
    }

    public List<T> getConteudo() {
        return conteudo != null ? conteudo : new ArrayList<>();
    }

    public int getPagina() {
        return pagina;
    }

    public int getTamanho() {
        return tamanho;
    }

    public long getTotalElementos() {
        return totalElementos;
    }

    public int getTotalPaginas() {
        return totalPaginas;
    }
}
