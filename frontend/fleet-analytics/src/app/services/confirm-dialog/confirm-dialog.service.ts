import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { filter, Observable } from 'rxjs';
import { ConfirmDialogData } from '../../models/confirm-dialog/confirm-dialog-data.model';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Injectable({
  providedIn: 'root'
})
export class ConfirmDialogService {
  private readonly _dialog = inject(MatDialog);

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this._dialog
      .open(ConfirmDialogComponent, {
        data,
        width: '420px',
        disableClose: true
      })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true));
  }
}
