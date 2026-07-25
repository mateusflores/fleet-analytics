import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Machine } from '../../../models/machine/machine.model';
import { AuthService } from '../../../services/auth/auth.service';
import { MachineService } from '../../../services/machine/machine.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { TripService } from '../../../services/trip/trip.service';
import { UserService } from '../../../services/user/user.service';

@Component({
  selector: 'app-start-trip-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatProgressSpinnerModule, MatSelectModule],
  templateUrl: './start-trip-dialog.component.html',
  styleUrl: './start-trip-dialog.component.css'
})
export class StartTripDialogComponent implements OnInit {
  private readonly _authService = inject(AuthService);
  private readonly _formBuilder = inject(FormBuilder);
  private readonly _machineService = inject(MachineService);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _tripService = inject(TripService);
  private readonly _userService = inject(UserService);
  private readonly _dialogRef = inject(MatDialogRef<StartTripDialogComponent>);
  private readonly errorMessages: Record<string, string> = {
    'Driver already has an active trip': 'Motorista já tem uma viagem ativa',
    'Vehicle already has an active trip': 'Veículo já tem uma viagem ativa'
  }

  readonly form = this._formBuilder.nonNullable.group({ vehicleId: ['', Validators.required] });
  machines: Machine[] = [];
  isLoadingMachines = true;
  isStarting = false;

  ngOnInit(): void {
    this._machineService.getMachines('nickname', '', 0, 1000).subscribe({
      next: (response) => { 
        this.machines = response.content; 
        this.isLoadingMachines = false; 
      },
      error: () => { 
        this._snackbar.error('Erro ao carregar veículos.'); 
        this.isLoadingMachines = false; 
      }
    });
  }

  onCancel(): void { 
    this._dialogRef.close(); 
  }

  onStart(): void {
    if (this.form.invalid || this.isStarting) {
      this.form.markAllAsTouched();
      return;
    }
    const userId = this._authService.decodeAccessToken()?.userId;
    if (!userId) {
      this._snackbar.error('Não foi possível identificar o usuário logado.');
      return;
    }
    this.createTrip(userId);
  }

  private createTrip(driverId: string): void {
    this._tripService.startTrip({ driverId, vehicleId: this.form.getRawValue().vehicleId }).subscribe({
      next: () => { 
        this._snackbar.success('Viagem iniciada com sucesso.'); 
        this._dialogRef.close(true); 
      },
      error: (error) => { 
        const message = this.errorMessages[error.message] || 'Erro ao iniciar viagem.';
        this._snackbar.error(message); 
        this.isStarting = false; 
      }
    });
  }
}
