package br.senai.sp.meustorage.api;

import java.util.Map;

import br.senai.sp.meustorage.model.TokenResposta;
import br.senai.sp.meustorage.model.Usuario;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

// auth-service (porta 8081)
public interface AuthApiService {

    // Corpo: { "name", "email", "password" }
    @POST("api/auth/register")
    Call<Usuario> cadastrar(@Body Map<String, String> corpo);

    // Corpo: { "email", "password" }
    @POST("api/auth/login")
    Call<TokenResposta> login(@Body Map<String, String> corpo);
}
