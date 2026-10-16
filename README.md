# Flesh & Bone

Mod NeoForge pour Minecraft **26.1.2** : une simulation médicale membre par membre qui **remplace les cœurs**.
Inspiré par les mécaniques de *Casualties: Unknown* (Orsoniks), sans lien avec le jeu, sans aucun de ses assets.

> **English summary.** Flesh & Bone replaces vanilla hearts with a per-limb body simulation modelled on
> *Casualties: Unknown*: skin and muscle per limb, bleeding in litres per minute, fractures, dislocations, shrapnel,
> pain and shock, consciousness, a baroreflex-driven heart and blood pressure, breathing and SpO2, stamina capped by
> oxygen, a heat-balance body temperature, C:U hunger (0–125) and body weight replacing the vanilla food bar, thirst,
> computed immunity driving infections and sepsis, strong and addictive drugs (absorption, tolerance, dependence,
> withdrawal, side effects, allergies), food poisoning, sleep anywhere, mood, a last stand, strength / resilience /
> intelligence with experience and workouts, and item-weight encumbrance. Biomes set the felt air temperature; glass,
> hard blocks and dry bushes hurt bare skin; vanilla effects feed the simulation. Every value is shown in a UNI-HEALTH
> readout. Wounds are treated with ~30 medical items (and improvised vanilla ones) through realistic multi-step
> procedures whose mistakes harm the patient, on yourself or on other players. Village doctors sell supplies. Press
> **H** for the health panel, **U** to cycle the readout, **J** to sleep. Not affiliated with Casualties: Unknown.

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
- Le temps ne s'arrête pas pendant les soins : le panneau de santé et les procédures laissent le monde tourner.

## Systèmes

Les valeurs suivent celles de *Casualties: Unknown* ; chaque vitesse a son multiplicateur dans la configuration.

