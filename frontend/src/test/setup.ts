import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// Limpa a tela, o localStorage e os "falsos" (mocks) depois de cada teste
afterEach(() => {
  cleanup();
  window.localStorage.clear();
  vi.restoreAllMocks();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

// O Next.js não roda nos testes: troca o roteador e o Link por versões simples
export const navegacao = { push: vi.fn(), replace: vi.fn() };

vi.mock("next/navigation", () => ({
  useRouter: () => navegacao,
  useParams: () => ({}),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", async () => {
  const React = await import("react");
  return {
    default: ({ href, children, ...resto }: { href: string; children: React.ReactNode }) =>
      React.createElement("a", { href, ...resto }, children),
  };
});
