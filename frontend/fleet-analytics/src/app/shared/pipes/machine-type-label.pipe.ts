import { Pipe, PipeTransform } from '@angular/core';
import { MachineType } from '../../models/machine/machine-type.model';

const LABELS: Record<MachineType, string> = {
  [MachineType.RIGID_TRUCK]: 'Veículo pesado',
  [MachineType.ARTICULATED_VEHICLE]: 'Veículo articulado',
  [MachineType.PASSAGER_VEHICLE]: 'Veículo de passageiros',
  [MachineType.AGRICULTURAL_VEHICLE]: 'Veículo agrícola'
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
