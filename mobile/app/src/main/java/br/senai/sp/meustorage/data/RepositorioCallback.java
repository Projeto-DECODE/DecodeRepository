package br.senai.sp.meustorage.data;

// Resposta do StorageRepository para as Activities.
// Elas não sabem se a resposta veio do mock ou da API.
public interface RepositorioCallback<T> {

    void onSucesso(T resultado);

    // codigo: código do back-end (ex.: BUCKET_NOT_FOUND) ou do app (ex.: SEM_CONEXAO)
    // mensagem: texto do servidor, quando houver (pode ser null)
    void onErro(String codigo, String mensagem);
}
