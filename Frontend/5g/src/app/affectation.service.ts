import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Client {
  id: number;
  adresse: string;
  typeAbonnement: number;
  latitude: number | null;
  longitude: number | null;
  pylone: {
    id: number;
    nom: string;
    latitude: number;
    longitude: number;
    capaciteMax: number;
    chargeActuelle: number;
    rayonCouverture: number;
    zoneNom?: string;
  } | null;
}

@Injectable({ providedIn: 'root' })
export class AffectationService {

  private url = 'http://localhost:8084/api/affectation';

  constructor(private http: HttpClient) {}

  // ✅ Lancer l'affectation automatique
  lancerAffectation(): Observable<string> {
    return this.http.post(this.url + '/auto', {}, { responseType: 'text' });
  }

  // ✅ Récupérer tous les clients
  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(this.url);
  }

  // ✅ Clients sans pylône (en attente de réaffectation)
  getClientsSansPylone(): Observable<Client[]> {
    return this.http.get<Client[]>(this.url).pipe(
      // filtrer côté Angular
      // ou ajouter un endpoint dédié côté backend
    ) as Observable<Client[]>;
  }
}