# stx-material — roadmap

Vivante : une case se coche dans le changement qui la livre, jamais après. Une case décrit un
livrable vérifiable, jamais une intention.

Le critère de fin de chaque phase est le même : le catalogue s'ouvre, chaque composant livré a sa
story, `./kotlin test` est vert, la doc est à jour — et la story « écran complet » se lit sans une
ligne de plomberie.

---

## Phase 0 — Reconnaissance ✅

- [x] Cibles Apple vérifiées : `iosX64` est refusé par les artefacts Compose ; `iosArm64` et
      `iosSimulatorArm64` sont **silencieusement ignorées** sur un hôte Linux (build vert, seuls
      `jvm` et `android` compilés)
- [x] Clés `$compose.*` réellement présentes cartographiées ; cinq clés « évidentes » n'existent pas
- [x] Kotest en `test/` commun : il faut `kotest-runner-junit5` sous `@jvm` **et** `@android`
- [x] SDK Android : le toolchain provisionne le sien, `ANDROID_HOME` n'est pas consulté
- [x] API Compose Styles : présente dès CMP 1.11.1, dans `foundation`, expérimentale
- [x] Skills `compose-multiplatform` et `material3-compose` créées et documentées

## Phase 1 — Le socle et le premier lot

**Thème et tokens**
- [x] `StrangeSpacing` — la seule échelle que M3 n'a pas ; formes et élévation restent celles de M3
- [x] `StrangeColors` — `success` / `info` / `warning` dérivés par material-kolor, le reste délégué à M3
- [x] `StrangeTheme` enveloppant `MaterialTheme`, tokens sur `CompositionLocal` statiques
- [x] `StyleScope.colors` / `.scheme` / `.shapes` / `.spacing` / `.motion` — les tokens dans un `Style`
- [x] Un `StrangeStyles` accessible en `StrangeTheme.styles`, agrégeant les styles de composants

**Motion**
- [x] `StrangeMotion` — durées nommées par rôle, easings M3, interrupteur `enabled`
- [x] `Transitions` — `fade`, `riseIn`, `popIn`, `expand` et leurs sorties
- [x] `Modifier.shimmer()`, `Modifier.animateStagger(index)`

**Texte et icônes**
- [x] `TypographyVariant` — les 15 rôles M3 plus `Overline`, `Caption`, `Metric`, `Code`, `Link`
- [x] `Typography(text, variant, emphasis)` et l'échelle `Emphasis`
- [x] `Icon(icon, description, size)` — la description est exigée, pas défaultée

**Boutons**
- [x] `ButtonVariant` × `ButtonColor` — 5 × 7, résolus dans une seule fonction
- [x] `buttonStyle()` — un `Style` portant ses états `hovered` / `pressed` / `disabled` animés
- [x] `Button(text, onClick)` et `ButtonSurface { }` avec `style: Style = Style`
- [x] `IconButton`, `ButtonGroup`
- [x] `StrangeIcons` — dix vecteurs tenus ici, faute de pack d'icônes atteignable
- [x] `ResponsiveButton`

**Affichage**
- [x] `Card`
- [x] `Chip`
- [x] `StatusBadge`
- [x] `ListTile`
- [x] `EmptyState`
- [x] `Skeleton`
- [x] `Alert`

**Tests**
- [x] Dérivation des rayons et monotonie de l'échelle d'espacement
- [x] Le jeu d'icônes : chaque tracé SVG écrit à la main est réellement analysable
- [x] `Emphasis` : trois niveaux distincts, décroissants, aucun invisible
- [x] `strangeColors` : même graine, même palette ; clair et sombre diffèrent
- [ ] ~~La matrice `variant × color` est totale~~ et ~~`TypographyVariant` → `TextStyle` est
      total~~ — **non écrits, et volontairement** : les deux sont des `when` exhaustifs sur une
      enum, donc déjà garantis à la compilation ; un spec ne pourrait pas échouer. Ce qui reste
      vraiment à vérifier — qu'aucune combinaison ne rende une couleur non spécifiée — demande
      un rendu — il y en a un depuis (voir *Révisions*), mais il sert aux affirmations sur les
      pixels, pas à retester un `when` exhaustif
