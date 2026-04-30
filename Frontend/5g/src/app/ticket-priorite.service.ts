import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { TicketPredictionRequest, TicketPredictionResponse } from './ticket/ticket-priorite.model';


@Injectable({
  providedIn: 'root'
})
export class TicketPrioriteService {

  private apiUrl = 'http://localhost:8084/api/ia/tickets';

  constructor(private http: HttpClient) {}

  /**
   * Appel API IA pour prédire la priorité
   */
  predictPriorite(request: TicketPredictionRequest): Observable<TicketPredictionResponse> {
    return this.http.post<TicketPredictionResponse>(
      `${this.apiUrl}/predict-priorite`,
      request
    );
  }

  /**
   * Health check
   */
  health(): Observable<string> {
    return this.http.get(`${this.apiUrl}/health`, { responseType: 'text' });
  }
}