import { Component, OnInit, HostListener, OnDestroy } from '@angular/core';
import { ZoneService } from '../zone.service';
import * as L from 'leaflet';
import { PyloneService } from '../pylone.service';
import { Router } from '@angular/router';
import { ZoneReseau } from './zone.model';
import { UtilisateurService } from '../utilisateur-service.service';
import { AffectationService, Client } from '../affectation.service';
import { ReclamationService } from '../reclamation.service';
import { Ticket, TicketService } from '../ticket.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TicketHistorique, TicketMetrics, TicketStatut } from '../ticket/ticket.model';
import { TicketCommunicationService } from '../ticket-communication.service';
import { GlobalStats, SaturationReport } from '../status-badge/saturation-report.model';
import { combineLatest, Subject, takeUntil } from 'rxjs';
import { SaturationService } from '../saturation.service';


@Component({
  selector: 'app-maps',
  templateUrl: './maps.component.html',
  styleUrls: ['./maps.component.css']
})
export class MapsComponent implements OnInit , OnDestroy {
  private map: any;
   clients: Client[] = [];
  clientsAffectes: Client[] = [];
  clientsEnAttente: Client[] = [];
  private tunisiaGeometry: any = null;
  private zones: ZoneReseau[] = [];
  private zonesLayer = L.layerGroup();
 private pylonesLayer = L.layerGroup();
 private reclamationsLayer = L.layerGroup();
 TicketStatut = TicketStatut;
 filterStatut: TicketStatut | 'TOUS' = 'TOUS';
  filterDateDebut: string = '';
  filterDateFin: string = '';
  filterRecherche: string = '';
  currentUserRegion = '';
 iaReports: SaturationReport[] = [];
  globalStats: GlobalStats | null = null;
  saturatedZones: SaturationReport[] = [];
  criticalZones: SaturationReport[] = [];
  prochaineSaturation: SaturationReport | null = null;
  iaLoading = false;
  private iaRefreshInterval: any;
  private destroy$ = new Subject<void>();
   messageGeneration: string = '';
  loading: boolean = false;
   historiqueTicket: TicketHistorique[] = [];
  ticketMetrics: TicketMetrics | null = null;
  showMessage(msg: string, type: 'success' | 'error'): void {
  this.message = msg;
  this.messageType = type;
  setTimeout(() => {
    this.message = '';
    this.messageType = '';
  }, 4000);
}

  // ── UI state ──────────────────────────────────────────────
  sidebarCollapsed = false;
  showFilters = false;
  userMenuOpen = false;
  showZones = true;
  showPylones = true;
  showConnections = true;

  // ── Current user ──────────────────────────────────────────
  currentUsername = '';
  currentUserRole = '';
  currentUserInitials = '';

  // ── Stats ─────────────────────────────────────────────────
  zonesActives = 0;
  totalPylones = 0;
  zonesNormales = 0;
  zonesMoyennes = 0;
  zonesCritiques = 0;
  alertCount = 0;
  showReclamations = true;
  //ticket
    tickets: Ticket[] = [];
  selectedTicket: Ticket | null = null;
  showTicketPanel = false;
  
  currentUserId = 0;
  currentUserName = '';
 
  interventionForm: FormGroup;
    message = '';
  messageType: 'success' | 'error' | '' = '';
  loadingAffectation = false;
  isAdmin: boolean = false;

  constructor(
    private affectationService : AffectationService,
    private zoneService: ZoneService,
    private pyloneService: PyloneService,
    private utilisateurService: UtilisateurService,
    private reclamationService: ReclamationService,
    private ticketService: TicketService,
    private ticketComm: TicketCommunicationService,
    private fb: FormBuilder,
    private saturationService: SaturationService,
   
    private router: Router
  ) {
  this.interventionForm = this.fb.group({
    intervention: ['', [Validators.required, Validators.minLength(10)]]
  });
}
 