- [x] Les bornes de `StrangeMotion`, et `enabled = false` met les durées à zéro

**Catalogue de démonstration**
- [x] `examples/material-demo/md-catalog` — registre de `Story`, contrôles typés, disposition 3 volets
- [x] `examples/material-demo/md-desktop` — fenêtre, hot reload
- [x] `examples/material-demo/md-android` — activité, manifeste
- [x] Bascule clair/sombre et sélecteur de graine pilotant `StrangeTheme` en direct
- [x] Une story par composant livré
- [x] La story « écran complet »

**Documentation**
- [x] `docs/roadmap.md` (ce fichier)
- [x] `README.md` — la forme de la librairie, comment `StrangeTheme` s'insère, comment ajouter un composant
- [x] `docs/tokens.md` — le vocabulaire des tokens
- [x] `docs/components.md` — un composant par ligne, ses paramètres, son id de story
- [x] `examples/material-demo/README.md` — la forme de la démo, comment ajouter une story
- [x] Lignes ajoutées aux tableaux d'`AGENTS.md` et du `README.md` racine

---

## Révisions

Ce qui a bougé après coup, et pourquoi. Une phase livrée n'est pas figée — mais un changement qui
touche une case déjà cochée se raconte ici plutôt que de la décocher.

### Le thème repose sur les entrées de Material 3

Après la phase 1, `StrangeTheme` ne prenait qu'une graine et fabriquait tout le reste lui-même. Il
prend maintenant les quatre entrées de `MaterialTheme` — `ColorScheme`, `Typography`, `Shapes`,
`MotionScheme` — chacune avec un défaut, comme M3.

- [x] `StrangeTheme(colorScheme, typography, shapes, motionScheme, …)` — un appelant qui calcule
      déjà l'une des quatre la passe et garde les trois autres
- [x] `MaterialExpressiveTheme` + `MotionScheme.expressive()` par défaut
- [x] `expect`/`actual platformColorScheme` — palette du fond d'écran sur Android 12+, graine
      ailleurs ; `supportsDynamicColor` dit laquelle. **C'est la seule décision spécifique à une
      plateforme de toute la librairie** ; `StrangeThemeProvider` et tout ce qui est au-dessus est
      écrit une seule fois
- [x] `strangeColors(scheme, isDark)` ajoute les rôles sémantiques au schéma *reçu*, quel qu'il
      soit — ils ne sont plus liés au chemin de la graine
- [x] `StrangeMotion` reconstruit sur `MotionScheme` : `spatial` (ce qui bouge, peut dépasser) et
      `effects` (couleur et alpha, doit atterrir juste) × `Fast`/`Default`/`Slow`. Les durées et
      easings écrits à la main ont disparu ; `enabled = false` rend `snap()`
- [x] `Transitions` choisit son axe par moitié — un fondu est un `effects`, un glissement un
      `spatial`. Les leur donner la même courbe faisait finir une transition combinée en deux temps
- [x] Stories `motion/spatial-and-effects` et `motion/every-speed-at-once`, plus les contrôles
      Motion et « Wallpaper colours » dans l'en-tête du catalogue
- [x] Specs : les deux axes restent distincts, les trois vitesses aussi, `enabled = false` rend
      bien un `snap`, et deux thèmes par défaut sont égaux — donc installer le thème n'est pas une
      recomposition

### Les composants sont ceux de Material 3, habillés

La règle est arrivée après coup et vaut pour toutes les phases suivantes : **on ne reconstruit pas
un composant que M3 a déjà.** Sept l'avaient été ; ils enveloppent maintenant celui de M3, qui
apporte l'ondulation, l'état désactivé, la sémantique de sélection et l'accessibilité — tout ce
qu'une reconstruction jette pour redessiner un conteneur.

- [x] `Button` / `IconButton` sur `Button`, `FilledIconButton`, `FilledTonalIconButton`,
      `OutlinedIconButton` ; la matrice 5 × 7 rend un `ButtonColors` de M3 plutôt qu'un `Style`
