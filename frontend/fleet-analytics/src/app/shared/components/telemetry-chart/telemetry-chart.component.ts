import { Component, DestroyRef, inject, Input, OnChanges, OnInit, signal, SimpleChanges, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NgApexchartsModule,
  ChartComponent,
  ApexAxisChartSeries,
  ApexChart,
  ApexXAxis,
  ApexStroke,
  ApexFill,
  ApexTooltip,
  ApexDataLabels
} from 'ng-apexcharts';
import { MachineService } from '../../../services/machine/machine.service';
import { MachineTelemetrySeriesPoint } from '../../../models/machine/telemetry/machine-telemetry-series-point.model';
import { TelemetryMetric } from '../../../models/machine/telemetry/machine-telemetry-metric.type';
import { TinyColor } from '@ctrl/tinycolor';
import { MachineTelemetry } from '../../../models/machine/telemetry/machine-telemetry.model';

export type ChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  xaxis: ApexXAxis;
  stroke: ApexStroke;
  tooltip: ApexTooltip;
  fill: ApexFill;
  colors: string[];
  dataLabels: ApexDataLabels;
};

@Component({
  selector: 'app-telemetry-chart',
  imports: [NgApexchartsModule],
  templateUrl: './telemetry-chart.component.html',
  styleUrl: './telemetry-chart.component.css'
})
export class TelemetryChartComponent implements OnInit, OnChanges {

  @Input({ required: true }) machineId!: string;
  @Input({ required: true }) title!: string;
  @Input() metric: TelemetryMetric = "rpm";
  @Input() periodStart = new Date('2026-05-05');
  @Input() colorAverageChart: string = "#007BFF";
  @Input() lightenFactor: number = 40
  @Input() unit: string = ""
  @Input() newData: MachineTelemetry | null = null;

  private readonly _machineService = inject(MachineService);
  private readonly _destroyRef = inject(DestroyRef);

  chart = viewChild<ChartComponent>('chart');
  chartSeries = signal<ApexAxisChartSeries>([]);
  chartOptions!: Partial<ChartOptions>;
  chartData: { x: number; y: number }[] = [];

  ngOnInit(): void {
    this.configChart();
    this.loadData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    console.log(changes)
    if (this.newData) {
      switch (this.metric) {
        case 'rpm':
          this.chartData.push({
            x: new Date(this.newData.timestamp).getTime(),
            y: this.newData.engineRpm ?? null
          });
          break;
        case 'temperature':
          this.chartData.push({
            x: new Date(this.newData.timestamp).getTime(),
            y: this.newData.engineTempC ?? null
          });
          break;
        case 'fuel':
          this.chartData.push({
            x: new Date(this.newData.timestamp).getTime(),
            y: this.newData.fuelLevelPct ?? null
          });
          break;
        case 'battery':
          this.chartData.push({
            x: new Date(this.newData.timestamp).getTime(),
            y: this.newData.batteryVoltage ?? null
          });
          break;
        case 'odometer':
          this.chartData.push({
            x: new Date(this.newData.timestamp).getTime(),
            y: this.newData.odometerKm ?? null
          });
          break;
        default:
          break;
      }
      this.chartSeries.set([
        { name: 'Média', type: 'line', data: this.chartData }
      ]);
    }
    if (changes['periodStart'] && 
      !changes['periodStart'].firstChange && 
      changes['periodStart'].currentValue !== changes['periodStart'].previousValue && 
      changes['periodStart'].previousValue !== undefined) {
      this.loadData();
    }
  }

  configChart(): void {
    this.chartOptions = {
      chart: {
        height: 350,
        type: 'line',
        animations: { enabled: false },
        toolbar: { show: true },
        defaultLocale: 'pt-br',
        locales: [{
          name: 'pt-br',
          options: {
            months: ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'],
            shortMonths: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'],
            days: ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'],
            shortDays: ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'],
            toolbar: {
              download: 'Baixar SVG',
              selection: 'Seleção',
              selectionZoom: 'Zoom na Seleção',
              zoomIn: 'Aumentar Zoom',
              zoomOut: 'Diminuir Zoom',
              pan: 'Mover',
              reset: 'Resetar Zoom',
            }
          }
        }],
        events: {
          mounted: (chartContext: any) => {
            chartContext.el.querySelectorAll('title').forEach((t: any) => t.remove());
          },
          updated: (chartContext: any) => {
            chartContext.el.querySelectorAll('title').forEach((t: any) => t.remove());
          }
        }
      },
      colors: [this.colorAverageChart],
      stroke: { curve: 'smooth', width: 3 },
      fill: { opacity: 0.6 },
      tooltip: {
        shared: true,
        intersect: false,
        x: { format: 'dd/MM/yyyy HH:mm' },
        custom: ({ dataPointIndex, w }) => {
          const colorAverageChart = this.colorAverageChart;
          const seriesData = w.config.series;
          const mainSeries = seriesData[0];
          const point = mainSeries?.data?.[dataPointIndex];

          const timestamp = Array.isArray(point) ? point[0] : point?.x;
          const value = Array.isArray(point) ? point[1] : point?.y;

          let formattedDate = 'Data indisponível';

          if (timestamp) {
            formattedDate = new Date(timestamp).toLocaleString('pt-BR', {
              day: '2-digit', month: '2-digit', year: 'numeric',
              hour: '2-digit', minute: '2-digit'
            });
          }

          return `
            <div style="padding: 16px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); min-width: 180px;">
              <div style="font-size: 12px; font-weight: 600; color: #64748b; margin-bottom: 12px; text-transform: uppercase; border-bottom: 1px solid #f1f5f9; padding-bottom: 8px;">
                ${formattedDate}
              </div>

              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="display: flex; align-items: center; gap: 6px;">
                  <span style="display: block; width: 10px; height: 10px; border-radius: 50%; background-color: ${colorAverageChart};"></span>
                  <span style="font-size: 13px; color: #475569; font-weight: 500;">Valor</span>
                </div>
                <span style="font-size: 14px; font-weight: 700; color: #0f172a;">${typeof value === "number" ? +value.toFixed(4) : value ?? 'N/A'}${this.unit}</span>
              </div>
            </div>
          `;
        }
      },
      dataLabels: { enabled: false },
      xaxis: { type: 'datetime', labels: { datetimeUTC: false } }
    };
  }

  loadData(): void {
    this._machineService
      .getMachineTelemetrySeries(this.machineId, this.periodStart)
      .pipe(takeUntilDestroyed(this._destroyRef))
      .subscribe({
        next: (points) => {
          this.chartData = this.mapToMetric(points);
          this.chartSeries.set([
            { name: 'Média', type: 'line', data: this.chartData }
          ]);
        },
        error: (err) => console.error('Error loading telemetry series:', err)
      });
  }

  private mapToMetric(points: MachineTelemetrySeriesPoint[]): { x: number; y: number }[] {
    switch (this.metric) {
      case 'rpm':
        return points.map((p) => ({
          x: new Date(p.timestamp).getTime(),
          y: p.engineRpm
        }));
      case 'temperature':
        return points.map((p) => ({
          x: new Date(p.timestamp).getTime(),
          y: p.engineTempC
        }));
      case 'fuel':
        return points.map((p) => ({
          x: new Date(p.timestamp).getTime(),
          y: p.fuelLevelPct
        }));
      case 'battery':
        return points.map((p) => ({
          x: new Date(p.timestamp).getTime(),
          y: p.batteryVoltage
        }));
      case 'odometer':
        return points.map((p) => ({
          x: new Date(p.timestamp).getTime(),
          y: p.odometerKm
        }));
      default:
        return [];
    }
  }
}
