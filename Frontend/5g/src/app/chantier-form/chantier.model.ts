// models/chantier.model.ts
export interface Chantier {
  id?: number;
  nom: string;
  description: string;
  typeChantier: 'MAINTENANCE' | 'INSTALLATION' | 'REPARATION' | 'EXTENSION';
  pyloneId: number;
  pyloneNom?: string;
  region?: string;
  latitude?: number;
  longitude?: number;
  statut: 'PLANIFIE' | 'EN_COURS' | 'VALIDE' | 'TERMINE' | 'ANNULE';
  technicienId: number;
  technicienNom?: string;
  dateDebut: Date;
  dateFin?: Date;
  dateCreation?: Date;
  dateValidation?: Date;
  pyloneBloque?: boolean;
}

export interface ChantierVerification {
  hasChantierActif: boolean;
  chantier: Chantier | null;
}