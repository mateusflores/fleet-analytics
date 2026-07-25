import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateTripRequest } from '../../models/trip/create-trip-request.model';
import { Trip } from '../../models/trip/trip.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';

@Injectable({ providedIn: 'root' })
export class TripService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _tripsUrl = `${environment.apiBaseUrl}/trips`;

  getTrips(page = 0, pageSize = 9): Observable<PaginatedResponse<Trip>> {
    return this._httpClient.get<PaginatedResponse<Trip>>(`${this._tripsUrl}?page=${page}&size=${pageSize}`);
  }

  startTrip(request: CreateTripRequest): Observable<Trip> {
    return this._httpClient.post<Trip>(this._tripsUrl, request);
  }

  endTrip(tripId: string): Observable<Trip> {
    return this._httpClient.patch<Trip>(`${this._tripsUrl}/${tripId}/end`, {});
  }
}
