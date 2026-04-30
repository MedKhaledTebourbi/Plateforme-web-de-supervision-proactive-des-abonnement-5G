import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReportData {
  mois: number;
  annee: number;
  totalTickets: number;
  ticketsClos: number;
  ticketsOuverts: number;
  totalChantiers: number;
  chantiersTermines: number;
  tempsMoyenResolution: number;
  tempsMedianResolution: number;
}

export interface QoSReportDTO {
  tempsMoyenResolution: number;
  tauxSatisfaction: number;
  disponibiliteReseau: number;
  totalIncidents: number;
  incidentsResolus: number;
  tauxSLA: number;
  pannesCritiques: number;
}

export interface TechnicienReportDTO {
  technicienId: number;
  ticketsTraites: number;
  ticketsClos: number;
  chantiersRealises: number;
  chantiersTermines: number;
  performance: number;
  tempsMoyenResolution: number;
  efficacite: number;
  chargeTravail: number;
}

@Injectable({ providedIn: 'root' })
export class ReportService {

  private readonly BASE = 'http://localhost:8084/reports';

  constructor(private http: HttpClient) {}

  getMonthlyReport(mois: number, annee: number): Observable<ReportData> {
    const params = new HttpParams().set('mois', mois).set('annee', annee);
    return this.http.get<ReportData>(`${this.BASE}/monthly`, { params });
  }

  getQoSReport(): Observable<QoSReportDTO> {
    return this.http.get<QoSReportDTO>(`${this.BASE}/qos`);
  }

  getTechnicienReport(technicienId: number): Observable<TechnicienReportDTO> {
    return this.http.get<TechnicienReportDTO>(`${this.BASE}/technicien/${technicienId}`);
  }

  downloadPdf(mois: number, annee: number): Observable<Blob> {
    const params = new HttpParams().set('mois', mois).set('annee', annee);
    return this.http.get(`${this.BASE}/monthly/pdf`, { params, responseType: 'blob' });
  }

  downloadExcel(mois: number, annee: number): Observable<Blob> {
    const params = new HttpParams().set('mois', mois).set('annee', annee);
    return this.http.get(`${this.BASE}/monthly/excel`, { params, responseType: 'blob' });
  }

  // Utilitaire : déclenche le téléchargement du fichier dans le navigateur
  triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href     = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}