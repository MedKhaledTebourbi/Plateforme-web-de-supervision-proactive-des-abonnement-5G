import { Component, OnInit } from '@angular/core';
import { QoSReportDTO, ReportData, ReportService } from '../report.service';

 
@Component({
  selector: 'app-report',
  templateUrl: './report.component.html',
  styleUrls: ['./report.component.css']
})
export class ReportComponent implements OnInit {
 
  // ── Sélecteurs ──────────────────────────────────────────────
  mois:  number = new Date().getMonth() + 1;
  annee: number = new Date().getFullYear();
 
  moisOptions = [
    { val: 1,  label: 'Janvier'   }, { val: 2,  label: 'Février'    },
    { val: 3,  label: 'Mars'      }, { val: 4,  label: 'Avril'      },
    { val: 5,  label: 'Mai'       }, { val: 6,  label: 'Juin'       },
    { val: 7,  label: 'Juillet'   }, { val: 8,  label: 'Août'       },
    { val: 9,  label: 'Septembre' }, { val: 10, label: 'Octobre'    },
    { val: 11, label: 'Novembre'  }, { val: 12, label: 'Décembre'   },
  ];
 
  anneeOptions: number[] = [];
 
  // ── États ────────────────────────────────────────────────────
  report:      ReportData    | null = null;
  qos:         QoSReportDTO  | null = null;
  loadingKpi   = false;
  loadingPdf   = false;
  loadingExcel = false;
  loadingQos   = false;
  errorMsg:    string | null = null;
  successMsg:  string | null = null;
 
  constructor(private reportService: ReportService) {}
 
  ngOnInit(): void {
    const current = new Date().getFullYear();
    for (let y = current; y >= current - 4; y--) this.anneeOptions.push(y);
    this.loadPreview();
    this.loadQos();
  }
 
  // ── Aperçu KPIs ──────────────────────────────────────────────
  loadPreview(): void {
    this.loadingKpi = true;
    this.errorMsg   = null;
    this.reportService.getMonthlyReport(this.mois, this.annee).subscribe({
      next:  r  => { this.report = r; this.loadingKpi = false; },
      error: () => { this.errorMsg = 'Impossible de charger les données.'; this.loadingKpi = false; }
    });
  }
 
  // ── QoS ──────────────────────────────────────────────────────
  loadQos(): void {
    this.loadingQos = true;
    this.reportService.getQoSReport().subscribe({
      next:  q  => { this.qos = q; this.loadingQos = false; },
      error: () => { this.loadingQos = false; }
    });
  }
 
  // ── Téléchargements ──────────────────────────────────────────
  downloadPdf(): void {
    this.loadingPdf  = true;
    this.successMsg  = null;
    this.reportService.downloadPdf(this.mois, this.annee).subscribe({
      next: blob => {
        this.reportService.triggerDownload(
          blob, `rapport-${this.mois}-${this.annee}.pdf`
        );
        this.successMsg = 'PDF téléchargé avec succès.';
        this.loadingPdf = false;
      },
      error: () => { this.errorMsg = 'Erreur lors de la génération du PDF.'; this.loadingPdf = false; }
    });
  }
 
  downloadExcel(): void {
    this.loadingExcel = true;
    this.successMsg   = null;
    this.reportService.downloadExcel(this.mois, this.annee).subscribe({
      next: blob => {
        this.reportService.triggerDownload(
          blob, `rapport-${this.mois}-${this.annee}.xlsx`
        );
        this.successMsg   = 'Excel téléchargé avec succès.';
        this.loadingExcel = false;
      },
      error: () => { this.errorMsg = 'Erreur lors de la génération Excel.'; this.loadingExcel = false; }
    });
  }
 
  // ── Utilitaires template ─────────────────────────────────────
  get tauxResolution(): number {
    if (!this.report || this.report.totalTickets === 0) return 0;
    return Math.round(this.report.ticketsClos / this.report.totalTickets * 100);
  }
 
  get tauxChantiers(): number {
    if (!this.report || this.report.totalChantiers === 0) return 0;
    return Math.round(this.report.chantiersTermines / this.report.totalChantiers * 100);
  }
 
  get moisLabel(): string {
    return this.moisOptions.find(m => m.val === this.mois)?.label ?? '';
  }
 
  fmtHours(h: number): string {
    if (!h) return 'N/A';
    const hh = Math.floor(h);
    const mm = Math.round((h - hh) * 60);
    return `${hh}h ${mm.toString().padStart(2, '0')}min`;
  }
 
  statusClass(pct: number): string {
    if (pct >= 80) return 'status-ok';
    if (pct >= 50) return 'status-warn';
    return 'status-danger';
  }
 
  statusLabel(pct: number): string {
    if (pct >= 80) return 'Excellent';
    if (pct >= 50) return 'Correct';
    return 'Insuffisant';
  }
}