import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vitest/config";

// Testes automáticos do site: npm test (ou npm run test:cobertura)
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      include: ["src/resources/**", "src/utils/**", "src/components/**"],
      exclude: ["**/index.ts", "src/test/**", "**/*.test.*"],
    },
  },
});