- [x] `Card` sur `Card` / `ElevatedCard` / `OutlinedCard`, `Chip` sur `FilterChip`, `ListTile` sur
      `ListItem`, `StatusBadge` sur `Badge` — celui-ci perd son `style` : un badge est inerte
- [x] `ButtonGroup` renommé **`ButtonRow`** — M3 a un `ButtonGroup`, et c'est un contrôle segmenté
      connecté qui prend un `ButtonGroupScope`. Deux choses différentes ne partagent pas un nom
- [x] Ce que M3 n'exprime pas reste un `Style` : l'échelle au pressage, l'alpha désactivé, et
      l'apparence entière d'`Alert` — M3 n'a pas de bannière
- [x] `chipColors(hovered)` ajoute le survol que `SelectableChipColors` n'a pas — le `TODO` est
      dans la source de M3. Sans lui, sur desktop, le pointeur traverse une puce sans rien changer
- [x] La règle écrite dans `AGENTS.md` (*Building a component*) et dans `CLAUDE.md`

### Un ressort spatial dépasse sa cible, et ça casse

- [x] `Modifier.animateStagger` anime **deux** valeurs : la montée en `spatial`, le fondu en
      `effects`. Une seule courbe pour les deux envoyait l'alpha au-delà de 1
- [x] La story `motion/spatial-and-effects` utilise `offset`, pas `padding` : le carré revenait en
      négatif et l'application mourait sur *Padding must be non-negative*
- [x] Spec : tout spec `spatial` est amorti sous 1, tout spec `effects` exactement à 1 — c'est le
      fait sur lequel repose la règle

### Un harnais qui rend, parce que deux bugs ne se voyaient qu'à l'écran

`ImageComposeScene` rend un composable dans un bitmap, sans fenêtre, en millisecondes et sur un
hôte sans écran. Les specs qui s'en servent sont dans `test@jvm/` : la bibliothèque native de skiko
vient de `$compose.desktop.currentOs`, qui n'existe que sur jvm.

- [x] `ChipHoverTest` — le survol se voit sur une puce sélectionnée comme sur une autre. La teinte
      est de 8 % sur un conteneur transparent et de 16 % sur un conteneur plein : une puce
      sélectionnée est celle qu'on vient de cliquer, donc elle porte déjà la couche de focus, et
      8 % de plus n'y déplaçait le pixel que de 0,068 contre 0,145 ailleurs
- [x] `ResponsiveButtonTest` — replié, le bouton fait 40 × 40, exactement le bouton-icône de M3, et
      non une pilule dont on a retiré le texte (68 × 40, le rembourrage du label resté sur place)
- [x] `ResponsiveButton` replié **est** un `IconButton` ; `AnimatedContent` passe de l'une à l'autre

---

## Phase 2 — Formulaires

Le critère de recette : `screens/sign-up-form` — huit contrôles, une validation croisée, des erreurs
qui attendent leur tour, un bouton qui sait s'il peut être pressé — s'écrit avec **un seul**
`remember`, et c'est de l'état métier.

**L'état**
- [x] `FieldState<T>` — valeur, validité, `touched`, `dirty`, et surtout `showError` : une erreur est
      *retenue* jusqu'à ce que le champ soit quitté après avoir été saisi, ou que `validate()`
      l'exige. Un formulaire vide qui accueille le lecteur avec six reproches est ce que ce type
      empêche
- [x] `FormState` + `rememberForm` + `form.field(name, initial, vararg rules)` — il ne copie rien,
      il interroge ses champs ; `isValid`, `dirty`, `errors`, `values()`, `validate()`,
      `submit { }`, `reset()`
- [x] `Validation<T>` — une règle est une fonction `(T) -> String?`. **Aucune dépendance de
      validation** : Konform ou autre se branche en trois lignes, la librairie n'impose rien
- [x] `Rules` — `required`, `minLength`, `maxLength`, `email`, `pattern`, `digits`, `matching`,
      `checked`, `chosen`, `anyOf`, `inRange`

