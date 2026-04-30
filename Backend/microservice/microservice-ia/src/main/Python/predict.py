"""
predict.py
==========
Chargé les modèles pré-entraînés et retourne une prédiction JSON
pour une zone donnée.

Appelé par Java via ProcessBuilder :
    python predict.py --zone_id 3 --features '{"taux_utilisation":82.5,...}'

Ou en mode batch :
    python predict.py --batch --features '[{...}, {...}]'

Retourne sur stdout un JSON exploitable directement par Java.
"""

import argparse
import json
import sys
import os
import warnings
warnings.filterwarnings("ignore")

import numpy as np
import joblib
from datetime import datetime, timedelta

try:
    from prophet import Prophet
    PROPHET_AVAILABLE = True
except ImportError:
    PROPHET_AVAILABLE = False


# ════════════════════════════════════════════════════════════════════
# CONSTANTES
# ════════════════════════════════════════════════════════════════════

MODELS_DIR          = os.environ.get("IA_MODELS_DIR", "models/")
SEUIL_SATURATION    = 80.0    # % taux au-delà duquel la zone est saturée
FEATURES_ISOLATION  = [
    "taux_utilisation", "ratio_satures", "tendance_6pts",
    "taux_lag1", "taux_lag3", "hour_of_day", "day_of_week",
]
FEATURES_LINEAR     = [
    "taux_lag1", "taux_lag3", "tendance_6pts",
    "ratio_satures", "hour_of_day", "day_of_week",
]


# ════════════════════════════════════════════════════════════════════
# CHARGEMENT DES MODÈLES (mis en cache en mémoire)
# ════════════════════════════════════════════════════════════════════

_cache = {}

def _load(path: str):
    """Charge et met en cache un modèle joblib."""
    if path not in _cache:
        if not os.path.exists(path):
            return None
        _cache[path] = joblib.load(path)
    return _cache[path]

def load_isolation_forest():
    model  = _load(os.path.join(MODELS_DIR, "isolation_forest.pkl"))
    scaler = _load(os.path.join(MODELS_DIR, "scaler_if.pkl"))
    meta_path = os.path.join(MODELS_DIR, "if_metadata.json")

    meta = {}
    if os.path.exists(meta_path):
        with open(meta_path) as f:
            meta = json.load(f)
    return model, scaler, meta

def load_prediction_model(zone_id: int):
    """
    Charge le modèle de prédiction pour une zone.
    Retourne (model, scaler_or_None, model_type).
    """
    # Prophet en priorité
    prophet_path = os.path.join(MODELS_DIR, "prediction_models",
                                f"prophet_zone_{zone_id}.pkl")
    if PROPHET_AVAILABLE and os.path.exists(prophet_path):
        return _load(prophet_path), None, "prophet"

    # Sinon régression linéaire
    linear_path  = os.path.join(MODELS_DIR, "prediction_models",
                                f"linear_zone_{zone_id}.pkl")
    scaler_path  = os.path.join(MODELS_DIR, "prediction_models",
                                f"scaler_zone_{zone_id}.pkl")
    if os.path.exists(linear_path):
        return _load(linear_path), _load(scaler_path), "linear"

    return None, None, None


# ════════════════════════════════════════════════════════════════════
# DÉTECTION D'ANOMALIE
# ════════════════════════════════════════════════════════════════════

def detect_anomaly(features: dict) -> dict:
    """
    Retourne le score d'anomalie pour un vecteur de features.
    Score : entre -1 (très anormal) et 1 (normal).
    """
    model, scaler, meta = load_isolation_forest()

    if model is None:
        # Fallback : score basé sur règles simples
        taux = features.get("taux_utilisation", 0)
        score = max(-1.0, 1.0 - (taux / 50.0))
        return {
            "anomaly_score": round(score, 3),
            "is_anomaly": taux >= SEUIL_SATURATION,
            "source": "fallback_rules",
        }

    # Construire le vecteur dans le bon ordre
    x = np.array([[
        features.get(f, 0) for f in FEATURES_ISOLATION
    ]])
    x_scaled = scaler.transform(x)

    raw_score  = float(model.decision_function(x_scaled)[0])
    prediction = int(model.predict(x_scaled)[0])  # -1 = anomalie

    # Normalisation du score en [-1, 1]
    threshold = meta.get("score_threshold", -0.1)
    normalized = max(-1.0, min(1.0, raw_score / max(abs(threshold), 0.01)))

    return {
        "anomaly_score": round(normalized, 3),
        "raw_score": round(raw_score, 4),
        "is_anomaly": prediction == -1,
        "source": "isolation_forest",
    }


# ════════════════════════════════════════════════════════════════════
# PRÉDICTION TEMPORELLE
# ════════════════════════════════════════════════════════════════════

