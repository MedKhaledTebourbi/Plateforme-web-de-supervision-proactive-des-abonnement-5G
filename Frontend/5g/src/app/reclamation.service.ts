import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
export interface Reclamation {
  id?: number;
  typeReclamation?: string;
  adresse?: string;
  latitude?: number;
  longitude?: number;
  dateReclamation?:Date;
  pyloneId: number | null;    // ✅ doit exister
  pyloneNom: string | null;   // ✅ doit exister
  // ajoute les autres champs selon ton entity Java
}

@Injectable({
  providedIn: 'root'
})
export class ReclamationService {

  private apiUrl = 'http://localhost:8084/api/reclamation';

  constructor(private http: HttpClient) {}

  // 🔥 appel de ton endpoint PUT
  geocodeEtSave(): Observable<Reclamation[]> {
    return this.http.put<Reclamation[]>(
      `${this.apiUrl}/reclamations/geocode`,
      {} // PUT nécessite un body même vide
    );
  }
}