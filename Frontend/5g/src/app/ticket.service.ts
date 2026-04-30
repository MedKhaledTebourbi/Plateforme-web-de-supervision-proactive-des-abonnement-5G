import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable } from 'rxjs';
import { TicketHistorique, TicketMetrics, TicketStats, TicketStatut, } from './ticket/ticket.model';
import { TicketPrioriteService } from './ticket-priorite.service';



export interface Ticket {
  id: number;
  zoneId: number;
  zoneNom: string;
  region: string;
  typePanne: string;
  nombreReclamations: number;
  statut: TicketStatut;
  dateCreation: Date;
 createdBy: string;
  createdByName: string;
  dateDebutTraitement?: Date;
  dateFinTraitement?: Date;
  updatedBy?: number;
  updatedByName?: string;
  dateMaj?: Date;
  priorite?: string;
  description?: string;
  historique?: TicketHistorique[];
  assignedTo?: number;        // ID du technicien affecté
  assignedToName?: string;
}

@Injectable({
  providedIn: 'root'
})
export class TicketService {

  private baseUrl = 'http://localhost:8084/api/reclamation';
  private apiUrl = 'http://localhost:8084/api/tickets/tracabilite';

  constructor(private http: HttpClient, private ticketPrioriteService: TicketPrioriteService) { }

  // Récupérer tous les tickets
  getTicketsByRegionEtTechnicien(region: string, technicienId: number): Observable<Ticket[]> {
  return this.http.get<Ticket[]>(
    `${this.baseUrl}/region/${region}/technicien/${technicienId}`
  );
}
  getAllTickets(): Observable<Ticket[]> {
  return this.http.get<Ticket[]>(`${this.baseUrl}`);
}

  // Récupérer tickets par région
 getTicketsByRegion(region: string): Observable<Ticket[]> {
  // ✅ baseUrl au lieu de apiUrl
  return this.http.get<Ticket[]>(`${this.baseUrl}/region/${region}`);
}

  // Mise à jour du statut
  updateStatut(ticketId: number, statut: string, technicienId: number, technicienNom: string): Observable<Ticket> {
    return this.http.put<Ticket>(
      `${this.baseUrl}/${ticketId}/statut?statut=${statut}&technicienId=${technicienId}&technicienNom=${technicienNom}`,
      {}
    );
  }
  // Ajoute cette méthode dans TicketService
genererTickets(): Observable<string> {
  return this.http.post(`${this.baseUrl}/generer`, {}, { responseType: 'text' });
}
getHistoriqueTicket(ticketId: number): Observable<TicketHistorique[]> {
    return this.http.get<TicketHistorique[]>(`${this.apiUrl}/${ticketId}/historique`);
  }

  getTicketMetrics(ticketId: number): Observable<TicketMetrics> {
    return this.http.get<TicketMetrics>(`${this.apiUrl}/${ticketId}/metrics`);
  }

  getGlobalStats(): Observable<TicketStats> {
    return this.http.get<TicketStats>(`${this.apiUrl}/statistiques`);
  }

  updateStatutTicket(
    ticketId: number,
    nouveauStatut: TicketStatut,
    technicienId: number,
    technicienNom: string,
    commentaire?: string
  ): Observable<Ticket> {
    const params: any = {
      nouveauStatut,
      technicienId,
      technicienNom
    };
    if (commentaire) {
      params.commentaire = commentaire;
    }
    return this.http.put<Ticket>(`${this.apiUrl}/${ticketId}/statut`, null, { params });
  }

  ajouterIntervention(
    ticketId: number,
    intervention: string,
    technicienId: number,
    technicienNom: string
  ): Observable<Ticket> {
    const params = {
      intervention,
      technicienId,
      technicienNom
    };
    return this.http.post<Ticket>(`${this.apiUrl}/${ticketId}/intervention`, null, { params });
  }
  affecterTicket(ticketId: number, technicienId: number, technicienNom: string): Observable<Ticket> {
  return this.http.put<Ticket>(
    `${this.baseUrl}/${ticketId}/affecter`,
    null,
    { params: { technicienId: technicienId.toString(), technicienNom } }
  );
}
    private mapToTicket(data: any): Ticket {
    return {
      id: data.id,
      zoneId: data.zoneId,
      zoneNom: data.zoneNom,
      region: data.region,
      typePanne: data.typePanne,
      nombreReclamations: data.nombreReclamations,
      statut: data.statut as TicketStatut, // Conversion explicite vers l'enum
     dateCreation: data.dateCreation ?? null,
      createdBy: data.createdBy,
      createdByName: data.createdByName,
      dateDebutTraitement: data.dateDebutTraitement ? new Date(data.dateDebutTraitement) : undefined,
      dateFinTraitement: data.dateFinTraitement ? new Date(data.dateFinTraitement) : undefined,
      updatedBy: data.updatedBy,
      updatedByName: data.updatedByName,
      dateMaj: data.dateMaj ? new Date(data.dateMaj) : undefined,
      priorite: data.priorite,
      description: data.description
    };
  }
}
 