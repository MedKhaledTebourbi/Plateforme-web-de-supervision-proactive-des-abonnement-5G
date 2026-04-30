// ticket-guide.model.ts
export interface GuideResponse {
  ticketId: number;
  typePanne: string;
  urgence: string;
  action: string;
  solution: string;
  etapes: string[];
  prioriteAction: string;
  tempsEstime: string;
  automatisable: boolean;
  outils: string[];
  raison: string;
  confidenceMl: number;
  fallback: boolean;
}