  ngOnInit(): void {
    this.interventionForm = this.fb.group({
    intervention: ['', [Validators.required, Validators.minLength(10)]]
  });
    this.loadCurrentUser();
    this.initMap();
    this.loadZones();
    this.loadPylones();
    this.loadReclamations();
    this.genererTicketsAuDemarrage();
    this.loadCurrentUserAndTickets();
    
   
    this.lancerAffectationAuDemarrage();
    this.loadIaData();
    this.startIaAutoRefresh();
  
  }
  ngOnDestroy(): void {
    if (this.iaRefreshInterval) {
      clearInterval(this.iaRefreshInterval);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }
  lancerAffectationAuDemarrage(): void {
  // ✅ Vérifier si déjà lancé dans cette session
  const dejaLance = sessionStorage.getItem('affectationLancee');
  if (dejaLance) {
    console.log('⏭️ Affectation déjà lancée dans cette session');
    this.loadClients();
    return;
  }

  this.loadingAffectation = true;
  this.affectationService.lancerAffectation().subscribe({
    next: (msg) => {
      this.loadingAffectation = false;
      this.showMessage(msg, 'success');
      sessionStorage.setItem('affectationLancee', 'true'); // ✅ marquer comme lancée
      this.loadClients();
    },
    error: () => {
      this.loadingAffectation = false;
      this.showMessage('Erreur lors de l\'affectation automatique', 'error');
      this.loadClients(); // charger quand même les clients
    }
  });
}

  // ── Load connected user ───────────────────────────────────
  loadCurrentUser(): void {
    const username = localStorage.getItem('username') || 'Utilisateur';
    const role = this.utilisateurService.getUserRole() || 'USER';

    this.currentUsername = username;
    this.currentUserRole = role;
    
     this.isAdmin = role === 'ADMIN';
      this.utilisateurService.getCurrentUser().subscribe({
      next: (user: any) => {
        this.currentUserName = user.username || '';
        this.currentUserRole = user.role || '';
        this.currentUserRegion = user.region || '';
        this.currentUserId = user.id || 0;
        console.log("USER ID:", this.currentUserId);
console.log("REGION:", this.currentUserRegion);
console.log("ROLE:", this.currentUserRole);

        this.loadTickets();
        this.genererTicketsAuDemarrage();
        this. lancerAffectationAuDemarrage();

        
      },
      error: (err: any) => {
        this.currentUserName = '';
        this.currentUserRole = '';
        this.currentUserRegion = '';
        this.currentUserId = 0;
        this.loadTickets();
      }
    });

    // Generate initials (e.g. "Ahmed Ben Ali" → "AB")
    this.currentUserInitials = username
      .split(' ')
      .slice(0, 2)
      .map(w => w.charAt(0).toUpperCase())
      .join('');

    if (!this.currentUserInitials) {
      this.currentUserInitials = username.substring(0, 2).toUpperCase();
    }
  }

  // ── Topbar actions ────────────────────────────────────────
  toggleFilters(): void {
    this.showFilters = !this.showFilters;
  }

  toggleUserMenu(): void {
    this.userMenuOpen = !this.userMenuOpen;
  }

  // Close dropdown when clicking outside
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.user-menu-trigger')) {
      this.userMenuOpen = false;
    }
  }

  logout(): void {
    this.utilisateurService.logout();
    this.router.navigate(['/login']);
  }

  // ── Map controls ─────────────────────────────────────────
  zoomIn(): void  { this.map?.zoomIn(); }
  zoomOut(): void { this.map?.zoomOut(); }
  resetView(): void { this.map?.setView([33.8, 9.5], 6); }

  // ── Map init ──────────────────────────────────────────────
  initMap() {
    this.map = L.map('map', { zoomControl: false }).setView([33.8, 9.5], 6);
      this.zonesLayer.addTo(this.map);
      this.pylonesLayer.addTo(this.map);
      this.reclamationsLayer.addTo(this.map);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: 'CartoDB',
      subdomains: 'abcd'
    }).addTo(this.map);

    this.map.createPane('tunisiaPane');
    this.map.getPane('tunisiaPane').style.zIndex = '250';

    this.map.createPane('zonesPane');
    this.map.getPane('zonesPane').style.zIndex = '400';

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: 'OpenStreetMap',
      pane: 'tunisiaPane'
    }).addTo(this.map);

    fetch('assets/countries.geojson')
      .then(r => r.json())
      .then(data => {
        const tunisia = data.features.find((f: any) =>
          f.properties.ISO_A3 === 'TUN' || f.properties.name === 'Tunisia'
        );
        if (!tunisia) return;
        this.tunisiaGeometry = tunisia.geometry;
        this.applyTunisiaClip();

        this.map.on('move zoom viewreset zoomend moveend', () => {
          this.updateClipPath();
          this.applyClipToZonesPane();
        });

        this.applyClipToZonesPane();
      })
      .catch(err => console.error('Erreur chargement GeoJSON:', err));
  }

  private applyTunisiaClip() {
    const svgNS = 'http://www.w3.org/2000/svg';
    const old = document.getElementById('tunisia-svg-clip');
    if (old) old.remove();

    const svg = document.createElementNS(svgNS, 'svg');
    svg.id = 'tunisia-svg-clip';
    svg.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden';
    document.body.appendChild(svg);

    const defs = document.createElementNS(svgNS, 'defs');
    const clip = document.createElementNS(svgNS, 'clipPath');
    clip.id = 'tunisia-clip-path';
    clip.setAttribute('clipPathUnits', 'userSpaceOnUse');
    const path = document.createElementNS(svgNS, 'path');
    path.id = 'tunisia-path';
    clip.appendChild(path);
    defs.appendChild(clip);
    svg.appendChild(defs);

    this.updateClipPath();
  }

  private updateClipPath() {
    const pathEl = document.getElementById('tunisia-path');
    if (!pathEl || !this.tunisiaGeometry) return;

    let d = '';
    const toPath = (ring: number[][]) => {
      ring.forEach((pt, i) => {
        const p = this.map.latLngToLayerPoint(L.latLng(pt[1], pt[0]));
        d += (i === 0 ? `M${p.x},${p.y}` : `L${p.x},${p.y}`);
      });
      d += 'Z';
    };

    if (this.tunisiaGeometry.type === 'Polygon') {
      this.tunisiaGeometry.coordinates.forEach((ring: number[][]) => toPath(ring));
    } else if (this.tunisiaGeometry.type === 'MultiPolygon') {
      this.tunisiaGeometry.coordinates.forEach((poly: number[][][]) =>
        poly.forEach((ring: number[][]) => toPath(ring))
      );
    }

    pathEl.setAttribute('d', d);
    const pane = this.map.getPane('tunisiaPane');
    if (pane) {
      pane.style.clipPath = 'url(#tunisia-clip-path)';
      (pane.style as any).webkitClipPath = 'url(#tunisia-clip-path)';
    }
  }

  private applyClipToZonesPane() {
    const pane = this.map.getPane('zonesPane');
    if (pane) {
      pane.style.clipPath = 'url(#tunisia-clip-path)';
      (pane.style as any).webkitClipPath = 'url(#tunisia-clip-path)';
    }
  }

  // ── Load zones ────────────────────────────────────────────
  loadZones() {
    this.zoneService.getZones().subscribe(zones => {
      this.zones = zones;
      this.zonesActives = zones.length;

      zones.forEach(zone => {
        const taux = zone.bandePassanteMax > 0
          ? (zone.chargeActuelle / zone.bandePassanteMax) * 100
          : 0;

        let color = '#22c55e';
        if (taux > 80) { color = '#ef4444'; this.zonesCritiques++; this.alertCount++; }
        else if (taux > 60) { color = '#f97316'; this.zonesMoyennes++; }
        else { this.zonesNormales++; }

        const circle = L.circle(
          [Number(zone.latitudeCentre), Number(zone.longitudeCentre)],
          {
            radius: Number(zone.rayonCouverture) * 1000,
            color: color,
            fillColor: color,
            fillOpacity: 0.3,
            weight: 2,
            pane: 'zonesPane'
          }
        ).addTo(this.zonesLayer);

        circle.bindPopup(`
          <b>${zone.nom}</b>
          Charge: ${zone.chargeActuelle} Mbps<br>
          Utilisation: ${taux.toFixed(1)}%
        `);
      });

      this.applyClipToZonesPane();
      setTimeout(() => this.map.invalidateSize(), 200);
    });
  }

  // ── Load pylones ──────────────────────────────────────────
 loadPylones() {
  this.pyloneService.getPylones().subscribe(pylones => {
    this.totalPylones = pylones.length;

    const pyloneIcon = L.icon({
      iconUrl: 'https://cdn-icons-png.flaticon.com/512/684/684908.png',
      iconSize: [25, 25],
      iconAnchor: [12, 25]
    });

    pylones.forEach(pylone => {

      const marker = L.marker([pylone.latitude, pylone.longitude], { icon: pyloneIcon })
        .addTo(this.pylonesLayer);

      // ✅ CERCLE DE COUVERTURE DU PYLÔNE
     // ✅ CORRECTION — ajouter le pane et réduire l'opacité
L.circle([pylone.latitude, pylone.longitude], {
  radius: pylone.rayonCouverture,
  color: '#3b82f6',
  fillColor: '#3b82f6',
  fillOpacity: 0.1,   // réduire pour moins envahissant
  weight: 1,
  pane: 'zonesPane'   // 👈 ajouter ceci pour appliquer le clip Tunisia
}).addTo(this.pylonesLayer);

      let popupContent = `
        <b>Pylône: ${pylone.nom}</b><br>
        Capacité: ${pylone.capaciteMax}<br>
        Charge: ${pylone.chargeActuelle}<br>
        Rayon: ${pylone.rayonCouverture} m
      `;

      if (pylone.zoneNom) {
        popupContent += `<br><b>Zone: ${pylone.zoneNom}</b>`;
      } else if (pylone.zoneReseau) {
        popupContent += `<br><b>Zone: ${pylone.zoneReseau.nom ?? 'Non défini'}</b>`;
      }

      marker.bindPopup(popupContent);

      if (pylone.zoneReseau) {
        const zone = this.zones.find(z => z.zone_id === pylone.zoneReseau!.zone_id);
        if (zone) {
          L.polyline([
            [pylone.latitude, pylone.longitude],
            [zone.latitudeCentre, zone.longitudeCentre]
          ], { color: '#3b82f6', dashArray: '4,4', opacity: 0.6 }).addTo(this.pylonesLayer);
        }
      }

    });
  });
}
// Afficher seulement les zones
showOnlyZones() {
  this.map.removeLayer(this.pylonesLayer);
  this.map.addLayer(this.zonesLayer);
}

