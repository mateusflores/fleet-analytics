import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Insight, InsightFilter, InsightUserStatus } from '../../models/insight/insight.model';
import { DeterministicAlert } from '../../models/insight/deterministic-alert.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { SseService } from '../sse/sse.service';
import { AuthService } from '../auth/auth.service';

@Injectable({ providedIn: 'root' })
export class InsightService {

  private readonly _httpClient = inject(HttpClient);
  private readonly _sseService = inject(SseService);
  private readonly _authService = inject(AuthService);
  private readonly _eventsUrl = `${environment.apiBaseUrl}/telemetry/events`;

  private readonly _insightsUrl = `${environment.apiBaseUrl}/insights`;

  /** Lista paginada, mais recentes (`updatedAt`) primeiro. */
  getInsights(filter: InsightFilter = {}): Observable<PaginatedResponse<Insight>> {
    let params = new HttpParams()
      .set('page', filter.page ?? 0)
      .set('size', filter.size ?? 20)
      .set('sort', 'updatedAt,desc');
    if (filter.status?.length) {
      params = params.set('status', filter.status.join(','));
    }
    if (filter.scope) {
      params = params.set('scope', filter.scope);
    }
    if (filter.subjectId) {
      params = params.set('subjectId', filter.subjectId);
    }
    if (filter.category) {
      params = params.set('category', filter.category);
    }
    return this._httpClient.get<PaginatedResponse<Insight>>(this._insightsUrl, { params });
  }

  getInsight(id: string): Observable<Insight> {
    return this._httpClient.get<Insight>(`${this._insightsUrl}/${id}`);
  }

  /** Marca como visto ou descarta; devolve o insight atualizado. */
  updateInsightStatus(id: string, status: InsightUserStatus): Observable<Insight> {
    return this._httpClient.patch<Insight>(`${this._insightsUrl}/${id}`, { status });
  }

  getDeterministicAlerts(): Observable<DeterministicAlert[]> {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - 30);

    return this._httpClient.get<DeterministicAlert[]>(`${this._eventsUrl}?from=${from.toISOString()}&to=${to.toISOString()}`);
  }

  streamEvents(): Observable<any> {
    return this._sseService.connect<any>(`${this._eventsUrl}/sse?token=${this._authService.getAccessToken()}`);
  }
}
