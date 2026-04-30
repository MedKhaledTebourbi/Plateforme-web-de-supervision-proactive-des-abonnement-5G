import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PyloneService } from '../pylone.service';
import { ChantierService } from '../chantier.service';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { UtilisateurService } from '../utilisateur-service.service';

@Component({
  selector: 'app-chantier-form',
  templateUrl: './chantier-form.component.html',
  styleUrls: ['./chantier-form.component.css']
})
export class ChantierFormComponent implements OnInit {

  chantierForm: FormGroup;
  pylones: any[] = [];
  pylonesFiltres: any[] = []; // ✅ pylônes filtrés par région
  typesChantier = [
    { value: 'MAINTENANCE', label: 'Maintenance' },
    { value: 'INSTALLATION', label: 'Installation' },
    { value: 'REPARATION', label: 'Réparation' },
    { value: 'EXTENSION', label: 'Extension' }
  ];
  loading = false;
  submitted = false;
  currentUser: any = null;
  currentUserRegion = '';
  currentUserId = 0;
  currentUserName = '';
  currentUserRole = '';
  currentUserInitials = '';
  sidebarCollapsed = false;

  constructor(
    private fb: FormBuilder,
    private chantierService: ChantierService,
    private pyloneService: PyloneService,
    private router: Router,
    @Inject(ToastrService) private toastr: ToastrService,
    private utilisateurService: UtilisateurService
  ) {
    this.chantierForm = this.fb.group({
      nom: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', [Validators.required, Validators.minLength(10)]],
      typeChantier: ['', Validators.required],
      pyloneId: ['', Validators.required],
      technicienId: ['', Validators.required],
      technicienNom: [''],
      dateDebut: ['', Validators.required],
      dateFin: ['']
    });
  }

  ngOnInit(): void {
    this.loadCurrentUserEtPylones();
  }
  logout(): void {
    localStorage.clear();
    sessionStorage.clear();
    this.router.navigate(['/login']);
  }

  // ✅ Charger user d'abord puis filtrer les pylônes
  loadCurrentUserEtPylones(): void {
    this.utilisateurService.getCurrentUser().subscribe({
      next: (user: any) => {
        this.currentUser = user;
        this.currentUserId = user.id || 0;
        this.currentUserName = user.username || '';
        this.currentUserRegion = user.region || '';
        this.currentUserRole = user.role || '';

        // ✅ Pré-remplir technicienId et technicienNom
        this.chantierForm.patchValue({
          technicienId: this.currentUserId,
          technicienNom: this.currentUserName
        });

        // ✅ Charger pylônes après avoir la région
        this.loadPylonesFiltres();
      },
      error: (err) => {
        console.error('Erreur utilisateur', err);
        this.loadPylonesSansFiltre();
      }
    });
  }

  // ✅ Pylônes filtrés par région du technicien
  loadPylonesFiltres(): void {
    this.loading = true;
    this.pyloneService.getPylones().subscribe({
      next: (data: any[]) => {
        this.pylones = data;

        if (this.currentUserRegion && this.currentUserRole !== 'ADMIN') {
          // ✅ Technicien → filtrer par sa région
          this.pylonesFiltres = data.filter(p => {
            const zoneNom = (p.zoneNom || p.zoneReseau?.nom || '').toLowerCase();
            return zoneNom.startsWith(this.currentUserRegion.toLowerCase());
          });

          if (this.pylonesFiltres.length === 0) {
            this.toastr.warning(
              `Aucun pylône trouvé dans la région ${this.currentUserRegion}`,
              'Information'
            );
          }
        } else {
          // ✅ Admin → tous les pylônes
          this.pylonesFiltres = data;
        }

        this.loading = false;
      },
      error: (error) => {
        this.toastr.error('Erreur lors du chargement des pylônes', 'Erreur');
        console.error(error);
        this.loading = false;
      }
    });
  }

  loadPylonesSansFiltre(): void {
    this.pyloneService.getPylones().subscribe({
      next: (data) => {
        this.pylones = data;
        this.pylonesFiltres = data;
      },
      error: (err) => console.error(err)
    });
  }

  onSubmit(): void {
    this.submitted = true;

    if (this.chantierForm.invalid) {
      this.toastr.warning(
        'Veuillez remplir tous les champs obligatoires',
        'Formulaire invalide'
      );
      return;
    }

    this.loading = true;
    const formValue = this.chantierForm.value;

    // ✅ Construire l'objet final avec infos technicien
    const chantierData = {
      ...formValue,
      pyloneId: Number(formValue.pyloneId),
      technicienId: this.currentUserId,
      technicienNom: this.currentUserName
    };

    this.chantierService.creerChantier(chantierData).subscribe({
      next: () => {
        this.toastr.success('Chantier créé avec succès', 'Succès');
        this.router.navigate(['/liste']);
      },
      error: (error) => {
        this.toastr.error(
          error.error || 'Erreur lors de la création du chantier',
          'Erreur'
        );
        this.loading = false;
      }
    });
  }

  get f() { return this.chantierForm.controls; }
}