# Flesh & Bone

Mod NeoForge pour Minecraft **26.1.2** : une simulation médicale membre par membre qui **remplace les cœurs**.
Inspiré par les mécaniques de *Casualties: Unknown* (Orsoniks), sans lien avec le jeu, sans aucun de ses assets.

> **English summary.** Flesh & Bone replaces vanilla hearts with a per-limb body simulation modelled on
> *Casualties: Unknown*: skin and muscle per limb, bleeding in litres per minute, fractures, dislocations, shrapnel,
> pain and shock, consciousness, a baroreflex-driven heart and blood pressure, breathing and SpO2, stamina capped by
> oxygen, a heat-balance body temperature, C:U hunger (0–125) and body weight replacing the vanilla food bar, thirst,
> computed immunity driving infections and sepsis, opioid tolerance and withdrawal, sleep anywhere, mood, a last
> stand, strength / resilience / intelligence with experience and workouts, and item-weight encumbrance. Every value
> is shown in a UNI-HEALTH readout. Wounds are treated with ~30 medical items through hands-on minigames, on yourself or
> on other players. Press **H** for the health panel, **U** to cycle the readout, **J** to sleep. Not affiliated with
> Casualties: Unknown.

## Le principe

- Plus de cœurs : chaque coup est réparti sur **13 parties du corps** (tête, thorax, abdomen, bras, avant-bras, mains,
  cuisses, jambes), selon l'endroit où il frappe. L'armure ne protège que ce qu'elle couvre.
- Chaque partie a une **peau** (saignement, infection) et un **muscle** (fonction). Le corps meurt de ce qu'il subit :
  hémorragie, arrêt cardiaque, asphyxie, traumatisme crânien, septicémie, froid, coup de chaleur, soif, faim, overdose,
  intoxication à l'eau, venin.
- Les variables forment un réseau de causes : l'effort fait monter le pouls, la respiration et la chaleur produite ; la
  perte de sang fait chuter la tension, que le cœur compense ; l'oxygène plafonne l'endurance ; le froid resserre les
  vaisseaux et fait frissonner, ce qui brûle la faim ; l'immunité se recalcule à partir de tout le corps et décide du
  sort des infections.
- Le temps ne s'arrête pas pendant les soins : le panneau de santé et les mini-jeux laissent le monde tourner.

## Systèmes

Les valeurs suivent celles de *Casualties: Unknown* ; chaque vitesse a son multiplicateur dans la configuration.

