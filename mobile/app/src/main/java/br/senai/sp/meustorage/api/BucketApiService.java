package br.senai.sp.meustorage.api;

import java.util.List;
import java.util.Map;

import br.senai.sp.meustorage.model.Bucket;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

// bucket-service (porta 8082)
public interface BucketApiService {

    @GET("api/buckets")
    Call<List<Bucket>> listar(@Header("Authorization") String token);

    // Corpo: { "name" }
    @POST("api/buckets")
    Call<Bucket> criar(@Header("Authorization") String token,
                       @Body Map<String, String> corpo);

    @GET("api/buckets/{bucketId}")
    Call<Bucket> buscar(@Header("Authorization") String token,
                        @Path("bucketId") String bucketId);

    @DELETE("api/buckets/{bucketId}")
    Call<Void> excluir(@Header("Authorization") String token,
                       @Path("bucketId") String bucketId);
}
