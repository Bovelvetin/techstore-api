package cl.techstore.api.dto;

public class LoginResponse {
    private String token;
    private String tipo;
    private String expiracion;

    public LoginResponse(String token, String tipo, String expiracion) {
        this.token = token;
        this.tipo = tipo;
        this.expiracion = expiracion;
    }

    public String getToken() { return token; }
    public String getTipo() { return tipo; }
    public String getExpiracion() { return expiracion; }
}