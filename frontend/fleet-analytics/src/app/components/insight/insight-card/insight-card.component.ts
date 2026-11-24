import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, inject, Input, OnChanges, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Insight, InsightUserStatus } from '../../../models/insight/insight.model';
import { ConfirmDialogService } from '../../../services/confirm-dialog/confirm-dialog.service';
import { InsightService } from '../../../services/insight/insight.service';
import { SnackbarService } from '../../../services/snackbar/snackbar.service';
import {
  INSIGHT_CATEGORY_ICONS,
  InsightCategoryLabelPipe,
  InsightScopeLabelPipe,
  InsightStatusLabelPipe
} from '../../../shared/pipes/insight-labels';
import { apiErrorMessage } from '../../../shared/utils/api-error';
import { EvidenceItem, evidenceItems } from '../../../shared/utils/insight-evidence';

@Component({
  selector: 'app-insight-card',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    InsightCategoryLabelPipe,
    InsightScopeLabelPipe,
    InsightStatusLabelPipe
  ],
  templateUrl: './insight-card.component.html',
  styleUrl: './insight-card.component.css'
})
export class InsightCardComponent implements OnChanges {
  @Input({ required: true }) insight!: Insight;
  /** Mostra o link para o detalhe do veículo (desligado na própria tela do veículo). */
  @Input() showSubjectLink = true;

  /** Insight devolvido pelo `PATCH`. */
  @Output() changed = new EventEmitter<Insight>();
  /** O insight mudou no servidor (409): o pai deve recarregar a lista. */
  @Output() stale = new EventEmitter<void>();

  private readonly _insightService = inject(InsightService);
  private readonly _confirmDialog = inject(ConfirmDialogService);
  private readonly _snackbar = inject(SnackbarService);

  evidence: EvidenceItem[] = [];
  showEvidence = false;
  pendingAction: InsightUserStatus | null = null;

  ngOnChanges(): void {
    this.evidence = evidenceItems(this.insight.evidence);
  }

  get icon(): string {
    return INSIGHT_CATEGORY_ICONS[this.insight.category] ?? 'lightbulb_outline';
  }

  get isOpen(): boolean {
    return this.insight.status === 'ACTIVE' || this.insight.status === 'ACKNOWLEDGED';
  }

  get vehicleLink(): string[] | null {
    return this.showSubjectLink && this.insight.scope === 'VEHICLE' && this.insight.subjectId
      ? ['/machines/details', this.insight.subjectId]
      : null;
  }

  acknowledge(): void {
    this.runAction('ACKNOWLEDGED');
  }

  dismiss(): void {
    this._confirmDialog
      .confirm({
        title: 'Descartar insight',
        message: 'O insight sai da lista e não será recriado pelos próximos 14 dias, a não ser que a situação se agrave.',
        confirmLabel: 'Descartar'
      })
      .subscribe(() => this.runAction('DISMISSED'));
  }

  private runAction(status: InsightUserStatus): void {
    if (this.pendingAction) {
      return;
    }
    this.pendingAction = status;
    this._insightService.updateInsightStatus(this.insight.id, status).subscribe({
      next: (updated) => {
        this.pendingAction = null;
        this._snackbar.success(status === 'DISMISSED' ? 'Insight descartado.' : 'Insight marcado como visto.');
        this.changed.emit(updated);
      },
      error: (error: HttpErrorResponse) => {
        this.pendingAction = null;
        if (error.status === 409 || error.status === 404) {
          this._snackbar.info('Este insight já foi descartado ou resolvido. A lista foi atualizada.');
          this.stale.emit();
          return;
        }
        this._snackbar.error(apiErrorMessage(error, 'Não foi possível atualizar o insight.'));
      }
    });
  }
}
