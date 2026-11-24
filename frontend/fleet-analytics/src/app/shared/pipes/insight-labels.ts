import { Pipe, PipeTransform } from '@angular/core';
import { InsightCategory, InsightScope, InsightStatus } from '../../models/insight/insight.model';

export const INSIGHT_CATEGORY_LABELS: Record<InsightCategory, string> = {
  DRIVER_SPEEDING: 'Velocidade acima da política',
  DRIVER_HARSH_DRIVING: 'Direção brusca',
  DRIVER_IDLE: 'Motor ligado parado',
  DRIVER_RPM: 'Rotação fora da faixa econômica',
  VEHICLE_FUEL_TREND: 'Consumo acima do histórico',
  VEHICLE_HEALTH_DRIFT: 'Desgaste progressivo',
  VEHICLE_ALERT_RECURRENCE: 'Alertas recorrentes',
  FLEET_IDLE_COST: 'Custo da ociosidade',
  FLEET_EFFICIENCY_RANKING: 'Eficiência da frota'
};

export const INSIGHT_CATEGORY_ICONS: Record<InsightCategory, string> = {
  DRIVER_SPEEDING: 'speed',
  DRIVER_HARSH_DRIVING: 'warning_amber',
  DRIVER_IDLE: 'hourglass_empty',
  DRIVER_RPM: 'av_timer',
  VEHICLE_FUEL_TREND: 'local_gas_station',
  VEHICLE_HEALTH_DRIFT: 'build',
  VEHICLE_ALERT_RECURRENCE: 'notifications_active',
  FLEET_IDLE_COST: 'payments',
  FLEET_EFFICIENCY_RANKING: 'leaderboard'
};

export const INSIGHT_SCOPE_LABELS: Record<InsightScope, string> = {
  VEHICLE: 'Veículo',
  DRIVER: 'Motorista',
  FLEET: 'Frota'
};

export const INSIGHT_STATUS_LABELS: Record<InsightStatus, string> = {
  ACTIVE: 'Ativo',
  ACKNOWLEDGED: 'Visto',
  DISMISSED: 'Descartado',
  RESOLVED: 'Resolvido'
};

@Pipe({ name: 'insightCategoryLabel', standalone: true })
export class InsightCategoryLabelPipe implements PipeTransform {
  transform(value: InsightCategory | string | null | undefined): string {
    return value ? INSIGHT_CATEGORY_LABELS[value as InsightCategory] ?? String(value) : '';
  }
}

@Pipe({ name: 'insightScopeLabel', standalone: true })
export class InsightScopeLabelPipe implements PipeTransform {
  transform(value: InsightScope | string | null | undefined): string {
    return value ? INSIGHT_SCOPE_LABELS[value as InsightScope] ?? String(value) : '';
  }
}

@Pipe({ name: 'insightStatusLabel', standalone: true })
export class InsightStatusLabelPipe implements PipeTransform {
  transform(value: InsightStatus | string | null | undefined): string {
    return value ? INSIGHT_STATUS_LABELS[value as InsightStatus] ?? String(value) : '';
  }
}