def predict_saturation(zone_id: int, features: dict,
                       historique: list = None) -> dict:
    """
    Prédit quand la zone atteindra le seuil de saturation.

    historique : liste de dicts [{timestamp, taux_utilisation}, ...]
                 utilisé par Prophet pour les prédictions futures.
    """
    taux_actuel = features.get("taux_utilisation", 0)

    # Zone déjà saturée
    if taux_actuel >= SEUIL_SATURATION:
        return {
            "saturation_predite": True,
            "heures_avant_saturation": 0,
            "date_predite": datetime.now().isoformat(),
            "confidence": 1.0,
            "message": "Zone déjà saturée",
            "model_type": "rule",
        }

    model, scaler, model_type = load_prediction_model(zone_id)

    if model_type == "prophet" and historique:
        return _predict_prophet(model, taux_actuel, historique)

    if model_type == "linear":
        return _predict_linear(model, scaler, features, taux_actuel)

    # Fallback : extrapolation linéaire naïve
    return _predict_naive(features, taux_actuel)


def _predict_prophet(model, taux_actuel: float, historique: list) -> dict:
    """Prédiction Prophet : génère les 48h futures et cherche le croisement."""
    import pandas as pd

    # Construire le dataframe historique pour Prophet
    hist_df = pd.DataFrame(historique)
    hist_df = hist_df.rename(columns={"timestamp": "ds", "taux_utilisation": "y"})
    hist_df["ds"] = pd.to_datetime(hist_df["ds"])

    # Prédire les 48 prochaines heures (intervalles de 30 min)
    future = model.make_future_dataframe(periods=96, freq="30min")
    forecast = model.predict(future)

    # Chercher le premier point où yhat dépasse le seuil
    future_forecast = forecast[forecast["ds"] > datetime.now()]
    above = future_forecast[future_forecast["yhat"] >= SEUIL_SATURATION]

    if above.empty:
        return {
            "saturation_predite": False,
            "heures_avant_saturation": None,
            "date_predite": None,
            "confidence": 0.85,
            "message": "Pas de saturation prévue dans les 48h",
            "model_type": "prophet",
        }

    first_sat = above.iloc[0]
    heures = (first_sat["ds"] - datetime.now()).total_seconds() / 3600
    # Confiance basée sur l'intervalle de prédiction
    interval_width = first_sat["yhat_upper"] - first_sat["yhat_lower"]
    confidence = max(0.3, min(0.95, 1.0 - interval_width / 100.0))

    return {
        "saturation_predite": True,
        "heures_avant_saturation": round(heures, 1),
        "date_predite": first_sat["ds"].isoformat(),
        "confidence": round(confidence, 2),
        "message": f"Saturation prévue dans {heures:.1f}h (Prophet)",
        "model_type": "prophet",
        "yhat": round(float(first_sat["yhat"]), 1),
        "yhat_lower": round(float(first_sat["yhat_lower"]), 1),
        "yhat_upper": round(float(first_sat["yhat_upper"]), 1),
    }


def _predict_linear(model, scaler, features: dict, taux_actuel: float) -> dict:
    """
    Prédiction par régression linéaire :
    simule les N prochaines heures en itérant le modèle.
    """
    current = dict(features)
    now = datetime.now()

    for h in range(1, 73):  # max 72h de prévision
        x = np.array([[current.get(f, 0) for f in FEATURES_LINEAR]])
        x_scaled = scaler.transform(x)
        y_pred = float(model.predict(x_scaled)[0])
        y_pred = max(0.0, min(100.0, y_pred))  # clamp [0, 100]

        if y_pred >= SEUIL_SATURATION:
            confidence = _linear_confidence(current, h)
            date_pred = (now + timedelta(hours=h)).isoformat()
            return {
                "saturation_predite": True,
                "heures_avant_saturation": float(h),
                "date_predite": date_pred,
                "taux_predit": round(y_pred, 1),
                "confidence": round(confidence, 2),
                "message": f"Saturation prévue dans {h}h (régression linéaire)",
                "model_type": "linear",
            }

        # Mise à jour du vecteur pour l'itération suivante (sliding window)
        current["taux_lag3"] = current.get("taux_lag1", taux_actuel)
        current["taux_lag1"] = y_pred
        current["tendance_6pts"] = y_pred - taux_actuel
        current["hour_of_day"]  = (now + timedelta(hours=h)).hour
        current["day_of_week"]  = (now + timedelta(hours=h)).weekday()

    return {
        "saturation_predite": False,
        "heures_avant_saturation": None,
        "date_predite": None,
        "confidence": 0.6,
        "message": "Pas de saturation prévue dans les 72h (régression linéaire)",
        "model_type": "linear",
    }


