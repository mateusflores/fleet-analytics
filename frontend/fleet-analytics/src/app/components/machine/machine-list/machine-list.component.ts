import { Component, effect, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Machine } from '../../../models/machine/machine.model';
import { MachineListCardComponent } from '../machine-list-card/machine-list-card.component';
import { MachineService } from '../../../services/machine/machine.service';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-machine-list',
  imports: [
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    MachineListCardComponent,
    MatProgressSpinnerModule
  ],
  providers: [
    { provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }
  ],
  templateUrl: './machine-list.component.html',
  styleUrl: './machine-list.component.css'
})
export class MachineListComponent {
  private readonly _machineService: MachineService = inject(MachineService);
  private readonly _router: Router = inject(Router);
  readonly authService: AuthService = inject(AuthService);

  readonly searchTerm = signal('');
  readonly searchField = signal<keyof Machine>('nickname');
  readonly searchFields: ReadonlyArray<{ value: keyof Machine; label: string }> = [
    { value: 'serialNumber', label: 'Número de série' },
    { value: 'licensePlate', label: 'Placa' },
    { value: 'brand', label: 'Marca' },
    { value: 'model', label: 'Modelo' },
    { value: 'nickname', label: 'Apelido' },
    { value: 'renavam', label: 'RENAVAM' },
  ];
  filteredMachines: Machine[] = []
  isLoading = true;
  pageIndex = 0;
  pageSize = 9;
  totalMachines = 0;

  constructor() {
    effect((onCleanup) => {
      const field = this.searchField();
      const term = this.searchTerm();
      const timeout = setTimeout(() => {
        this.pageIndex = 0;
        this.getMachines(field, term, this.pageIndex);
      }, 300);

      onCleanup(() => clearTimeout(timeout));
    });
  }

  private getMachines(field: keyof Machine, term: string, page: number) {
    this.isLoading = true;
    this._machineService.getMachines(field, term, page, this.pageSize).subscribe({
      next: (response) => {
        this.filteredMachines = response.content;
        this.totalMachines = response.totalElements;
        this.isLoading = false;
      },
      error: (error) => {
        console.error(error);
        this.isLoading = false;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.getMachines(this.searchField(), this.searchTerm(), this.pageIndex);
  }

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTerm.set(input?.value ?? '');
  }

  onSearchFieldChange(field: keyof Machine): void {
    this.searchField.set(field);
  }

  goToAddMachine(): void {
    this._router.navigate(['/machines/add']);
  }
}
