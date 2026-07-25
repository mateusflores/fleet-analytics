import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export type SnackbarType = 'success' | 'error' | 'info';

const PANEL_CLASS: Record<SnackbarType, string> = {
  success: 'snackbar-success',
  error: 'snackbar-error',
  info: 'snackbar-info'
};

@Injectable({
  providedIn: 'root'
})
export class SnackbarService {
  private readonly _snackBar = inject(MatSnackBar);

  show(message: string, type: SnackbarType): void {
    this._snackBar.open(message, 'Fechar', {
      duration: 4000,
      horizontalPosition: 'end',
      verticalPosition: 'top',
      panelClass: [PANEL_CLASS[type]]
    });
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  info(message: string): void {
    this.show(message, 'info');
  }
}

