import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { SaturationRecord, SaturationReport } from '../status-badge/saturation-report.model';
import { ZoneReseau } from '../maps/zone.model';
import { Chart } from 'chart.js';
import { ActivatedRoute } from '@angular/router';
import { SaturationService } from '../saturation.service';
import { ZonesatService } from '../zonesat.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-zone-detail',
  templateUrl: './zone-detail.component.html',
  styleUrls: ['./zone-detail.component.css']
})
export class ZoneDetailComponent implements OnInit {
  @ViewChild('histChart') chartRef!: ElementRef<HTMLCanvasElement>;

  report: SaturationReport | null = null;
  zone: ZoneReseau | null = null;
  chart: Chart | null = null;
 

  // ── Ajouter ces 4 propriétés ──
  iaResult: any = null;
  anomalyScore: number | null = null;
  isAnomaly: boolean = false;
  predModel: string = 'n/a';

  periods = [
    { label: '6h',  value: 6  },
    { label: '24h', value: 24 },
    { label: '48h', value: 48 },
    { label: '7j',  value: 168 },
  ];
  activePeriod = 24;

  constructor(
    private route: ActivatedRoute,
    private satService: SaturationService,
    private zoneService: ZonesatService,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    forkJoin({
      report: this.satService.getZoneAnalysis(id),
      zone: this.zoneService.getZoneById(id),
    }).subscribe(({ report, zone }) => {
      this.report = report;
      this.zone = zone;
      setTimeout(() => this.loadHistory(24), 100);
    });
  }

  loadHistory(heures: number): void {
    this.activePeriod = heures;
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.satService.getHistorique(id, heures).subscribe(records => {
      this.renderChart(records);
    });
  }

  private renderChart(records: SaturationRecord[]): void {
    const labels = records.map(r =>
      new Date(r.timestamp).toLocaleString('fr-FR', {
        day: '2-digit', month: '2-digit',
        hour: '2-digit', minute: '2-digit'
      })
    );
    const taux   = records.map(r => r.tauxUtilisation);
    const scores = records.map(r => r.anomalyScore);

    if (this.chart) this.chart.destroy();

    this.chart = new Chart(this.chartRef.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Taux d\'utilisation (%)',
            data: taux,
            borderColor: '#378ADD',
            backgroundColor: 'rgba(55,138,221,0.08)',
            fill: true,
            tension: 0.4,
            pointRadius: 2,
            yAxisID: 'y',
          },
          {
            label: 'Score anomalie',
            data: scores,
            borderColor: '#E24B4A',
            borderDash: [4, 4],
            fill: false,
            tension: 0.4,
            pointRadius: 2,
            yAxisID: 'y2',
          }
        ]
      },
      options: {
        responsive: true,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'top' },
          tooltip: { backgroundColor: '#2C2C2A' },
        },
        scales: {
          y: {
            title: { display: true, text: 'Taux (%)' },
            min: 0, max: 100,
            grid: { color: 'rgba(0,0,0,0.05)' },
          },
          y2: {
            title: { display: true, text: 'Anomalie' },
            position: 'right',
            min: -1, max: 1,
            grid: { drawOnChartArea: false },
          },
          x: {
            ticks: { maxRotation: 45, maxTicksLimit: 12 },
            grid: { color: 'rgba(0,0,0,0.04)' }
          }
        }
      }
    });
  }
  loadIaResult(zoneId: number): void {
  this.satService.getIaResult(zoneId).subscribe({
    next: (result) => {
      this.iaResult = result;
      this.anomalyScore  = result?.anomaly_detection?.anomaly_score ?? null;
      this.isAnomaly     = result?.anomaly_detection?.is_anomaly ?? false;
      this.predModel     = result?.prediction?.model_type ?? 'n/a';
    },
    error: () => {
      // Silencieux — le rapport Java est déjà affiché
      console.warn('Python IA indisponible, fallback Java actif');
    }
  });
}
}
