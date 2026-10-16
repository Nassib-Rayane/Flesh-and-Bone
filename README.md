# Flesh & Bone

Mod NeoForge pour Minecraft **26.1.2** : une simulation médicale membre par membre qui **remplace les cœurs**.
Inspiré par les mécaniques de *Casualties: Unknown* (Orsoniks), sans lien avec le jeu, sans aucun de ses assets.

> **English summary.** Flesh & Bone replaces vanilla hearts with a per-limb body simulation: skin and muscle health,
> bleeding in litres per minute, fractures, dislocations, shrapnel, pain and shock, consciousness, infections and
> sepsis, blood pressure, heart rhythm and oxygen, thirst, body temperature, sleepiness, stamina, nausea, mood and
> trauma. Wounds are treated with ~30 medical items, through hands-on minigames (bandaging, suturing, shrapnel
> extraction, joint reduction, amputation, defibrillation, CPR), on yourself or on other players. Press **H** for the
> health panel. Inspired by *Casualties: Unknown*; not affiliated.

## Le principe

- Plus de cœurs : chaque coup est réparti sur **13 parties du corps** (tête, thorax, abdomen, bras, avant-bras, mains,
  cuisses, jambes), selon l'endroit où il frappe. L'armure ne protège que ce qu'elle couvre.
- Chaque partie a une **peau** (saignement, infection) et un **muscle** (fonction). Le corps meurt par ce qu'il subit :
  hémorragie, arrêt cardiaque, asphyxie, traumatisme crânien, septicémie, froid, coup de chaleur, soif, faim, overdose.
- Le temps ne s'arrête pas pendant les soins : le panneau de santé et les mini-jeux laissent le monde tourner.

## Systèmes

| Système | Ce qui se passe |
|---|---|
| Saignement | Débit en L/min par partie, plafonné par la peau ; coagulation naturelle, bloquée par les éclats. 5 L de sang, régénérés en mangeant. |
| Lésions | Fractures (≈30 min, ×2,5 avec attelle), luxations, éclats, commotion. Jambe cassée : on boite, pas de sprint. Bras cassé : main inutilisable. |
| Douleur et choc | Douleur par partie ; au-delà de 75, le choc monte jusqu'à l'évanouissement. L'adrénaline et les antidouleurs l'atténuent. |
| Conscience | Plafonnée par le muscle de la tête, l'oxygène, la tension, la douleur, le cerveau, la fatigue, la nausée et la température. Sous 30 % : au sol, incapable d'agir. |
| Cardio-respiratoire | Tension et pouls qui compensent la perte de sang, fibrillation puis arrêt cardiaque, SpO2, hémothorax, embolie, AVC, lésions cérébrales. |
| Infection | Les plaies sales s'infectent, la nécrose et la propagation suivent, puis la septicémie. Antiseptique, antibiotiques, amputation en dernier recours. |
| Besoins | Soif, température corporelle (biome, altitude, nuit, pluie, eau, sources de chaleur, vêtements), fatigue et évanouissement, endurance, nausée et vomissements. |
| Mental | Moral et traumatisme. |
| Drogues | Antidouleurs, morphine (tolérance, dépendance, sevrage, overdose), naloxone, adrénaline, antiarythmique, procoagulant, thrombolytique, caféine. |

## Interface

- **HUD** : 10 gouttes de sang (0,5 L chacune) à la place des cœurs, silhouette du corps en bas à gauche, moodles à
  droite, barre de soif au-dessus de la faim.
- **Effets d'écran** : vignettes de douleur, de perte de sang, d'hypoxie, de froid et de chaleur, assombrissement vers
  l'inconscience, flou d'évanouissement, roulis et tremblements de caméra, battements de cœur. Tout est réglable.
- **Panneau de santé (H)** : silhouette cliquable, détail de chaque partie, soins disponibles dans l'inventaire, bilan
  complet.
- **Moniteur de signes vitaux** : gardé dans l'inventaire, il affiche les constantes exactes et un ECG.
- **Pensées** du personnage au-dessus de la barre d'action, brouillées si le cerveau est atteint.

## Soigner

- **Clic droit** avec un objet médical : soin rapide sur soi, sur la partie la plus urgente, fait à la hâte.
- **Panneau de santé** : on choisit la partie, puis le soin. Le soin passe par un **mini-jeu** (bandage, suture,
  extraction, réduction de luxation, amputation, défibrillation) ou par un minuteur si l'option est désactivée.
- **Clic droit sur un autre joueur** avec un objet médical : ouvre **son** panneau. Les autres joueurs peuvent vous
  bander, vous transfuser, vous défibriller, ou vous masser le cœur (**RCP**) quand vous êtes inconscient.

Objets : chiffon, bandage, bandage stérile, antiseptique, kit de suture, pince, garrot, poche de glace, attelle,
antidouleurs, morphine, antibiotiques, scie chirurgicale, défibrillateur, injecteurs (adrénaline, antiarythmique,
naloxone, procoagulant, thrombolytique), poches de sang (avec groupe sanguin), sérum, drain thoracique, moniteur,
gourde, eau purifiée, café. Toutes les recettes utilisent des matériaux vanilla (voir JEI/EMI ou `data/fleshbone/recipe`).
On en trouve aussi dans les coffres (donjons, mines, villages, épaves, cités antiques, chambres d'épreuves…), et le
clerc des villages en vend.

## Commandes (opérateurs)

```
/fleshbone status [joueur]
/fleshbone heal [joueurs]
/fleshbone injure <joueurs> <partie> <type> <sévérité>     ex. /fleshbone injure @s shin_l fall 40
/fleshbone fracture <joueurs> <partie>
/fleshbone set <joueurs> <valeur> <nombre>                ex. /fleshbone set @s blood 3.2
/fleshbone arrest <joueurs>  |  /fleshbone restart <joueurs>
```

## Configuration

- `fleshbone-server.toml` : vitesse de simulation, échelle des dégâts, armure par partie, vitesses de guérison, et un
  interrupteur par système (infections, soif, température, fatigue, endurance, moral, dépendance).
- `fleshbone-client.toml` : HUD, moodles, constantes toujours visibles, mini-jeux, intensité des effets, caméra, flou,
  battements de cœur.

Les data maps (`data/<namespace>/data_maps/...`) permettent aux modpacks de régler boissons, effets des aliments,
sources de chaleur et isolation des vêtements ; des tags classent les dégâts et les attaques des mobs.

## Développement

Prérequis : Gradle télécharge lui-même Java 25. Il faut IntelliJ 2025.2 ou plus récent (ou VS Code avec l'extension Java).

```
gradlew build              # compile et lance les tests unitaires (simulation)
gradlew runGameTestServer  # tests en jeu sur un vrai serveur
gradlew runClient          # lance le jeu avec le mod
python tools/gen_textures.py   # régénère les textures provisoires manquantes
```

- `com.fleshbone.sim` : la simulation, en Java pur (aucun import Minecraft), entièrement testée avec JUnit.
- Le reste relie la simulation au jeu : dégâts, effets, objets, réseau, interface.

## Limites connues

- Textures provisoires générées par script, à remplacer par de vraies textures.
- Seuls les joueurs sont simulés ; les mobs gardent leurs points de vie.
- En journée, on ne peut faire qu'une courte sieste, et seulement si on est fatigué (énergie sous 60 %).
- L'équilibrage demandera des retours de jeu.
