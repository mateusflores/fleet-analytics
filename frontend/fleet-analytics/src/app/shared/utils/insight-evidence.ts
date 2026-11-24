export interface EvidenceItem {
  label: string;
  value: string;
}

/**
 * Rótulos das chaves de `evidence`. A chave é normalizada (minúsculas, sem `_`), então
 * `baseline_ratio`, `baselineRatio` e `ratioBaseline` caem na mesma entrada quando listadas.
 */
const EVIDENCE_LABELS: Record<string, string> = {
  ratio: 'Razão observado / esperado',
  baselineratio: 'Razão no histórico',
  ratiobaseline: 'Razão no histórico',
  ratiorecent: 'Razão no período recente',
  days: 'Dias analisados',
  daysbaseline: 'Dias de histórico',
  daysrecent: 'Dias do período recente',
  trips: 'Viagens',
  tripsbaseline: 'Viagens no histórico',
  tripsrecent: 'Viagens no período recente',
  distancekm: 'Distância (km)',
  idlefuell: 'Combustível em ociosidade (L)',
  excessiveidlefuell: 'Combustível em ociosidade excessiva (L)',
  cost: 'Custo (R$)',
  price: 'Preço do diesel (R$/L)',
  savinghalfexcessive: 'Economia reduzindo metade do excesso (R$)',
  peermedian: 'Mediana dos pares',
  peers: 'Pares comparados',
  fleetmedianratio: 'Razão mediana da frota',
  abovemedian: 'Acima da mediana',
  pvalue: 'Valor-p',
  rate: 'Taxa',
  recent: 'Período recente',
  baselineperweek: 'Média semanal no histórico',
  baselinedev: 'Desvio no histórico',
  recentdev: 'Desvio recente',
  harshaccel: 'Acelerações bruscas',
  harshbrake: 'Frenagens bruscas',
  eventtype: 'Tipo de alerta',
  vehicletype: 'Tipo de veículo',
  vehicles: 'Veículos',
  change: 'Variação',
  relative: 'Variação relativa',
  exposure: 'Exposição',
  worsening: 'Piorando',
  criticalrecent: 'Críticos no período recente',
  signal: 'Sinal',
  kind: 'Tipo'
};

/** Chaves internas que não ajudam o usuário. */
const HIDDEN_KEYS = new Set(['vehicleid', 'driverid', 'subjectid']);

const numberFormat = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

/** Lista `evidence` como pares rótulo/valor legíveis. */
export function evidenceItems(evidence: Record<string, unknown> | null | undefined): EvidenceItem[] {
  if (!evidence) {
    return [];
  }
  return Object.entries(evidence)
    .filter(([key, value]) => value !== null && value !== undefined && !HIDDEN_KEYS.has(normalize(key)))
    .map(([key, value]) => ({ label: EVIDENCE_LABELS[normalize(key)] ?? humanize(key), value: formatValue(value) }));
}

function normalize(key: string): string {
  return key.replace(/_/g, '').toLowerCase();
}

function humanize(key: string): string {
  const words = key.replace(/_/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function formatValue(value: unknown): string {
  if (typeof value === 'number') {
    return numberFormat.format(value);
  }
  if (typeof value === 'boolean') {
    return value ? 'Sim' : 'Não';
  }
  if (Array.isArray(value)) {
    return value.map(formatValue).join(', ');
  }
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([k, v]) => `${humanize(k)}: ${formatValue(v)}`)
      .join('; ');
  }
  return String(value);
}
