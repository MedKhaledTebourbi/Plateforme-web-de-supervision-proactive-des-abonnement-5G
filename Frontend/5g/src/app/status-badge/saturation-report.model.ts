// core/models/saturation-report.model.ts
export type SaturationStatus = 'NORMAL' | 'ATTENTION' | 'SATURE' | 'CRITIQUE';

export interface SaturationReport {
  zoneId: number;
  zoneNom: string;
  tauxUtilisation: number;
  statut: SaturationStatus;
  anomalyScore: number;
  nbPylonesSatures: number;
  nbPylonesTotal: number;
  ratioSatures: number;
  tendance6h: number;
  saturationPredite: boolean;
  heuresAvantSaturation: number | null;
  datePredicteSaturation: string | null;
  confidencePrediction: number;
  messagePrediction: string;
  details: string;
  timestamp: string;
}

export interface SaturationRecord {
  id: number;
  zoneId: number;
  zoneNom: string;
  tauxUtilisation: number;
  nbPylonesSatures: number;
  nbPylonesTotal: number;
  statut: SaturationStatus;
  anomalyScore: number;
  heuresAvantSaturation: number | null;
  datePredicteSaturation: string | null;
  timestamp: string;
}

export interface GlobalStats {
  totalZones: number;
  zonesNormales: number;
  zonesEnAttention: number;
  zonesSaturees: number;
  zonesCritiques: number;
  timestamp: string;
}

