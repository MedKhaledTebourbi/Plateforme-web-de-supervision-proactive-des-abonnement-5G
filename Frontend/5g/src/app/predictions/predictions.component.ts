import { Component, OnInit } from '@angular/core';
import { SaturationService } from '../saturation.service';
import { SaturationReport } from '../status-badge/saturation-report.model';

@Component({
  selector: 'app-predictions',
  templateUrl: './predictions.component.html',
  styleUrls: ['./predictions.component.css']
})
export class PredictionsComponent implements OnInit {
  reports: SaturationReport[] = [];
  filtered: SaturationReport[] = [];
  loading = true;
  horizonFilter = 48;
  confidenceFilter = 20;

  constructor(private satService: SaturationService) {}

  ngOnInit(): void {
    this.satService.getAllZonesAnalysis().subscribe(reports => {
      this.reports = reports
        .filter(r => r.saturationPredite)
        .sort((a, b) =>
          (a.heuresAvantSaturation ?? 999) - (b.heuresAvantSaturation ?? 999)
        );
      this.loading = false;
      this.applyFilters();
    });
  }

  applyFilters(): void {
    this.filtered = this.reports.filter(r =>
      (r.heuresAvantSaturation ?? 999) <= this.horizonFilter &&
      r.confidencePrediction >= this.confidenceFilter / 100
    );
  }

  urgenceColor(h: number | null): string {
    if (!h) return '#1D9E75';
    if (h <= 6)  return '#A32D2D';
    if (h <= 24) return '#E24B4A';
    if (h <= 48) return '#BA7517';
    return '#639922';
  }

  confidenceColor(c: number): string {
    if (c >= 0.7) return '#1D9E75';
    if (c >= 0.4) return '#BA7517';
    return '#E24B4A';
  }
}