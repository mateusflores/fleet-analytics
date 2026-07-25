import { Pipe, PipeTransform } from '@angular/core';
import { MachineType } from '../../models/machine/machine-type.model';
import { MachineFuelType } from '../../models/machine/mahcine-fuel-type.model';

const LABELS: Record<MachineFuelType, string> = {
  [MachineFuelType.GASOLINE]: 'Gasolina',
  [MachineFuelType.ETHANOL]: 'Etanol',
  [MachineFuelType.DIESEL]: 'Diesel',
  [MachineFuelType.FLEX]: 'Flex',
  [MachineFuelType.CNG]: 'CNG',
  [MachineFuelType.LPG]: 'LPG',
  [MachineFuelType.ELECTRIC]: 'Elétrico',
  [MachineFuelType.HYBRID]: 'Híbrido',
  [MachineFuelType.HYDROGEN]: 'Hidrogênio',
  [MachineFuelType.BIODIESEL]: 'Biodiesel'
};

@Pipe({
  name: 'machineFuelTypeLabel',
  standalone: true
})
export class MachineFuelTypeLabelPipe implements PipeTransform {
  transform(value: MachineFuelType | string | null | undefined): string {
    if (value == null || value === '') {
      return '';
    }
    const label = LABELS[value as MachineFuelType];
    return label ?? String(value);
  }
}
