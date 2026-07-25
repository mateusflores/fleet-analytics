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

  private readonly _machineService = inject(MachineService);
  private readonly _destroyRef = inject(DestroyRef);

  chart = viewChild<ChartComponent>('chart');
  chartSeries = signal<ApexAxisChartSeries>([]);
  chartOptions!: Partial<ChartOptions>; 

  ngOnInit(): void {
    this.configChart();
    this.loadData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.loadData()
  }

  configChart(): void {
    this.chartOptions = {
      chart: {
        height: 350,
        type: 'rangeArea',
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
      colors: [
        new TinyColor(this.colorAverageChart).lighten(this.lightenFactor).toString(), 
        this.colorAverageChart
      ],
      stroke: { curve: 'smooth', width: [0, 3] },
      fill: { opacity: [0.8, 1] },
      tooltip: {
        shared: true,
        intersect: false,
        x: { format: 'dd/MM/yyyy HH:mm' },
        custom: ({ series, seriesIndex, dataPointIndex, w }) => {
          const colorAverageChart = this.colorAverageChart
          const colorRangeChart = new TinyColor(this.colorAverageChart).lighten(this.lightenFactor).toString();

          const seriesData = w.config.series;
  
          const rangeSeries = seriesData[0]; 
          const averageSeries = seriesData[1]; 
  
          const rangePoint = rangeSeries.data[dataPointIndex];
          const avgPoint = averageSeries.data[dataPointIndex];
  
          const yRange = Array.isArray(rangePoint) ? rangePoint[1] : rangePoint.y;
          const min = yRange[0];
          const max = yRange[1];
          
          const avg = Array.isArray(avgPoint) ? avgPoint[1] : avgPoint.y;
  
          let timestamp;
  
          if (rangePoint) {
            timestamp = Array.isArray(rangePoint) ? rangePoint[0] : rangePoint.x;
          } else if (avgPoint) {
            timestamp = Array.isArray(avgPoint) ? avgPoint[0] : avgPoint.x;
          }
  
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
              
              <div style="display: flex; flex-direction: column; gap: 8px;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="display: block; width: 10px; height: 10px; border-radius: 50%; background-color: ${colorAverageChart};"></span>
                    <span style="font-size: 13px; color: #475569; font-weight: 500;">Média</span>
                  </div>
                  <span style="font-size: 14px; font-weight: 700; color: #0f172a;">${avg}${this.unit}</span>
                </div>
  
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="display: block; width: 10px; height: 10px; border-radius: 50%; background-color: ${colorRangeChart};"></span>
                    <span style="font-size: 13px; color: #475569; font-weight: 500;">Máximo</span>
                  </div>
                  <span style="font-size: 13px; font-weight: 600; color: #334155;">${max}${this.unit}</span>
                </div>
  
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="display: block; width: 10px; height: 10px; border-radius: 50%; background-color: ${colorRangeChart};"></span>
                    <span style="font-size: 13px; color: #475569; font-weight: 500;">Mínimo</span>
                  </div>
                  <span style="font-size: 13px; font-weight: 600; color: #334155;">${min}${this.unit}</span>
                </div>
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
      .getMachineTelemetrySeries(this.machineId, this.periodStart, this.metric)
      .pipe(takeUntilDestroyed(this._destroyRef))
      .subscribe({
        next: (points) => {
          const minMaxData = this.mapToMinMaxSeries(points);
          const averageData = this.mapToAverageSeries(points);
          this.chartSeries.set([
            { name: 'Faixa', type: 'rangeArea', data: minMaxData },
            { name: 'Média', type: 'line', data: averageData }
          ]);
        },
        error: (err) => console.error('Error loading telemetry series:', err)
      });
  }

  private mapToMinMaxSeries(points: MachineTelemetrySeriesPoint[]): { x: number; y: [number, number] }[] {
    return points.map((p) => ({
      x: new Date(p.timestamp).getTime(),
      y: [p.min, p.max]
    }));
  }

  private mapToAverageSeries(points: MachineTelemetrySeriesPoint[]): { x: number; y: number }[] {
    return points.map((p) => ({
      x: new Date(p.timestamp).getTime(),
      y: p.average
    }));
  }
}
