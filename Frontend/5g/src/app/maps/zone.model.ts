export interface ZoneReseau {
  zone_id?: number;
  nom: string;
  description: string;
  bandePassanteMax: number;
  chargeActuelle: number;
  latitudeCentre: number;
  longitudeCentre: number;
  rayonCouverture: number;
  pylones?: Pylone[];
  tauxUtilisation: number;
}

export interface Pylone {
  id?: number;
  nom: string;
  latitude: number;
  longitude: number;
  capaciteMax: number;
  chargeActuelle: number;
  rayonCouverture: number;
 // 👇 AJOUTE ÇA
  zoneNom?: string;
  tauxUtilisation: number;
  estBloque: boolean;

  // optionnel si tu gardes aussi zoneReseau
  zoneReseau?: {
    zone_id: number;
    nom?: string;
    
  };
}