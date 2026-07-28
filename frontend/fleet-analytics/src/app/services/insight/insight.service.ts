import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AiInsight } from '../../models/insight/ai-insight.model';
import { DeterministicAlert } from '../../models/insight/deterministic-alert.model';
import { PaginatedResponse } from '../../responses/paginated-response.model';

@Injectable({ providedIn: 'root' })
export class InsightService {
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

  private readonly deterministicAlerts: DeterministicAlert[] = [
    { vehicleId: '3fa85f64-5717-4562-b3fc-2c963f66afa6', tripId: '8ed17539-79c2-4fd2-a177-0ba9b4a7143b', timestamp: '2026-07-27T20:09:31.000Z', eventType: 'TEMPERATURA', severity: 'HIGH', message: 'Temperatura do motor se aproxima do limite configurado.' },
    { vehicleId: '482d41c5-f8ab-4995-b439-13c6cfc21a55', tripId: '5ca6e412-6283-41be-bc28-32a6ee9a7bda', timestamp: '2026-07-27T19:39:31.000Z', eventType: 'MANUTENÇÃO', severity: 'MEDIUM', message: 'Manutenção programada prevista para as próximas 50 horas de operação.' },
    { vehicleId: '0fd018d6-1c81-4dd2-a8c6-42d85a7f21e0', tripId: 'ffb89da1-6b14-4bb0-9693-4e30f6f9f28d', timestamp: '2026-07-27T18:39:31.000Z', eventType: 'FREIO', severity: 'CRITICAL', message: 'Desgaste elevado identificado no sistema de freios.' },
    { vehicleId: 'a2c0876e-bf21-4554-88de-3392292b2b38', tripId: 'cd77f09a-26ec-41a9-a0dc-f4fca4f8c3df', timestamp: '2026-07-27T17:48:12.000Z', eventType: 'COMBUSTÍVEL', severity: 'LOW', message: 'Consumo acima da média observado no trajeto atual.' },
    { vehicleId: 'e8af2b8e-0fd4-4b4e-9cb0-c75bd6fe8d0e', tripId: '9e6e58b6-21cd-4050-a3a1-7a4d282cd9bb', timestamp: '2026-07-27T16:20:09.000Z', eventType: 'VELOCIDADE', severity: 'MEDIUM', message: 'Velocidade acima do limite definido para a área operacional.' }
  ];

  getAiInsights(): Observable<AiInsight[]> {
    return of(this.aiInsights);
  }

  getDeterministicAlerts(page = 0, pageSize = 5): Observable<PaginatedResponse<DeterministicAlert>> {
    const start = page * pageSize;
    const content = this.deterministicAlerts.slice(start, start + pageSize);
    const totalElements = this.deterministicAlerts.length;
    const totalPages = Math.ceil(totalElements / pageSize);

    return of({
      content,
      totalElements,
      totalPages,
      pageable: { pageNumber: page, pageSize },
      last: page >= totalPages - 1,
      first: page === 0,
      numberOfElements: content.length,
      size: pageSize,
      number: page,
      sort: { sorted: false, unsorted: true, empty: true },
      empty: content.length === 0
    });
  }
}
