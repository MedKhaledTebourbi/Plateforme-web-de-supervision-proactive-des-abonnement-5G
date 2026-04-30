import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Pylone, ZoneReseau } from '../maps/zone.model';
import { PyloneService } from '../pylone.service';
import { ZoneService } from '../zone.service';
import { UtilisateurService } from '../utilisateur-service.service';

@Component({
  selector: 'app-pylone-list',
  templateUrl: './pylone-list.component.html',
  styleUrls: ['./pylone-list.component.css']
})
export class PyloneListComponent implements OnInit {

  /* ── Sidebar ── */
  sidebarCollapsed   = false;
  currentUserName    = '';
  currentUserInitials = '';
  currentUserRole    = '';
  isAdmin            = false;

  /* ── Data ── */
  pylones: Pylone[]     = [];
  zones:   ZoneReseau[] = [];
  showForm   = false;
  isEditing  = false;
  selectedZoneId: number | null = null;
  userRegion = '';
  zoneIdsByRegion: number[] = [];

  emptyPylone(): Pylone {
    return {
      nom: '', latitude: 0, longitude: 0,
      capaciteMax: 0, chargeActuelle: 0,
      rayonCouverture: 0, tauxUtilisation: 0, estBloque: false
    };
  }

  formPylone: Pylone = this.emptyPylone();

  constructor(
    private pyloneService: PyloneService,
    private zoneService: ZoneService,
    private userService: UtilisateurService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.userService.getCurrentUser().subscribe(user => {
     this.userRegion = (user.region ?? '').trim().split(' ')[0].toLowerCase();

      /* ── Sidebar user info ── */
      this.currentUserName  = user.username ?? user.username ?? 'Utilisateur';
      this.currentUserRole  = user.role ?? '';
      this.isAdmin          = this.currentUserRole === 'ADMIN';
      const parts = this.currentUserName.trim().split(' ');
      this.currentUserInitials = parts.length >= 2
        ? (parts[0][0] + parts[1][0]).toUpperCase()
        : this.currentUserName.slice(0, 2).toUpperCase();

      /* ── Zones filtrées ── */
      this.zoneService.getZones().subscribe(zones => {
        const filtered = zones.filter(z =>
          this.extractRegion(z.nom) === this.userRegion
        );
        this.zones           = filtered;
        this.zoneIdsByRegion = filtered.map(z => z.zone_id!);
        this.loadPylones();
      });
    });
  }

  logout(): void {
    localStorage.clear();
    sessionStorage.clear();
    this.router.navigate(['/login']);
  }

  loadPylones(): void {
    this.pyloneService.getPylones().subscribe(data => {
    // Filtrer uniquement les pylônes dont la zone appartient à la région
    this.pylones = data.filter(p =>
      p.zoneReseau?.zone_id != null &&
      this.zoneIdsByRegion.includes(p.zoneReseau.zone_id)
    );
  });
  }

  openCreate(): void {
    this.formPylone     = this.emptyPylone();
    this.selectedZoneId = null;
    this.isEditing      = false;
    this.showForm       = true;
    setTimeout(() => {
      document.querySelector('.py-form-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 50);
  }

  openEdit(pylone: Pylone): void {
    this.formPylone     = { ...pylone };
    this.selectedZoneId = pylone.zoneReseau?.zone_id ?? null;
    this.isEditing      = true;
    this.showForm       = true;
  }

  save(): void {
    if (this.selectedZoneId) {
      this.formPylone.zoneReseau = { zone_id: this.selectedZoneId };
    }
    if (this.isEditing && this.formPylone.id) {
      this.pyloneService.updatePylone(this.formPylone.id, this.formPylone)
        .subscribe(() => { this.loadPylones(); this.showForm = false; });
    } else {
      this.pyloneService.createPylone(this.formPylone)
        .subscribe(() => { this.loadPylones(); this.showForm = false; });
    }
  }

  delete(id: number): void {
    if (confirm('Supprimer ce pylône ?')) {
      this.pyloneService.deletePylone(id).subscribe(() => this.loadPylones());
    }
  }

  cancel(): void { this.showForm = false; }

  /* ── Taux : retourne un NUMBER (pour les comparaisons dans le template) ── */
  getTauxNum(p: Pylone): number {
    if (!p.capaciteMax) return 0;
    const capaciteMaxMb = p.capaciteMax * 1000;
    return Math.round((p.chargeActuelle / capaciteMaxMb) * 1000) / 10; // 1 décimale
  }

  /* ── Taux : retourne string formaté pour l'affichage ── */
  getTaux(p: Pylone): string {
    return this.getTauxNum(p).toFixed(1);
  }

  getTauxColor(p: Pylone): string {
    const t = this.getTauxNum(p);
    if (t > 80) return '#ef4444';
    if (t > 60) return '#f59e0b';
    return '#10b981';
  }

  /* ── Stats bar ── */
  getPylonesSains(): number {
    return this.pylones.filter(p => !p.estBloque && this.getTauxNum(p) <= 60).length;
  }

  getPylonesWarning(): number {
    return this.pylones.filter(p => !p.estBloque && this.getTauxNum(p) > 60 && this.getTauxNum(p) <= 80).length;
  }

  getPylonesCritiques(): number {
    return this.pylones.filter(p => !p.estBloque && this.getTauxNum(p) > 80).length;
  }

  getPylonesBloques(): number {
    return this.pylones.filter(p => p.estBloque).length;
  }

 extractRegion(nom: string): string {
  if (!nom) return '';
  return nom.trim().split(' ')[0].toLowerCase();
}
// ── Pagination ──
currentPage = 1;
pageSize = 10;

get pagedPylones(): Pylone[] {
  const start = (this.currentPage - 1) * this.pageSize;
  return this.pylones.slice(start, start + this.pageSize);
}

get totalPages(): number {
  return Math.ceil(this.pylones.length / this.pageSize);
}

get pages(): number[] {
  return Array.from({ length: this.totalPages }, (_, i) => i + 1);
}

goToPage(page: number): void {
  if (page >= 1 && page <= this.totalPages) {
    this.currentPage = page;
  }
}
}