| Système | Ce qui se passe |
|---|---|
| Cœur et vaisseaux | Tension 120/80 modifiée par le volume sanguin (±0,66/0,44 mmHg par 25 mL), la viscosité, le tonus vasculaire, la septicémie, la chaleur, le poids, la soif et l'effort. Pouls = 70 + baroréflexe (−30 à +80 bpm, pour tenir 120) + adrénaline ×0,55 + douleur + endurance manquante ×0,6 + fibrillation − opiacés ×0,2. Fibrillation déclenchée sous 78 mmHg, au-delà de 200 bpm, par le froid profond, l'hypoxie, un poids extrême ou un sevrage sévère ; arrêt cardiaque à 100 %. |
| Respiration | 25/min au repos ; plus vite à l'effort et dans la douleur, ralentie par les opiacés, l'hémothorax, les côtes cassées. La SpO2 gagne 0,04 %/s par respiration et en perd 0,5 %/s : sous 12,5 respirations/min, le sang se vide d'oxygène. |
| Endurance | Coûts : marche 0,1 %/s, sprint 1 %/s, saut 1 %, coup de poing 1 %, coup d'arme 3 %, exercices. Récupération 1,4 %/s à l'arrêt. Jamais au-dessus de la SpO2 (plancher 70). La force des muscles suit une courbe cubique de l'endurance. À 0 en plein effort : effondrement. |
| Température | Bilan thermique : échanges avec l'air (vêtements, graisse, peau mouillée, eau), chaleur produite (moins quand on est épuisé ou sous opiacés), effort, frissons qui brûlent la faim, chauffage d'urgence sous 32 °C, transpiration qui coûte de l'eau. Les infections font monter la consigne (fièvre jusqu'à 40,5 °C). |
| Faim et poids | Faim de 0 à 125 (2,6 points par minute) à la place de la barre vanilla. Chaque repas fait aussi grossir (1 kg pour 150 points) ; le corps brûle ses réserves au même rythme, plus vite en activité. Au-delà de 125 : nausée, une fois sur cinq on vomit. À 0 : les muscles fondent. Le poids (70 kg au départ) agit sur la tension, l'isolation, la capacité de port et la mobilité. |
| Soif | De −50 à 250, environ 3,5 points par minute, plus vite quand on a chaud. Au-dessus de 175 : intoxication à l'eau. En dessous de 0 : vaisseaux contractés, cœur qui s'emballe, puis défaillance des organes. |
| Saignement | Débit en L/min par partie, plafonné par la peau, poussé par la tension ; coagulation naturelle, bloquée par les éclats. 5 L de sang, refaits à partir de la nourriture. |
| Lésions | Fractures (≈30 min, ×2,5 avec attelle), luxations, éclats, commotion. Nuque cassée, côtes cassées, colonne luxée : −40 % de force de frappe et des douleurs à chaque effort. Mâchoire luxée : manger fait mal. Un œil peut être perdu, une explosion assourdit. |
| Douleur et choc | Douleur ressentie = pire membre × résistance (×1,25 au niveau 0, ×0,75 au niveau 20) − adrénaline/2 − antidouleurs. Au-delà de 75, le choc monte jusqu'à l'évanouissement. |
| Immunité et infections | Immunité = 100 % ± faim, soif, énergie, température, sang, saleté, nausée, + antibiotiques. Une infection progresse de 7,2 %/min à 100 % d'immunité, 18 %/min à 0 %, s'arrête vers 196 % et recule au-delà ; désinfectée, elle recule d'environ 5 %/min. Puis nécrose, propagation, septicémie. |
| Nausée | S'atténue de 2,4 points par minute. Au-dessus de 50, l'envie de vomir monte ; la crise dure 5 s (presque inconscient, incapable de bouger), puis l'estomac se vide. Sang dans le vomi si quelque chose saigne à l'intérieur. |
| Sommeil | Énergie : environ 24 minutes d'éveil. On peut dormir n'importe où une fois fatigué (35 % ou moins). La qualité dépend du sol, de la pluie, du froid ; la douleur, le froid, la nausée, l'eau ou le sevrage réveillent. Un mauvais sommeil laisse vaseux. Le lit de nuit garde le comportement vanilla. |
| Drogues | Opiacés : l'effet ressenti est la dose moins une tolérance qui la suit ; quand la dose retombe plus vite que la tolérance, c'est le sevrage (humeur, nausée, fibrillation). Naloxone, adrénaline (peut relancer un cœur arrêté sous massage), antiarythmique, procoagulant, thrombolytique, antibiotiques, antivenin, caféine. |
| Humeur | Humeur durable (repas, os ressoudés, amputations...) qui revient doucement à 0, plus ce que la situation ajoute ou retire. Très basse, elle vide l'énergie et peut faire refuser de manger ou de se soigner. |
| Cerveau | Abîmé par l'hypoxie, la chaleur, l'intoxication à l'eau. Sous 95 % : épisodes (fausse attaque, commandes inversées, objet lâché, tremblements, flash). Sous 80 % : vision floue. |
| Second souffle | Une fois par vie, au bord de la mort, une chance (selon l'humeur) que le corps tienne encore une minute : cœur relancé, douleur ignorée. |

## Stats et encombrement

- **Force, résistance, intelligence** (10 au départ, jusqu'à 20), avec de l'expérience :
  - force : coups portés, minage, charges lourdes, pompes et gainage. +3,3 % de dégâts par niveau, plus de capacité de port ;
  - résistance : douleur endurée, monstres tués, charges lourdes, squats et gainage. Moins de douleur, endurance plus rapide, mains plus sûres dans les mini-jeux ;
  - intelligence : premières fabrications, échanges, soins, nouvelles profondeurs. Soins de meilleure qualité, plus de temps dans les mini-jeux.
- **Exercices** depuis le panneau de santé : pompes, squats, gainage. Ils coûtent de l'endurance et s'arrêtent si l'on bouge.
- **Encombrement** : chaque objet pèse son poids (armure selon sa protection, outils selon leurs dégâts, blocs légers, contenu des boîtes de Shulker et des sacs compris ; réglable par data map). Capacité de base 25 kg, modulée par les stats, la faim, la soif, la nausée et le poids du corps. Au-delà : plus lent, coups plus lents, endurance qui fond.

## Interface

- **UNI-HEALTH** en haut à gauche : cerveau, conscience, douleur, sang en litres, immunité, faim, nausée, soif,
  température, endurance, humeur, poids, énergie, respiration, charge, pouls, tension, SpO2, ECG, et le membre le plus
  touché. Les valeurs passent au jaune puis clignotent en rouge aux seuils d'alerte. **U** : complet, compact, masqué.
- **HUD** : 10 gouttes de sang (0,5 L chacune) à la place des cœurs, faim C:U à la place de la barre vanilla, soif
  au-dessus, silhouette du corps en bas à gauche, moodles à droite.
- **Panneau de santé (H)** : UNI-HEALTH, silhouette cliquable et équipement, détail de la partie choisie, soins
  disponibles dans l'inventaire, force / résistance / intelligence avec leur barre d'expérience, boutons Dormir,
  Pompes, Squats, Gainage. Onglet Bilan : tous les moodles et les mesures fines (baroréflexe, tonus vasculaire,
  viscosité, tolérance aux opiacés...).
- **Dormir ici (J)** : s'allonger n'importe où, ou se lever.
- **Effets d'écran** : douleur, perte de sang, hypoxie, froid, chaleur, assombrissement vers l'inconscience, flou,
  roulis et tremblements, œil perdu, épisodes cérébraux, battements de cœur. Tout est réglable.
- **Mode sans puce** (option serveur `healthChip = false`) : sans moniteur de signes vitaux, les mesures d'instrument
  sont remplacées par ce qu'on peut sentir ou voir.

## Soigner

- **Clic droit** avec un objet médical : soin rapide sur soi, sur la partie la plus urgente, fait à la hâte.
- **Panneau de santé** : on choisit la partie, puis le soin. Le soin passe par un **mini-jeu** (bandage, suture,
  extraction, réduction de luxation, amputation, défibrillation) ou par un minuteur si l'option est désactivée. La main
  tremble avec la douleur, l'épuisement, le froid ; la résistance la stabilise.
- **Clic droit sur un autre joueur** avec un objet médical : ouvre **son** panneau. Les autres joueurs peuvent vous
  bander, vous transfuser, vous défibriller, ou vous masser le cœur (**RCP**) quand vous êtes inconscient.

Objets : chiffon, bandage, bandage stérile, antiseptique, kit de suture, pince, garrot, poche de glace, attelle,
antidouleurs, morphine, antibiotiques, scie chirurgicale, défibrillateur, injecteurs (adrénaline, antiarythmique,
naloxone, procoagulant, thrombolytique, antivenin), poches de sang (avec groupe sanguin), sérum, drain thoracique,
moniteur, gourde, eau purifiée, café. Toutes les recettes utilisent des matériaux vanilla (voir JEI/EMI ou
`data/fleshbone/recipe`). On en trouve aussi dans les coffres (donjons, mines, villages, épaves, cités antiques,
chambres d'épreuves...), et le clerc des villages en vend.

## Commandes (opérateurs)

```
/fleshbone status [joueur]
/fleshbone heal [joueurs]
/fleshbone injure <joueurs> <partie> <type> <sévérité>     ex. /fleshbone injure @s shin_l fall 40
/fleshbone fracture <joueurs> <partie>
/fleshbone set <joueurs> <valeur> <nombre>                ex. /fleshbone set @s hunger 20, /fleshbone set @s strength 15
/fleshbone arrest <joueurs>  |  /fleshbone restart <joueurs>
```

## Configuration

- `fleshbone-server.toml` : vitesse de simulation, puce de santé, faim C:U, vitesses de la faim, de la soif, de la
  fatigue et des infections, encombrement (capacité, multiplicateur de poids), stats gardées à la mort, second souffle,
  épisodes cérébraux, échelle des dégâts, armure par partie, vitesses de guérison, et un interrupteur par système.
- `fleshbone-client.toml` : panneau UNI-HEALTH (mode, taille), HUD, moodles, mini-jeux, intensité des effets, caméra,
  flou, battements de cœur.

Les data maps (`data/<namespace>/data_maps/...`) permettent aux modpacks de régler boissons, nutrition (faim, poids,
effets), sources de chaleur (température et force), isolation des vêtements et poids des objets ; des tags classent
les dégâts et les attaques des mobs.

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
- La thermorégulation reprend la structure de *Casualties: Unknown* mais ses taux sont recalibrés pour les climats de
  Minecraft ; le poids de départ est de 70 kg (les seuils de C:U, pensés pour 50 kg, sont multipliés par 1,4).
- Masquer la faim vanilla peut gêner les mods qui lisent la barre de nourriture (ils la voient toujours à 19).
- L'équilibrage demandera des retours de jeu.
