package br.senai.sp.meustorage.model;

import java.io.Serializable;

import br.senai.sp.meustorage.util.Formatos;

// Resumo de um bucket, calculado no app a partir da lista de arquivos.
// Mostra os números "Imagens · Docs · Outros" e o tipo que mais aparece (ícone do cartão).
public class ResumoBucket implements Serializable {

    private long total;
    private int imagens;
    private int docs;
    private int outros;

    // Quantos arquivos de cada tipo (para achar o mais comum)
    private int qtdImagem;
    private int qtdPdf;
    private int qtdPlanilha;
    private int qtdOutro;

    public void adicionar(Arquivo arquivo) {
        String categoria = Formatos.categoria(arquivo);
        if (Formatos.CATEGORIA_IMAGEM.equals(categoria)) {
            imagens++;
        } else if (Formatos.CATEGORIA_DOC.equals(categoria)) {
            docs++;
        } else {
            outros++;
        }

        switch (Formatos.tipo(arquivo)) {
            case Formatos.TIPO_IMAGEM: qtdImagem++; break;
            case Formatos.TIPO_PDF: qtdPdf++; break;
            case Formatos.TIPO_PLANILHA: qtdPlanilha++; break;
            default: qtdOutro++;
        }
    }

    // Tipo que mais aparece, ou null se o bucket estiver vazio
    public String getTipoPrincipal() {
        int maior = Math.max(Math.max(qtdImagem, qtdPdf), Math.max(qtdPlanilha, qtdOutro));
        if (maior == 0) return null;
        if (maior == qtdImagem) return Formatos.TIPO_IMAGEM;
        if (maior == qtdPdf) return Formatos.TIPO_PDF;
        if (maior == qtdPlanilha) return Formatos.TIPO_PLANILHA;
        return Formatos.TIPO_OUTRO;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getImagens() {
        return imagens;
    }

    public int getDocs() {
        return docs;
    }

    public int getOutros() {
        return outros;
    }
}
