import { chamarApi } from "../api";
import { Sessao } from "../sessao";
import { TokenResposta, Usuario } from "./auth.resource";

class AuthService {
  baseURL: string = "/api/auth";

  async cadastrar(nome: string, email: string, senha: string): Promise<Usuario> {
    const response = await chamarApi(
      `${this.baseURL}/register`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: nome, email: email, password: senha }),
      },
      false
    );
    return await response.json();
  }

  // Em caso de sucesso, a sessão (token, validade e e-mail) já fica salva
  async login(email: string, senha: string): Promise<void> {
    const response = await chamarApi(
      `${this.baseURL}/login`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email, password: senha }),
      },
      false
    );
    const token: TokenResposta = await response.json();
    new Sessao().salvarLogin(token.accessToken ?? "", token.expiresIn ?? 0, email);
  }
}

export const useAuthService = () => new AuthService();
