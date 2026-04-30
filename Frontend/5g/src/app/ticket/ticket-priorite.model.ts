export interface TicketPredictionRequest {
  ticketId: number;
  typePanne: string;
  region: string;
  nombreReclamations: number;
  description: string;
  heure: number;
  zonePopulation: string;
  zoneNom: string;
}
export interface TicketPredictionResponse {
  prediction: string;        // BASSE | MOYENNE | HAUTE | CRITIQUE
  confidence: number;
  probabilities: { [key: string]: number };
  featuresUsed: { [key: string]: any };
  ticketId: number;
  modelVersion: string;
  error?: string;
  fallback: boolean;
}