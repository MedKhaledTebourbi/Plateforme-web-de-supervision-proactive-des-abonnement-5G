import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ZoneReseau } from './maps/zone.model';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ZoneService {
  private apiUrl = 'http://localhost:8084/api/zones';

  constructor(private http: HttpClient) {}

  getZones(): Observable<ZoneReseau[]> {
    return this.http.get<ZoneReseau[]>(this.apiUrl);
  }

  getZoneById(id: number): Observable<ZoneReseau> {
    return this.http.get<ZoneReseau>(`${this.apiUrl}/${id}`);
  }

  createZone(zone: ZoneReseau): Observable<ZoneReseau> {
    return this.http.post<ZoneReseau>(this.apiUrl, zone);
  }

  updateZone(id: number, zone: ZoneReseau): Observable<ZoneReseau> {
    return this.http.put<ZoneReseau>(`${this.apiUrl}/${id}`, zone);
  }

  deleteZone(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
   // =============================
  // 🔥 Simulation réseau
  // =============================

  // POST /api/zones/zones/simulate-load
  simulateLoad(): Observable<any> {
    return this.http.post(`${this.apiUrl}/zones/simulate-load`, {});
  }

  // POST /api/zones/zones/reset-load
  resetLoad(): Observable<string> {
    return this.http.post(`${this.apiUrl}/zones/reset-load`, {}, { responseType: 'text' });
  }

  // POST /api/zones/zones/{id}/force-load?tauxPourcent=80
  forceLoad(id: number, tauxPourcent: number): Observable<any> {
    const params = new HttpParams().set('tauxPourcent', tauxPourcent);
    return this.http.post(`${this.apiUrl}/zones/${id}/force-load`, {}, { params });
  }
}