def _predict_naive(features: dict, taux_actuel: float) -> dict:
    """
    Fallback sans modèle entraîné.
    Extrapolation linéaire brute à partir de la tendance observée.
    """
    tendance = features.get("tendance_6pts", 0)  # delta sur 6 mesures ≈ 6h

    if tendance <= 0:
        return {
            "saturation_predite": False,
            "heures_avant_saturation": None,
            "date_predite": None,
            "confidence": 0.2,
            "message": "Tendance stable — pas de saturation prévue (naïf)",
            "model_type": "naive",
        }

    vitesse_horaire = tendance / 6.0  # % par heure
    delta_restant   = SEUIL_SATURATION - taux_actuel
    heures          = delta_restant / vitesse_horaire
    date_pred       = (datetime.now() + timedelta(hours=heures)).isoformat()

    return {
        "saturation_predite": heures < 72,
        "heures_avant_saturation": round(heures, 1),
        "date_predite": date_pred,
        "confidence": 0.2,
        "message": f"Estimation naïve — saturation dans {heures:.1f}h",
        "model_type": "naive",
    }


def _linear_confidence(features: dict, horizon_h: int) -> float:
    """
    La confiance décroît avec l'horizon temporel.
    Elle est aussi pénalisée si les features sont incomplètes.
    """
    horizon_factor = max(0.1, 1.0 - horizon_h / 72.0)
    data_factor    = 0.9 if features.get("taux_lag3", 0) > 0 else 0.5
    return horizon_factor * data_factor


# ════════════════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ════════════════════════════════════════════════════════════════════

def run_single(zone_id: int, features_json: str,
               historique_json: str = None) -> dict:
    # Nettoyer et parser les features
    features_str = features_json.strip()

    # Remplacer les guillemets simples par des doubles
    features_str = features_str.replace("'", '"')

    # Corriger les booléens si nécessaire
    features_str = features_str.replace('True', 'true').replace('False', 'false')

    try:
        features = json.loads(features_str)
    except json.JSONDecodeError as e:
        print(f"ERREUR: Impossible de parser: {features_str}", file=sys.stderr)
        print(f"Erreur: {e}", file=sys.stderr)
        # Fallback: essayer d'évaluer comme un dict Python
        try:
            import ast
            features = ast.literal_eval(features_json)
        except:
            raise ValueError(f"Format JSON invalide: {features_json}")

    print("DEBUG: Features parsed successfully", file=sys.stderr)
    print("DEBUG INPUT:", features_json, file=sys.stderr)

    historique = None
    if historique_json:
        hist_str = historique_json.strip().replace("'", '"')
        try:
            historique = json.loads(hist_str)
        except:
            try:
                import ast
                historique = ast.literal_eval(historique_json)
            except:
                historique = None

    anomaly = detect_anomaly(features)
    prediction = predict_saturation(zone_id, features, historique)

    return {
        "zone_id": zone_id,
        "timestamp": datetime.now().isoformat(),
        "anomaly_detection": anomaly,
        "prediction": prediction,
    }


def run_batch(features_list_json: str) -> list:
    items = json.loads(features_list_json)
    results = []
    for item in items:
        zone_id = item.get("zone_id")
        anomaly    = detect_anomaly(item)
        prediction = predict_saturation(zone_id, item)
        results.append({
            "zone_id": zone_id,
            "timestamp": datetime.now().isoformat(),
            "anomaly_detection": anomaly,
            "prediction": prediction,
        })
    return results


def main():
    parser = argparse.ArgumentParser(description="Prédiction saturation réseau")
    parser.add_argument("--zone_id",    type=int, help="ID de la zone à analyser")
    parser.add_argument("--features",   help="JSON des features")
    parser.add_argument("--historique", help="JSON de l'historique")
    parser.add_argument("--batch",      action="store_true")
    parser.add_argument("--models_dir", default="models/")
    parser.add_argument("--stdin",      action="store_true", help="Lire les features depuis stdin")
    args = parser.parse_args()

    global MODELS_DIR
    MODELS_DIR = args.models_dir

    if args.stdin:
        # Lire depuis stdin
        data = json.load(sys.stdin)
        if isinstance(data, list):
            result = run_batch(json.dumps(data))
        else:
            zone_id = data.get("zone_id", args.zone_id)
            features = json.dumps(data.get("features", {}))
            historique = json.dumps(data.get("historique")) if data.get("historique") else None
            result = run_single(zone_id, features, historique)
    elif args.batch:
        result = run_batch(args.features)
    else:
        if not args.zone_id:
            print(json.dumps({"error": "--zone_id requis en mode single"}))
            sys.exit(1)
        result = run_single(args.zone_id, args.features, args.historique)

    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()