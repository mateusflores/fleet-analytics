import { DatePipe } from '@angular/common';
import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Trip } from '../../../models/trip/trip.model';
import { AuthService } from '../../../services/auth/auth.service';

@Component({
  selector: 'app-trip-list-card',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './trip-list-card.component.html',
  styleUrl: './trip-list-card.component.css'
})
export class TripListCardComponent {
  readonly authService = inject(AuthService);
  @Input({ required: true }) trip!: Trip;
  @Input() isEnding = false;
  @Output() endTrip = new EventEmitter<Trip>();

  onEndTrip(event: MouseEvent): void {
    event.stopPropagation();
    this.endTrip.emit(this.trip);
  }
}
