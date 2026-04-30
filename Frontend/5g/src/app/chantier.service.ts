import { Injectable } from '@angular/core';


import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Chantier } from './chantier-form/chantier.model';


@Injectable({
  providedIn: 'root'
})
export class ChantierService {
  private apiUrl = 'http://localhost:8083/api/chantiers';

  constructor(private http: HttpClient) { }

  // Créer un chantier
  creerChantier(chantier: Chantier): Observable<Chantier> {
    return this.http.post<Chantier>(this.apiUrl, chantier);
  }

  // Récupérer tous les chantiers
  getAllChantiers(): Observable<Chantier[]> {
    return this.http.get<Chantier[]>(this.apiUrl);
  }

  // Récupérer les chantiers par pylône
  getChantiersByPylone(pyloneId: number): Observable<Chantier[]> {
    return this.http.get<Chantier[]>(`${this.apiUrl}/pylone/${pyloneId}`);
  }

  // Récupérer les chantiers par région
  getChantiersByRegion(region: string): Observable<Chantier[]> {
    return this.http.get<Chantier[]>(`${this.apiUrl}/region/${region}`);
  }

  // Récupérer les chantiers par technicien
  getChantiersByTechnicien(technicienId: number): Observable<Chantier[]> {
    return this.http.get<Chantier[]>(`${this.apiUrl}/technicien/${technicienId}`);
  }

  // Valider un chantier
  validerChantier(id: number): Observable<Chantier> {
    return this.http.put<Chantier>(`${this.apiUrl}/${id}/valider`, {});
  }

  // Terminer un chantier
  terminerChantier(id: number): Observable<Chantier> {
    return this.http.put<Chantier>(`${this.apiUrl}/${id}/terminer`, {});
  }

  // Annuler un chantier
  annulerChantier(id: number): Observable<Chantier> {
    return this.http.put<Chantier>(`${this.apiUrl}/${id}/annuler`, {});
  }

  // Vérifier si un pylône est bloqué
  verifierBloquage(pyloneId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/pylone/${pyloneId}/bloque`);
  }
}
