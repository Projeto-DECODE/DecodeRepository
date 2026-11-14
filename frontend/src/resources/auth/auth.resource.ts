// Espelho do UserResponse do auth-service
export class Usuario {
  id?: string;
  name?: string;
  email?: string;
  createdAt?: string;
}

// Espelho do TokenResponse do auth-service
export class TokenResposta {
  accessToken?: string;
  tokenType?: string;
  expiresIn?: number;
}
