import { inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AiInsight } from '../../models/insight/ai-insight.model';
import { DeterministicAlert } from '../../models/insight/deterministic-alert.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InsightService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _eventsUrl = `${environment.apiBaseUrl}/telemetry/events`;

  private readonly aiInsights: AiInsight[] = [
    {
      title: 'Manutenção preventiva recomendada',
      description: 'Dois veículos estão se aproximando do intervalo programado de manutenção.',
      type: 'WARNING',
      createdAt: '2026-07-27T20:09:31.000Z'
    },
    {
      title: 'Melhoria na eficiência de combustível',
      description: 'A eficiência média de combustível da frota aumentou 12% nos últimos 30 dias.',
      type: 'INFO',
      createdAt: '2026-07-27T19:39:31.000Z'
    },
    {
      title: 'Pico de temperatura detectado',
      description: 'Foi identificado um padrão incomum de temperatura durante o horário de operação.',
      type: 'CRITICAL',
      createdAt: '2026-07-27T18:39:31.000Z'
    },
    {
      title: 'Oportunidade de reduzir tempo ocioso',
      description: 'A análise indica potencial de redução de 8% no tempo ocioso em toda a frota.',
      type: 'WARNING'
    }
  ];

  getAiInsights(): Observable<AiInsight[]> {
    return of(this.aiInsights);
  }

  getDeterministicAlerts(): Observable<DeterministicAlert[]> {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - 30);

    return this._httpClient.get<DeterministicAlert[]>(`${this._eventsUrl}?from=${from.toISOString()}&to=${to.toISOString()}`);
  }
}
