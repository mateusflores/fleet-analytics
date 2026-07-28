import { Pipe, PipeTransform } from '@angular/core';

const ALERT_TYPE_LABELS: Record<string, string> = {
  RPM_LIMIT: 'Limite de RPM atingido',
  ENGINE_OVERHEAT: 'Superaquecimento do motor',
  LOW_FUEL: 'Nível de combustível baixo',
  LOW_BATTERY: 'Bateria fraca',
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
