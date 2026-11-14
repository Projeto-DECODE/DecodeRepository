package br.senai.sp.meustorage.api;

public final class Config {

    // true  = dados de exemplo em memória, sem back-end.
    // false = API real (antes, rode o "adb reverse" das três portas).
    public static final boolean USAR_MOCK = true;

    // Celular por cabo USB com "adb reverse": localhost.
    // No emulador seria http://10.0.2.2:PORTA/
    public static final String URL_AUTH = "http://localhost:8081/";
    public static final String URL_BUCKETS = "http://localhost:8082/";
    public static final String URL_OBJETOS = "http://localhost:8083/";

    public static final int TAMANHO_PAGINA = 20;
    public static final long LIMITE_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final long LIMITE_PREVIA_BYTES = 10L * 1024 * 1024;
    public static final long ATRASO_MOCK_MS = 600;

    private Config() {
    }
}
