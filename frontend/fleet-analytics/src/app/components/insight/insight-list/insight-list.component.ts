import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AiInsight, AiInsightType } from '../../../models/insight/ai-insight.model';
import { DeterministicAlert } from '../../../models/insight/deterministic-alert.model';
import { InsightService } from '../../../services/insight/insight.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { AlertSeverityLabelPipe } from '../../../shared/pipes/alert-severity-label.pipe';
import { AlertTypeLabelPipe } from '../../../shared/pipes/alert-type-label.pipe';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-insight-list',
  imports: [
    DatePipe, 
    MatIconModule, 
    MatPaginatorModule, 
    MatProgressSpinnerModule, 
    AlertSeverityLabelPipe,
    AlertTypeLabelPipe
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }],
  templateUrl: './insight-list.component.html',
  styleUrl: './insight-list.component.css'
})
export class InsightListComponent implements OnInit {
  private readonly _insightService = inject(InsightService);
  private destroyRef = inject(DestroyRef);

  aiInsights: AiInsight[] = [];
  alerts: DeterministicAlert[] = [];
  currentLimit = 5;
  isLoadingInsights = true;
  isLoadingAlerts = true;

  ngOnInit(): void {
    this.loadAiInsights();
    this.loadAlerts();
    this.getNewEvents();
  }

  insightIcon(type: AiInsightType): string {
    return { WARNING: 'build', INFO: 'trending_up', CRITICAL: 'warning_amber' }[type];
  }

  alertIcon(severity: string): string {
    return severity === 'CRITICAL' ? 'error_outline' : 'warning_amber';
  }

  severityClass(severity: string): string {
    return `alert-card severity-${severity.toLowerCase()}`;
  }

  private loadAiInsights(): void {
    this.isLoadingInsights = true;
    this._insightService.getAiInsights().subscribe({
      next: (insights) => { 
        this.aiInsights = insights;
        this.isLoadingInsights = false; 
      },
      error: () => { 
        this.isLoadingInsights = false; 
      }
    });
  }

  private loadAlerts(): void {
    this.isLoadingAlerts = true;
    this._insightService.getDeterministicAlerts().subscribe({
      next: (response) => { 
        this.alerts = response;
        console.log(this.alerts);
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
