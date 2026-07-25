import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Trip } from '../../../models/trip/trip.model';
import { AuthService } from '../../../services/auth/auth.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import { TripService } from '../../../services/trip/trip.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { StartTripDialogComponent } from '../start-trip-dialog/start-trip-dialog.component';
import { TripListCardComponent } from '../trip-list-card/trip-list-card.component';

@Component({
  selector: 'app-trip-list',
  imports: [MatButtonModule, MatDialogModule, MatIconModule, MatPaginatorModule, MatProgressSpinnerModule, TripListCardComponent],
  providers: [{ provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }],
  templateUrl: './trip-list.component.html',
  styleUrl: './trip-list.component.css'
})
export class TripListComponent implements OnInit {
  private readonly _dialog = inject(MatDialog);
  private readonly _snackbar = inject(SnackbarService);
  private readonly _tripService = inject(TripService);
  readonly authService = inject(AuthService);
  trips: Trip[] = [];
  isLoading = true;
  endingTripId: string | null = null;
  pageIndex = 0;
  pageSize = 9;
  totalTrips = 0;

  ngOnInit(): void { this.getTrips(); }
  getTrips(): void {
    this.isLoading = true;
    this._tripService.getTrips(this.pageIndex, this.pageSize).subscribe({
      next: (response) => { this.trips = response.content; this.totalTrips = response.totalElements; this.isLoading = false; },
      error: () => { this.isLoading = false; }
    });
  }
  onPageChange(event: PageEvent): void { this.pageIndex = event.pageIndex; this.pageSize = event.pageSize; this.getTrips(); }
  openStartTripDialog(): void {
    this._dialog.open(StartTripDialogComponent, { width: '440px' }).afterClosed().subscribe((started) => { if (started) this.getTrips(); });
  }
  onEndTrip(trip: Trip): void {
    this.endingTripId = trip.id;
    this._tripService.endTrip(trip.id).subscribe({
      next: () => { this._snackbar.success('Viagem encerrada com sucesso.'); this.endingTripId = null; this.getTrips(); },
      error: () => { this._snackbar.error('Erro ao encerrar viagem.'); this.endingTripId = null; }
    });
  }
}
