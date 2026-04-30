"""
====================================================================
  MICROSERVICE IA — Classification de Priorité des Tickets 5G
  train_ticket_model.py

  Pipeline : TF-IDF (description) + Features structurelles → XGBoost
  Cibles   : BASSE | MOYENNE | HAUTE | CRITIQUE
====================================================================
"""

import pandas as pd
import numpy as np
import pickle
import os
import json
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import Pipeline
from sklearn.metrics import (
    classification_report, confusion_matrix,
    accuracy_score, f1_score
)
from xgboost import XGBClassifier
import scipy.sparse as sp
import warnings
warnings.filterwarnings("ignore")

# ─────────────────────────────────────────────────────────────────
# 1.  GÉNÉRATION DE DONNÉES SYNTHÉTIQUES RÉALISTES
#     (basées sur la logique métier 5G de votre projet)
# ─────────────────────────────────────────────────────────────────

TYPES_PANNE = [
    "RESEAU", "ELECTRIQUE", "MATERIEL", "LOGICIEL",
    "INTERFERENCE", "SURCHARGE", "MAINTENANCE", "SECURITE"
]

REGIONS = [
    "TUNIS",
    "ARIANA",
    "BEN_AROUS",
    "MANOUBA",
    "NABEUL",
    "ZAGHOUAN",
    "BIZERTE",
    "BEJA",
    "JENDOUBA",
    "KEF",
    "SILIANA",
    "KAIROUAN",
    "KASSERINE",
    "SIDI_BOUZID",
    "SOUSSE",
    "MONASTIR",
    "MAHDIA",
    "SFAX",
    "GABES",
    "MEDENINE",
    "TATAOUINE",
    "GAFSA",
    "TOZEUR",
    "KEBILI"
]

DESCRIPTIONS_PAR_PRIORITE = {
    "BASSE": [
        "maintenance préventive planifiée de la zone",
        "vérification routinière des équipements",
        "mise à jour logicielle mineure prévue",
        "test de performance standard",
        "inspection périodique du pylône",
        "nettoyage des équipements de la station",
        "calibration des antennes en cours",
        "diagnostic préventif sans impact service",
    ],
    "MOYENNE": [
        "dégradation partielle du signal détectée",
        "plusieurs abonnés signalent des coupures intermittentes",
        "baisse de débit constatée sur la zone",
        "interférences mineures sur la bande fréquence",
        "surcharge temporaire du nœud réseau",
        "panne partielle de l équipement de backup",
        "anomalie détectée sur le module transmission",
        "qualité de service dégradée pour certains usagers",
    ],
    "HAUTE": [
        "panne majeure affectant plusieurs centaines d abonnés",
        "coupure totale du service sur la zone",
        "défaillance critique de l équipement principal",
        "nombreuses réclamations urgentes reçues",
        "interruption service impact fort secteur industriel",
        "panne réseau cascade zone densément peuplée",
        "défaut alimentation électrique pylône principal",
        "surcharge critique dépassant 90 pourcent capacité",
    ],
    "CRITIQUE": [
        "panne totale zone stratégique infrastructure nationale",
        "interruption massive service milliers abonnés affectés",
        "défaillance systémique plusieurs pylônes simultanément",
        "incident sécurité réseau compromis intrusion détectée",
        "panne backbone réseau impact régional total",
        "catastrophe technique site central hors service",
        "urgence absolue service essentiel interrompu hopital aeroport",
        "effondrement complet infrastructure zone critique",
    ]
}

def generer_description(priorite: str) -> str:
    base = np.random.choice(DESCRIPTIONS_PAR_PRIORITE[priorite])
    extras = [
        "intervention requise immédiatement",
        "équipe technique alertée",
        "rapport incident ouvert",
        "suivi en temps réel activé",
        "escalade en cours",
    ]
    if np.random.random() > 0.4:
        base += " " + np.random.choice(extras)
    return base


def generer_dataset(n_samples: int = 5000, seed: int = 42) -> pd.DataFrame:
    np.random.seed(seed)
    records = []

    # Distribution réaliste des priorités
    priorites = np.random.choice(
        ["BASSE", "MOYENNE", "HAUTE", "CRITIQUE"],
        size=n_samples,
        p=[0.30, 0.35, 0.25, 0.10]
    )

    for priorite in priorites:
        type_panne = np.random.choice(TYPES_PANNE)
        region = np.random.choice(REGIONS)

        # Features corrélées à la priorité (logique métier)
        if priorite == "BASSE":
            nb_rec    = np.random.randint(1, 15)
            heure     = np.random.randint(8, 18)
            zone_pop  = np.random.choice(["FAIBLE", "MOYENNE"])
        elif priorite == "MOYENNE":
            nb_rec    = np.random.randint(10, 40)
            heure     = np.random.randint(6, 22)
            zone_pop  = np.random.choice(["FAIBLE", "MOYENNE", "HAUTE"])
        elif priorite == "HAUTE":
            nb_rec    = np.random.randint(30, 100)
            heure     = np.random.randint(0, 24)
            zone_pop  = np.random.choice(["MOYENNE", "HAUTE", "TRES_HAUTE"])
        else:  # CRITIQUE
            nb_rec    = np.random.randint(80, 300)
            heure     = np.random.randint(0, 24)
            zone_pop  = "TRES_HAUTE"

        description = generer_description(priorite)

        records.append({
            "typePanne":          type_panne,
            "region":             region,
            "nombreReclamations": nb_rec,
            "heure":              heure,
            "zonePopulation":     zone_pop,
            "description":        description,
            "priorite":           priorite,
        })

    df = pd.DataFrame(records)
    print(f"✅  Dataset généré : {len(df)} tickets")
    print(df["priorite"].value_counts())
    return df


