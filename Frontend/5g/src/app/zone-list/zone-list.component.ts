import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ZoneReseau } from '../maps/zone.model';
import { ZoneService } from '../zone.service';
import { interval, Subscription } from 'rxjs';
import { UtilisateurService } from '../utilisateur-service.service';


@Component({
  selector: 'app-zone-list',
  templateUrl: './zone-list.component.html',
  styleUrls: ['./zone-list.component.css']
})
export class ZoneListComponent implements OnInit, OnDestroy {

  /* ── Sidebar ── */
  sidebarCollapsed = false;

  /* ── User info ── */
  currentUserName    = '';
  currentUserInitials = '';
  currentUserRole    = '';
  isAdmin            = false;

  /* ── Zones ── */
  zones: ZoneReseau[] = [];
  selectedZone: ZoneReseau | null = null;
  showForm  = false;
  isEditing = false;
  userRegion = '';

  private simulationSub!: Subscription;

  emptyZone(): ZoneReseau {
    return {
      nom: '', description: '',
      bandePassanteMax: 0, chargeActuelle: 0,
      latitudeCentre: 0, longitudeCentre: 0,
      rayonCouverture: 0, tauxUtilisation: 0
    };
  }

  formZone: ZoneReseau = this.emptyZone();

  constructor(
    private zoneService: ZoneService,
    private userService: UtilisateurService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.userService.getCurrentUser().subscribe(user => {
      this.userRegion = user.region ?? '';

      /* ── Remplir les infos sidebar ── */
      this.currentUserName  = user.username ?? user.username ?? 'Utilisateur';
      this.currentUserRole  = user.role ?? '';
      this.isAdmin          = this.currentUserRole === 'ADMIN';

      const parts = this.currentUserName.trim().split(' ');
      this.currentUserInitials = parts.length >= 2
        ? (parts[0][0] + parts[1][0]).toUpperCase()
        : this.currentUserName.slice(0, 2).toUpperCase();

      this.loadZones();
    });

    /* Simulation automatique toutes les 5 s */
    this.simulationSub = interval(5000).subscribe(() => {
      this.zoneService.simulateLoad().subscribe(() => this.loadZones());
    });
  }

  ngOnDestroy(): void {
    this.simulationSub?.unsubscribe();
  }

  logout(): void {
    localStorage.clear();
    sessionStorage.clear();
    this.router.navigate(['/login']);
  }

  /* ── Zones ── */
  loadZones() {
    this.zoneService.getZones().subscribe(data => {
      this.zones = data.filter(zone =>
        this.extractRegion(zone.nom) === this.userRegion.toLowerCase()
      );
    });
  }

  extractRegion(nom: string): string {
    if (!nom) return '';
    const parts = nom.trim().split(' ');
    if (parts.length === 1) return parts[0].toLowerCase();
    parts.pop();
    return parts.join(' ').toLowerCase();
  }

  openCreate() {
    this.formZone = this.emptyZone();
    this.isEditing = false;
    this.showForm  = true;
  }

  openEdit(zone: ZoneReseau) {
    this.formZone  = { ...zone };
    this.isEditing = true;
    this.showForm  = true;
  }

  save() {
    if (this.isEditing && this.formZone.zone_id) {
      this.zoneService.updateZone(this.formZone.zone_id, this.formZone)
        .subscribe(() => { this.loadZones(); this.showForm = false; });
    } else {
      this.zoneService.createZone(this.formZone)
        .subscribe(() => { this.loadZones(); this.showForm = false; });
    }
  }

  delete(id: number) {
    if (confirm('Supprimer cette zone ?')) {
      this.zoneService.deleteZone(id).subscribe(() => this.loadZones());
    }
  }

  cancel() { this.showForm = false; }

  /* ── Taux : retourne un NUMBER pour les comparaisons dans le template ── */
  getTaux(zone: ZoneReseau): number {
    if (!zone.bandePassanteMax) return 0;
    return Math.round((zone.chargeActuelle / zone.bandePassanteMax) * 100 * 10) / 10;
  }

  getTauxColor(zone: ZoneReseau): string {
    const t = this.getTaux(zone);
    if (t > 80) return '#ef4444';
    if (t > 60) return '#f59e0b';
    return '#10b981';
  }

  /* ── Stats bar ── */
  getZonesSaines(): number {
    return this.zones.filter(z => this.getTaux(z) <= 70).length;
  }

  getZonesWarning(): number {
    return this.zones.filter(z => this.getTaux(z) > 70 && this.getTaux(z) <= 90).length;
  }

  getZonesCritiques(): number {
    return this.zones.filter(z => this.getTaux(z) > 90).length;
  }

  /* ── Simulation ── */
  simulateLoad() {
    this.zoneService.simulateLoad().subscribe(() => this.loadZones());
  }

  resetLoad() {
    if (confirm('Réinitialiser toutes les charges ?')) {
      this.zoneService.resetLoad().subscribe(() => this.loadZones());
    }
  }

  forceLoad(zone: ZoneReseau) {
    const taux = prompt('Entrer le taux (%) à appliquer', '90');
    if (taux) {
      this.zoneService.forceLoad(zone.zone_id!, +taux)
        .subscribe(() => this.loadZones());
    }
  }
}