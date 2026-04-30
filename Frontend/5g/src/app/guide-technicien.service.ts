import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GuideResponse } from './ticket/ticket-guide.model';

@Injectable({
  providedIn: 'root'
})
export class GuideTechnicienService {

  private apiUrl = 'http://localhost:8084/api/ia/guide';

  constructor(private http: HttpClient) {}

  getGuide(request: any): Observable<GuideResponse> {
    return this.http.post<GuideResponse>(`${this.apiUrl}/ticket`, request);
  }
}