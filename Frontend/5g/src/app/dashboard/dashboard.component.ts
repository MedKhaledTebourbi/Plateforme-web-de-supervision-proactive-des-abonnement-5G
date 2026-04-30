import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { GlobalStats, SaturationReport, SaturationStatus } from '../status-badge/saturation-report.model';
import { combineLatest, Subject, switchMap, takeUntil } from 'rxjs';
import { SaturationService } from '../saturation.service';

import { Router } from '@angular/router';
import { UtilisateurService } from '../utilisateur-service.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  reports: SaturationReport[] = [];
  filtered: SaturationReport[] = [];
  critiques: SaturationReport[] = [];
  stats: GlobalStats | null = null;
  loading = true;
  filter = '';
  sortKey: keyof SaturationReport = 'statut';
  sortAsc = false;

  userRegion: string | null = null;
  isAdmin = false;

  private destroy$ = new Subject<void>();

  constructor(
    private saturationService: SaturationService,
    private utilisateurService: UtilisateurService,
    private cd: ChangeDetectorRef,
    private router: Router
  ) {}

  ngOnInit(): void {
    const role   = localStorage.getItem('role');
    this.isAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN';

    // ✅ Récupérer l'utilisateur connecté puis charger les données
    this.utilisateurService.getCurrentUser().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (user) => {
        // ✅ Extraire le premier mot du champ region (ex: "Sfax Centre" → "Sfax")
        if (user?.region) {
          this.userRegion = user.region.trim().split(/\s+/)[0].toLowerCase();
        }
        this.loadData();
      },
      error: () => this.loadData()
    });
  }

  private loadData(): void {
    combineLatest([
      this.saturationService.autoRefreshReports(),
      this.saturationService.autoRefreshStats(),
    ]).pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ([reports, stats]) => {

          // ✅ Filtrer les zones dont le nom commence par la région de l'utilisateur
          if (!this.isAdmin && this.userRegion) {
            this.reports = reports.filter(r =>
              r.zoneNom.trim().toLowerCase().startsWith(this.userRegion!)
            );
          } else {
            this.reports = reports;
          }

          // ✅ Stats recalculées sur les zones visibles uniquement
          this.stats = this.isAdmin ? stats : this.computeLocalStats(this.reports);

          this.critiques = this.reports.filter(
            r => r.statut === 'CRITIQUE' || r.statut === 'SATURE'
          );

          this.applyFilter();
          this.loading = false;
          this.cd.markForCheck();
        },
        error: () => { this.loading = false; this.cd.markForCheck(); }
      });
  }

  private computeLocalStats(reports: SaturationReport[]): GlobalStats {
    return {
      totalZones:       reports.length,
      zonesNormales:    reports.filter(r => r.statut === 'NORMAL').length,
      zonesEnAttention: reports.filter(r => r.statut === 'ATTENTION').length,
      zonesSaturees:    reports.filter(r => r.statut === 'SATURE').length,
      zonesCritiques:   reports.filter(r => r.statut === 'CRITIQUE').length,
      timestamp:        new Date().toISOString()
    };
  }

  applyFilter(): void {
    const q = this.filter.toLowerCase();
    let result = q
      ? this.reports.filter(r => r.zoneNom.toLowerCase().includes(q))
      : [...this.reports];

    result.sort((a, b) => {
      const av = a[this.sortKey] as any;
      const bv = b[this.sortKey] as any;
      return this.sortAsc ? (av > bv ? 1 : -1) : (av < bv ? 1 : -1);
    });
    this.filtered = result;
  }

  sort(key: keyof SaturationReport): void {
    this.sortAsc = this.sortKey === key ? !this.sortAsc : false;
    this.sortKey = key;
    this.applyFilter();
  }

  statusColor(s: SaturationStatus): string {
    return { NORMAL: '#1D9E75', ATTENTION: '#BA7517',
             SATURE: '#E24B4A', CRITIQUE: '#A32D2D' }[s];
  }

  goToZone(id: number): void {
    this.router.navigate(['/zones', id]);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}