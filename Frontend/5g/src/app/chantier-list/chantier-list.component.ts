import { Component, Inject, OnInit } from '@angular/core';
import { ChantierService } from '../chantier.service';
import { ToastrService } from 'ngx-toastr';
import { UtilisateurService } from '../utilisateur-service.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-chantier-list',
  templateUrl: './chantier-list.component.html',
  styleUrls: ['./chantier-list.component.css']
})
export class ChantierListComponent implements OnInit {
  chantiers: any[] = [];
  loading = false;
  currentUsername = '';
  sidebarCollapsed = false;
  currentUserRole = '';
  currentUserInitials = '';
  isAdmin: boolean = false;
  role: string = '';
  selectedStatut: string = '';
  statuts = [
    { value: '', label: 'Tous' },
    { value: 'PLANIFIE', label: 'Planifié' },
    { value: 'EN_COURS', label: 'En cours' },
    { value: 'VALIDE', label: 'Validé' },
    { value: 'TERMINE', label: 'Terminé' },
    { value: 'ANNULE', label: 'Annulé' }
  ];

  constructor(
    private chantierService: ChantierService,
    private utilisateurService: UtilisateurService,
    @Inject(ToastrService) private toastr: ToastrService,
     private router: Router
    // ✅ ConfirmationService supprimé
  ) {}

  ngOnInit(): void {
    this.loadChantiers();
    this.role = localStorage.getItem('role') || '';
    this.isAdmin = this.role === 'ADMIN';
    this.currentUsername = localStorage.getItem('username') || '';
    this.currentUserInitials = this.currentUsername
      .split(' ')
      .map(name => name.charAt(0).toUpperCase())
      .join('');
  }
getStatutLabel(statut: string): string {
  const map: { [key: string]: string } = {
    'PLANIFIE': 'Planifié',
    'EN_COURS': 'En cours',
    'VALIDE':   'Validé',
    'TERMINE':  'Terminé',
    'ANNULE':   'Annulé'
  };
  return map[statut] || statut;
}

  logout(): void {
    this.utilisateurService.logout();
    this.router.navigate(['/login']);
  }
  loadChantiers(): void {
    this.loading = true;
    this.chantierService.getAllChantiers().subscribe({
      next: (data) => {
        this.chantiers = data;
        this.loading = false;
      },
      error: () => {
        this.toastr.error('Erreur lors du chargement des chantiers', 'Erreur');
        this.loading = false;
      }
    });
  }

  validerChantier(id: number): void {
    // ✅ confirm() natif au lieu de ConfirmationService
    if (!confirm('Êtes-vous sûr de vouloir valider ce chantier ? Le pylône sera bloqué.')) return;

    this.chantierService.validerChantier(id).subscribe({
      next: () => {
        this.toastr.success('Chantier validé avec succès', 'Succès');
        this.loadChantiers();
      },
      error: (error) => {
        this.toastr.error(error.error || 'Erreur lors de la validation', 'Erreur');
      }
    });
  }

  terminerChantier(id: number): void {
    if (!confirm('Êtes-vous sûr de vouloir terminer ce chantier ? Le pylône sera débloqué.')) return;

    this.chantierService.terminerChantier(id).subscribe({
      next: () => {
        this.toastr.success('Chantier terminé avec succès', 'Succès');
        this.loadChantiers();
      },
      error: (error) => {
        this.toastr.error(error.error || 'Erreur lors de la terminaison', 'Erreur');
      }
    });
  }

  annulerChantier(id: number): void {
    if (!confirm('Êtes-vous sûr de vouloir annuler ce chantier ?')) return;

    this.chantierService.annulerChantier(id).subscribe({
      next: () => {
        this.toastr.success('Chantier annulé avec succès', 'Succès');
        this.loadChantiers();
      },
      error: (error) => {
        this.toastr.error(error.error || 'Erreur lors de l\'annulation', 'Erreur');
      }
    });
  }

  get chantiersFiltres(): any[] {
    if (!this.selectedStatut) return this.chantiers;
    return this.chantiers.filter(c => c.statut === this.selectedStatut);
  }

  getBadgeClass(statut: string): string {
    const map: { [key: string]: string } = {
      'PLANIFIE': 'badge bg-secondary',
      'EN_COURS':  'badge bg-info',
      'VALIDE':    'badge bg-success',
      'TERMINE':   'badge bg-primary',
      'ANNULE':    'badge bg-danger'
    };
    return map[statut] || 'badge bg-secondary';
  }
  getCountByStatut(value: string): number {
  if (!this.chantiers) return 0;

  if (value === '') {
    return this.chantiers.length;
  }

  return this.chantiers.filter(c => c.statut === value).length;
}
}