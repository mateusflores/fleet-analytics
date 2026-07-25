export interface PermissionTranslation {
  name: string;
  description: string;
}

export const PERMISSION_TRANSLATIONS: Record<string, PermissionTranslation> = {
  'users:create': {
    name: 'Criar usuários',
    description: 'Criar novos usuários'
  },
  'users:read': {
    name: 'Visualizar usuários',
    description: 'Visualizar detalhes de usuários'
  },
  'users:management': {
    name: 'Gerenciar usuários',
    description: 'Ativar ou desativar contas de usuários'
  },
  'analytics:view': {
    name: 'Visualizar analytics',
    description: 'Visualizar analytics e relatórios'
  },
  'travel:start': {
    name: 'Iniciar viagem',
    description: 'Iniciar uma viagem'
  },
  'travel:read': {
    name: 'Visualizar viagens',
    description: 'Visualizar detalhes de viagens'
  },
  'drivers:view': {
    name: 'Visualizar motoristas',
    description: 'Visualizar lista e detalhes de motoristas'
  },
  'drivers:create': {
    name: 'Criar motoristas',
    description: 'Criar novos motoristas'
  },
  'drivers:update': {
    name: 'Atualizar motoristas',
    description: 'Atualizar informações de motoristas'
  },
  'vehicles:view': {
    name: 'Visualizar veículos',
    description: 'Visualizar lista e detalhes de veículos'
  },
  'vehicles:create': {
    name: 'Criar veículos',
    description: 'Criar novos veículos'
  },
  'vehicles:update': {
    name: 'Atualizar veículos',
    description: 'Atualizar informações de veículos'
  },
  'roles:create': {
    name: 'Criar perfis',
    description: 'Criar novos perfis'
  },
  'roles:read': {
    name: 'Visualizar perfis',
    description: 'Visualizar perfis e permissões'
  },
  'roles:update': {
    name: 'Atualizar perfis',
    description: 'Atualizar perfis e permissões'
  },
  'roles:delete': {
    name: 'Excluir perfis',
    description: 'Excluir perfis'
  }
};
