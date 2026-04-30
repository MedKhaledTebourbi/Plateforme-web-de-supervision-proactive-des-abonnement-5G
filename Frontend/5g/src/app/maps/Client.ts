import { Pylone } from './zone.model';

export interface Client {
  id: number;
  adresse: string;
  typeAbonnement: number;
  latitude: number;
  longitude: number;
  pylone?: {
    id: number;
    nom: string;
  };
}