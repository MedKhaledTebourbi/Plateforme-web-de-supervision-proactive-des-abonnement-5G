"""
====================================================================
  MICROSERVICE IA — Guide Technicien 5G Tunisie
  train_guide_model.py — Version complète avec pannes réelles
====================================================================
"""

import pandas as pd
import numpy as np
import pickle
import os
import json
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score
import scipy.sparse as sp
import warnings
warnings.filterwarnings("ignore")


# ══════════════════════════════════════════════════════════════════
# 1. TYPES DE PANNES RÉELS RÉSEAU 5G
# ══════════════════════════════════════════════════════════════════

TYPES_PANNE = [
    # Pannes radio
    "PERTE_SIGNAL_5G",          # Perte signal NR SA/NSA
    "DEGRADATION_DEBIT",        # Débit en dessous SLA
    "HANDOVER_ECHEC",           # Mobilité dégradée
    "INTERFERENCE_COCANAL",     # Interférence co-canal 5G NR
    "COUVERTURE_INSUFFISANTE",  # Trou de couverture
    # Pannes équipements
    "PANNE_BBU",                # Base Band Unit hors service
    "PANNE_RRH",                # Remote Radio Head défaillant
    "PANNE_ANTENNE",            # Antenne endommagée
    "PANNE_TRANSPORT",          # Lien transport (backhaul) coupé
    "PANNE_ALIMENTATION",       # Alimentation électrique défaillante
    # Pannes cœur réseau
    "PANNE_AMF",                # Access & Mobility Management Function
    "PANNE_UPF",                # User Plane Function
    "PANNE_SMF",                # Session Management Function
    "PANNE_CORE_SLICE",         # Network Slicing défaillant
    # Pannes transmission
    "COUPURE_FIBRE",            # Fibre optique coupée
    "SATURATION_BACKHAUL",      # Lien backhaul saturé
    "LATENCE_ELEVEE",           # Latence > SLA (>20ms 5G)
    # Pannes sécurité & logiciel
    "INTRUSION_RESEAU",         # Tentative d'intrusion détectée
    "MISE_A_JOUR_ECHOUEE",      # Échec MAJ firmware/software
    "SURCHARGE_CELLULE",        # Cellule 5G surchargée (>90%)
    # Pannes infrastructure
    "PANNE_CLIMATISATION",      # Clim salle technique en panne
    "INONDATION_SITE",          # Site inondé (pluie/dégât eaux)
    "VANDALISME_PYLONE",        # Vandalisme équipement terrain
    "PANNE_GROUPE_ELECTROGENE", # Groupe électrogène défaillant
]


# ══════════════════════════════════════════════════════════════════
# 2. 24 GOUVERNORATS TUNISIENS
# ══════════════════════════════════════════════════════════════════

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
    "KEBILI",
]


# ══════════════════════════════════════════════════════════════════
# 3. BASE DE CONNAISSANCES MÉTIER 5G — PANNES RÉELLES DÉTAILLÉES
# ══════════════════════════════════════════════════════════════════

