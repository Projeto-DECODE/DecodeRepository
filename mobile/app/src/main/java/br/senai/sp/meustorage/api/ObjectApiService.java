package br.senai.sp.meustorage.api;

import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Pagina;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

// object-service (porta 8083)
public interface ObjectApiService {

    @GET("api/buckets/{bucketId}/objects")
    Call<Pagina<Arquivo>> listar(@Header("Authorization") String token,
                                 @Path("bucketId") String bucketId,
                                 @Query("page") int pagina,
                                 @Query("size") int tamanho);

    // Campo "file" do multipart/form-data
    @Multipart
    @POST("api/buckets/{bucketId}/objects")
    Call<Arquivo> enviar(@Header("Authorization") String token,
                         @Path("bucketId") String bucketId,
                         @Part MultipartBody.Part arquivo);

    @GET("api/buckets/{bucketId}/objects/{objectId}")
    Call<Arquivo> buscar(@Header("Authorization") String token,
                         @Path("bucketId") String bucketId,
                         @Path("objectId") String objectId);

    // @Streaming: os bytes chegam aos poucos, sem carregar tudo na memória
    @Streaming
    @GET("api/buckets/{bucketId}/objects/{objectId}/download")
    Call<ResponseBody> baixar(@Header("Authorization") String token,
                              @Path("bucketId") String bucketId,
                              @Path("objectId") String objectId);

    @DELETE("api/buckets/{bucketId}/objects/{objectId}")
    Call<Void> excluir(@Header("Authorization") String token,
                       @Path("bucketId") String bucketId,
                       @Path("objectId") String objectId);
}
