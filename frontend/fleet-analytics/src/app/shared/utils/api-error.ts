import { HttpErrorResponse } from '@angular/common/http';

/** Corpo de erro padrão da API: `{ status, error, message }`. */
interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
}

export type ApiErrorMessages = Partial<Record<number, string>>;

/**
 * Traduções das mensagens do backend (em inglês). A chave é o início da mensagem; o que vier depois
 * de `: ` (ex.: a placa repetida) é acrescentado à tradução.
 */
const BACKEND_MESSAGES: [prefix: string, translation: string][] = [
  ['Vehicle is inactive', 'Veículo inativo. Ative o veículo para iniciar uma viagem.'],
  ['Driver is disabled', 'Motorista desativado. Procure o administrador.'],
  ['Vehicle already has an active trip', 'O veículo já tem uma viagem ativa.'],
  ['Driver already has an active trip', 'O motorista já tem uma viagem ativa.'],
  ['License plate already registered', 'Placa já cadastrada'],
  ['Serial number already registered', 'Número de série já cadastrado'],
  ['Role already exists', 'Já existe um perfil com este nome'],
  ['Role not found', 'Perfil não encontrado'],
  ['Username already taken', 'Nome de usuário já em uso'],
  ['Email already registered', 'E-mail já cadastrado'],
  ['User is disabled', 'Usuário desativado. Procure o administrador.'],
  ['Data conflicts with an existing record', 'Os dados conflitam com um registro existente.']
];

/** Mensagens do Bean Validation (`campo: mensagem`) mais comuns nos formulários. */
const VALIDATION_MESSAGES: [pattern: RegExp, translate: (...groups: string[]) => string][] = [
  [/^size must be between (\d+) and (\d+)$/, (min, max) => `deve ter entre ${min} e ${max} caracteres`],
  [/^must be a well-formed email address$/, () => 'e-mail inválido'],
  [/^must not be (blank|null|empty)$/, () => 'obrigatório'],
  [/^must be greater than or equal to (.+)$/, (min) => `deve ser maior ou igual a ${min}`],
  [/^must be less than or equal to (.+)$/, (max) => `deve ser menor ou igual a ${max}`],
  [/^must be greater than (.+)$/, (min) => `deve ser maior que ${min}`]
];

/** Traduz a mensagem do backend quando conhecida; senão devolve a original. */
export function translateBackendMessage(message: string): string {
  for (const [prefix, translation] of BACKEND_MESSAGES) {
    if (message.startsWith(prefix)) {
      const detail = message.slice(prefix.length).replace(/^:\s*/, '').trim();
      return detail ? `${translation}: ${detail}` : translation;
    }
  }

  const field = message.match(/^([\w.\[\]]+):\s*(.+)$/);
  if (field) {
    for (const [pattern, translate] of VALIDATION_MESSAGES) {
      const match = field[2].match(pattern);
      if (match) {
        return `${field[1]}: ${translate(...match.slice(1))}`;
      }
    }
  }
  return message;
}

/**
 * Monta a mensagem a exibir para um erro HTTP.
 * Ordem: mensagem específica do status em `messages` → `message` do corpo (traduzida) → texto padrão do status
 * → `fallback`.
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
  const backendMessage = typeof body?.message === 'string' && body.message.trim()
    ? translateBackendMessage(body.message.trim())
    : null;

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