BASE_CONNAISSANCE = {

    # ── RADIO ─────────────────────────────────────────────────────

    "PERTE_SIGNAL_5G": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_SIGNAL_URGENTE",
            "solution": "Restaurer signal 5G NR SA — basculement cellule de secours",
            "etapes": [
                "Vérifier état gNB dans OMC (alarmes actives)",
                "Contrôler lien fronthaul eCPRI entre BBU et RRH",
                "Vérifier synchronisation PTP/IEEE 1588 (écart < 1.5µs)",
                "Tester puissance émission RRH (objectif: -60 à -70 dBm RSRP)",
                "Activer cellule 5G de secours (NSA fallback LTE)",
                "Lancer cellule virtuelle via vRAN si disponible",
                "Valider couverture avec drive test outil TEMS",
                "Documenter RCA dans ticketing NOC",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["OMC Nokia/Ericsson", "TEMS Investigation", "PTP Analyzer", "vRAN Console"],
        },
        "HAUTE": {
            "action": "BASCULEMENT_NSA",
            "solution": "Basculer 5G SA → 5G NSA + LTE anchor",
            "etapes": [
                "Activer mode NSA (Non-Standalone) comme fallback",
                "Configurer LTE anchor cell (B3/B7/B20)",
                "Vérifier ENDC configuration (E-UTRA NR Dual Connectivity)",
                "Monitorer KPI SINR > 5dB sur zone",
                "Programmer intervention physique sous 4h",
            ],
            "temps_estime": "20-40 min",
            "automatisable": True,
            "outils": ["OMC", "ENDC Manager", "NetAct"],
        },
        "MOYENNE": {
            "action": "OPTIMISATION_COUVERTURE",
            "solution": "Optimiser paramètres couverture et puissance",
            "etapes": [
                "Analyser rapports MDT (Minimization of Drive Tests)",
                "Ajuster tilt électrique antenne (RET ±3°)",
                "Optimiser paramètres RS (Reference Signal) power",
                "Vérifier configuration PCI (Physical Cell ID)",
                "Valider amélioration sur heatmap couverture",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["OMC", "RET Controller", "Coverage Heatmap Tool"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_SIGNAL",
            "solution": "Monitoring signal renforcé 4h",
            "etapes": [
                "Activer monitoring RSRP/RSRQ/SINR continu",
                "Configurer alarme si RSRP < -100 dBm",
                "Analyser rapport dans 4h",
            ],
            "temps_estime": "10 min",
            "automatisable": True,
            "outils": ["NOC Dashboard", "Prometheus/Grafana"],
        },
    },

    "DEGRADATION_DEBIT": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_DEBIT_URGENTE",
            "solution": "Restaurer débit 5G (objectif > 500 Mbps DL)",
            "etapes": [
                "Mesurer débit actuel avec iPerf3 (DL/UL)",
                "Vérifier bande passante allouée dans scheduler",
                "Contrôler taux d'erreur HARQ (objectif < 5%)",
                "Vérifier activation Carrier Aggregation (CA 2-4 carriers)",
                "Activer 256-QAM si SINR > 22dB",
                "Configurer 4x4 MIMO ou 8x8 Massive MIMO",
                "Vérifier congestion backhaul (bande passante > 10Gbps)",
                "Escalader vers équipe cœur réseau si UPF saturé",
            ],
            "temps_estime": "20-45 min",
            "automatisable": True,
            "outils": ["iPerf3", "OMC", "Spectrum Analyzer", "Backhaul Monitor"],
        },
        "HAUTE": {
            "action": "OPTIMISATION_SCHEDULER",
            "solution": "Optimiser scheduler radio pour maximiser débit",
            "etapes": [
                "Analyser distribution PRB (Physical Resource Block)",
                "Activer Proportional Fair scheduler",
                "Configurer QoS profiles par slice réseau",
                "Vérifier activation beamforming massif",
                "Tester débit post-optimisation",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["OMC", "PRB Analyzer", "QoS Manager"],
        },
        "MOYENNE": {
            "action": "ANALYSE_PERFORMANCE",
            "solution": "Analyser KPIs et identifier goulot d'étranglement",
            "etapes": [
                "Extraire KPIs: Throughput, PRB util, MCS, BLER",
                "Identifier heure pic de trafic",
                "Analyser profil utilisateurs (devices 5G vs 4G)",
                "Soumettre rapport optimisation",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["OMC Analytics", "Grafana", "Jupyter Notebook"],
        },
        "FAIBLE": {
            "action": "MONITORING_DEBIT",
            "solution": "Surveillance débit avec alertes seuils SLA",
            "etapes": [
                "Configurer alertes SLA (DL < 100 Mbps)",
                "Analyser tendance hebdomadaire",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["SLA Monitor", "Grafana"],
        },
    },

    "HANDOVER_ECHEC": {
        "TRES_HAUTE": {
            "action": "CORRECTION_MOBILITE_URGENTE",
            "solution": "Corriger paramètres mobilité — utilisateurs en appel chutent",
            "etapes": [
                "Vérifier taux d'échec handover (HO Failure Rate > 2% → critique)",
                "Analyser cause: A3 event mal configuré ou X2/Xn interface down",
                "Vérifier interface X2/Xn entre gNB (latence < 5ms)",
                "Reconfigurer paramètres A3 (offset, TTT - Time To Trigger)",
                "Activer DAPS handover (Dual Active Protocol Stack) si 5G SA",
                "Tester mobilité avec UE test en déplacement",
                "Vérifier idle mode mobility (RRC reestablishment)",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["OMC", "X2/Xn Monitor", "Drive Test UE", "RRC Analyzer"],
        },
        "HAUTE": {
            "action": "RECONFIGURATION_MOBILITE",
            "solution": "Reconfigurer paramètres A3/A5 et interfaces inter-gNB",
            "etapes": [
                "Analyser logs RRC Connection Reconfiguration Failed",
                "Ajuster hysteresis A3 event (-3 à +3 dB)",
                "Reconfigurer TTT (160ms → 80ms si mobilité élevée)",
                "Vérifier cohérence PCI pour éviter confusion",
            ],
            "temps_estime": "45-90 min",
            "automatisable": False,
            "outils": ["OMC", "Mobility Parameter Tool"],
        },
        "MOYENNE": {
            "action": "OPTIMISATION_MOBILITE",
            "solution": "Optimiser paramètres mobilité selon profil trafic",
            "etapes": [
                "Extraire statistiques HO par cellule",
                "Identifier cellules avec HO failure > 1%",
                "Appliquer paramètres optimaux recommandés par SON",
            ],
            "temps_estime": "1-3 heures",
            "automatisable": True,
            "outils": ["SON Engine", "OMC Analytics"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_MOBILITE",
            "solution": "Monitoring KPIs mobilité",
            "etapes": [
                "Configurer tableau de bord KPI mobilité",
                "Alerte si HO Success Rate < 98%",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["NOC Dashboard"],
        },
    },

    "INTERFERENCE_COCANAL": {
        "TRES_HAUTE": {
            "action": "ELIMINATION_INTERFERENCE_URGENTE",
            "solution": "Éliminer interférence co-canal — qualité réseau critique",
            "etapes": [
                "Scanner spectre fréquences avec analyseur (bandes n78/n257/n258)",
                "Identifier source interférence (gNB voisin, équipement illégitime)",
                "Activer ICIC/eICIC (Inter-Cell Interference Coordination)",
                "Réduire puissance cellule interférente (-3dB incrémental)",
                "Reconfigurer PCI si collision détectée",
                "Activer Interference Rejection Combining (IRC) sur UE",
                "Mesurer amélioration SINR (objectif > 15dB)",
                "Signaler à ANCT (Agence Nationale des Fréquences Tunisie) si source externe",
            ],
            "temps_estime": "30-90 min",
            "automatisable": True,
            "outils": ["Spectrum Analyzer R&S", "ICIC Manager", "OMC", "Drive Test"],
        },
        "HAUTE": {
            "action": "RECONFIGURATION_RF",
            "solution": "Reconfigurer paramètres RF pour réduire interférences",
            "etapes": [
                "Analyser rapport BLER/CQI cellules affectées",
                "Ajuster tilt antenne pour réduire overlap",
                "Activer ABS (Almost Blank Subframe) si LTE co-site",
                "Optimiser beamforming pour nulling interférence",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["OMC", "RF Planning Tool", "Beamforming Manager"],
        },
        "MOYENNE": {
            "action": "ANALYSE_INTERFERENCE",
            "solution": "Analyser et cartographier sources d'interférence",
            "etapes": [
                "Drive test zone affectée",
                "Cartographier SINR avec outil heatmap",
                "Identifier pattern interférence temporelle",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["TEMS", "MapInfo", "Spectrum Analyzer"],
        },
        "FAIBLE": {
            "action": "MONITORING_INTERFERENCE",
            "solution": "Surveillance continue niveau d'interférence",
            "etapes": [
                "Activer monitoring SINR/BLER continu",
                "Configurer alertes si dégradation",
            ],
            "temps_estime": "10 min",
            "automatisable": True,
            "outils": ["NOC Dashboard", "OMC Analytics"],
        },
    },

    "COUVERTURE_INSUFFISANTE": {
        "TRES_HAUTE": {
            "action": "DEPLOIEMENT_CELLULE_URGENCE",
            "solution": "Déployer cellule 5G mobile ou Small Cell d'urgence",
            "etapes": [
                "Localiser zone sans couverture sur heatmap",
                "Déployer COW (Cell On Wheels) 5G si disponible",
                "Configurer fréquences basses (n28/n71) pour couverture maximale",
                "Activer booster de signal si disponible (RIS - Reconfigurable Intelligent Surface)",
                "Coordonner avec NOC pour configuration rapide",
                "Valider couverture avec RSRP > -100 dBm",
                "Planifier déploiement infrastructure permanent",
            ],
            "temps_estime": "2-6 heures",
            "automatisable": False,
            "outils": ["COW 5G", "RF Planning Tool", "Coverage Mapper"],
        },
        "HAUTE": {
            "action": "EXTENSION_COUVERTURE",
            "solution": "Étendre couverture via ajustement antenne et puissance",
            "etapes": [
                "Augmenter puissance émission (+3dB avec validation réglementaire)",
                "Ajuster tilt mécanique/électrique antenne (down-tilt -2°)",
                "Activer bande de fréquence basse si disponible",
                "Valider amélioration RSRP zone",
            ],
            "temps_estime": "1-3 heures",
            "automatisable": False,
            "outils": ["OMC", "Antenna Controller RET", "RF Tool"],
        },
        "MOYENNE": {
            "action": "PLANIFICATION_COUVERTURE",
            "solution": "Planifier amélioration couverture à court terme",
            "etapes": [
                "Analyser complaints utilisateurs géolocalisés",
                "Modéliser couverture avec outil prédictif",
                "Proposer sites Small Cell complémentaires",
            ],
            "temps_estime": "3-8 heures",
            "automatisable": False,
            "outils": ["Atoll RF Planning", "ArcGIS", "Customer Complaint Tool"],
        },
        "FAIBLE": {
            "action": "AUDIT_COUVERTURE",
            "solution": "Audit couverture périodique",
            "etapes": [
                "Drive test trimestriel zone",
                "Mise à jour heatmap couverture",
                "Rapport conformité ITU couverture",
            ],
            "temps_estime": "4-8 heures",
            "automatisable": False,
            "outils": ["TEMS", "Nemo Outdoor", "MapInfo"],
        },
    },

    # ── ÉQUIPEMENTS ────────────────────────────────────────────────

    "PANNE_BBU": {
        "TRES_HAUTE": {
            "action": "REMPLACEMENT_BBU_URGENCE",
            "solution": "Remplacer BBU défaillante — site complètement muet",
            "etapes": [
                "Confirmer panne BBU (alarmes critiques OMC: BBU HW Fault)",
                "Localiser BBU spare en stock régional",
                "Dispatcher technicien terrain sous 1h (SLA critique)",
                "Sauvegarder configuration depuis NMS avant intervention",
                "Déconnecter BBU défaillante (procédure ESD obligatoire)",
                "Installer BBU neuve et câbler eCPRI/CPRI vers RRH",
                "Restaurer configuration depuis backup NMS",
                "Valider synchronisation GPS/PTP et montée en service",
                "Tester tous les secteurs antennaires",
                "Fermer alarmes et mettre à jour CMDB",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["NMS (Network Management System)", "CMDB", "ESD Kit", "CPRI Tester"],
        },
        "HAUTE": {
            "action": "REDEMARRAGE_BBU",
            "solution": "Tenter redémarrage logiciel BBU avant remplacement",
            "etapes": [
                "Tenter redémarrage distant via NMS (cold reset)",
                "Attendre 5 min remontée service",
                "Si échec: redémarrage physique (power cycle)",
                "Si persistant: planifier remplacement sous 4h",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["NMS", "Remote Management"],
        },
        "MOYENNE": {
            "action": "DIAGNOSTIC_BBU",
            "solution": "Diagnostiquer BBU et identifier module défaillant",
            "etapes": [
                "Analyser alarmes hardware BBU (carte défaillante)",
                "Tester modules séparément (baseband, timing, power)",
                "Remplacer module défaillant uniquement",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["NMS", "Hardware Diagnostics Tool"],
        },
        "FAIBLE": {
            "action": "MAINTENANCE_BBU",
            "solution": "Maintenance préventive BBU",
            "etapes": [
                "Vérifier température interne (< 55°C)",
                "Contrôler ventilateurs",
                "Mettre à jour firmware si version obsolète",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["NMS", "Thermal Camera"],
        },
    },

    "PANNE_RRH": {
        "TRES_HAUTE": {
            "action": "REMPLACEMENT_RRH_URGENCE",
            "solution": "Remplacer RRH défaillant — secteurs sans émission",
            "etapes": [
                "Identifier RRH défaillant via alarmes OMC (RRH TX Fault)",
                "Vérifier lien eCPRI/CPRI avec BBU (BER < 10e-12)",
                "Couper alimentation secteur antennaire concerné",
                "Décâbler RRH défaillant en toute sécurité (hauteur > 20m)",
                "Installer RRH neuve (mêmes caractéristiques fréquentielles)",
                "Recâbler CPRI/eCPRI et alimentation DC -48V",
                "Configurer et activer depuis NMS",
                "Mesurer puissance sortie (VSWR < 1.5)",
                "Valider couverture secteur",
            ],
            "temps_estime": "3-6 heures",
            "automatisable": False,
            "outils": ["NMS", "VSWR Meter", "Power Meter", "Harnais sécurité hauteur"],
        },
        "HAUTE": {
            "action": "BYPASS_RRH",
            "solution": "Bypass RRH défaillant — rediriger trafic secteurs actifs",
            "etapes": [
                "Activer load balancing vers secteurs opérationnels",
                "Augmenter puissance secteurs adjacents (+2dB)",
                "Planifier remplacement RRH sous 24h",
            ],
            "temps_estime": "30 min",
            "automatisable": True,
            "outils": ["OMC", "Power Control Manager"],
        },
        "MOYENNE": {
            "action": "DIAGNOSTIC_RRH",
            "solution": "Diagnostiquer RRH — test VSWR et puissance",
            "etapes": [
                "Tester VSWR (Voltage Standing Wave Ratio)",
                "Mesurer puissance sortie par port antennaire",
                "Vérifier température RRH (< 65°C)",
                "Analyser erreurs eCPRI",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["VSWR Meter", "Power Meter", "eCPRI Analyzer"],
        },
        "FAIBLE": {
            "action": "INSPECTION_RRH",
            "solution": "Inspection préventive RRH",
            "etapes": [
                "Contrôle visuel connecteurs RF",
                "Vérifier fixations mécaniques",
                "Nettoyer connecteurs (contact cleaner)",
            ],
            "temps_estime": "1 heure",
            "automatisable": False,
            "outils": ["Kit nettoyage RF", "Torchon antistatique"],
        },
    },

    "PANNE_ANTENNE": {
        "TRES_HAUTE": {
            "action": "REMPLACEMENT_ANTENNE_URGENCE",
            "solution": "Remplacer antenne endommagée — couverture nulle secteur",
            "etapes": [
                "Confirmer panne antenne (VSWR > 3 → court-circuit ou rupture)",
                "Évaluer dégâts (tempête, vandalisme, corrosion)",
                "Commander antenne spare identique (même gain/bande)",
                "Intervention équipe spécialisée travaux en hauteur",
                "Démonter antenne endommagée",
                "Installer nouvelle antenne + câblage RF",
                "Configurer RET (Remote Electrical Tilt)",
                "Mesurer pattern rayonnement antenne",
                "Valider couverture secteur",
            ],
            "temps_estime": "4-8 heures",
            "automatisable": False,
            "outils": ["VSWR Meter", "Antenne de remplacement", "RET Controller", "Harnais sécurité"],
        },
        "HAUTE": {
            "action": "RECONFIGURATION_ANTENNE",
            "solution": "Reconfigurer antenne — ajuster paramètres électriques",
            "etapes": [
                "Vérifier état mécanique antenne (azimut, tilt)",
                "Réajuster orientation via RET",
                "Mesurer VSWR avant/après",
                "Tester couverture avec drive test",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["RET Controller", "Boussole", "VSWR Meter"],
        },
        "MOYENNE": {
            "action": "INSPECTION_ANTENNE",
            "solution": "Inspection antenne et câblage RF",
            "etapes": [
                "Inspection visuelle antenne depuis sol (jumelles)",
                "Vérifier connecteurs câbles jumper RF",
                "Mesurer pertes câble (objectif < 3dB/100m)",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Jumelles", "Cable Loss Meter", "VSWR Meter"],
        },
        "FAIBLE": {
            "action": "CONTROLE_ANTENNE",
            "solution": "Contrôle préventif antenne",
            "etapes": [
                "Vérifier alignement azimut/tilt selon planning RF",
                "Contrôler serrage connecteurs",
                "Mettre à jour fiche site dans CMDB",
            ],
            "temps_estime": "45 min",
            "automatisable": False,
            "outils": ["Boussole digitale", "CMDB Mobile App"],
        },
    },

    "PANNE_TRANSPORT": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_TRANSPORT_URGENTE",
            "solution": "Restaurer lien transport — site complètement isolé",
            "etapes": [
                "Identifier type liaison: fibre optique, micro-ondes, satellite",
                "Vérifier alarmes transmission (Los Of Signal, AIS, RDI)",
                "Tester continuité fibre avec OTDR si liaison optique",
                "Basculer sur lien de secours (protection 1+1 si disponible)",
                "Configurer liaison micro-ondes de backup si disponible",
                "Contacter fournisseur transmission si liaison louée",
                "Activer 4G/LTE comme transport temporaire (LTE Backhaul)",
                "Documenter SLA violation et ouvrir incident avec opérateur transport",
            ],
            "temps_estime": "1-4 heures",
            "automatisable": True,
            "outils": ["OTDR Viavi", "MW Analyzer", "NMS Transport", "Cisco NSO"],
        },
        "HAUTE": {
            "action": "BASCULEMENT_TRANSPORT",
            "solution": "Basculer sur lien transport de secours",
            "etapes": [
                "Activer protection automatique transport (APS)",
                "Vérifier intégrité lien backup",
                "Notifier équipe transport pour réparation",
                "Monitorer qualité lien backup",
            ],
            "temps_estime": "20-45 min",
            "automatisable": True,
            "outils": ["NMS Transport", "Protection Switch Manager"],
        },
        "MOYENNE": {
            "action": "DIAGNOSTIC_TRANSPORT",
            "solution": "Diagnostiquer lien transport et isoler défaut",
            "etapes": [
                "Analyser compteurs erreurs BER/ES/SES",
                "Tester qualité signal transmission",
                "Identifier segment défaillant",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["BER Tester", "OTDR", "Spectrum Analyzer MW"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_TRANSPORT",
            "solution": "Surveillance proactive qualité transport",
            "etapes": [
                "Activer monitoring BER/latence transport",
                "Configurer alertes dégradation",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["NMS Transport", "Grafana"],
        },
    },

    "PANNE_ALIMENTATION": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_ALIMENTATION_URGENTE",
            "solution": "Restaurer alimentation — équipements hors service",
            "etapes": [
                "Vérifier état UPS (Self-test, charge batterie)",
                "Basculer sur alimentation UPS si non automatique",
                "Démarrer groupe électrogène (GE) si panne > 30 min",
                "Vérifier tension DC -48V (±2% tolérance)",
                "Contacter STEG (Société Tunisienne Electricité et Gaz) urgence",
                "Prioriser équipements actifs (éteindre non-critiques)",
                "Surveiller température batterie UPS (< 40°C)",
                "Planifier remplacement UPS si batterie < 60% capacité",
            ],
            "temps_estime": "30-120 min",
            "automatisable": False,
            "outils": ["APC PowerChute", "BMS", "Multimètre Fluke", "Clamp Meter"],
        },
        "HAUTE": {
            "action": "INTERVENTION_ELECTRIQUE",
            "solution": "Intervention électrique — diagnostic et réparation",
            "etapes": [
                "Vérifier disjoncteurs (tableau général + départs)",
                "Mesurer tension entrée STEG (380V triphasé)",
                "Inspecter câblage alimentation",
                "Remplacer fusibles/disjoncteurs défaillants",
                "Tester mise sous tension progressive",
            ],
            "temps_estime": "1-3 heures",
            "automatisable": False,
            "outils": ["Multimètre", "Pinces ampèremétriques", "Analyseur réseau électrique"],
        },
        "MOYENNE": {
            "action": "MAINTENANCE_ELECTRIQUE",
            "solution": "Maintenance préventive installation électrique",
            "etapes": [
                "Vérifier état batterie UPS (test décharge)",
                "Contrôler connexions borniers",
                "Mesurer résistance terre (< 5 ohms)",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["Testeur UPS", "Telluromètre"],
        },
        "FAIBLE": {
            "action": "CONTROLE_PREVENTIF_ELECTRIQUE",
            "solution": "Contrôle préventif alimentation",
            "etapes": [
                "Vérifier autonomie UPS (test 30 min)",
                "Planifier remplacement batteries si > 3 ans",
            ],
            "temps_estime": "1 heure",
            "automatisable": False,
            "outils": ["APC PowerChute", "Testeur batterie"],
        },
    },

    # ── CŒUR RÉSEAU ────────────────────────────────────────────────

    "PANNE_AMF": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_AMF_URGENTE",
            "solution": "Restaurer AMF (Access & Mobility Management Function)",
            "etapes": [
                "Identifier pod AMF défaillant (kubectl get pods -n 5g-core)",
                "Analyser logs AMF (kubectl logs -f amf-pod -n 5g-core)",
                "Vérifier connectivité NRF (Network Repository Function)",
                "Basculer vers instance AMF redondante (GEO-redundancy)",
                "Redémarrer pod AMF si software issue (kubectl rollout restart)",
                "Vérifier interfaces N1/N2/N11 avec gNB et SMF",
                "Valider NAS (Non-Access Stratum) signaling",
                "Tester enregistrement UE (Registration Request OK)",
                "Analyser latence enregistrement (< 50ms)",
            ],
            "temps_estime": "20-60 min",
            "automatisable": True,
            "outils": ["Kubernetes kubectl", "Prometheus", "Jaeger Tracing", "5G Core Dashboard"],
        },
        "HAUTE": {
            "action": "FAILOVER_AMF",
            "solution": "Basculement AMF vers instance redondante",
            "etapes": [
                "Déclencher failover AMF automatique",
                "Vérifier reprise sessions actives",
                "Analyser cause panne AMF primaire",
            ],
            "temps_estime": "15-30 min",
            "automatisable": True,
            "outils": ["Kubernetes", "Service Mesh (Istio)"],
        },
        "MOYENNE": {
            "action": "DIAGNOSTIC_AMF",
            "solution": "Diagnostiquer AMF et interfaces 5G Core",
            "etapes": [
                "Analyser métriques AMF (CPU/RAM/sessions)",
                "Vérifier interfaces SBI (Service Based Interface)",
                "Tester connectivité NRF/SMF/UDM",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Prometheus", "Grafana", "Wireshark HTTP/2"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_AMF",
            "solution": "Monitoring AMF proactif",
            "etapes": [
                "Configurer alertes métriques AMF",
                "Vérifier capacité (sessions actives vs max)",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["Prometheus/Grafana", "5G KPI Dashboard"],
        },
    },

    "PANNE_UPF": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_UPF_URGENTE",
            "solution": "Restaurer UPF — tout le trafic data interrompu",
            "etapes": [
                "Confirmer panne UPF (N4 session reports: no GTP-U traffic)",
                "Analyser logs UPF (erreurs PDU session establishment)",
                "Basculer vers UPF redondant (N9 interface)",
                "Vérifier interfaces N3 (vers gNB), N6 (vers Internet), N9 (inter-UPF)",
                "Contrôler GTP-U tunnels (objectif: 0 tunnel drop)",
                "Vérifier règles PFCP depuis SMF",
                "Tester connectivité data utilisateur (ping 8.8.8.8)",
                "Valider débit post-restauration",
            ],
            "temps_estime": "15-45 min",
            "automatisable": True,
            "outils": ["Wireshark GTP", "PFCP Analyzer", "kubectl", "iPerf3"],
        },
        "HAUTE": {
            "action": "FAILOVER_UPF",
            "solution": "Basculer UPF vers instance secondaire",
            "etapes": [
                "Déclencher failover UPF",
                "Redistribuer sessions PDU",
                "Vérifier continuité service data",
            ],
            "temps_estime": "10-20 min",
            "automatisable": True,
            "outils": ["SMF Console", "Kubernetes"],
        },
        "MOYENNE": {
            "action": "OPTIMISATION_UPF",
            "solution": "Optimiser UPF — performances dégradées",
            "etapes": [
                "Analyser CPU/mémoire UPF",
                "Vérifier règles QoS/PFCP",
                "Optimiser routage N6",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Prometheus", "tcpdump N6", "Grafana"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_UPF",
            "solution": "Surveillance trafic UPF",
            "etapes": [
                "Monitorer débit N6 (upstream/downstream)",
                "Configurer alertes saturation",
            ],
            "temps_estime": "10 min",
            "automatisable": True,
            "outils": ["Prometheus", "Grafana"],
        },
    },

    "PANNE_SMF": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_SMF_URGENTE",
            "solution": "Restaurer SMF — établissement sessions PDU impossible",
            "etapes": [
                "Analyser logs SMF (PDU Session Establishment Reject)",
                "Vérifier interface N4 vers UPF",
                "Basculer vers SMF redondant",
                "Valider enregistrement SMF dans NRF",
                "Tester création session PDU (type IPv4/IPv6)",
                "Vérifier QoS flows (5QI mapping)",
            ],
            "temps_estime": "20-45 min",
            "automatisable": True,
            "outils": ["kubectl", "NRF Console", "Wireshark SBI", "Prometheus"],
        },
        "HAUTE": {
            "action": "REDEMARRAGE_SMF",
            "solution": "Redémarrer SMF et vérifier sessions",
            "etapes": [
                "Backup sessions actives SMF",
                "Redémarrer pod SMF",
                "Restaurer sessions",
                "Valider QoS profiles",
            ],
            "temps_estime": "15-30 min",
            "automatisable": True,
            "outils": ["kubectl", "SMF Session Backup Tool"],
        },
        "MOYENNE": {
            "action": "DIAGNOSTIC_SMF",
            "solution": "Diagnostiquer SMF et interfaces SBI",
            "etapes": [
                "Analyser interface N4/N7/N10/N11",
                "Vérifier politiques QoS",
                "Tester avec UE de test",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Wireshark HTTP/2 SBI", "Prometheus"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_SMF",
            "solution": "Monitoring SMF — sessions et QoS",
            "etapes": [
                "Monitorer taux succès PDU session",
                "Vérifier capacité sessions actives",
            ],
            "temps_estime": "10 min",
            "automatisable": True,
            "outils": ["5G Core KPI Dashboard"],
        },
    },

    "PANNE_CORE_SLICE": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_SLICE_URGENTE",
            "solution": "Restaurer Network Slice défaillant",
            "etapes": [
                "Identifier slice défaillant (S-NSSAI: SST/SD)",
                "Vérifier NSSF (Network Slice Selection Function)",
                "Analyser ressources allouées slice (CPU/RAM/bande passante)",
                "Redéployer slice via MANO (Management & Orchestration)",
                "Vérifier isolation entre slices",
                "Tester connectivité UE sur slice restauré",
                "Valider SLA slice (latence, débit, disponibilité)",
            ],
            "temps_estime": "30-90 min",
            "automatisable": True,
            "outils": ["MANO OSM/ONAP", "kubectl", "NSSF Console", "Slice Monitor"],
        },
        "HAUTE": {
            "action": "MIGRATION_SLICE",
            "solution": "Migrer trafic vers slice alternatif",
            "etapes": [
                "Basculer UE vers slice de secours",
                "Vérifier QoS maintenu",
                "Redéployer slice défaillant",
            ],
            "temps_estime": "20-40 min",
            "automatisable": True,
            "outils": ["MANO", "Slice Manager"],
        },
        "MOYENNE": {
            "action": "OPTIMISATION_SLICE",
            "solution": "Optimiser ressources slice dégradé",
            "etapes": [
                "Analyser utilisation ressources slice",
                "Redimensionner CPU/RAM slice",
                "Valider performance",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["MANO", "Kubernetes HPA"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_SLICE",
            "solution": "Monitoring slices réseau",
            "etapes": [
                "Monitorer KPIs par slice",
                "Vérifier isolation slices",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["Slice KPI Dashboard"],
        },
    },

    # ── TRANSMISSION ────────────────────────────────────────────────

    "COUPURE_FIBRE": {
        "TRES_HAUTE": {
            "action": "REPARATION_FIBRE_URGENTE",
            "solution": "Réparer fibre coupée — site isolé",
            "etapes": [
                "Localiser coupure avec OTDR (précision ±1m)",
                "Identifier segment affecté sur carte GIS réseau fibre",
                "Dépêcher équipe civiworks sous 2h",
                "Basculer sur lien micro-ondes backup si disponible",
                "Activer 4G/LTE comme transport temporaire",
                "Effectuer soudure fibre (perte < 0.1dB/épissure)",
                "Mesurer atténuation post-réparation",
                "Valider remontée service",
                "Signaler à Tunisie Telecom/TOPNET si infrastructure partagée",
            ],
            "temps_estime": "2-8 heures",
            "automatisable": False,
            "outils": ["OTDR Viavi/EXFO", "Soudeuse fibre Fujikura", "GIS Network Map", "Power Meter"],
        },
        "HAUTE": {
            "action": "BYPASS_FIBRE",
            "solution": "Bypass fibre — activer lien alternatif",
            "etapes": [
                "Activer protection fibre 1+1 si disponible",
                "Configurer micro-ondes de backup",
                "Informer équipe civiworks pour réparation",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["NMS Transport", "MW Controller"],
        },
        "MOYENNE": {
            "action": "LOCALISATION_DEFAUT_FIBRE",
            "solution": "Localiser et préparer réparation fibre",
            "etapes": [
                "Test OTDR pour localiser défaut",
                "Planifier intervention civiworks",
                "Vérifier droits accès terrain",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["OTDR", "GIS Map"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_FIBRE",
            "solution": "Monitoring continu infrastructure fibre",
            "etapes": [
                "Activer OTDR monitoring automatique",
                "Configurer alertes dégradation signal",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["OTDR Monitor", "NMS Transport"],
        },
    },

    "SATURATION_BACKHAUL": {
        "TRES_HAUTE": {
            "action": "DELESTAGE_BACKHAUL_URGENT",
            "solution": "Délester backhaul saturé — QoS dégradée sur tout le site",
            "etapes": [
                "Mesurer utilisation backhaul (objectif: < 80%)",
                "Activer QoS priorisation (voix > vidéo > data)",
                "Activer compression données si disponible",
                "Activer lien micro-ondes supplémentaire si disponible",
                "Limiter débit par UE (rate limiting temporaire)",
                "Contacter équipe transport pour augmentation capacité",
                "Planifier upgrade backhaul (10G → 100G)",
            ],
            "temps_estime": "20-45 min",
            "automatisable": True,
            "outils": ["MPLS QoS Manager", "Bandwidth Manager", "NMS Transport"],
        },
        "HAUTE": {
            "action": "OPTIMISATION_BACKHAUL",
            "solution": "Optimiser trafic backhaul",
            "etapes": [
                "Activer Traffic Engineering MPLS",
                "Prioriser trafic signalisation",
                "Activer lien backup partiel",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["MPLS Controller", "NMS"],
        },
        "MOYENNE": {
            "action": "ANALYSE_CAPACITE_BACKHAUL",
            "solution": "Analyser et planifier upgrade capacité backhaul",
            "etapes": [
                "Analyser tendance utilisation 30 jours",
                "Identifier heures de pic",
                "Soumettre demande upgrade capacité",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["Capacity Planning Tool", "Grafana"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_BACKHAUL",
            "solution": "Monitoring capacité backhaul",
            "etapes": [
                "Configurer alertes utilisation > 70%",
                "Rapport hebdomadaire utilisation",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["Grafana", "NMS Transport"],
        },
    },

    "LATENCE_ELEVEE": {
        "TRES_HAUTE": {
            "action": "REDUCTION_LATENCE_URGENTE",
            "solution": "Réduire latence critique — SLA 5G violé (> 20ms)",
            "etapes": [
                "Mesurer latence bout-en-bout (ping, iPerf3 UDP)",
                "Identifier segment latent: radio / transport / core",
                "Radio: vérifier scheduler TTI (1ms en NR)",
                "Transport: vérifier latence backhaul (objectif < 5ms)",
                "Core: déployer MEC (Multi-access Edge Computing) si disponible",
                "Optimiser routing (éviter aller-retour datacenter distant)",
                "Activer URLLC slice si disponible (latence < 1ms)",
                "Valider SLA respecté post-optimisation",
            ],
            "temps_estime": "30-90 min",
            "automatisable": True,
            "outils": ["iPerf3", "Traceroute", "MEC Platform", "URLLC Manager"],
        },
        "HAUTE": {
            "action": "OPTIMISATION_LATENCE",
            "solution": "Optimiser chemins de données pour réduire latence",
            "etapes": [
                "Analyser routing data plane",
                "Activer local breakout UPF",
                "Optimiser paramètres scheduler",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["UPF Local Breakout", "OMC Scheduler"],
        },
        "MOYENNE": {
            "action": "ANALYSE_LATENCE",
            "solution": "Analyser sources de latence",
            "etapes": [
                "Mesurer latence par segment",
                "Identifier goulot étranglement",
                "Proposer plan optimisation",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["Network Analyzer", "Wireshark", "Traceroute"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_LATENCE",
            "solution": "Monitoring latence avec alertes SLA",
            "etapes": [
                "Configurer sondes latence continues",
                "Alertes si latence > seuil SLA",
            ],
            "temps_estime": "15 min",
            "automatisable": True,
            "outils": ["Prometheus", "Grafana SLA Dashboard"],
        },
    },

    # ── SÉCURITÉ & LOGICIEL ────────────────────────────────────────

    "INTRUSION_RESEAU": {
        "TRES_HAUTE": {
            "action": "ISOLEMENT_URGENCE_SECURITE",
            "solution": "Isoler équipement compromis — incident sécurité critique",
            "etapes": [
                "ISOLER IMMÉDIATEMENT l'équipement/segment compromis",
                "Alerter SOC (Security Operations Center) — P1 incident",
                "Bloquer IP/subnet attaquant sur firewall périmétrique",
                "Capturer trafic réseau (pcap) pour forensic",
                "Changer TOUS les credentials (root, admin, SNMP)",
                "Auditer logs accès (qui/quand/depuis où)",
                "Notifier ANSI Tunisie (Agence Nationale Sécurité Informatique)",
                "Restaurer depuis backup sain vérifié",
                "Post-incident: analyse forensic complète",
            ],
            "temps_estime": "2-8 heures",
            "automatisable": False,
            "outils": ["SIEM Splunk/QRadar", "Wireshark", "Firewall Palo Alto/Fortinet", "Forensic Tools"],
        },
        "HAUTE": {
            "action": "REMEDIATION_SECURITE",
            "solution": "Appliquer mesures correctives sécurité",
            "etapes": [
                "Bloquer source attaque",
                "Appliquer patches sécurité critiques",
                "Renforcer règles firewall/IPS",
                "Scanner vulnérabilités",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": True,
            "outils": ["Nessus/Qualys", "IPS Manager", "Patch Manager"],
        },
        "MOYENNE": {
            "action": "AUDIT_SECURITE",
            "solution": "Audit sécurité équipements réseau",
            "etapes": [
                "Scanner ports et services exposés",
                "Réviser politiques accès",
                "Mettre à jour signatures IDS/IPS",
            ],
            "temps_estime": "3-6 heures",
            "automatisable": True,
            "outils": ["Nmap", "Nessus", "SIEM"],
        },
        "FAIBLE": {
            "action": "RENFORCEMENT_SECURITE",
            "solution": "Renforcement préventif sécurité réseau",
            "etapes": [
                "Vérifier conformité politique sécurité",
                "Renouveler certificats proches expiration",
                "Planifier pentest trimestriel",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["Security Compliance Tool", "Certificate Manager"],
        },
    },

    "MISE_A_JOUR_ECHOUEE": {
        "TRES_HAUTE": {
            "action": "ROLLBACK_URGENCE",
            "solution": "Rollback version précédente stable — service dégradé post-MAJ",
            "etapes": [
                "Confirmer version précédente stable disponible",
                "Créer snapshot état actuel (même si dégradé)",
                "Déclencher rollback via CI/CD pipeline ou NMS",
                "Vérifier intégrité tous services post-rollback",
                "Valider métriques retour à la normale",
                "Analyser cause échec MAJ (logs CI/CD)",
                "Documenter et planifier nouvelle tentative MAJ",
            ],
            "temps_estime": "20-60 min",
            "automatisable": True,
            "outils": ["Ansible/Puppet", "CI/CD Jenkins/GitLab", "NMS Version Manager"],
        },
        "HAUTE": {
            "action": "ROLLBACK_PARTIEL",
            "solution": "Rollback service spécifique défaillant",
            "etapes": [
                "Identifier composant affecté par MAJ",
                "Rollback composant uniquement",
                "Valider intégration avec reste du système",
            ],
            "temps_estime": "30-60 min",
            "automatisable": True,
            "outils": ["Kubernetes Rollback", "Ansible"],
        },
        "MOYENNE": {
            "action": "CORRECTION_MAJ",
            "solution": "Corriger MAJ défaillante et relancer",
            "etapes": [
                "Analyser logs erreur MAJ",
                "Identifier dépendances manquantes",
                "Corriger playbook et relancer",
            ],
            "temps_estime": "1-3 heures",
            "automatisable": True,
            "outils": ["Ansible", "Git", "CI/CD Logs"],
        },
        "FAIBLE": {
            "action": "PLANIFICATION_MAJ",
            "solution": "Planifier MAJ en fenêtre maintenance",
            "etapes": [
                "Tester MAJ en environnement staging",
                "Planifier fenêtre maintenance nuit",
                "Préparer procédure rollback",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["Staging Environment", "Change Management Tool"],
        },
    },

    "SURCHARGE_CELLULE": {
        "TRES_HAUTE": {
            "action": "DELESTAGE_CELLULE_URGENT",
            "solution": "Délestage immédiat cellule saturée (> 95% PRB)",
            "etapes": [
                "Vérifier utilisation PRB (Physical Resource Block > 90%)",
                "Activer load balancing vers cellules adjacentes",
                "Activer Carrier Aggregation (CA) si disponible",
                "Déployer Small Cell d'urgence si événement planifié",
                "Activer QoS priorisation (voix prioritaire sur data)",
                "Réduire TTI pour augmenter capacité scheduler",
                "Coordonner NOC pour support régional",
                "Planifier renforcement capacité",
            ],
            "temps_estime": "15-30 min",
            "automatisable": True,
            "outils": ["OMC", "QoS Manager", "Small Cell Controller", "CA Manager"],
        },
        "HAUTE": {
            "action": "REEQUILIBRAGE_CELLULE",
            "solution": "Rééquilibrer charge entre cellules",
            "etapes": [
                "Activer MLB (Mobility Load Balancing) SON",
                "Configurer handover basé sur charge",
                "Augmenter capacité via MIMO avancé",
            ],
            "temps_estime": "20-45 min",
            "automatisable": True,
            "outils": ["SON Engine", "OMC", "MIMO Manager"],
        },
        "MOYENNE": {
            "action": "OPTIMISATION_CAPACITE",
            "solution": "Optimiser capacité cellule",
            "etapes": [
                "Analyser profil trafic (heure pic, type contenu)",
                "Activer optimisations scheduler",
                "Planifier upgrade équipements",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": True,
            "outils": ["OMC Analytics", "Traffic Analyzer"],
        },
        "FAIBLE": {
            "action": "SURVEILLANCE_CAPACITE",
            "solution": "Monitoring capacité cellule",
            "etapes": [
                "Configurer alertes PRB > 70%",
                "Rapport hebdomadaire utilisation",
            ],
            "temps_estime": "10 min",
            "automatisable": True,
            "outils": ["OMC Dashboard", "Grafana"],
        },
    },

    # ── INFRASTRUCTURE ─────────────────────────────────────────────

    "PANNE_CLIMATISATION": {
        "TRES_HAUTE": {
            "action": "REFROIDISSEMENT_URGENCE",
            "solution": "Refroidissement urgence salle technique — risque surchauffe équipements",
            "etapes": [
                "Vérifier température salle (alarme si > 35°C)",
                "Éteindre équipements non-critiques pour réduire chaleur",
                "Installer climatisation mobile d'urgence",
                "Augmenter ventilation naturelle (portes si sécurisé)",
                "Contacter dépanneur clim urgence sous 2h",
                "Monitorer température équipements (seuil critique BBU: 55°C)",
                "Si température > 45°C: éteindre équipements par priorité",
                "Rétablir clim et vérifier retour température normale avant remise en service",
            ],
            "temps_estime": "1-4 heures",
            "automatisable": False,
            "outils": ["Thermomètre infrarouge", "Clim mobile", "BMS (Building Management System)"],
        },
        "HAUTE": {
            "action": "REMPLACEMENT_CLIM",
            "solution": "Remplacer unité climatisation défaillante",
            "etapes": [
                "Diagnostiquer panne clim (compresseur, gaz, électronique)",
                "Basculer sur clim redondante si disponible",
                "Commander technicien réfrigération",
                "Monitorer température en continu",
            ],
            "temps_estime": "2-6 heures",
            "automatisable": False,
            "outils": ["Manifold de charge", "Détecteur fuite gaz", "BMS"],
        },
        "MOYENNE": {
            "action": "MAINTENANCE_CLIM",
            "solution": "Maintenance préventive climatisation",
            "etapes": [
                "Nettoyer filtres climatisation",
                "Vérifier niveau gaz frigorigène",
                "Contrôler performances refroidissement",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["Kit maintenance clim"],
        },
        "FAIBLE": {
            "action": "INSPECTION_CLIM",
            "solution": "Inspection préventive climatisation",
            "etapes": [
                "Vérifier températures salle (< 25°C nominal)",
                "Contrôler filtres (nettoyage si colmaté)",
                "Planifier maintenance préventive semestrielle",
            ],
            "temps_estime": "30 min",
            "automatisable": False,
            "outils": ["Thermomètre", "BMS"],
        },
    },

    "INONDATION_SITE": {
        "TRES_HAUTE": {
            "action": "EVACUATION_URGENCE_SITE",
            "solution": "Sécuriser site inondé — protection équipements",
            "etapes": [
                "COUPER ALIMENTATION GÉNÉRALE si eau atteint équipements (sécurité électrique)",
                "Alerter équipe sécurité et management",
                "Pomper eau avec pompe de relevage d'urgence",
                "Mettre équipements sur racks surélevés si possible",
                "Installer générateur surélevé si GE compromis",
                "Évaluer dégâts matériels post-inondation",
                "Tester équipements après séchage complet (48h minimum)",
                "Contacter assurance et préparer rapport sinistre",
                "Renforcer étanchéité pour prévenir récidive",
            ],
            "temps_estime": "4-48 heures",
            "automatisable": False,
            "outils": ["Pompe évacuation eau", "Détecteur humidité", "Mégohmmètre isolation"],
        },
        "HAUTE": {
            "action": "PROTECTION_SITE_INONDATION",
            "solution": "Protéger équipements des infiltrations d'eau",
            "etapes": [
                "Surélever équipements critiques",
                "Colmater infiltrations avec produit étanchéité",
                "Activer pompe de relevage",
                "Transférer équipements portables vers site sec",
            ],
            "temps_estime": "2-6 heures",
            "automatisable": False,
            "outils": ["Pompe de relevage", "Matériaux étanchéité"],
        },
        "MOYENNE": {
            "action": "INSPECTION_POST_PLUIE",
            "solution": "Inspection site après fortes pluies",
            "etapes": [
                "Vérifier infiltrations eau",
                "Contrôler état toiture et joints",
                "Vérifier drains et évacuations",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Lampe torche", "Hygromètre"],
        },
        "FAIBLE": {
            "action": "PREVENTION_INONDATION",
            "solution": "Prévention inondation — inspection étanchéité",
            "etapes": [
                "Inspecter étanchéité site avant saison pluies",
                "Vérifier fonctionnement pompe relevage",
                "Installer capteurs humidité si absent",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Capteur humidité", "Pompe de relevage"],
        },
    },

    "VANDALISME_PYLONE": {
        "TRES_HAUTE": {
            "action": "SECURISATION_URGENCE_PYLONE",
            "solution": "Sécuriser site vandalisé et restaurer service",
            "etapes": [
                "Sécuriser périmètre site (danger: câbles HT coupés)",
                "Alerter police/gendarmerie (dépôt plainte obligatoire)",
                "Évaluer dégâts: câbles volés, équipements cassés, antennes arrachées",
                "Couper alimentation si risque électrique",
                "Photographier dégâts pour assurance",
                "Commencer réparation dès site sécurisé",
                "Installer caméras de surveillance si absent",
                "Renforcer clôture et serrures anti-effraction",
                "Coordination avec services de sécurité locaux",
            ],
            "temps_estime": "4-24 heures",
            "automatisable": False,
            "outils": ["Matériel remplacement câbles", "Caméras surveillance", "Détecteur intrusion"],
        },
        "HAUTE": {
            "action": "REPARATION_VANDALISME",
            "solution": "Réparer équipements vandalisés",
            "etapes": [
                "Évaluer dégâts complets",
                "Commander pièces manquantes",
                "Effectuer réparation",
                "Renforcer sécurité physique site",
            ],
            "temps_estime": "4-12 heures",
            "automatisable": False,
            "outils": ["Outillage réseau", "Système anti-intrusion"],
        },
        "MOYENNE": {
            "action": "INSPECTION_SECURITE",
            "solution": "Inspection sécurité physique site",
            "etapes": [
                "Vérifier intégrité clôture et accès",
                "Contrôler état cadenas et serrures",
                "Vérifier caméras de surveillance",
            ],
            "temps_estime": "1-2 heures",
            "automatisable": False,
            "outils": ["Checklist sécurité physique"],
        },
        "FAIBLE": {
            "action": "AUDIT_SECURITE_PHYSIQUE",
            "solution": "Audit sécurité physique préventif",
            "etapes": [
                "Audit trimestriel sécurité site",
                "Vérifier fonctionnement alarmes",
                "Mettre à jour procédures sécurité",
            ],
            "temps_estime": "2-3 heures",
            "automatisable": False,
            "outils": ["Checklist audit sécurité", "Rapport inspection"],
        },
    },

    "PANNE_GROUPE_ELECTROGENE": {
        "TRES_HAUTE": {
            "action": "RESTAURATION_GE_URGENTE",
            "solution": "Restaurer groupe électrogène — autonomie critique",
            "etapes": [
                "Vérifier niveau carburant (diesel/gaz)",
                "Contrôler batterie démarrage GE (12/24V)",
                "Tenter démarrage manuel GE",
                "Si échec: appeler technicien GE urgence sous 1h",
                "Ravitaillement carburant en urgence si vide",
                "Surveiller autonomie UPS (estimée en minutes)",
                "Prioriser extinction équipements non-critiques",
                "Contacter fournisseur GE pour réparation rapide",
                "Louer GE mobile si panne longue durée",
            ],
            "temps_estime": "1-4 heures",
            "automatisable": False,
            "outils": ["Multimètre", "Testeur batterie", "GE mobile location"],
        },
        "HAUTE": {
            "action": "REPARATION_GE",
            "solution": "Réparer groupe électrogène",
            "etapes": [
                "Diagnostiquer panne (démarrage, alternateur, régulateur)",
                "Remplacer composant défaillant",
                "Tester GE en charge",
            ],
            "temps_estime": "2-6 heures",
            "automatisable": False,
            "outils": ["Outillage mécanique", "Testeur alternateur"],
        },
        "MOYENNE": {
            "action": "MAINTENANCE_GE",
            "solution": "Maintenance préventive groupe électrogène",
            "etapes": [
                "Vidange huile moteur",
                "Remplacement filtres (air, huile, carburant)",
                "Test démarrage automatique (ATS)",
                "Vérifier niveau carburant (remplir si < 50%)",
            ],
            "temps_estime": "2-4 heures",
            "automatisable": False,
            "outils": ["Kit maintenance GE", "Testeur ATS"],
        },
        "FAIBLE": {
            "action": "CONTROLE_GE",
            "solution": "Contrôle périodique groupe électrogène",
            "etapes": [
                "Test démarrage mensuel (15 min en charge)",
                "Vérifier niveau carburant/huile",
                "Mettre à jour registre maintenance",
            ],
            "temps_estime": "1 heure",
            "automatisable": False,
            "outils": ["Registre maintenance GE"],
        },
    },
}


# ══════════════════════════════════════════════════════════════════
# 4. DESCRIPTIONS RÉELLES PAR TYPE DE PANNE
# ══════════════════════════════════════════════════════════════════

DESCRIPTIONS_PAR_TYPE = {
    "PERTE_SIGNAL_5G": [
        "signal 5G NR SA perdu RSRP inférieur 105 dBm zone complète",
        "UE ne peut pas s enregistrer gNB hors service alarmes critiques",
        "perte synchronisation PTP cellule 5G muette aucun abonné connecté",
        "NSA fallback impossible LTE anchor défaillant couverture nulle",
        "beam management échoué beamforming inactif zone sans signal",
        "RRC connection reject massif gNB surchargé signal absent",
        "NR SA registration failure N1 interface down aucun service",
    ],
    "DEGRADATION_DEBIT": [
        "débit descendant inférieur 50 Mbps SLA violé abonnés 5G",
        "throughput réduit 90 pourcent HARQ erreurs élevées scheduler",
        "carrier aggregation inactive débit limité bande unique",
        "256 QAM non activé SINR insuffisant débit dégradé",
        "backhaul saturé goulot étranglement débit cellule limité",
        "UPF surcharé latence data élevée débit utilisateurs réduit",
        "PRB utilisation 95 pourcent scheduler saturé abonnés impactés",
    ],
    "HANDOVER_ECHEC": [
        "handover failure rate 5 pourcent appels chutent mobilité",
        "X2 interface down inter gNB HO impossible utilisateurs bloqués",
        "A3 event mal configuré HO trop tardif déconnexions fréquentes",
        "DAPS handover échec 5G SA mobilité dégradée zone frontière",
        "PCI confusion cellules voisines HO vers mauvaise cellule",
        "TTT trop long mobilité rapide déconnexions autoroute TGM",
        "RRC reestablishment échec après HO perdu données en cours",
    ],
    "INTERFERENCE_COCANAL": [
        "SINR inférieur 0 dB interférence co canal bande n78",
        "BLER élevé 30 pourcent interférence cellule voisine même PCI",
        "brouillage externe détecté bande 3500 MHz source inconnue",
        "CQI bas 4 interférence persistante throughput réduit",
        "intermodulation détectée entre émetteurs voisins distorsion signal",
        "ICIC insuffisant surcharge spectrale zone dense interférences",
        "IRC non activé rejection combining absent SINR dégradé",
    ],
    "COUVERTURE_INSUFFISANTE": [
        "trou couverture 5G quartier résidentiel dense plaintes abonnés",
        "RSRP inférieur 110 dBm bâtiment profond indoor coverage null",
        "zone industrielle aucune couverture 5G NR production arrêtée",
        "couverture route nationale insuffisante appels chutent autoroute",
        "antenne orientation incorrecte après tempête couverture réduite",
        "fréquence haute n78 pénétration insuffisante bâtiments zone dense",
        "expansion résidentielle nouvelle zone non couverte réclamations",
    ],
    "PANNE_BBU": [
        "BBU hors service alarme critique HW Fault OMC site muet",
        "BBU redémarrage spontané perte sessions actives service coupé",
        "carte baseband défaillante LED rouge secteurs inactifs",
        "synchronisation GPS perdue BBU dérive timing cellule hors service",
        "surchauffe BBU ventilateur défaillant température 60 degrés",
        "firmware corrompu BBU ne démarre plus maintenance urgente",
        "alimentation DC 48V BBU instable ondulations éteint redémarre",
    ],
    "PANNE_RRH": [
        "RRH TX défaillant secteur nord sans émission VSWR élevé",
        "lien eCPRI coupé BBU RRH communication perdue antenne inactive",
        "RRH surchauffe 70 degrés protection thermique déclenchée",
        "puissance sortie RRH chutée 20 dB couverture réduite secteur",
        "connecteur CPRI endommagé corrosion signal perdu communication",
        "RRH water ingress infiltration eau dégâts composants internes",
        "port antennaire RRH défaillant VSWR 4 couverture secteur nulle",
    ],
    "PANNE_ANTENNE": [
        "antenne endommagée tempête azimut dévié 45 degrés couverture",
        "câble jumper RF rupture VSWR 6 secteur sans émission",
        "antenne RET hors service tilt électrique bloqué optimisation",
        "corrosion connecteurs N antenne dégradation signal mesurée",
        "antenne MIMO passive éléments défaillants gain réduit",
        "foudre antenne grillée couverture secteur perdue remplacement",
        "perte passive câblage RF 8 dB couverture réduite significative",
    ],
    "PANNE_TRANSPORT": [
        "lien transport fibre coupé site complètement isolé aucun service",
        "micro ondes backhaul link down pluie forte atténuation",
        "routeur transport panne OS crash lien S1 X2 perdu",
        "saturation lien transport 1Gbps trafic 5G dépasse capacité",
        "latence transport 50 ms backhaul dégradé SLA violé",
        "protection transport APS non active panne lien principal fatal",
        "BGP session down peering transport perdu routage impossible",
    ],
    "PANNE_ALIMENTATION": [
        "coupure STEG prolongée UPS épuisé équipements éteints",
        "batterie UPS défaillante autonomie 5 minutes insuffisant",
        "disjoncteur principal déclenché surcharge site hors tension",
        "tension DC 48V instable équipements redémarrent continuellement",
        "câble alimentation rongé par animaux court circuit site hors service",
        "redresseur défaillant batterie non chargée UPS criticale",
        "coupure foudre protection parafoudre déclenchée site éteint",
    ],
    "PANNE_AMF": [
        "AMF pod crash kubernetes enregistrement UE impossible réseau muet",
        "AMF N2 interface down gNB ne peut pas connecter core réseau",
        "surcharge AMF 100000 sessions limite dépassée nouveaux UE rejetés",
        "AMF NRF registration perdue service discovery défaillant",
        "NAS signaling timeout AMF surchargé abonnés non joignables",
        "AMF failover raté instance redondante indisponible service coupé",
        "AMF certificate expiré TLS handshake échec connexions rejetées",
    ],
    "PANNE_UPF": [
        "UPF pod OOMKilled mémoire insuffisante trafic data coupé",
        "N3 interface down gNB UPF GTP tunnel impossible data coupé",
        "N6 interface saturée sortie internet indisponible navigation coupée",
        "PFCP session establishment failure SMF UPF communication rompue",
        "GTP U tunnels effacés UPF redémarrage sessions perdues data",
        "UPF CPU 100 pourcent DPI surcharge trafic ralenti drop packets",
        "routing N6 incorrect trafic data ne sort pas vers internet",
    ],
    "PANNE_SMF": [
        "SMF PDU session establishment reject tous abonnés sans data",
        "N4 interface down SMF UPF communication coupée sessions impossibles",
        "SMF NRF heartbeat timeout service indisponible core 5G",
        "QoS policy enforcement échoué 5QI mapping incorrect débit",
        "SMF database corrompue sessions perdues migration urgente",
        "N7 interface PCF down politiques QoS non appliquées",
        "SMF surcharge threads bloqués sessions en attente timeouts",
    ],
    "PANNE_CORE_SLICE": [
        "network slice eMBB défaillant abonnés grand public sans service",
        "URLLC slice down industrie 4.0 IoT critique interrompu",
        "mMTC slice hors service millions capteurs IoT déconnectés",
        "NSSF erreur sélection slice mauvais SNSSAI assigné abonnés",
        "ressources slice épuisées MANO ne peut plus instancier VNF",
        "isolation slice compromise slice eMBB interfère avec URLLC",
        "slice migration échouée cloud provider défaillance service perdu",
    ],
    "COUPURE_FIBRE": [
        "fibre optique coupée travaux BTP tranchée section 200 mètres",
        "câble sous marin fibre endommagé tempête littoral connectivité",
        "fibre rongée par rongeurs dans conduite coupure signal perdu",
        "soudure fibre dégradée après séisme vibration perte signal",
        "conduite fibre inondée infiltration eau connecteurs oxydés",
        "vol câble fibre cuivre vandalisme infrastructure détruite nuit",
        "fibre aérienne tombée après forte tempête vent 80 km h",
    ],
    "SATURATION_BACKHAUL": [
        "backhaul 1Gbps saturé trafic 5G pic 1.2 Gbps débit réduit",
        "lien transport micro ondes congestionné pluie atténuation",
        "MPLS TE tunnel saturé QoS dégradée toutes classes service",
        "événement sportif stade trafic exceptionnel backhaul insuffisant",
        "migration cloud trafic data explosé backhaul goulot étranglement",
        "video streaming 4K multiplié backhaul site surchargé latence",
        "pic matin 8h trafic entreprises backhaul saturé 95 pourcent",
    ],
    "LATENCE_ELEVEE": [
        "latence 5G mesurée 80 ms SLA 20 ms violé applications temps réel",
        "jeu en ligne latence inacceptable 100 ms utilisateurs mécontents",
        "chirurgie robotique téléopérée latence trop élevée non conforme",
        "véhicule autonome V2X latence critique sécurité compromise",
        "trading haute fréquence latence augmentée pertes financières",
        "vidéoconférence entreprise saccades latence backhaul élevée",
        "IoT industrie 4.0 latence contrôle commande dépassée production",
    ],
    "INTRUSION_RESEAU": [
        "tentative brute force accès NMS administrateur alarme SIEM",
        "DDoS attack gNB O RAN controller trafic anormal 100Gbps",
        "accès non autorisé core 5G credentials compromis forensic",
        "scanning ports équipements réseau source IP étrangère suspecte",
        "ransomware détecté serveur OSS NMS chiffrement en cours",
        "fausse station de base stingray détectée IMSI catcher actif",
        "API SBI 5G core exploitée injection requêtes malformées",
    ],
    "MISE_A_JOUR_ECHOUEE": [
        "firmware gNB mise à jour échouée rollback automatique déclenché",
        "5G core kubernetes deployment failed pods en CrashLoopBackOff",
        "OMC software upgrade incompatibilité version base données corrompue",
        "patch sécurité critique déploiement échoué service dégradé",
        "ansible playbook erreur configuration partielle site incohérent",
        "AMF nouvelle version incompatible SMF interface SBI changée",
        "RAN software upgrade BBU rejet nouveau firmware signature invalide",
    ],
    "SURCHARGE_CELLULE": [
        "cellule 5G PRB 97 pourcent saturation totale Tunis centre événement",
        "match football 60000 spectateurs cellule stade saturée service nul",
        "concert festival cellule voisine surchargée débordement utilisateurs",
        "zone touristique haute saison cellule saturation quotidienne",
        "marché hebdomadaire trafic explosé cellule rurale saturée",
        "quartier commercial vendredi midi cellule 95 pourcent utilisation",
        "nouvelle zone résidentielle densification sans upgrade cellule",
    ],
    "PANNE_CLIMATISATION": [
        "clim tombée en panne température salle 45 degrés équipements surchauffent",
        "compresseur climatisation grillé chaleur été Tunisie site critique",
        "fuite gaz frigorigène clim inefficace température monte progressivement",
        "coupure électrique clim UPS non alimenté salle chaleur réseau",
        "filtre clim colmaté débit air réduit température salle augmente",
        "thermostat clim défaillant refroidissement hors contrôle alternance",
        "deux clim en panne simultanées redondance insuffisante urgence",
    ],
    "INONDATION_SITE": [
        "pluies torrentielles inondation salle technique équipements immergés",
        "rupture canalisation eau fuite importante salle serveurs réseau",
        "oued débordement zone Medjerda site inondé équipements perdus",
        "infiltration eau toit fissure pluie salle technique mouillée",
        "crue flash flood site bas niveau eau monte rapidement urgent",
        "condensation massive clim défaillante sol mouillé risque électrique",
        "dégât eaux étage supérieur écoulement salle télécoms endommagée",
    ],
    "VANDALISME_PYLONE": [
        "câbles cuivre pylône volés nuit site hors service panne totale",
        "antennes arrachées vandalisme accès non autorisé tour télécoms",
        "équipements détruits actes malveillants pylône rural zone isolée",
        "batteries site volées alimentation perdue groupe électrogène off",
        "pylône graffitis dégradation mineure accès forcé constaté",
        "clôture découpée intrusion site traces effraction relevées",
        "câbles fibre sectionnés délibérément concurrence déloyale suspecte",
    ],
    "PANNE_GROUPE_ELECTROGENE": [
        "groupe électrogène ne démarre pas STEG coupée UPS critique",
        "GE en panne batterie démarrage déchargée démarrage impossible",
        "fuel diesel épuisé groupe électrogène arrêt automatique site",
        "régulateur tension GE défaillant sortie instable équipements",
        "filtre carburant bouché GE cale après quelques minutes démarrage",
        "alternateur GE défaillant tension sortie insuffisante sous charge",
        "ATS automatique non commuté coupure STEG GE non démarré auto",
    ],
}


# ══════════════════════════════════════════════════════════════════
# 5. GÉNÉRATION DATASET
# ══════════════════════════════════════════════════════════════════

ZONE_POP_MAP = {"FAIBLE": 0, "MOYENNE": 1, "HAUTE": 2, "TRES_HAUTE": 3}
ZONES_POP    = ["FAIBLE", "MOYENNE", "HAUTE", "TRES_HAUTE"]


def get_urgence(nb_rec: int, zone_pop: str) -> str:
    score = 0
    if nb_rec > 100: score += 3
    elif nb_rec > 50: score += 2
    elif nb_rec > 20: score += 1
    pop_score = {"FAIBLE": 0, "MOYENNE": 1, "HAUTE": 2, "TRES_HAUTE": 3}
    score += pop_score.get(zone_pop, 0)
    if score >= 5: return "TRES_HAUTE"
    if score >= 3: return "HAUTE"
    if score >= 1: return "MOYENNE"
    return "FAIBLE"


def generer_dataset(n_samples: int = 8000) -> pd.DataFrame:
    np.random.seed(42)
    records = []

    for _ in range(n_samples):
        type_panne  = np.random.choice(TYPES_PANNE)
        zone_pop    = np.random.choice(ZONES_POP, p=[0.20, 0.30, 0.30, 0.20])
        nb_rec      = int(np.random.exponential(40)) + 1
        nb_rec      = min(nb_rec, 300)
        region      = np.random.choice(REGIONS)
        description = np.random.choice(DESCRIPTIONS_PAR_TYPE[type_panne])
        heure       = np.random.randint(0, 24)
        urgence     = get_urgence(nb_rec, zone_pop)

        guide = BASE_CONNAISSANCE.get(type_panne, {}).get(urgence, {})
        if not guide:
            first_key = list(BASE_CONNAISSANCE.get(type_panne, {"FAIBLE": {}}).keys())[0]
            guide = BASE_CONNAISSANCE.get(type_panne, {}).get(first_key, {})

        action = guide.get("action", "DIAGNOSTIC_GENERAL")

        records.append({
            "typePanne":          type_panne,
            "zonePopulation":     zone_pop,
            "nombreReclamations": nb_rec,
            "region":             region,
            "description":        description,
            "heure":              heure,
            "urgence":            urgence,
            "action_cible":       action,
        })

    df = pd.DataFrame(records)
    print(f"✅ Dataset : {len(df)} tickets — {df['typePanne'].nunique()} types pannes")
    print(f"\nDistribution types pannes:\n{df['typePanne'].value_counts().to_string()}")
    return df


# ══════════════════════════════════════════════════════════════════
# 6. ENTRAÎNEMENT
# ══════════════════════════════════════════════════════════════════

def train_guide_model(df: pd.DataFrame, models_dir: str = "models/") -> dict:
    os.makedirs(models_dir, exist_ok=True)

    type_enc    = LabelEncoder().fit(TYPES_PANNE)
    region_enc  = LabelEncoder().fit(REGIONS)
    action_enc  = LabelEncoder().fit(df["action_cible"].unique())
    urgence_enc = LabelEncoder().fit(["FAIBLE", "MOYENNE", "HAUTE", "TRES_HAUTE"])

    X_struct = np.column_stack([
        type_enc.transform(df["typePanne"]),
        df["zonePopulation"].map(ZONE_POP_MAP).values,
        df["nombreReclamations"].values,
        df["heure"].values,
        urgence_enc.transform(df["urgence"]),
    ])

    scaler     = StandardScaler()
    X_struct_s = scaler.fit_transform(X_struct)

    tfidf  = TfidfVectorizer(max_features=300, ngram_range=(1, 2), sublinear_tf=True)
    X_text = tfidf.fit_transform(df["description"])

    X = sp.hstack([sp.csr_matrix(X_struct_s), X_text])
    y = action_enc.transform(df["action_cible"])

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    model = RandomForestClassifier(
        n_estimators=300, max_depth=12,
        class_weight="balanced", random_state=42, n_jobs=-1
    )
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    acc    = accuracy_score(y_test, y_pred)

    print(f"\n[GuideModel] Accuracy : {acc:.4f}")
    print(classification_report(y_test, y_pred,
                                target_names=action_enc.classes_, zero_division=0))

    artifacts = {
        "model": model, "type_enc": type_enc, "region_enc": region_enc,
        "action_enc": action_enc, "urgence_enc": urgence_enc,
        "scaler": scaler, "tfidf": tfidf,
        "base_connaissance": BASE_CONNAISSANCE,
        "types_panne": TYPES_PANNE,
        "regions": REGIONS,
    }
    for name, obj in artifacts.items():
        with open(os.path.join(models_dir, f"guide_{name}.pkl"), "wb") as f:
            pickle.dump(obj, f)

    metrics = {
        "accuracy":    round(acc, 4),
        "n_samples":   len(df),
        "n_types":     len(TYPES_PANNE),
        "n_regions":   len(REGIONS),
        "n_actions":   len(action_enc.classes_),
        "classes":     list(action_enc.classes_),
    }
    with open(os.path.join(models_dir, "guide_metrics.json"), "w",
              encoding="utf-8") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Guide sauvegardé → {models_dir}")
    print(f"   {len(TYPES_PANNE)} types pannes | {len(REGIONS)} gouvernorats | {len(action_enc.classes_)} actions")
    return metrics


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--models_dir", default="models/")
    parser.add_argument("--n_samples",  default=8000, type=int)
    args = parser.parse_args()

    df = generer_dataset(args.n_samples)
    metrics = train_guide_model(df, args.models_dir)
    print(json.dumps(metrics, indent=2))