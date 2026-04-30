import argparse
import os
import sys
import json
import warnings
warnings.filterwarnings("ignore")

import numpy as np
import pandas as pd
import joblib
from datetime import datetime

from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import cross_val_score
from sklearn.metrics import mean_absolute_error, r2_score

# Prophet (optionnel)
try:
    from prophet import Prophet
    PROPHET_AVAILABLE = True
except ImportError:
    PROPHET_AVAILABLE = False
    print("[WARN] Prophet non installé — fallback Linear Regression")


# ════════════════════════════════════════════════════════════
# 1. LOAD + CLEAN DATA
# ════════════════════════════════════════════════════════════
def load_and_clean(csv_path: str) -> pd.DataFrame:

    df = pd.read_csv(csv_path)

    # ✅ FIX TIMESTAMP
    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    df = df.dropna(subset=["timestamp", "taux_utilisation"])
    df = df.sort_values("timestamp").reset_index(drop=True)

    # ✅ AJOUT BRUIT (anti overfitting)
    df["taux_utilisation"] = df["taux_utilisation"] + np.random.normal(0, 2, len(df))

    # ✅ SAISONNALITÉ (jour/nuit)
    df["hour_of_day"] = df["timestamp"].dt.hour
    df["taux_utilisation"] += np.sin(df["hour_of_day"] / 24 * 2 * np.pi) * 5

    # Features
    df["ratio_satures"] = (
                                  df["nb_pylones_satures"] / df["nb_pylones_total"].replace(0, np.nan)
                          ).fillna(0) * 100

    df["day_of_week"] = df["timestamp"].dt.dayofweek

    # Lag + tendance
    df = df.sort_values(["zone_id", "timestamp"])

    df["tendance_6pts"] = df.groupby("zone_id")["taux_utilisation"] \
        .transform(lambda s: s.diff(6).fillna(0))

    df["taux_lag1"] = df.groupby("zone_id")["taux_utilisation"].shift(1).fillna(0)
    df["taux_lag3"] = df.groupby("zone_id")["taux_utilisation"].shift(3).fillna(0)

    print(f"[DATA] {len(df)} lignes, {df['zone_id'].nunique()} zones")

    return df


# ════════════════════════════════════════════════════════════
# 2. ISOLATION FOREST
# ════════════════════════════════════════════════════════════
FEATURES_IF = [
    "taux_utilisation",
    "ratio_satures",
    "tendance_6pts",
    "taux_lag1",
    "taux_lag3",
    "hour_of_day",
    "day_of_week",
]

def train_isolation_forest(df, output_dir):

    X = df[FEATURES_IF].fillna(0).values

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    model = IsolationForest(
        n_estimators=200,
        contamination=0.1,
        random_state=42,
        n_jobs=-1
    )

    model.fit(X_scaled)

    os.makedirs(output_dir, exist_ok=True)

    joblib.dump(model, os.path.join(output_dir, "isolation_forest.pkl"))
    joblib.dump(scaler, os.path.join(output_dir, "scaler_if.pkl"))

    print("[IF] Modèle sauvegardé")

    return {"samples": len(X)}


# ════════════════════════════════════════════════════════════
# 3. PROPHET FIX
# ════════════════════════════════════════════════════════════
def train_prediction_models(df, output_dir):

    pred_dir = os.path.join(output_dir, "prediction_models")
    os.makedirs(pred_dir, exist_ok=True)

    results = {}

    for zone_id in df["zone_id"].unique():

        zone_df = df[df["zone_id"] == zone_id].copy().sort_values("timestamp")

        if len(zone_df) < 10:
            continue

        if PROPHET_AVAILABLE and len(zone_df) >= 30:
            metrics = train_prophet(zone_id, zone_df, pred_dir)
        else:
            metrics = train_linear(zone_id, zone_df, pred_dir)

        results[str(zone_id)] = metrics

    return results


def train_prophet(zone_id, zone_df, pred_dir):

    prophet_df = zone_df[["timestamp", "taux_utilisation"]] \
        .rename(columns={"timestamp": "ds", "taux_utilisation": "y"})

    # ✅ SPLIT CORRECT
    split = int(len(prophet_df) * 0.8)
    train_df = prophet_df.iloc[:split]
    test_df = prophet_df.iloc[split:]

    model = Prophet(
        daily_seasonality=True,
        weekly_seasonality=True,
        changepoint_prior_scale=0.1
    )

    model.fit(train_df)

    forecast = model.predict(test_df[["ds"]])

    mae = mean_absolute_error(test_df["y"], forecast["yhat"])
    r2 = r2_score(test_df["y"], forecast["yhat"])

    joblib.dump(model, os.path.join(pred_dir, f"prophet_{zone_id}.pkl"))

    print(f"[Zone {zone_id}] MAE={mae:.2f} R2={r2:.2f}")

    return {
        "model": "prophet",
        "mae": float(mae),
        "r2": float(r2)
    }


# ════════════════════════════════════════════════════════════
# 4. LINEAR (fallback)
# ════════════════════════════════════════════════════════════
def train_linear(zone_id, zone_df, pred_dir):

    X = zone_df[[
        "taux_lag1", "taux_lag3",
        "tendance_6pts", "ratio_satures",
        "hour_of_day", "day_of_week"
    ]].fillna(0)

    y = zone_df["taux_utilisation"]

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    model = LinearRegression()
    model.fit(X_scaled, y)

    joblib.dump(model, os.path.join(pred_dir, f"linear_{zone_id}.pkl"))
    joblib.dump(scaler, os.path.join(pred_dir, f"scaler_{zone_id}.pkl"))

    return {"model": "linear"}


# ════════════════════════════════════════════════════════════
# MAIN
# ════════════════════════════════════════════════════════════
def main():

    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True)
    parser.add_argument("--output", default="models/")
    args = parser.parse_args()

    if not os.path.exists(args.data):
        print("Fichier introuvable")
        sys.exit(1)

    df = load_and_clean(args.data)

    print("\n--- Isolation Forest ---")
    train_isolation_forest(df, args.output)

    print("\n--- Prediction ---")
    train_prediction_models(df, args.output)

    print("\n✅ TRAINING TERMINÉ")


if __name__ == "__main__":
    main()