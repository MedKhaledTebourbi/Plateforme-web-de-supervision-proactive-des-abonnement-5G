// ticket.component.ts
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import * as moment from 'moment';
import { Ticket, TicketHistorique, TicketMetrics, TicketStatut, TicketStatutColors, TicketStatutLabels } from './ticket.model';
import { TicketService } from '../ticket.service';
import { UtilisateurService } from '../utilisateur-service.service';
import { TicketCommunicationService } from '../ticket-communication.service';
import { TicketPrioriteService } from '../ticket-priorite.service';
import { GuideResponse } from './ticket-guide.model';
import { HttpClient } from '@angular/common/http';
import { GuideTechnicienService } from '../guide-technicien.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-ticket',
  templateUrl: './ticket.component.html',
  styleUrls: ['./ticket.component.css']
})
export class TicketComponent implements OnInit {
  tickets: Ticket[] = [];
  currentUserRegion = '';
  currentUserId = 0;
  currentUserName = '';
  currentUserRole = '';
  selectedRegion: string = '';
  loading: boolean = false;
  selectedTicket: Ticket | null = null;
  historiqueTicket: TicketHistorique[] = [];
  ticketMetrics: TicketMetrics | null = null;
  interventionForm: FormGroup;
  commentaireForm: FormGroup;
  globalStats: any = null;
  messageGeneration: string = '';
  showModal: boolean = false;
  autoGenerationEnabled: boolean = true; // Activation de la génération auto
  techniciens: any[] = [];
  // Filtres
  filterStatut: TicketStatut | 'TOUS' = 'TOUS';
  filterDateDebut: string = '';
  filterDateFin: string = '';
  filterRecherche: string = '';
  alertCount: number = 0;
  sidebarCollapsed = false;
  currentUsername = '';
  currentUserInitials = '';
  isAdmin: boolean = false;
  // Pagination
  page: number = 1;
  pageSize: number = 10;
  guideIA: GuideResponse | null = null;
 loadingGuide: boolean = false;
  // Constantes pour le template
  TicketStatut = TicketStatut;
  TicketStatutLabels = TicketStatutLabels;
  TicketStatutColors = TicketStatutColors;
  statutsDisponibles = Object.values(TicketStatut);
  errorGuide: string = '';
  regions: string[] = [
    'Tunis', 'Ariana', 'Ben Arous', 'Manouba', 'Nabeul', 
    'Zaghouan', 'Bizerte', 'Béja', 'Jendouba', 'Le Kef', 
    'Siliana', 'Kairouan', 'Kasserine', 'Sousse', 'Monastir', 
    'Mahdia', 'Sfax', 'Gabès', 'Médenine', 'Tataouine', 
    'Gafsa', 'Tozeur', 'Kebili', 'Sidi Bouzid'
  ];
  

