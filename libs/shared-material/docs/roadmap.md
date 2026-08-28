# shared-material — roadmap

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
- [x] `StrangeSpacing`, `StrangeRadii` (dérivés d'un rayon), `StrangeElevation`
- [x] `StrangeColors` — `success` / `info` / `warning` dérivés par material-kolor, le reste délégué à M3
- [x] `StrangeTheme` enveloppant `MaterialTheme`, tokens sur `CompositionLocal` statiques
- [x] `StyleScope.colors` / `.scheme` / `.spacing` / `.radii` / `.motion` — les tokens dans un `Style`
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
      un rendu, et il n'y a pas encore de harnais de test Compose ici (phase 2)
- [x] Les bornes de `StrangeMotion`, et `enabled = false` met les durées à zéro

**Catalogue de démonstration**
- [x] `examples/material-demo/catalog` — registre de `Story`, contrôles typés, disposition 3 volets
- [x] `examples/material-demo/desktop` — fenêtre, hot reload
- [x] `examples/material-demo/android` — activité, manifeste
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

## Phases suivantes

Esquisse. Chaque phase est indépendamment livrable ; l'ordre n'est pas figé.

- [ ] **2 — Formulaires.** `Field` / `FieldState`, `FormState` + `rememberForm`, validation Konform,
      `TextField`, `TextareaField`, `SelectField`, `Checkbox` (+ groupe), `RadioGroup`, `Switch`,
      `Slider`, `OtpField`, `InputGroup`, `HelperText`, `ExtendedLabel`
- [ ] **3 — Navigation et layouts adaptatifs.** `TopAppBar`, `BottomAppBar`, `NavigationRail`,
      `Sidebar`, `Tabs`, `Breadcrumb`, `Stepper`, `Switcher`, `ListDetailsLayout`, `PaneLayout`,
      `ResponsiveGrid`, `ScrollToTop`, `LoadMoreButton`
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
