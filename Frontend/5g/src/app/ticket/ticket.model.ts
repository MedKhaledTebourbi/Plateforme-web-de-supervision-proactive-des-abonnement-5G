// models/ticket.model.ts
export enum TicketStatut {
  OUVERT = 'OUVERT',
  EN_COURS = 'EN_COURS',
  EN_ATTENTE = 'EN_ATTENTE',
  RESOLU = 'RESOLU',
  CLOS = 'CLOS',
  ANNULE = 'ANNULE'
}

export const TicketStatutLabels: { [key in TicketStatut]: string } = {
  [TicketStatut.OUVERT]: 'Ouvert',
  [TicketStatut.EN_COURS]: 'En cours',
  [TicketStatut.EN_ATTENTE]: 'En attente',
  [TicketStatut.RESOLU]: 'Résolu',
  [TicketStatut.CLOS]: 'Clos',
  [TicketStatut.ANNULE]: 'Annulé'
};

export const TicketStatutColors: { [key in TicketStatut]: string } = {
  [TicketStatut.OUVERT]: 'danger',
  [TicketStatut.EN_COURS]: 'warning',
  [TicketStatut.EN_ATTENTE]: 'info',
  [TicketStatut.RESOLU]: 'primary',
  [TicketStatut.CLOS]: 'success',
  [TicketStatut.ANNULE]: 'secondary'
};

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

export interface TicketHistorique {
  id: number;
  ticketId: number;
  ancienStatut?: string;
  nouveauStatut: string;
  action: string;
  description: string;
  utilisateurId: number;
  utilisateurNom: string;
  dateAction: Date;
  detailsJson?: string;
}

export interface TicketMetrics {
  ticketId: number;
  zoneNom: string;
  region: string;
  dateCreation: Date;
  dateDebutTraitement?: Date;
  dateFinTraitement?: Date;
  dureeTraitementMinutes?: number;
  dureePremiereReponseMinutes?: number;
  nombreChangementsStatut: number;
  nombreInterventions: number;
  actions: TicketAction[];
}

export interface TicketAction {
  date: Date;
  action: string;
  utilisateur: string;
  details: string;
}

export interface TicketStats {
  tempsMoyenTraitementMinutes: number;
  ticketsTraites: number;
  repartitionStatuts: { [key: string]: number };
  ticketsParTechnicien: { [key: string]: number };
}