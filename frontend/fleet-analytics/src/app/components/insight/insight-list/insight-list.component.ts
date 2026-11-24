import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Subscription } from 'rxjs';
import { DeterministicAlert } from '../../../models/insight/deterministic-alert.model';
import { Insight, InsightCategory, InsightScope, InsightStatus } from '../../../models/insight/insight.model';
import { InsightService } from '../../../services/insight/insight.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { AlertSeverityLabelPipe } from '../../../shared/pipes/alert-severity-label.pipe';
import { AlertTypeLabelPipe } from '../../../shared/pipes/alert-type-label.pipe';
import { INSIGHT_CATEGORY_LABELS } from '../../../shared/pipes/insight-labels';
import { apiErrorMessage } from '../../../shared/utils/api-error';
import { InsightCardComponent } from '../insight-card/insight-card.component';

/** Grupos do filtro de situação. `OPEN` não envia `status`: a API devolve ACTIVE + ACKNOWLEDGED. */
type InsightStatusGroup = 'OPEN' | 'RESOLVED' | 'DISMISSED';

const STATUS_GROUPS: Record<InsightStatusGroup, InsightStatus[]> = {
  OPEN: ['ACTIVE', 'ACKNOWLEDGED'],
  RESOLVED: ['RESOLVED'],
  DISMISSED: ['DISMISSED']
};

@Component({
  selector: 'app-insight-list',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    AlertSeverityLabelPipe,
    AlertTypeLabelPipe,
    InsightCardComponent
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }],
  templateUrl: './insight-list.component.html',
  styleUrl: './insight-list.component.css'
})
export class InsightListComponent implements OnInit {
  private readonly _insightService = inject(InsightService);
  private destroyRef = inject(DestroyRef);

  readonly pageSize = 20;
  readonly categories = Object.entries(INSIGHT_CATEGORY_LABELS) as [InsightCategory, string][];

  insights: Insight[] = [];
  totalInsights = 0;
  pageIndex = 0;
  scopeFilter: InsightScope | '' = '';
  categoryFilter: InsightCategory | '' = '';
  statusFilter: InsightStatusGroup = 'OPEN';
  insightsError: string | null = null;
  isLoadingInsights = true;
  private _insightsRequest?: Subscription;

  alerts: DeterministicAlert[] = [];
  currentLimit = 5;
  isLoadingAlerts = true;

  ngOnInit(): void {
    this.loadInsights();
    this.loadAlerts();
    this.getNewEvents();
  }

  loadInsights(): void {
    this._insightsRequest?.unsubscribe();
    this.isLoadingInsights = true;
    this.insightsError = null;
    this._insightsRequest = this._insightService.getInsights({
      // "Abertos" vai sem status: é o padrão da API (ACTIVE + ACKNOWLEDGED)
      status: this.statusFilter === 'OPEN' ? undefined : STATUS_GROUPS[this.statusFilter],
      scope: this.scopeFilter || undefined,
      category: this.categoryFilter || undefined,
      page: this.pageIndex,
      size: this.pageSize
    }).subscribe({
      next: (page) => {
        this.insights = page.content;
        this.totalInsights = page.totalElements;
        this.isLoadingInsights = false;
      },
      error: (error) => {
        this.insights = [];
        this.totalInsights = 0;
        this.insightsError = apiErrorMessage(error, 'Não foi possível carregar os insights.');
        this.isLoadingInsights = false;
      }
    });
  }

  onFilterChange(): void {
    this.pageIndex = 0;
    this.loadInsights();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.loadInsights();
  }

  /** Atualiza o card com a resposta do PATCH; o que deixa de pertencer ao filtro sai da lista. */
  onInsightChanged(updated: Insight): void {
    if (STATUS_GROUPS[this.statusFilter].includes(updated.status)) {
      this.insights = this.insights.map((insight) => insight.id === updated.id ? updated : insight);
      return;
    }
    this.insights = this.insights.filter((insight) => insight.id !== updated.id);
    this.totalInsights = Math.max(0, this.totalInsights - 1);
    if (this.insights.length === 0 && this.totalInsights > 0) {
      this.pageIndex = Math.max(0, this.pageIndex - 1);
      this.loadInsights();
    }
  }

  alertIcon(severity: string): string {
    return severity === 'CRITICAL' ? 'error_outline' : 'warning_amber';
  }

  severityClass(severity: string): string {
    return `alert-card severity-${severity.toLowerCase()}`;
  }

  private loadAlerts(): void {
    this.isLoadingAlerts = true;
    this._insightService.getDeterministicAlerts().subscribe({
      next: (response) => {
        this.alerts = response;
        this.isLoadingAlerts = false;
      },
      error: () => {
        this.isLoadingAlerts = false;
      }
    });
  }

  updateLimit(): void {
    this.currentLimit += 5;
  }

  private getNewEvents(): void {
    this._insightService.streamEvents()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          const newEvent = event as DeterministicAlert;
          newEvent.isNew = true;
          this.alerts.unshift(newEvent);
        },
        error: (error) => {
          console.error('Error streaming events:', error);
        }
      });
  }
}
