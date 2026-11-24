export type InsightScope = 'VEHICLE' | 'DRIVER' | 'FLEET';

export type InsightSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export type InsightStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'DISMISSED' | 'RESOLVED';

/** Status que o usuário pode definir pelo `PATCH`. */
export type InsightUserStatus = Extract<InsightStatus, 'ACKNOWLEDGED' | 'DISMISSED'>;

export type InsightCategory =
  | 'DRIVER_SPEEDING'
  | 'DRIVER_HARSH_DRIVING'
  | 'DRIVER_IDLE'
  | 'DRIVER_RPM'
  | 'VEHICLE_FUEL_TREND'
  | 'VEHICLE_HEALTH_DRIFT'
  | 'VEHICLE_ALERT_RECURRENCE'
  | 'FLEET_IDLE_COST'
  | 'FLEET_EFFICIENCY_RANKING';

/** Insight histórico gerado pelo motor (`/insights`). Textos já chegam prontos em português. */
export interface Insight {
  id: string;
  scope: InsightScope;
  /** id do veículo ou motorista; `null` no escopo FLEET */
  subjectId: string | null;
  subjectName: string | null;
  category: InsightCategory;
  severity: InsightSeverity;
  title: string;
  description: string;
  periodStart: string;
  periodEnd: string;
  /** números usados para gerar o insight */
  evidence: Record<string, unknown> | null;
  status: InsightStatus;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InsightFilter {
  /** sem status, a API devolve os abertos (ACTIVE + ACKNOWLEDGED) */
  status?: InsightStatus[];
  scope?: InsightScope;
  subjectId?: string;
  category?: InsightCategory;
  page?: number;
  size?: number;
}
