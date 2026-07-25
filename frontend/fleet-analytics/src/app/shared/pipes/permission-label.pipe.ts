import { Pipe, PipeTransform } from '@angular/core';
import { PERMISSION_TRANSLATIONS } from './permission-translations';

export type PermissionLabelField = 'name' | 'description';

@Pipe({
  name: 'permissionLabel',
  standalone: true
})
export class PermissionLabelPipe implements PipeTransform {
  transform(
    value: string | null | undefined,
    field: PermissionLabelField = 'name'
  ): string {
    if (value == null || value === '') {
      return '';
    }

    const translation = PERMISSION_TRANSLATIONS[value];
    if (!translation) {
      return value;
    }

    return translation[field];
  }
}
