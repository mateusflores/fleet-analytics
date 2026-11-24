import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FleetPolicy, UpdateFleetPolicyRequest } from '../../models/fleet-policy/fleet-policy.model';
import { MachineType } from '../../models/machine/machine-type.model';
import { AuthService } from '../../services/auth/auth.service';
import { FleetPolicyService } from '../../services/fleet-policy/fleet-policy.service';
import { SnackbarService } from '../../services/snackbar/snackbar.service';
import { MachineTypeLabelPipe } from '../../shared/pipes/machine-type-label.pipe';
import { apiErrorMessage } from '../../shared/utils/api-error';

/** Faixas aceitas pelo backend (mesmas regras do `PUT /fleet-policy`). */
export const FLEET_POLICY_LIMITS = {
  speedLimitKmh: { min: 20, max: 150 },
  harshAccelMs2: { min: 0.1, max: 5 },
  harshBrakeMs2: { min: 0.1, max: 10 },
  /** em minutos na tela; o backend usa segundos (30–3600) */
  idleThresholdMin: { min: 0.5, max: 60 },
  fuelPricePerLiter: { min: 0.01, max: null },
  economyRpmMax: { min: 500, max: 8000 }
} as const;

type VehicleTypeGroup = FormGroup<{
  vehicleType: FormControl<MachineType>;
  economyRpmMax: FormControl<number>;
}>;

@Component({
  selector: 'app-fleet-policy',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MachineTypeLabelPipe
  ],
  templateUrl: './fleet-policy.component.html',
  styleUrl: './fleet-policy.component.css'
})
export class FleetPolicyComponent implements OnInit {
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _fleetPolicyService = inject(FleetPolicyService);
  private readonly _authService = inject(AuthService);
  private readonly _snackbar = inject(SnackbarService);

  readonly limits = FLEET_POLICY_LIMITS;
  readonly canEdit = this._authService.hasPermission('fleet-policy:update');

  readonly form = this._formBuilder.nonNullable.group({
    speedLimitKmh: [0, this.range(FLEET_POLICY_LIMITS.speedLimitKmh)],
    harshAccelMs2: [0, this.range(FLEET_POLICY_LIMITS.harshAccelMs2)],
    harshBrakeMs2: [0, this.range(FLEET_POLICY_LIMITS.harshBrakeMs2)],
    idleThresholdMin: [0, this.range(FLEET_POLICY_LIMITS.idleThresholdMin)],
    fuelPricePerLiter: [0, this.range(FLEET_POLICY_LIMITS.fuelPricePerLiter)],
    vehicleTypes: this._formBuilder.array<VehicleTypeGroup>([])
  });

  policy: FleetPolicy | null = null;
  isLoading = true;
  isSaving = false;
  loadError: string | null = null;

  get vehicleTypes(): FormArray<VehicleTypeGroup> {
    return this.form.controls.vehicleTypes;
  }

  ngOnInit(): void {
    this.loadPolicy();
  }

  loadPolicy(): void {
    this.isLoading = true;
    this.loadError = null;
    this._fleetPolicyService.getPolicy().subscribe({
      next: (policy) => {
        this.applyPolicy(policy);
        this.isLoading = false;
      },
      error: (error) => {
        this.loadError = apiErrorMessage(error, 'Não foi possível carregar a política da frota.');
        this.isLoading = false;
      }
    });
  }

  onSubmit(): void {
    if (!this.canEdit) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this._fleetPolicyService.updatePolicy(this.toRequest()).subscribe({
      next: (policy) => {
        this.applyPolicy(policy);
        this.isSaving = false;
        this._snackbar.success('Política da frota atualizada.');
      },
      error: (error) => {
        this.isSaving = false;
        this._snackbar.error(apiErrorMessage(error, 'Erro ao salvar a política da frota.'));
      }
    });
  }

  onDiscard(): void {
    if (this.policy) {
      this.applyPolicy(this.policy);
    }
  }

  fieldError(control: FormControl<number>, unit = ''): string | null {
    if (!control.invalid || !control.touched || !control.errors) {
      return null;
    }
    const errs = control.errors;
    if (errs['required']) {
      return 'Campo obrigatório.';
    }
    const min = errs['min'] as { min: number } | undefined;
    if (min) {
      return `Informe um valor a partir de ${this.formatNumber(min.min)}${unit}.`;
    }
    const max = errs['max'] as { max: number } | undefined;
    if (max) {
      return `Informe um valor até ${this.formatNumber(max.max)}${unit}.`;
    }
    return null;
  }

  private applyPolicy(policy: FleetPolicy): void {
    this.policy = policy;
    this.vehicleTypes.clear();
    for (const type of policy.vehicleTypes) {
      this.vehicleTypes.push(this._formBuilder.nonNullable.group({
        vehicleType: [type.vehicleType],
        economyRpmMax: [type.economyRpmMax, this.range(FLEET_POLICY_LIMITS.economyRpmMax)]
      }));
    }
    this.form.patchValue({
      speedLimitKmh: policy.speedLimitKmh,
      harshAccelMs2: policy.harshAccelMs2,
      harshBrakeMs2: policy.harshBrakeMs2,
      idleThresholdMin: +(policy.idleThresholdS / 60).toFixed(2),
      fuelPricePerLiter: policy.fuelPricePerLiter
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    if (this.canEdit) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  private toRequest(): UpdateFleetPolicyRequest {
    const value = this.form.getRawValue();
    return {
      speedLimitKmh: value.speedLimitKmh,
      harshAccelMs2: value.harshAccelMs2,
      harshBrakeMs2: value.harshBrakeMs2,
      idleThresholdS: Math.round(value.idleThresholdMin * 60),
      fuelPricePerLiter: value.fuelPricePerLiter,
      vehicleTypes: value.vehicleTypes
    };
  }

  private range(limit: { min: number; max: number | null }) {
    return limit.max === null
      ? [Validators.required, Validators.min(limit.min)]
      : [Validators.required, Validators.min(limit.min), Validators.max(limit.max)];
  }

  private formatNumber(value: number): string {
    return value.toLocaleString('pt-BR');
  }
}