  constructor(
    private ticketService: TicketService,
    private utilisateurService: UtilisateurService,
    private fb: FormBuilder,
    private ticketComm: TicketCommunicationService ,
    private ticketPrioriteService: TicketPrioriteService,
    private http: HttpClient,
    private guideService: GuideTechnicienService,
     private router: Router,
  ) {
    this.interventionForm = this.fb.group({
      intervention: ['', [Validators.required, Validators.minLength(10)]]
    });
    
    this.commentaireForm = this.fb.group({
      commentaire: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.loadCurrentUser();
    this.ecouterInterventionsDepuisMaps();
    this.genererTicketsAuto();
  }
  ecouterInterventionsDepuisMaps() {
  this.ticketComm.intervention$.subscribe(data => {
    if (!data) return;

    console.log('📩 Intervention reçue depuis maps:', data);

    // Recharger l'historique du ticket concerné
    this.loadHistorique(data.ticketId);

    // Si le ticket est actuellement ouvert dans le modal, recharger aussi les métriques
    if (this.selectedTicket?.id === data.ticketId) {
      this.loadMetrics(data.ticketId);
    }

    // Recharger la liste des tickets
    this.loadTickets();
  });
}

   loadCurrentUser() {
    this.utilisateurService.getCurrentUser().subscribe({
      next: (user: any) => {
        this.currentUserName = user.username || '';
        this.currentUserRole = user.role || '';
        this.currentUserRegion = user.region || '';
        this.currentUserId = user.id || 0;

        this.loadTickets();

        if (this.currentUserRole === 'ADMIN' && this.autoGenerationEnabled) {
          this.genererTicketsAuto();
        }
        if (this.currentUserRole === 'ADMIN') {
          this.loadGlobalStats();
        }
        // ✅ Chef de région : charger la liste des techniciens
        if (this.currentUserRole === 'CHEF' && this.currentUserRegion) {
          this.loadTechniciens();
        }
      },
      error: (err: any) => {
        this.currentUserName = '';
        this.currentUserRole = '';
        this.currentUserRegion = '';
        this.currentUserId = 0;
        this.loadTickets();
      }
    });
  }
 loadTechniciens() {
  this.utilisateurService.getTechniciensByRegion(this.currentUserRegion)
    .subscribe({
      next: (data: any[]) => {
        this.techniciens = data;
        console.log('Techniciens région:', this.techniciens);
      },
      error: (err: any) => {
        console.error('Erreur chargement techniciens', err);
        this.techniciens = [];
      }
    });
}

 loadTickets() {
  this.loading = true;
  let obs;

  if (this.currentUserRole === 'ADMIN') {
    obs = this.ticketService.getAllTickets();

  } else if (this.currentUserRole === 'CHEF' && this.currentUserRegion) {
    obs = this.ticketService.getTicketsByRegion(this.currentUserRegion);

  } else if (this.currentUserRole === 'TECHNICIEN') {
    obs = this.ticketService.getTicketsByRegion(this.currentUserRegion);

  } else {
    obs = this.ticketService.getAllTickets();
  }

  obs.subscribe({
    next: (data: Ticket[]) => {

      if (this.currentUserRole === 'ADMIN' || this.currentUserRole === 'CHEF') {
        this.tickets = data;

      } else if (this.currentUserRole === 'TECHNICIEN') {
        // ✅ FILTRAGE ICI
        this.tickets = data.filter(t =>
          t.assignedTo === this.currentUserId
        );

      } else {
        this.tickets = [];
     }

      // ── Appeler IA uniquement pour tickets sans priorité ──
      const sansPriorite = this.tickets.filter(
        t => !t.priorite || t.priorite.trim() === ''
      );

      console.log(`[IA] ${sansPriorite.length}/${this.tickets.length} tickets sans priorité`);
      sansPriorite.forEach(ticket => this.predictPrioriteForTicket(ticket));


      this.alertCount = this.tickets.filter(t =>
        t.statut === TicketStatut.OUVERT
      ).length;

      this.applyFilters();
      this.loading = false;
    },
    error: (err: any) => {
      console.error('Erreur chargement tickets', err);
      this.loading = false;
    }
  });
}
   affecterTicket(ticket: Ticket) {
    if (this.techniciens.length === 0) {
      this.showNotification('warning', 'Aucun technicien disponible dans votre région');
      return;
    }

    // Construire le menu de sélection via prompt (simple)
    const options = this.techniciens
      .map((t, i) => `${i + 1}. ${t.username}`)
      .join('\n');
    const choix = prompt(
      `Affecter le ticket #${ticket.id} (${ticket.zoneNom}) à :\n\n${options}\n\nEntrez le numéro :`
    );

    if (!choix) return;

    const index = parseInt(choix, 10) - 1;
    if (isNaN(index) || index < 0 || index >= this.techniciens.length) {
      this.showNotification('error', 'Sélection invalide');
      return;
    }

    const technicien = this.techniciens[index];
    this.loading = true;

    this.ticketService.affecterTicket(ticket.id, technicien.id, technicien.username)
      .subscribe({
        next: (updated: Ticket) => {
          const idx = this.tickets.findIndex(t => t.id === updated.id);
          if (idx !== -1) this.tickets[idx] = updated;
          this.showNotification('success',
            `Ticket #${updated.id} affecté à ${technicien.username}`);
          this.loadTickets();
          this.loading = false;
        },
        error: (err: any) => {
          console.error('Erreur affectation ticket', err);
          this.showNotification('error', "Erreur lors de l'affectation");
          this.loading = false;
        }
      });
  }

  // ✅ Vérifier si l'utilisateur courant peut affecter
  peutAffecter(): boolean {
    return this.currentUserRole === 'CHEF' || this.currentUserRole === 'ADMIN';
  }

  // ✅ Vérifier si un ticket peut être affecté
  ticketAffectable(ticket: Ticket): boolean {
    return ticket.statut !== TicketStatut.CLOS &&
           ticket.statut !== TicketStatut.ANNULE;
  }
  
  applyFilters() {
    let filtered = [...this.tickets];
    
    // Filtre par statut
    if (this.filterStatut !== 'TOUS') {
      filtered = filtered.filter(t => t.statut === this.filterStatut);
    }
    
    // Filtre par date
    if (this.filterDateDebut) {
      filtered = filtered.filter(t => 
        new Date(t.dateCreation) >= new Date(this.filterDateDebut)
      );
    }
    
    if (this.filterDateFin) {
      filtered = filtered.filter(t => 
        new Date(t.dateCreation) <= new Date(this.filterDateFin)
      );
    }
    
    // Filtre par recherche (zone, région, type panne)
    if (this.filterRecherche) {
      const recherche = this.filterRecherche.toLowerCase();
      filtered = filtered.filter(t => 
        t.zoneNom.toLowerCase().includes(recherche) ||
        t.region.toLowerCase().includes(recherche) ||
        t.typePanne.toLowerCase().includes(recherche)
      );
    }
    
    this.tickets = filtered;
  }
  
  resetFilters() {
    this.filterStatut = 'TOUS';
    this.filterDateDebut = '';
    this.filterDateFin = '';
    this.filterRecherche = '';
    this.loadTickets();
  }
  
  get filteredTickets(): Ticket[] {
    return this.tickets;
  }
  
  get paginatedTickets(): Ticket[] {
    const start = (this.page - 1) * this.pageSize;
    const end = start + this.pageSize;
    return this.filteredTickets.slice(start, end);
  }
  
  get totalPages(): number {
    return Math.ceil(this.filteredTickets.length / this.pageSize);
  }

  changerStatut(ticket: Ticket, nouveauStatut: TicketStatut) {
    let message = '';
    let confirmRequired = false;
    
    switch (nouveauStatut) {
      case TicketStatut.CLOS:
        message = `Clôturer le ticket #${ticket.id} (${ticket.zoneNom}) ?\n` +
                  `⚠️ Toutes les réclamations de cette zone seront supprimées.`;
        confirmRequired = true;
        break;
      case TicketStatut.ANNULE:
        message = `Annuler le ticket #${ticket.id} (${ticket.zoneNom}) ?\n` +
                  `Cette action est irréversible.`;
        confirmRequired = true;
        break;
      case TicketStatut.EN_COURS:
        message = `Mettre en cours le ticket #${ticket.id} ?`;
        break;
    }
    
    if (confirmRequired) {
      if (!confirm(message)) return;
    }
    
    if (nouveauStatut === TicketStatut.CLOS || nouveauStatut === TicketStatut.ANNULE) {
      this.openCommentaireModal(ticket, nouveauStatut);
    } else {
      this.executeChangementStatut(ticket, nouveauStatut, '');
    }
  }
  
  executeChangementStatut(ticket: Ticket, nouveauStatut: TicketStatut, commentaire: string) {
    this.loading = true;
    this.ticketService.updateStatutTicket(
      ticket.id,
      nouveauStatut,
      this.currentUserId,
      this.currentUserName,
      commentaire
    ).subscribe({
      next: (updated: Ticket) => {
        const index = this.tickets.findIndex(t => t.id === updated.id);
        if (index !== -1) {
          this.tickets[index] = updated;
        }
        this.showNotification('success', `Ticket #${updated.id} mis à jour avec succès`);
        this.loadTickets();
        if (this.currentUserRole === 'ADMIN') {
          this.loadGlobalStats();
        }
        this.loading = false;
      },
      error: (err: any) => {
        console.error('Erreur mise à jour statut', err);
        this.showNotification('error', 'Erreur lors de la mise à jour du statut');
        this.loading = false;
      }
    });
  }
  
  ouvrirDetailsTicket(ticket: Ticket) {
    this.selectedTicket = ticket;
    this.loadGuideIA(ticket);
    this.loadHistorique(ticket.id);
    this.loadMetrics(ticket.id);
    this.showModal = true;
  }
  
  fermerModal() {
    this.showModal = false;
    this.selectedTicket = null;
    this.historiqueTicket = [];
    this.ticketMetrics = null;
  }
  
  loadHistorique(ticketId: number) {
    this.ticketService.getHistoriqueTicket(ticketId).subscribe({
      next: (data: TicketHistorique[]) => {
        this.historiqueTicket = data;
      },
      error: (err: any) => {
        console.error('Erreur chargement historique', err);
        this.historiqueTicket = [];
      }
    });
  }
  
  loadMetrics(ticketId: number) {
    this.ticketService.getTicketMetrics(ticketId).subscribe({
      next: (data: TicketMetrics) => {
        this.ticketMetrics = data;
      },
      error: (err: any) => {
        console.error('Erreur chargement métriques', err);
        this.ticketMetrics = null;
      }
    });
  }
  
  loadGlobalStats() {
    this.ticketService.getGlobalStats().subscribe({
      next: (data: any) => {
        this.globalStats = data;
      },
      error: (err: any) => {
        console.error('Erreur chargement stats globales', err);
      }
    });
  }
  
  ajouterIntervention() {
    if (this.interventionForm.invalid || !this.selectedTicket) return;
    
    this.loading = true;
    this.ticketService.ajouterIntervention(
      this.selectedTicket.id,
      this.interventionForm.get('intervention')?.value,
      this.currentUserId,
      this.currentUserName
    ).subscribe({
      next: (updated: Ticket) => {
        this.showNotification('success', 'Intervention ajoutée avec succès');
        this.interventionForm.reset();
        if (this.selectedTicket) {
          this.loadHistorique(this.selectedTicket.id);
          this.loadMetrics(this.selectedTicket.id);
        }
        this.loading = false;
      },
      error: (err: any) => {
        console.error('Erreur ajout intervention', err);
        this.showNotification('error', 'Erreur lors de l\'ajout de l\'intervention');
        this.loading = false;
      }
    });
  }
  
  openCommentaireModal(ticket: Ticket, nouveauStatut: TicketStatut) {
    const commentaire = prompt(`Veuillez saisir un commentaire pour justifier le passage en statut ${TicketStatutLabels[nouveauStatut]}:`);
    if (commentaire !== null && commentaire.trim() !== '') {
      this.executeChangementStatut(ticket, nouveauStatut, commentaire);
    } else if (commentaire !== null) {
      this.showNotification('warning', 'Un commentaire est requis pour cette action');
    }
  }
  
  formatDate(date: Date | string | undefined): string {
    if (!date) return '-';
    return moment(date).format('DD/MM/YYYY HH:mm');
  }
  
 formatDuree(minutes?: number): string {
  if (minutes === null || minutes === undefined) return '-';
  if (minutes === 0) return '0 min';  // ✅ affiche 0 min au lieu de -
  const heures = Math.floor(minutes / 60);
  const mins = minutes % 60;
  if (heures > 0) {
    return `${heures}h ${mins}min`;
  }
  return `${mins} min`;
}
  
  getStatutClass(statut: string | TicketStatut | undefined): string {
    if (!statut) return 'badge badge-secondary';
    const colorMap: { [key: string]: string } = {
      'OUVERT': 'danger',
      'EN_COURS': 'warning',
      'EN_ATTENTE': 'info',
      'RESOLU': 'primary',
      'CLOS': 'success',
      'ANNULE': 'secondary'
    };
    return `badge ${colorMap[statut] || 'secondary'}`;
  }
  
  getStatutLabel(statut: string | TicketStatut | undefined): string {
    if (!statut) return 'Inconnu';
    const labelMap: { [key: string]: string } = {
      'OUVERT': 'Ouvert',
      'EN_COURS': 'En cours',
      'EN_ATTENTE': 'En attente',
      'RESOLU': 'Résolu',
      'CLOS': 'Clos',
      'ANNULE': 'Annulé'
    };
    return labelMap[statut] || statut;
  }
  
  getStatutIcon(statut: TicketStatut): string {
    const iconMap: { [key: string]: string } = {
      'OUVERT': 'fa-exclamation-circle',
      'EN_COURS': 'fa-spinner',
      'EN_ATTENTE': 'fa-clock',
      'RESOLU': 'fa-check-circle',
      'CLOS': 'fa-check-double',
      'ANNULE': 'fa-ban'
    };
    return iconMap[statut] || 'fa-ticket';
  }
  
  
  
  showNotification(type: string, message: string) {
    // Utilisation d'une notification moderne (vous pouvez utiliser une librairie comme ngx-toastr)
    console.log(`${type}: ${message}`);
    // Pour l'instant, on utilise alert, mais vous pouvez remplacer par une meilleure solution
    if (type === 'error') {
      alert(`❌ ${message}`);
    } else if (type === 'success') {
      alert(`✅ ${message}`);
    } else if (type === 'warning') {
      alert(`⚠️ ${message}`);
    }
  }
  
  exportHistorique() {
    if (!this.selectedTicket) return;
    
    const data = this.historiqueTicket.map(h => ({
      Date: this.formatDate(h.dateAction),
      Action: h.action,
      'Ancien statut': h.ancienStatut ? this.getStatutLabel(h.ancienStatut) : '-',
      'Nouveau statut': this.getStatutLabel(h.nouveauStatut),
      Utilisateur: h.utilisateurNom,
      Description: h.description
    }));
    
    if (data.length === 0) {
      this.showNotification('warning', 'Aucun historique à exporter');
      return;
    }
    
    const csv = this.convertToCSV(data);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `historique_ticket_${this.selectedTicket.id}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
    
    this.showNotification('success', 'Export CSV effectué avec succès');
  }
  
  private convertToCSV(data: any[]): string {
    if (!data || data.length === 0) return '';
    const headers = Object.keys(data[0]);
    const rows = data.map(obj => headers.map(header => {
      const value = obj[header];
      if (typeof value === 'string' && (value.includes(',') || value.includes('"'))) {
        return `"${value.replace(/"/g, '""')}"`;
      }
      return value;
    }).join(','));
    return [headers.join(','), ...rows].join('\n');
  }
  
 genererTicketsAuto() {
  // ✅ Eviter la duplication dans la même session
  const dejaGenere = sessionStorage.getItem('ticketsGeneres');
  if (dejaGenere) {
    console.log('⏭️ Génération déjà effectuée dans cette session');
    this.loadTickets();
    return;
  }

  this.loading = true;
  this.ticketService.genererTickets().subscribe({
    next: (msg: string) => {
      this.messageGeneration = msg;
      sessionStorage.setItem('ticketsGeneres', 'true'); // ✅
      this.loadTickets();
      if (this.currentUserRole === 'ADMIN') {
        this.loadGlobalStats();
      }
      this.loading = false;
      setTimeout(() => { this.messageGeneration = ''; }, 5000);
    },
    error: (err: any) => {
      console.error('Erreur génération tickets', err);
      this.loading = false;
    }
  });
}
  
  // Méthodes pour la pagination
  goToPage(page: number) {
    this.page = page;
  }
  
  previousPage() {
    if (this.page > 1) {
      this.page--;
    }
  }
  
  nextPage() {
    if (this.page < this.totalPages) {
      this.page++;
    }
  }
  
  getPageNumbers(): number[] {
    const pages: number[] = [];
    const total = this.totalPages;
    const current = this.page;
    
    if (total <= 7) {
      for (let i = 1; i <= total; i++) {
        pages.push(i);
      }
    } else {
      if (current <= 4) {
        for (let i = 1; i <= 5; i++) pages.push(i);
        pages.push(-1);
        pages.push(total);
      } else if (current >= total - 3) {
        pages.push(1);
        pages.push(-1);
        for (let i = total - 4; i <= total; i++) pages.push(i);
      } else {
        pages.push(1);
        pages.push(-1);
        for (let i = current - 1; i <= current + 1; i++) pages.push(i);
        pages.push(-1);
        pages.push(total);
      }
    }
    
    return pages;
  }
  parseIntervention(detailsJson: string): string {
  try {
    const details = JSON.parse(detailsJson);
    return details.intervention || details.commentaire || '-';
  } catch {
    return detailsJson || '-';
  }
}
// ✅ Nouvelles propriétés
showAffectationModal: boolean = false;
ticketAAffecter: Ticket | null = null;
technicienSelectionne: any = null;

