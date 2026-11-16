import { chamarApi } from "../api";
import { Bucket } from "./bucket.resource";

class BucketService {
  baseURL: string = "/api/buckets";

  async listar(): Promise<Bucket[]> {
    const response = await chamarApi(this.baseURL);
    return await response.json();
  }

  async buscar(bucketId: string): Promise<Bucket> {
    const response = await chamarApi(`${this.baseURL}/${bucketId}`);
    return await response.json();
  }

  async criar(nome: string): Promise<Bucket> {
    const response = await chamarApi(this.baseURL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: nome }),
    });
    return await response.json();
  }

  async excluir(bucketId: string): Promise<void> {
    await chamarApi(`${this.baseURL}/${bucketId}`, { method: "DELETE" });
  }
}

export const useBucketService = () => new BucketService();
