import { HttpErrorResponse } from '@angular/common/http';

/** Corpo de erro padrão da API: `{ status, error, message }`. */
interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
}

export type ApiErrorMessages = Partial<Record<number, string>>;

/**
 * Monta a mensagem a exibir para um erro HTTP.
 * Ordem: mensagem específica do status em `messages` → `message` do corpo (422) → texto padrão do status → `fallback`.
 */
export function apiErrorMessage(error: unknown, fallback: string, messages: ApiErrorMessages = {}): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  const specific = messages[error.status];
  if (specific) {
    return specific;
  }

  const body = error.error as ApiErrorBody | null;
  const backendMessage = typeof body?.message === 'string' && body.message.trim() ? body.message : null;

  switch (error.status) {
    case 0:
      return 'Não foi possível conectar ao servidor.';
    case 403:
      return 'Você não tem permissão para esta ação.';
    case 404:
      return backendMessage ?? 'Registro não encontrado.';
    case 409:
    case 422:
      return backendMessage ?? fallback;
    default:
      return fallback;
  }
}
