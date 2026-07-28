import { Pipe, PipeTransform } from '@angular/core';

const ALERT_SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: 'CRÍTICO',
  WARNING: 'AVISO',
};

@Pipe({
  name: 'alertSeverityLabel',
  standalone: true
})
export class AlertSeverityLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (value == null || value === '') {
      return '';
    }

    return ALERT_SEVERITY_LABELS[value.toUpperCase()] ?? String(value);
  }
}
