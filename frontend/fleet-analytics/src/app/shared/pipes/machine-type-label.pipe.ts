import { Pipe, PipeTransform } from '@angular/core';
import { MachineType } from '../../models/machine/machine-type.model';

const LABELS: Record<MachineType, string> = {
  [MachineType.Truck]: 'Caminhão',
  [MachineType.Tractor]: 'Trator',
  [MachineType.Utility]: 'Utilitário',
  [MachineType.Excavator]: 'Escavadeira',
  [MachineType.Loader]: 'Carregadeira'
};

@Pipe({
  name: 'machineTypeLabel',
  standalone: true
})
export class MachineTypeLabelPipe implements PipeTransform {
  transform(value: MachineType | string | null | undefined): string {
    if (value == null || value === '') {
      return '';
    }
    const label = LABELS[value as MachineType];
    return label ?? String(value);
  }
}
