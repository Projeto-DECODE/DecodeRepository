// Espelho do BucketResponse do bucket-service
export class Bucket {
  id?: string;
  name?: string;
  status?: string; // "ACTIVE" ou "DELETING"
  createdAt?: string;
}