**Les contrôles**
- [x] `TextField` (avec `secret` et son bouton de révélation), `TextareaField` (avec compteur),
      `SelectField` — tous trois sur l'`OutlinedTextField` de M3, qui porte déjà le label flottant,
      le texte d'aide et les couleurs d'erreur
- [x] `Checkbox`, `Switch`, `RadioGroup`, `CheckboxGroup` — la ligne entière est **une** cible, label
      compris ; M3 ne livre que la case
- [x] `SliderField` — la valeur reste visible, ce que le `Slider` de M3 ne fait qu'au glissement
- [x] `OtpField` — **un** champ portant plusieurs cases, et non une case par chiffre : le collage
      fonctionne, l'effacement aussi, et le lecteur d'écran reçoit une seule saisie
- [x] `InputGroup`, `ExtendedLabel`, `HelperText`, `FieldScaffold` — l'habillage des contrôles que
      M3 laisse nus, écrit une fois
- [x] `ChevronDown`, `Eye`, `EyeOff` ajoutés au jeu d'icônes

**Vérification**
- [x] `RulesTest`, `FieldStateTest`, `FormStateTest` — l'ordre des règles est le message, une règle
      de format laisse passer le vide, une erreur attend son tour, `validate()` les fait toutes
      parler, et déclarer deux fois le même nom rend le même champ
- [x] `FormRenderTest` — chaque contrôle est réellement rendu (`ImageComposeScene`), pour la classe
      de panne qui compile puis explose à l'affichage
- [x] Une story par contrôle, plus `screens/sign-up-form`

---

## Phases suivantes

Esquisse. Chaque phase est indépendamment livrable ; l'ordre n'est pas figé.

- [x] **3 — Navigation (chrome).** `AppBar`, `Search`, `NavigationSuite` (bar / rail / drawer from
      one destinations list), `Tabs` (M3 `PrimaryTabRow`, replacing the hand-built `widgets/Tab`),
      `SegmentedControl`, `FloatingToolbar`, `Breadcrumb`, `Stepper`
- [x] **3b — Layouts Navigation 3.** `AdaptiveNavDisplay` (list-detail + supporting-pane scenes),
      `ListDetail` / `SupportingPane` metadata, destination badge *and* chip. The catalogue still
      folds itself; the rewrite onto these scenes is slice 7.
- [ ] **3c — Layouts restants.** `ListDetailsLayout` wrappers, `PaneLayout`, `ResponsiveGrid`,
      `ScrollToTop`, `LoadMoreButton`
- [ ] **4 — Surfaces et retour.** `Dialog`, `ConfirmDialog`, `Sheet`, `Drawer`, `Popover`,
      `Tooltip`, `HoverCard`, `DropdownMenu`, `ContextMenu`, `Toast` + `Toaster`, `Progress`,
      `CircularProgress`, `Spinner`, `Accordion`, `AccordionCard`, `Collapsible`, `Carousel`
- [ ] **5 — Date et heure.** `Calendar`, `CalendarYearView`, `DateField`, `DateRangeField`,
      `TimeField`, `TimePicker`, sur kotlinx-datetime
- [ ] **6 — Données.** `DataTable` (tri, sélection, colonnes, pagination), `Pagination`, `Table`,
      `SummaryData`, `Description`, `EntityHeader`, `Activity`, `Autocomplete`, `Combobox`, `Command`
- [ ] **7 — Filtres.** Le sous-système complet : schéma, chips, presets, persistance, validation
- [ ] **8 — Médias et saisie riche.** `Avatar`, `Gallery`, `LightboxGallery`, `ImageField`,
      `UploadField`, `QRCode`, `Barcode`, `Camera`, `RichTextEditor`
- [ ] **9 — Graphiques.** `Area`, `Bar`, `Line`, `Pie`, `Radar`, `Scatter`, `Funnel`, `Heatmap`
      au `Canvas`, thème dérivé des tokens
- [ ] **10 — i18n et publication.** Catalogue de messages multiplateforme, locales `en`/`fr`,
      puis `settings.publishing` et publication Maven
