import { Pipe, PipeTransform } from '@angular/core';

const ALERT_TYPE_LABELS: Record<string, string> = {
  RPM_LIMIT: 'Limite de RPM atingido',
  ENGINE_OVERHEAT: 'Superaquecimento do motor',
  LOW_FUEL: 'Nível de combustível baixo',
  LOW_BATTERY: 'Bateria fraca',
  LOW_OIL_PRESSURE: 'Pressão do óleo baixa',
  // insights do serviço de ML (ml-service/src/ml_service/inference/engine.py)
  ML_COOLANT_C: 'Temperatura do motor fora do esperado',
  ML_OIL_KPA: 'Pressão do óleo fora do esperado',
  ML_FUEL_RATE_LPH: 'Consumo fora do esperado',
  ML_BOOST_KPA: 'Pressão do turbo fora do esperado',
  ML_INTAKE_C: 'Temperatura de admissão fora do esperado',
  ML_BATTERY_V: 'Tensão da bateria fora do esperado',
  ML_FUEL_LEVEL_PCT: 'Queda anormal do nível de combustível',
  ML_RPM_ROUGHNESS: 'Rotação irregular',
  ML_PATTERN: 'Padrão de operação anômalo',
};

@Pipe({
  name: 'alertTypeLabel',
  standalone: true
})
export class AlertTypeLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (value == null || value === '') {
      return '';
    }

    return ALERT_TYPE_LABELS[value.toUpperCase()] ?? String(value);
  }
}