// Afficher seulement les pylônes
showOnlyPylones() {
  this.map.removeLayer(this.zonesLayer);
  this.map.addLayer(this.pylonesLayer);
}

// Afficher tout
showAll() {
  this.map.addLayer(this.zonesLayer);
  this.map.addLayer(this.pylonesLayer);
}
loadClients() {
  this.affectationService.getClients().subscribe(clients => {

    console.log("===== LISTE DES CLIENTS =====");

    clients.forEach(client => {

      console.log("Client ID:", client.id);
      console.log("Adresse:", client.adresse);
      console.log("Abonnement:", client.typeAbonnement);
      console.log("Latitude:", client.latitude);
      console.log("Longitude:", client.longitude);

      // 🔥 Pylône associé
      if (client.pylone) {
        console.log("Pylône ID:", client.pylone.id);
        console.log("Pylône Nom:", client.pylone.nom);
      } else {
        console.log("❌ Aucun pylône affecté");
      }

      console.log("---------------------------");
    });

  });
}
loadReclamations() {
  this.reclamationService.geocodeEtSave().subscribe({
    next: (reclamations) => {

      const reclamationIcon = L.icon({
        iconUrl: 'https://cdn-icons-png.flaticon.com/512/1828/1828843.png',
        iconSize: [25, 25],
        iconAnchor: [12, 25]
      });

      reclamations.forEach(rec => {

        // ✅ Ne pas afficher si pylône bloqué (pyloneId === null)
        if (rec.pyloneId === null || rec.pyloneId === undefined) {
          console.warn("⚠️ Réclamation non affichée — pylône bloqué:", rec.adresse);
          return;
        }

        if (rec.latitude && rec.longitude) {
          const marker = L.marker(
            [rec.latitude, rec.longitude],
            { icon: reclamationIcon }
          ).addTo(this.reclamationsLayer);

          marker.bindPopup(`
            <b>Réclamation</b><br>
            ${rec.typeReclamation || ''}<br>
            📍 ${rec.adresse}<br>
            📡 Pylône: ${rec.pyloneNom || ''}<br>
            Lat: ${rec.latitude}<br>
            Lng: ${rec.longitude}
          `);
        }

      });
    },
    error: (err) => console.error("Erreur géocodage:", err)
  });
}
showOnlyReclamations() {
  this.map.removeLayer(this.zonesLayer);
  this.map.removeLayer(this.pylonesLayer);
  this.map.addLayer(this.reclamationsLayer);
}

