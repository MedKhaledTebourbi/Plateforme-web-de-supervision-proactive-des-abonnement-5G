import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { GlobalStats, SaturationRecord, SaturationReport } from './status-badge/saturation-report.model';
import { interval, Observable, shareReplay, startWith, switchMap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SaturationService {
  private readonly base = `http://localhost:8084/api/saturation`;

  constructor(private http: HttpClient) {}

  // ── Analyse complète ──────────────────────────────────────────
  getAllZonesAnalysis(): Observable<SaturationReport[]> {
    return this.http.get<SaturationReport[]>(`${this.base}/zones`);
  }

  getZoneAnalysis(zoneId: number): Observable<SaturationReport> {
    return this.http.get<SaturationReport>(`${this.base}/zones/${zoneId}`);
  }

  getSaturatedZones(): Observable<SaturationReport[]> {
    return this.http.get<SaturationReport[]>(`${this.base}/zones/saturees`);
  }

  getGlobalStats(): Observable<GlobalStats> {
    return this.http.get<GlobalStats>(`${this.base}/stats`);
  }

  // ── Historique ────────────────────────────────────────────────
  getHistorique(zoneId: number, heures = 24): Observable<SaturationRecord[]> {
    const params = new HttpParams().set('heures', heures.toString());
    return this.http.get<SaturationRecord[]>(
      `${this.base}/zones/${zoneId}/historique`, { params }
    );
  }

  // ── Auto-refresh ──────────────────────────────────────────────
  autoRefreshStats(): Observable<GlobalStats> {
    return interval(60000).pipe(
      startWith(0),
      switchMap(() => this.getGlobalStats()),
      shareReplay(1)
    );
  }

  autoRefreshReports(): Observable<SaturationReport[]> {
    return interval(60000).pipe(
      startWith(0),
      switchMap(() => this.getAllZonesAnalysis()),
      shareReplay(1)
    );
  }
  getIaResult(zoneId: number): Observable<any> {
  return this.http.get(`${this.base}/zones/${zoneId}/ia-result`);
}

// Export CSV (téléchargement)
exportCsv(heures = 168): void {
  window.open(`${this.base}/export/csv?heures=${heures}`, '_blank');
}
}