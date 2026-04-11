# fix_csv.py — corrige le CSV mal formaté
# Usage : python fix_csv.py

import re

with open("historique.csv", "r", encoding="utf-8") as f:
    lines = f.readlines()

fixed = []
for i, line in enumerate(lines):
    if i == 0:
        fixed.append(line)  # header intact
        continue

    # Chaque ligne a trop de colonnes à cause de "65,00" → "65.00"
    # Format attendu : zone_id,zone_nom,taux_utilisation,nb_pylones_satures,
    #                  nb_pylones_total,anomaly_score,statut,timestamp

    # Stratégie : le timestamp est toujours à la fin (format 2026-...)
    # Le statut est toujours NORMAL/ATTENTION/SATURE/CRITIQUE
    # On reconstruit en partant de la fin

    parts = line.strip().split(",")

    # timestamp = dernier élément
    timestamp = parts[-1]
    # statut = avant-dernier
    statut = parts[-2]
    # zone_id = premier
    zone_id = parts[0]

    # Les éléments du milieu sont bruités par les virgules décimales
    # On cherche le statut et on reconstruit
    statut_idx = len(parts) - 2

    # Les 3 derniers avant statut : anomaly_score (peut être "0,8000" → "0.8000")
    # nb_pylones_total, nb_pylones_satures
    # On joint les morceaux numériques avec un point

    middle = parts[1:statut_idx]

    # Reconstruire : zone_nom peut contenir des espaces mais pas de virgules
    # zone_id est un entier pur
    # Ensuite : taux (peut être "65,00"), nb_sat (entier), nb_total (entier), anomaly (peut être "0,8000")

    # Trouver l'index du premier nombre après zone_nom
    # zone_nom = middle[0] (premier élément du milieu)
    zone_nom = middle[0]
    rest = middle[1:]  # tout le reste = numbers bruités

    # rest ressemble à : ['65', '00', '0', '4', '1', '0000'] ou ['65', '00', '0', '3', '0', '8000']
    # On joint les paires "X,YY" → "X.YY" pour les décimaux
    # Règle : si un élément a 2+ chiffres et le précédent aussi, c'est une décimale

    nums = []
    j = 0
    while j < len(rest):
        # Si l'élément courant ressemble à un nombre décimal partiel (2-4 chiffres après virgule)
        if j + 1 < len(rest) and re.match(r'^\d{1,2}$', rest[j+1]):
            # Vérifier si c'est bien une décimale (ex: "65" + "00" → "65.00")
            # mais pas "0" + "4" qui sont deux entiers séparés
            if re.match(r'^\d+$', rest[j]) and int(rest[j]) >= 1:
                nums.append(rest[j] + "." + rest[j+1])
                j += 2
                continue
        nums.append(rest[j])
        j += 1

    # On s'attend à exactement 4 valeurs numériques :
    # taux_utilisation, nb_pylones_satures, nb_pylones_total, anomaly_score
    if len(nums) == 4:
        fixed_line = f"{zone_id},{zone_nom},{nums[0]},{nums[1]},{nums[2]},{nums[3]},{statut},{timestamp}\n"
    else:
        # Fallback : ligne problématique, on saute
        continue

    fixed.append(fixed_line)

with open("historique_fixed.csv", "w", encoding="utf-8") as f:
    f.writelines(fixed)

print(f"Fichier réparé : {len(fixed)-1} lignes → historique_fixed.csv")