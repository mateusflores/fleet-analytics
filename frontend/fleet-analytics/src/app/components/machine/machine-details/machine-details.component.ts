import { Component, Input, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { Machine } from '../../../models/machine/machine.model';
import { MachineService } from '../../../services/machine/machine.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { MachineTypeLabelPipe } from '../../../shared/pipes/machine-type-label.pipe';
import { MachineFuelTypeLabelPipe } from '../../../shared/pipes/machine-fuel-type-label.pipe';
import { MachineType } from '../../../models/machine/machine-type.model';
import { CardComponent } from '../../../shared/components/card/card.component';
import { MachineTelemetry } from '../../../models/machine/telemetry/machine-telemetry.model';
import { TelemetryChartComponent } from '../../../shared/components/telemetry-chart/telemetry-chart.component';
import { AuthService } from '../../../services/auth/auth.service';
import { MachineFuelType } from '../../../models/machine/mahcine-fuel-type.model';

@Component({
  selector: 'app-machine-details',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCardModule,
    MachineTypeLabelPipe,
    MachineFuelTypeLabelPipe,
    CardComponent,
    TelemetryChartComponent
  ],
  templateUrl: './machine-details.component.html',
  styleUrl: './machine-details.component.css'
})
export class MachineDetailsComponent implements OnInit {
  @Input() id!: string;

  private readonly _machineService = inject(MachineService);
  private readonly _router = inject(Router);
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _snackbar = inject(SnackbarService);
  readonly authService: AuthService = inject(AuthService);

  readonly machineTypes = Object.values(MachineType);
  readonly machineFuelTypes = Object.values(MachineFuelType);
  readonly chartPeriods = [
    new Date(new Date().setHours(new Date().getHours() - 1)),
    new Date(new Date().setHours(new Date().getHours() - 24)),
    new Date(new Date().setDate(new Date().getDate() - 7)),
    new Date(new Date().setDate(new Date().getDate() - 30))
  ];
  selectedPeriod = 0;

  machine: Machine | null = null;
  machineTelemetry: MachineTelemetry | null = null;
  isEditing = false;
  isSaving = false;
  isLoading = true;

  readonly editForm = this._formBuilder.nonNullable.group({
    serialNumber: ['', Validators.required],
    licensePlate: ['', Validators.required],
    brand: ['', Validators.required],
    model: ['', Validators.required],
    year: [new Date().getFullYear(), [
      Validators.required,
      Validators.min(1900),
      Validators.max(new Date().getFullYear() + 1)
    ]],
    color: [''],
    mileageKm: [0, [Validators.required, Validators.min(0)]],
    vehicleType: [MachineType.RIGID_TRUCK, Validators.required],
    maxLoadKg: [0, [Validators.required, Validators.min(0)]],
    fuelType: [MachineFuelType.GASOLINE, Validators.required],
    nickname: ['', [Validators.required]],
    renavam: ['', [Validators.required]],
    active: [true, Validators.required]
  });

  ngOnInit(): void {
    this.isLoading = true;
    this._machineService.getMachineById(this.id).subscribe({
      next: (machine) => {
        if (!machine) {
          console.error('Machine not found with ID:', this.id);
          this._router.navigate(['/machines']);
          this.isLoading = false;
          return;
        }
        this.machine = machine;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error fetching machine details:', error);
        this._router.navigate(['/machines']);
        this.isLoading = false;
      }
    });
  }

  startEdit(): void {
    if (!this.machine || !this.authService.hasPermission('vehicles:update')) {
      return;
    }
    this.editForm.patchValue({
      serialNumber: this.machine.serialNumber,
      licensePlate: this.machine.licensePlate,
      brand: this.machine.brand,
      model: this.machine.model,
      year: this.machine.year,
      color: this.machine.color ?? '',
      mileageKm: this.machine.mileageKm,
      vehicleType: this.machine.vehicleType,
      maxLoadKg: this.machine.maxLoadKg,
      fuelType: this.machine.fuelType,
      nickname: this.machine.nickname ?? '',
      renavam: this.machine.renavam ?? '',
      active: this.machine.active
    });
    this.isEditing = true;
  }

  cancelEdit(): void {
    this.isEditing = false;
  }

  saveEdit(): void {
    if (!this.machine || !this.authService.hasPermission('vehicles:update')) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    const patch = this.editForm.getRawValue();
    this._machineService.updateMachine(this.machine.id, patch).subscribe({
      next: (updated) => {
        if (updated) {
          this.machine = updated;
          this.isEditing = false;
          this._snackbar.success('Máquina atualizada com sucesso.');
        }
        this.isSaving = false;
      },
      error: (error) => {
        console.error('Error saving machine:', error);
        this._snackbar.error('Erro ao atualizar máquina. Tente novamente.');
        this.isSaving = false;
      }
    });
  }

  fieldError(controlName: string): string | null {
    const control = this.editForm.get(controlName);
    if (!control?.invalid || !control.touched) {
      return null;
    }
    const errs = control.errors;
    if (!errs) {
      return null;
    }
    if (errs['required']) {
      return 'Campo obrigatório.';
    }
    const minErr = errs['min'] as { min: number } | undefined;
    if (minErr) {
      if (controlName === 'year') {
        return `Informe um ano igual ou maior que ${minErr.min}.`;
      }
      return `O valor deve ser igual ou maior que ${minErr.min}.`;
    }
    const maxErr = errs['max'] as { max: number } | undefined;
    if (maxErr) {
      return `Informe um ano igual ou menor que ${maxErr.max}.`;
    }
    return null;
  }

  changeSelectedPeriod(index: number): void {
    if (index >= 0 && index < this.chartPeriods.length) {
      this.selectedPeriod = index;
    }
  }
}