# ─────────────────────────────────────────────────────────────────
# 2.  FEATURE ENGINEERING
# ─────────────────────────────────────────────────────────────────

ZONE_POP_MAP = {"FAIBLE": 0, "MOYENNE": 1, "HAUTE": 2, "TRES_HAUTE": 3}

def build_structural_features(df: pd.DataFrame) -> np.ndarray:
    """
    Features structurelles encodées numériquement.
    """
    type_enc    = LabelEncoder().fit(TYPES_PANNE)
    region_enc  = LabelEncoder().fit(REGIONS)

    X_struct = np.column_stack([
        type_enc.transform(df["typePanne"]),
        region_enc.transform(df["region"]),
        df["nombreReclamations"].values,
        df["heure"].values,
        df["zonePopulation"].map(ZONE_POP_MAP).values,
    ])
    return X_struct, type_enc, region_enc


# ─────────────────────────────────────────────────────────────────
# 3.  ENTRAÎNEMENT DU MODÈLE HYBRIDE
# ─────────────────────────────────────────────────────────────────

def train_model(df: pd.DataFrame, models_dir: str = "models/"):
    os.makedirs(models_dir, exist_ok=True)

    # Encodage cible
    label_enc = LabelEncoder()
    y = label_enc.fit_transform(df["priorite"])
    print(f"\n📌 Classes : {list(label_enc.classes_)}")

    # Features structurelles
    X_struct, type_enc, region_enc = build_structural_features(df)

    scaler = StandardScaler()
    X_struct_scaled = scaler.fit_transform(X_struct)

    # Features textuelles (description)
    tfidf = TfidfVectorizer(
        max_features=300,
        ngram_range=(1, 2),
        sublinear_tf=True,
        min_df=2
    )
    X_text = tfidf.fit_transform(df["description"])

    # Fusion : struct + text
    X_combined = sp.hstack([
        sp.csr_matrix(X_struct_scaled),
        X_text
    ])

    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X_combined, y, test_size=0.2,
        random_state=42, stratify=y
    )

    # ── XGBoost ──
    model = XGBClassifier(
        n_estimators=200,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        use_label_encoder=False,
        eval_metric="mlogloss",
        random_state=42,
        n_jobs=-1
    )

    print("\n🚀  Entraînement XGBoost...")
    model.fit(X_train, y_train)

    # ── Évaluation ──
    y_pred = model.predict(X_test)
    acc    = accuracy_score(y_test, y_pred)
    f1     = f1_score(y_test, y_pred, average="weighted")

    print(f"\n📊  Accuracy  : {acc:.4f}")
    print(f"📊  F1-score  : {f1:.4f}")
    print("\n" + classification_report(
        y_test, y_pred,
        target_names=label_enc.classes_
    ))

    # ── Cross-validation ──
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = cross_val_score(model, X_combined, y, cv=cv, scoring="f1_weighted")
    print(f"📊  CV F1-weighted : {cv_scores.mean():.4f} ± {cv_scores.std():.4f}")

    # ── Sauvegarde ──
    artifacts = {
        "model":       model,
        "label_enc":   label_enc,
        "type_enc":    type_enc,
        "region_enc":  region_enc,
        "scaler":      scaler,
        "tfidf":       tfidf,
    }

    for name, obj in artifacts.items():
        path = os.path.join(models_dir, f"{name}.pkl")
        with open(path, "wb") as f:
            pickle.dump(obj, f)
        print(f"💾  Sauvegardé : {path}")

    # Métriques JSON
    metrics = {
        "accuracy":    round(acc, 4),
        "f1_weighted": round(f1, 4),
        "cv_f1_mean":  round(float(cv_scores.mean()), 4),
        "cv_f1_std":   round(float(cv_scores.std()), 4),
        "classes":     list(label_enc.classes_),
        "n_samples":   len(df),
    }
    with open(os.path.join(models_dir, "metrics.json"), "w") as f:
        json.dump(metrics, f, indent=2)

    print(f"\n✅  Modèle sauvegardé dans '{models_dir}'")
    return metrics


# ─────────────────────────────────────────────────────────────────
# 4.  MAIN
# ─────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Train ticket priority model")
    parser.add_argument("--models_dir",  default="models/",    help="Dossier de sortie des modèles")
    parser.add_argument("--n_samples",   default=5000, type=int, help="Nombre de tickets synthétiques")
    args = parser.parse_args()

    df      = generer_dataset(n_samples=args.n_samples)
    metrics = train_model(df, models_dir=args.models_dir)

    print("\n🏆  Entraînement terminé !")
    print(json.dumps(metrics, indent=2))