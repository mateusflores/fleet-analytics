import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AiInsight, AiInsightType } from '../../../models/insight/ai-insight.model';
import { DeterministicAlert } from '../../../models/insight/deterministic-alert.model';
import { InsightService } from '../../../services/insight/insight.service';
import { getPtBrPaginatorIntl } from '../../../shared/config/paginator/paginator-intl-pt-br';
import { AlertSeverityLabelPipe } from '../../../shared/pipes/alert-severity-label.pipe';

@Component({
  selector: 'app-insight-list',
  imports: [
    DatePipe, 
    MatIconModule, 
    MatPaginatorModule, 
    MatProgressSpinnerModule, 
    AlertSeverityLabelPipe
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: getPtBrPaginatorIntl }],
  templateUrl: './insight-list.component.html',
  styleUrl: './insight-list.component.css'
})
export class InsightListComponent implements OnInit {
  private readonly insightService = inject(InsightService);

  aiInsights: AiInsight[] = [];
  alerts: DeterministicAlert[] = [];
  isLoadingInsights = true;
  isLoadingAlerts = true;
  pageIndex = 0;
  pageSize = 5;
  totalAlerts = 0;

  ngOnInit(): void {
    this.loadAiInsights();
    this.loadAlerts();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadAlerts();
  }

  insightIcon(type: AiInsightType): string {
    return { WARNING: 'build', INFO: 'trending_up', CRITICAL: 'warning_amber' }[type];
  }

  alertIcon(severity: string): string {
    return severity === 'CRITICAL' ? 'error_outline' : severity === 'LOW' ? 'info_outline' : 'warning_amber';
  }

  severityClass(severity: string): string {
    return `alert-card severity-${severity.toLowerCase()}`;
  }

  private loadAiInsights(): void {
    this.isLoadingInsights = true;
    this.insightService.getAiInsights().subscribe({
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
    this.insightService.getDeterministicAlerts(this.pageIndex, this.pageSize).subscribe({
      next: (response) => { 
        this.alerts = response.content; 
        this.totalAlerts = response.totalElements; 
        this.isLoadingAlerts = false; 
      },
      error: () => { 
        this.isLoadingAlerts = false; 
      }
    });
  }
}
