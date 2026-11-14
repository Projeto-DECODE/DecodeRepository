package br.senai.sp.meustorage.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// Cria um Retrofit para cada serviço (cada um tem a sua porta).
// O token vai em cada chamada pelo parâmetro @Header("Authorization").
public final class ApiClient {

    private static AuthApiService auth;
    private static BucketApiService buckets;
    private static ObjectApiService objetos;

    private ApiClient() {
    }

    public static synchronized AuthApiService auth() {
        if (auth == null) {
            auth = criar(Config.URL_AUTH).create(AuthApiService.class);
        }
        return auth;
    }

    public static synchronized BucketApiService buckets() {
        if (buckets == null) {
            buckets = criar(Config.URL_BUCKETS).create(BucketApiService.class);
        }
        return buckets;
    }

    public static synchronized ObjectApiService objetos() {
        if (objetos == null) {
            objetos = criar(Config.URL_OBJETOS).create(ObjectApiService.class);
        }
        return objetos;
    }

    private static Retrofit criar(String url) {
        return new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
