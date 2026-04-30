import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Pylone } from './maps/zone.model';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PyloneService {
  private apiUrl = 'http://localhost:8084/api/pylones';

  constructor(private http: HttpClient) {}

  getPylones(): Observable<Pylone[]> {
    return this.http.get<Pylone[]>(this.apiUrl);
  }

  getPyloneById(id: number): Observable<Pylone> {
    return this.http.get<Pylone>(`${this.apiUrl}/${id}`);
  }

  getPylonesByZone(zoneId: number): Observable<Pylone[]> {
    return this.http.get<Pylone[]>(`${this.apiUrl}/zone/${zoneId}`);
  }

 updatePylone(id: number, pylone: Pylone): Observable<Pylone> {
  return this.http.put<Pylone>(`${this.apiUrl}/${id}`, pylone, {
    headers: { 'Content-Type': 'application/json' }
  });
}

createPylone(pylone: Pylone): Observable<Pylone> {
  return this.http.post<Pylone>(this.apiUrl, pylone, {
    headers: { 'Content-Type': 'application/json' }
  });
}

  deletePylone(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}