import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MachineType } from '../../../models/machine/machine-type.model';
import { Machine } from '../../../models/machine/machine.model';
import { MachineTypeLabelPipe } from '../../../shared/pipes/machine-type-label.pipe';
import { MachineService } from '../../../services/machine/machine.service';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

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
    MatProgressSpinnerModule
  ],
  templateUrl: './add-machine.component.html',
  styleUrl: './add-machine.component.css'
})
export class AddMachineComponent {
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _router = inject(Router);
  private readonly _machineService = inject(MachineService)

  readonly machineTypes = Object.values(MachineType);
  readonly form = this._formBuilder.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    type: [MachineType.Truck, Validators.required],
    year: [new Date().getFullYear(), [
      Validators.required, 
      Validators.min(1900), 
      Validators.max(new Date().getFullYear()+1)
    ]],
    mileage: [0, [Validators.required, Validators.min(0)]]
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

    const newMachine = this.form.getRawValue() as Machine;
    this.isAddingMachine = true;
    this._machineService.addMachine(newMachine).subscribe(() => {
      this._router.navigate(['/machines']);
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
