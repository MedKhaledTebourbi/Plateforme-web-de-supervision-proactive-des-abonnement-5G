"""
====================================================================
  MICROSERVICE IA — predict_ticket.py

  Appelé par PythonBridgeService (Spring Boot via subprocess).
  Reçoit un JSON sur stdin, retourne une prédiction sur stdout.

  Input JSON :
  {
    "ticketId": 42,
    "typePanne": "RESEAU",
    "region": "TUNIS",
    "nombreReclamations": 85,
    "description": "panne majeure zone dense",
    "heure": 14,
    "zonePopulation": "HAUTE"   // optionnel
  }

  Output JSON :
  {
    "prediction": "HAUTE",
    "confidence": 0.87,
    "probabilities": {"BASSE":0.02,"MOYENNE":0.07,"HAUTE":0.87,"CRITIQUE":0.04},
    "features_used": {...},
    "model_version": "1.0"
  }
====================================================================
"""

import sys
import json
import pickle
import os
import numpy as np
import scipy.sparse as sp
import warnings
warnings.filterwarnings("ignore")

# ─────────────────────────────────────────────────────────────────
# CONSTANTES (identiques au script d'entraînement)
# ─────────────────────────────────────────────────────────────────

TYPES_PANNE_DEFAULT = [
    "RESEAU", "ELECTRIQUE", "MATERIEL", "LOGICIEL",
    "INTERFERENCE", "SURCHARGE", "MAINTENANCE", "SECURITE"
]

REGIONS_DEFAULT = [
    "NORD", "SUD", "EST", "OUEST", "CENTRE",
    "TUNIS", "SFAX", "SOUSSE", "BIZERTE", "GABES"
]

ZONE_POP_MAP = {"FAIBLE": 0, "MOYENNE": 1, "HAUTE": 2, "TRES_HAUTE": 3}

# ─────────────────────────────────────────────────────────────────
# CHARGEMENT DES MODÈLES
# ─────────────────────────────────────────────────────────────────

def load_models(models_dir: str) -> dict:
    required = ["model", "label_enc", "type_enc", "region_enc", "scaler", "tfidf"]
    artifacts = {}
    for name in required:
        path = os.path.join(models_dir, f"{name}.pkl")
        if not os.path.exists(path):
            raise FileNotFoundError(f"Modèle manquant : {path}")
        with open(path, "rb") as f:
            artifacts[name] = pickle.load(f)
    return artifacts


# ─────────────────────────────────────────────────────────────────
# PRÉPARATION DES FEATURES
# ─────────────────────────────────────────────────────────────────

def safe_encode(encoder, value: str, known_values: list) -> int:
    """Encode une valeur, retourne 0 si inconnue (robustesse)."""
    val = str(value).upper()
    if val not in known_values:
        val = known_values[0]
    return int(encoder.transform([val])[0])


def prepare_features(data: dict, artifacts: dict) -> sp.csr_matrix:
    type_enc   = artifacts["type_enc"]
    region_enc = artifacts["region_enc"]
    scaler     = artifacts["scaler"]
    tfidf      = artifacts["tfidf"]

    # ── Features structurelles ──
    type_panne  = data.get("typePanne", "RESEAU")
    region      = data.get("region", "CENTRE")
    nb_rec      = float(data.get("nombreReclamations", 1))
    heure       = float(data.get("heure", 12))
    zone_pop    = data.get("zonePopulation", "MOYENNE")

    type_encoded   = safe_encode(type_enc,   type_panne, TYPES_PANNE_DEFAULT)
    region_encoded = safe_encode(region_enc, region,     REGIONS_DEFAULT)
    zone_pop_num   = ZONE_POP_MAP.get(str(zone_pop).upper(), 1)

    X_struct = np.array([[
        type_encoded,
        region_encoded,
        nb_rec,
        heure,
        zone_pop_num,
    ]])
    X_struct_scaled = scaler.transform(X_struct)

    # ── Features textuelles ──
    description = str(data.get("description", ""))
    X_text = tfidf.transform([description])

    # ── Fusion ──
    X_combined = sp.hstack([
        sp.csr_matrix(X_struct_scaled),
        X_text
    ])
    return X_combined


# ─────────────────────────────────────────────────────────────────
# PRÉDICTION
# ─────────────────────────────────────────────────────────────────

def predict(data: dict, artifacts: dict) -> dict:
    model     = artifacts["model"]
    label_enc = artifacts["label_enc"]

    X = prepare_features(data, artifacts)

    # Prédiction + probabilités
    y_pred       = model.predict(X)[0]
    y_proba      = model.predict_proba(X)[0]
    classes      = list(label_enc.classes_)
    predicted_label = str(label_enc.inverse_transform([y_pred])[0])
    confidence   = float(y_proba[y_pred])

    probabilities = {
        cls: round(float(prob), 4)
        for cls, prob in zip(classes, y_proba)
    }

    result = {
        "prediction":   predicted_label,
        "confidence":   round(confidence, 4),
        "probabilities": probabilities,
        "features_used": {
            "typePanne":          data.get("typePanne"),
            "region":             data.get("region"),
            "nombreReclamations": data.get("nombreReclamations"),
            "description_len":    len(str(data.get("description", ""))),
            "zonePopulation":     data.get("zonePopulation"),
        },
        "model_version": "1.0",
        "ticketId":      data.get("ticketId"),
    }
    return result


# ─────────────────────────────────────────────────────────────────
# MAIN — lit stdin, écrit sur stdout
# ─────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--stdin",      action="store_true", help="Lire JSON depuis stdin")
    parser.add_argument("--models_dir", default="models/",   help="Dossier des modèles")
    args = parser.parse_args()

    try:
        # 1. Lecture input
        if args.stdin:
            raw = sys.stdin.read().strip()
        else:
            raw = sys.argv[-1]

        data = json.loads(raw)

        # 2. Chargement modèles
        artifacts = load_models(args.models_dir)

        # 3. Prédiction
        result = predict(data, artifacts)

        # 4. Sortie JSON propre (une seule ligne, pas de debug autour)
        print(json.dumps(result))
        sys.stdout.flush()

    except Exception as e:
        error_result = {
            "error":      str(e),
            "prediction": "MOYENNE",   # valeur de fallback sûre
            "confidence": 0.0,
            "probabilities": {},
            "model_version": "1.0",
        }
        print(json.dumps(error_result))
        sys.stdout.flush()
        sys.exit(1)