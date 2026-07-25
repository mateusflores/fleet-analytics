import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MachineType } from '../../../models/machine/machine-type.model';
import { Machine } from '../../../models/machine/machine.model';
import { MachineTypeLabelPipe } from '../../../shared/pipes/machine-type-label.pipe';
import { MachineService } from '../../../services/machine/machine.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { MachineFuelType } from '../../../models/machine/mahcine-fuel-type.model';
import { MachineFuelTypeLabelPipe } from '../../../shared/pipes/machine-fuel-type-label.pipe';

@Component({
  selector: 'app-add-machine',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MachineTypeLabelPipe,
    MachineFuelTypeLabelPipe,
    MatProgressSpinnerModule
  ],
  templateUrl: './add-machine.component.html',
  styleUrl: './add-machine.component.css'
})
export class AddMachineComponent {
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _router = inject(Router);
  private readonly _machineService = inject(MachineService);
  private readonly _snackbar = inject(SnackbarService);

  readonly machineTypes = Object.values(MachineType);
  readonly machineFuelTypes = Object.values(MachineFuelType);
  readonly form = this._formBuilder.nonNullable.group({
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
    nickname: ['', Validators.required],
    renavam: ['', Validators.required]
  });
  isAddingMachine = false;

  onCancel(): void {
    this._router.navigate(['/machines']);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const newMachine = this.form.getRawValue() as unknown as Machine;
    newMachine.active = true;
    this.isAddingMachine = true;
    this._machineService.addMachine(newMachine).subscribe({
      next: () => {
        this._snackbar.success('Máquina criada com sucesso.');
        this._router.navigate(['/machines']);
      },
      error: () => {
        this._snackbar.error('Erro ao criar máquina.');
        this.isAddingMachine = false;
      }
    });
  }

  fieldError(controlName: string): string | null {
    const control = this.form.get(controlName);
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
}
