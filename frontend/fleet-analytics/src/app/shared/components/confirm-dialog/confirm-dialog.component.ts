import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { ConfirmDialogData } from '../../../models/confirm-dialog/confirm-dialog-data.model';

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.css'
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly _dialogRef = inject(MatDialogRef<ConfirmDialogComponent, boolean>);

  readonly confirmLabel = this.data.confirmLabel ?? 'Confirmar';
  readonly cancelLabel = this.data.cancelLabel ?? 'Cancelar';

  onCancel(): void {
    this._dialogRef.close(false);
  }

  onConfirm(): void {
    this._dialogRef.close(true);
  }
}
