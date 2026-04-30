import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
@Injectable({
  providedIn: 'root'
})
export class TicketCommunicationService {

  constructor() { }
  
  private interventionSource = new BehaviorSubject<{ticketId: number, intervention: string} | null>(null);
  intervention$ = this.interventionSource.asObservable();

  // Envoyer une intervention depuis maps
  envoyerIntervention(ticketId: number, intervention: string) {
    this.interventionSource.next({ ticketId, intervention });
  }

  // Reset
  reset() {
    this.interventionSource.next(null);
  }
}