| Système | Ce qui se passe |
|---|---|
| Cœur et vaisseaux | Tension 120/80 modifiée par le volume sanguin (±0,66/0,44 mmHg par 25 mL), la viscosité, le tonus vasculaire, la septicémie, la chaleur, le poids, la soif et l'effort. Pouls = 70 + baroréflexe (−30 à +80 bpm, pour tenir 120) + adrénaline ×0,55 + douleur + endurance manquante ×0,6 + fibrillation − opiacés ×0,2. Fibrillation déclenchée sous 78 mmHg, au-delà de 200 bpm, par le froid profond, l'hypoxie, un poids extrême ou un sevrage sévère ; arrêt cardiaque à 100 %. |
| Respiration | 25/min au repos ; plus vite à l'effort et dans la douleur, ralentie par les opiacés, l'hémothorax, les côtes cassées. La SpO2 gagne 0,04 %/s par respiration et en perd 0,5 %/s : sous 12,5 respirations/min, le sang se vide d'oxygène. |
| Endurance | Coûts : marche 0,1 %/s, sprint 1 %/s, saut 1 %, coup de poing 1 %, coup d'arme 3 %, exercices. Récupération 1,4 %/s à l'arrêt. Jamais au-dessus de la SpO2 (plancher 70). La force des muscles suit une courbe cubique de l'endurance. À 0 en plein effort : effondrement. |
| Température | Bilan thermique : échanges avec l'air ressenti (vêtements, graisse, peau mouillée, eau), chaleur produite (moins quand on est épuisé ou sous opiacés), effort, frissons qui brûlent la faim, chauffage d'urgence sous 32 °C, transpiration qui coûte de l'eau et refroidit moins par air humide. Les infections font monter la consigne (fièvre jusqu'à 40,5 °C). Marcher en plaine reste vers 37,2 °C ; 39 °C et plus demandent une chaleur extrême, la soif ou la fièvre. |
| Climat | Chaque biome a sa température de jour (neige −5 °C, forêt 15, plaine 18, jungle 27, désert 38) et son humidité ; le cycle jour/nuit est continu, les nuits des biomes secs sont glaciales. +3 °C en plein soleil, l'intérieur tend vers 18 °C, l'eau est plus froide que l'air. Les vêtements mouillés sèchent avec la chaleur du corps, l'air sec et surtout près d'un feu, et l'évaporation refroidit. |
| Faim et poids | Faim de 0 à 125 (2,6 points par minute) à la place de la barre vanilla. Chaque repas fait aussi grossir (1 kg pour 150 points) ; le corps brûle ses réserves au même rythme, plus vite en activité. Au-delà de 125 : nausée, une fois sur cinq on vomit. À 0 : les muscles fondent. Le poids (70 kg au départ) agit sur la tension, l'isolation, la capacité de port et la mobilité. |
| Soif | De −50 à 250, environ 3,5 points par minute, plus vite quand on a chaud. Au-dessus de 175 : intoxication à l'eau. En dessous de 0 : vaisseaux contractés, cœur qui s'emballe, puis défaillance des organes. |
| Saignement | Débit en L/min par partie, plafonné par la peau, poussé par la tension ; coagulation naturelle, bloquée par les éclats. 5 L de sang, refaits à partir de la nourriture. |
| Lésions | Fractures (≈30 min, ×2,5 avec attelle), luxations, éclats, commotion. Nuque cassée, côtes cassées, colonne luxée : −40 % de force de frappe et des douleurs à chaque effort. Mâchoire luxée : manger fait mal. Un œil peut être perdu, une explosion assourdit. |
| Douleur et choc | Douleur ressentie = pire membre × résistance (×1,25 au niveau 0, ×0,75 au niveau 20) − adrénaline/2 − antidouleurs. Au-delà de 75, le choc monte jusqu'à l'évanouissement. |
| Immunité et infections | Immunité = 100 % ± faim, soif, énergie, température, sang, saleté, nausée, + antibiotiques. Une infection progresse de 7,2 %/min à 100 % d'immunité, 18 %/min à 0 %, s'arrête vers 196 % et recule au-delà ; désinfectée, elle recule d'environ 5 %/min. Puis nécrose, propagation, septicémie. |
| Nausée | S'atténue de 2,4 points par minute. Au-dessus de 50, l'envie de vomir monte ; la crise dure 5 s (presque inconscient, incapable de bouger), puis l'estomac se vide. Sang dans le vomi si quelque chose saigne à l'intérieur. |
| Sommeil | Énergie : environ 24 minutes d'éveil. On peut dormir n'importe où une fois fatigué (35 % ou moins). La qualité dépend du sol, de la pluie, du froid ; la douleur, le froid, la nausée, l'eau ou le sevrage réveillent. Un mauvais sommeil laisse vaseux. Le lit de nuit garde le comportement vanilla. |
| Drogues | Chaque prise passe dans le sang en une minute (injection) à cinq (comprimés, café) et s'élimine lentement : morphine ≈ 20 min de demi-vie. Opiacés : soulagement fort, somnolence, nausée, respiration ralentie ; la tolérance réduit chaque dose suivante, la **dépendance** s'installe en 3 ou 4 doses par heure et redescend en quelques heures. Le **sevrage** (dépendance − effet) apporte douleur accrue, crampes, sueurs, nausée, diarrhée, insomnie, angoisse, mains tremblantes, et la fibrillation s'il est sévère. Antidouleurs (ulcère en excès), caféine (énergie puis coup de barre, dépendance légère, mal de tête), adrénaline, antiarythmique, thrombolytique (hémorragie), antibiotiques (troubles digestifs, 5 % d'allergie), antivenin (3 % d'allergie). Une **réaction allergique** fait chuter la tension et gonfler la gorge : l'adrénaline la traite. |
| Intoxication alimentaire | La viande crue (15 à 25 %), le poulet cru (40 %), le poisson cru (10 %) et la chair putréfiée (80 %) peuvent rendre malade après quelques minutes : nausée qui monte, fièvre, crampes, diarrhée. Le lait la soigne. |
| Humeur | Humeur durable (repas, os ressoudés, amputations...) qui revient doucement à 0, plus ce que la situation ajoute ou retire. Très basse, elle vide l'énergie et peut faire refuser de manger ou de se soigner. |
| Cerveau | Abîmé par l'hypoxie, la chaleur, l'intoxication à l'eau. Sous 95 % : épisodes (fausse attaque, commandes inversées, objet lâché, tremblements, flash). Sous 80 % : vision floue. |
| Second souffle | Une fois par vie, au bord de la mort, une chance (selon l'humeur) que le corps tienne encore une minute : cœur relancé, douleur ignorée. |

## Stats et encombrement

- **Force, résistance, intelligence** (10 au départ, jusqu'à 20), avec de l'expérience :
  - force : coups portés, minage, charges lourdes, pompes et gainage. +3,3 % de dégâts par niveau, plus de capacité de port ;
  - résistance : douleur endurée, monstres tués, charges lourdes, squats et gainage. Moins de douleur, endurance plus rapide, mains plus sûres pendant les soins ;
  - intelligence : premières fabrications, échanges, soins, nouvelles profondeurs. Soins de meilleure qualité, plus de temps pour les procédures.
- **Exercices** depuis le panneau de santé : pompes, squats, gainage. Ils coûtent de l'endurance et s'arrêtent si l'on bouge.
- **Encombrement** : chaque objet pèse son poids (armure selon sa protection, outils selon leurs dégâts, blocs légers, contenu des boîtes de Shulker et des sacs compris ; réglable par data map). Capacité de base 25 kg, modulée par les stats, la faim, la soif, la nausée et le poids du corps. Au-delà : plus lent, coups plus lents, endurance qui fond.

## Le monde de Minecraft

- **Mains nues** : casser du verre (blocs et vitres) coupe la main et peut y laisser des éclats ; frapper la pierre,
  les minerais ou le métal fait mal et laisse des contusions.
- **Arbustes** : traverser buissons morts, buissons, rosiers ou herbes sèches égratigne les jambes (moins avec des
  jambières et des bottes).
- **Effets vanilla** : poison, wither, nausée, faim, saturation, résistance au feu, respiration aquatique, grâce du
  dauphin, cécité, obscurité, héros du village agissent sur la simulation.
- **Bien-être** : un jukebox qui joue réconforte ; clic droit main vide sur un chaudron d'eau pour se laver ; seau
  d'eau sur soi (accroupi) pour se mouiller et se rafraîchir.
- **Soins de fortune** : briquet ou boule de feu (cautérisation), fiole de miel (antiseptique), toile d'araignée
  (coagulant sale), papier (pansement), bâton (attelle), neige et glace (froid), lait (antidote partiel).
- **Médecin du village** : un métier dont le poste de travail est l'**armoire à pharmacie** ; il vend les fournitures
  du mod, du chiffon au défibrillateur. Le marchand ambulant en propose quelques-unes.
- Des tags (`fleshbone:sharp_when_broken`, `hurts_bare_hands`, `scratchy_plants`...) et des data maps (nutrition avec
  `poisoning` et `poisoning_chance`) permettent aux modpacks de tout régler.

## Interface

- **UNI-HEALTH** en haut à gauche : cerveau, conscience, douleur, sang en litres, immunité, faim, nausée, soif,
  température, endurance, humeur, poids, énergie, respiration, charge, pouls, tension, SpO2, ECG, et le membre le plus
  touché. Les valeurs passent au jaune puis clignotent en rouge aux seuils d'alerte. **U** : complet, compact, masqué.
- **HUD** : 10 gouttes de sang (0,5 L chacune) à la place des cœurs, faim C:U à la place de la barre vanilla, soif
  au-dessus, silhouette du corps en bas à gauche, moodles à droite.
- **Silhouette** : le remplissage montre la **douleur** (blanc, jaune, rouge), l'assombrissement les tissus abîmés,
  les **gouttes de sang** animées le saignement (une à trois selon le débit, sombre pour l'hémorragie interne), les
  points verts l'infection.
- **T° corps** et **Air** (température ressentie avec sa tendance) dans UNI-HEALTH ; l'onglet Bilan détaille l'air,
  les sources de chaleur, l'isolation, l'humidité, la transpiration, les doses, la tolérance et la dépendance.
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
- **Panneau de santé** : on choisit la partie, puis le soin. Le soin passe par une **procédure** en plusieurs
  étapes, ou par un minuteur si l'option `minigames` est désactivée. La main tremble avec la douleur, l'épuisement, le
  froid, le sevrage ou la caféine ; la résistance la stabilise, l'intelligence donne du temps, la force aide à tirer et
  à scier. Les **erreurs** (douleur, déchirure, saleté) atteignent le patient, même si l'on abandonne en route.

| Procédure | Étapes |
|---|---|
| Bandage | comprimer la plaie, enrouler en tours qui se chevauchent, fixer |
| Suture | pour chaque point : piquer, passer sous la plaie à la bonne profondeur, tirer à la bonne tension ; puis le nœud |
| Extraction | dégager le sang, descendre la pince sans toucher les parois, saisir, remonter lentement (à la main : plus dur) |
| Luxation | traction dans la zone de relâchement (que la douleur rétrécit), puis rotation jusqu'au repère, sans le dépasser |
| Amputation | garrot, incision circulaire, sciage en longs va-et-vient, ligature des vaisseaux |
| Défibrillation | électrodes, lecture du rythme (fibrillation, tachycardie, asystolie, sinusal) et décision, énergie, charge, choc |
| RCP | 30 compressions à la bonne profondeur et au bon rythme, bascule de la tête, 2 insufflations |
| Perfusion | garrot, palper la veine, piquer à 15–30°, desserrer |
| Drain thoracique | compter les côtes jusqu'au 5e espace, inciser, insérer sans remonter sous la côte |
| Attelle | réaligner lentement, sangler au-dessus et en dessous de la fracture |
| Cautérisation | passer la flamme sur chaque point qui saigne, sans s'attarder |
- **Clic droit sur un autre joueur** avec un objet médical : ouvre **son** panneau. Les autres joueurs peuvent vous
  bander, vous transfuser, vous défibriller, ou vous masser le cœur (**RCP**) quand vous êtes inconscient.

Objets : chiffon, bandage, bandage stérile, antiseptique, kit de suture, pince, garrot, poche de glace, attelle,
antidouleurs, morphine, antibiotiques, scie chirurgicale, défibrillateur, injecteurs (adrénaline, antiarythmique,
naloxone, procoagulant, thrombolytique, antivenin), poches de sang (avec groupe sanguin), sérum, drain thoracique,
moniteur, gourde, eau purifiée, café. Toutes les recettes utilisent des matériaux vanilla (voir JEI/EMI ou
`data/fleshbone/recipe`). On en trouve aussi dans les coffres (donjons, mines, villages, épaves, cités antiques,
chambres d'épreuves...), et le médecin et le clerc des villages en vendent.

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
  fatigue et des infections, force des drogues (`drugStrength`) et vitesse de la dépendance (`dependenceSpeed`), encombrement (capacité, multiplicateur de poids), stats gardées à la mort, second souffle,
  épisodes cérébraux, échelle des dégâts, armure par partie, vitesses de guérison, et un interrupteur par système.
- `fleshbone-client.toml` : panneau UNI-HEALTH (mode, taille), HUD, moodles, procédures (`minigames`), intensité des
  effets, caméra, flou, battements de cœur.

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

- `com.fleshbone.sim` : la simulation, en Java pur (aucun import Minecraft), entièrement testée avec JUnit ; les
  procédures de soin (`sim.procedure`) y sont aussi, l'écran ne fait que les dessiner.
- Le reste relie la simulation au jeu : dégâts, effets, objets, réseau, interface.

## Limites connues

- Textures provisoires générées par script, à remplacer par de vraies textures.
- Seuls les joueurs sont simulés ; les mobs gardent leurs points de vie.
- La thermorégulation reprend la structure de *Casualties: Unknown* mais ses taux sont recalibrés pour les climats de
  Minecraft ; le poids de départ est de 70 kg (les seuils de C:U, pensés pour 50 kg, sont multipliés par 1,4).
- Masquer la faim vanilla peut gêner les mods qui lisent la barre de nourriture (ils la voient toujours à 19).
- L'équilibrage demandera des retours de jeu.