// ✅ Ouvrir le modal d'affectation
ouvrirModalAffectation(ticket: Ticket) {
  this.ticketAAffecter = ticket;
  this.technicienSelectionne = null;
  this.showAffectationModal = true;
}

// ✅ Fermer le modal d'affectation
fermerModalAffectation() {
  this.showAffectationModal = false;
  this.ticketAAffecter = null;
  this.technicienSelectionne = null;
}

// ✅ Sélectionner un technicien dans la liste
selectionnerTechnicien(tech: any) {
  this.technicienSelectionne = tech;
}

// ✅ Confirmer l'affectation
confirmerAffectation() {
  if (!this.ticketAAffecter || !this.technicienSelectionne) return;

  this.loading = true;
  this.ticketService.affecterTicket(
    this.ticketAAffecter.id,
    this.technicienSelectionne.id,
    this.technicienSelectionne.username
  ).subscribe({
    next: (updated: Ticket) => {
      const idx = this.tickets.findIndex(t => t.id === updated.id);
      if (idx !== -1) this.tickets[idx] = updated;
      this.showNotification('success',
        `Ticket #${updated.id} affecté à ${this.technicienSelectionne.username}`);
      this.fermerModalAffectation();
      this.loadTickets();
      this.loading = false;
    },
    error: (err: any) => {
      console.error('Erreur affectation', err);
      this.showNotification('error', "Erreur lors de l'affectation");
      this.loading = false;
    }
  });
}
predictPrioriteForTicket(ticket: Ticket): void {

  // Ne pas recalculer si priorité déjà sauvegardée en base
  if (ticket.priorite && ticket.priorite.trim() !== '') {
    return;
  }

  const request = {
    ticketId:           ticket.id,
    typePanne:          ticket.typePanne,
    region:             ticket.region,
    nombreReclamations: ticket.nombreReclamations,
    description:        ticket.description || '',
    heure:              new Date(ticket.dateCreation).getHours(),
    zonePopulation:     'HAUTE',
    zoneNom:            ticket.zoneNom
  };

  this.ticketPrioriteService.predictPriorite(request).subscribe({
    next: (res) => {
      if (res.prediction && !res.fallback) {
        ticket.priorite = res.prediction;
        console.log(`✅ Ticket ${ticket.id} → priorité ${res.prediction} sauvegardée`);
      } else if (res.fallback) {
        console.warn(`⚠️ Fallback IA pour ticket ${ticket.id}`);
      }
    },
    error: (err) => {
      console.error('Erreur IA priorité ticket', ticket.id, err);
    }
  });
}
getPrioriteClass(priorite?: string): string {
  if (!priorite) return '';
  const classMap: { [key: string]: string } = {
    'CRITIQUE': 'priority-critical',
    'HAUTE': 'priority-high',
    'MOYENNE': 'priority-medium',
    'BASSE': 'priority-low'
  };
  return classMap[priorite] || '';
}

// Dans ticket.component.ts — ajouter
loadGuideIA(ticket: Ticket) {
  this.loadingGuide = true;
  this.guideIA = null;
  this.errorGuide = '';

  const request = {
    ticketId: ticket.id,
    typePanne: ticket.typePanne,
    description: ticket.description || '',
    region: ticket.region
  };

  this.guideService.getGuide(request).subscribe({
    next: (res) => {
      this.guideIA = res;
      this.loadingGuide = false;
    },
    error: (err) => {
      console.error(err);
      this.errorGuide = "Erreur lors de l'analyse IA";
      this.loadingGuide = false;
    }
  });
}
showGuideModal: boolean = false;

ouvrirGuideIA(ticket: Ticket) {
  this.selectedTicket = ticket;
  this.showGuideModal = true;
  this.loadGuideIA(ticket); // appel IA automatique
}

fermerGuideModal() {
  this.showGuideModal = false;
  this.guideIA = null;
}

  logout(): void {
    this.utilisateurService.logout();
    this.router.navigate(['/login']);
  }
}