toggleReclamations() {
  if (this.map.hasLayer(this.reclamationsLayer)) {
    this.map.removeLayer(this.reclamationsLayer);
  } else {
    this.map.addLayer(this.reclamationsLayer);
  }
}
loadCurrentUserAndTickets() {
    this.utilisateurService.getCurrentUser().subscribe({
      next: (user) => {
        this.currentUserName = user.username || '';
        this.currentUserId = user.id || 0;
        this.currentUserRegion = user.role === 'TECHNICIEN' ? user.region || '' : '';
        this.loadTickets();
      },
      error: () => this.loadTickets()
    });
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
  

loadTickets() {
  this.loading = true;

  console.log("========== LOAD TICKETS ==========");
  console.log("ROLE:", this.currentUserRole);
  console.log("USER ID:", this.currentUserId);
  console.log("REGION:", this.currentUserRegion);

  let obs;

  if (this.currentUserRole === 'ADMIN') {
    console.log("👉 ADMIN → getAllTickets()");
    obs = this.ticketService.getAllTickets();

  } else if (this.currentUserRole === 'CHEF') {
    console.log("👉 CHEF → getTicketsByRegion:", this.currentUserRegion);
    obs = this.ticketService.getTicketsByRegion(this.currentUserRegion);

  } else if (this.currentUserRole === 'TECHNICIEN') {
    console.log("👉 TECHNICIEN → getTicketsByRegion:", this.currentUserRegion);
    obs = this.ticketService.getTicketsByRegion(this.currentUserRegion);

  } else {
    console.log("👉 FALLBACK → getAllTickets()");
    obs = this.ticketService.getAllTickets();
  }

  obs.subscribe({
    next: (data: Ticket[]) => {

      console.log("📦 DATA FROM BACKEND:", data);

      data.forEach(t => {
        console.log(`🎯 Ticket #${t.id}`);
        console.log("   assignedTo:", t.assignedTo);
        console.log("   region:", t.region);
        console.log("   statut:", t.statut);
      });

      if (this.currentUserRole === 'TECHNICIEN') {

        console.log("🔍 FILTRAGE TECHNICIEN...");

        this.tickets = data.filter(t => {
          const match = Number(t.assignedTo) === Number(this.currentUserId);

          console.log(
            `➡️ Ticket ${t.id} | assignedTo=${t.assignedTo} | userId=${this.currentUserId} | MATCH=${match}`
          );

          return match;
        });

      } else {
        this.tickets = data;
      }

      console.log("✅ FINAL TICKETS:", this.tickets);

      this.loading = false;
    },

    error: (err) => {
      console.error("❌ ERREUR LOAD TICKETS:", err);
      this.loading = false;
    }
  });
}

  // ✅ Ouvrir/fermer le panel
  toggleTicketPanel() {
    this.showTicketPanel = !this.showTicketPanel;
    if (!this.showTicketPanel) {
      this.selectedTicket = null;
    }
  }

  // ✅ Voir détail d'un ticket
  voirDetail(ticket: Ticket) {
    this.selectedTicket = ticket;
  }

  // ✅ Retour à la liste
  retourListe() {
    this.selectedTicket = null;
  }

  // ✅ Changer statut depuis le panel
  changerStatut(ticket: Ticket, statut: string) {
    if (statut === 'CLOS') {
      const ok = confirm(`Clôturer le ticket #${ticket.id} ?\n⚠️ Les réclamations seront supprimées.`);
      if (!ok) return;
    }
    this.ticketService.updateStatut(ticket.id, statut, this.currentUserId, this.currentUserName)
      .subscribe({
        next: (updated) => {
          Object.assign(ticket, updated);
          this.loadTickets(); // recharger le badge
          if (statut === 'CLOS') this.selectedTicket = null;
        },
        error: (err) => console.error(err)
      });
  }
genererTicketsAuDemarrage() {
  console.log("🚀 APPEL GENERATION TICKETS");

  const dejaGenere = sessionStorage.getItem('ticketsGeneres');

  if (dejaGenere) {
    console.log('⏭️ Tickets déjà générés');
    return;
  }

  this.loading = true;

  this.ticketService.genererTickets().subscribe({
    next: (msg) => {
      console.log("✅ REPONSE BACKEND:", msg);
      this.showMessage(msg, 'success');
      sessionStorage.setItem('ticketsGeneres', 'true');
      this.loadTickets();
      this.loading = false;
    },
    error: (err) => {
      console.error("❌ ERREUR API:", err);
      this.loading = false;
    }
  });
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
  ajouterIntervention() {
  if (this.interventionForm.invalid || !this.selectedTicket) return;

  const interventionText = this.interventionForm.get('intervention')?.value;
  this.loading = true;

  this.ticketService.ajouterIntervention(
    this.selectedTicket.id,
    interventionText,
    this.currentUserId,
    this.currentUserName
  ).subscribe({
    next: (updated: Ticket) => {
      // ✅ Partager l'intervention avec ticket.component
      this.ticketComm.envoyerIntervention(this.selectedTicket!.id, interventionText);
      
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
loadIaData(): void {
    this.iaLoading = true;

    // Charger stats globales + tous les rapports en parallèle
    combineLatest([
      this.saturationService.getGlobalStats(),
      this.saturationService.getAllZonesAnalysis()
    ]).pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ([stats, reports]) => {
          this.globalStats = stats;
          this.iaReports = reports;

          // Zones saturées ou critiques
          this.saturatedZones = reports.filter(r =>
            r.statut === 'SATURE' || r.statut === 'CRITIQUE'
          );

          // Zones critiques seulement
          this.criticalZones = reports.filter(r => r.statut === 'CRITIQUE');

          // Zone qui sera saturée le plus tôt
          this.prochaineSaturation = reports
            .filter(r => r.saturationPredite && r.heuresAvantSaturation != null)
            .sort((a, b) =>
              (a.heuresAvantSaturation ?? 999) - (b.heuresAvantSaturation ?? 999)
            )[0] ?? null;

          // Mettre à jour les cercles de la carte avec les couleurs IA
          this.updateMapWithIaData(reports);

          this.iaLoading = false;
        },
        error: (err) => {
          console.warn('[IA] Service indisponible:', err.message);
          this.iaLoading = false;
        }
      });
  }

  // Auto-refresh toutes les 60 secondes
  startIaAutoRefresh(): void {
    this.iaRefreshInterval = setInterval(() => {
      this.loadIaData();
    }, 60000);
  }
  updateMapWithIaData(reports: SaturationReport[]): void {
    // Réinitialiser la couche des zones
    this.zonesLayer.clearLayers();
    this.zonesActives = 0;
    this.zonesNormales = 0;
    this.zonesMoyennes = 0;
    this.zonesCritiques = 0;
    this.alertCount = 0;

    this.zones.forEach(zone => {
      const report = reports.find(r => r.zoneId === zone.zone_id);
      const taux = report?.tauxUtilisation ??
        (zone.bandePassanteMax > 0 ? (zone.chargeActuelle / zone.bandePassanteMax) * 100 : 0);

      let color = '#22c55e';
      if (report?.statut === 'CRITIQUE') {
        color = '#dc2626'; this.zonesCritiques++; this.alertCount++;
      } else if (report?.statut === 'SATURE') {
        color = '#ef4444'; this.alertCount++;
      } else if (report?.statut === 'ATTENTION') {
        color = '#f97316'; this.zonesMoyennes++;
      } else {
        this.zonesNormales++;
      }
      this.zonesActives++;

      // Cercle clignotant pour zones critiques
      const options: any = {
        radius: Number(zone.rayonCouverture) * 1000,
        color: color,
        fillColor: color,
        fillOpacity: report?.statut === 'CRITIQUE' ? 0.5 : 0.3,
        weight: report?.statut === 'CRITIQUE' ? 3 : 2,
        pane: 'zonesPane'
      };

      const circle = L.circle(
        [Number(zone.latitudeCentre), Number(zone.longitudeCentre)],
        options
      ).addTo(this.zonesLayer);

      // Popup enrichi avec données IA
      let popupHtml = `
        <div style="min-width:200px;font-family:sans-serif">
          <b style="font-size:14px">${zone.nom}</b>
          <hr style="margin:6px 0">
          <div>Charge: <b>${zone.chargeActuelle} Mbps</b></div>
          <div>Utilisation: <b>${taux.toFixed(1)}%</b></div>`;

      if (report) {
        const statutColor = {
          NORMAL: '#22c55e', ATTENTION: '#f97316',
          SATURE: '#ef4444', CRITIQUE: '#dc2626'
        }[report.statut] ?? '#888';

        popupHtml += `
          <hr style="margin:6px 0">
          <div>Statut IA: <b style="color:${statutColor}">${report.statut}</b></div>
          <div>Pylônes saturés: <b>${report.nbPylonesSatures}/${report.nbPylonesTotal}</b></div>
          <div>Score anomalie: <b>${report.anomalyScore?.toFixed(3)}</b></div>`;

        if (report.saturationPredite && report.heuresAvantSaturation != null) {
          popupHtml += `
          <hr style="margin:6px 0">
          <div style="color:#ef4444">
            ⚠ Saturation dans <b>${report.heuresAvantSaturation.toFixed(1)}h</b>
          </div>
          <div style="color:#888;font-size:11px">${report.messagePrediction}</div>`;
        }
      }

      popupHtml += `</div>`;
      circle.bindPopup(popupHtml);
    });

    this.applyClipToZonesPane();
  }

  // Helper pour couleur statut dans template
  getStatutColor(statut: string): string {
    const colors: Record<string, string> = {
      NORMAL:    '#22c55e',
      ATTENTION: '#f97316',
      SATURE:    '#ef4444',
      CRITIQUE:  '#dc2626'
    };
    return colors[statut] ?? '#888';
  }

  getUrgenceLabel(heures: number | null): string {
    if (heures === null || heures === undefined) return '';
    if (heures === 0) return 'Maintenant';
    if (heures < 6)  return `dans ${heures.toFixed(1)}h ⚠`;
    if (heures < 24) return `dans ${heures.toFixed(0)}h`;
    return `dans ${(heures / 24).toFixed(1)}j`;
  }
 /*  genererTicketsAuto() {
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
}*/
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
  
}

