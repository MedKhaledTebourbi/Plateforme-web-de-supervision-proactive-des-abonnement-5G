import { Injectable } from '@angular/core';
import { Pylone, ZoneReseau } from './maps/zone.model';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class ZonesatService {

 private readonly base = `http://localhost:8084/api/saturation`;

  constructor(private http: HttpClient) {}

  getAllZones(): Observable<ZoneReseau[]> {
    return this.http.get<ZoneReseau[]>(`${this.base}/zones`);
  }

  getZoneById(id: number): Observable<ZoneReseau> {
    return this.http.get<ZoneReseau>(`${this.base}/zones/${id}`);
  }

  getAllPylones(): Observable<Pylone[]> {
    return this.http.get<Pylone[]>(`${this.base}/pylones`);
  }
}
