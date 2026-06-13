# Progress

Journal de suivi du projet DonjonMC. Les entrées datées ci-dessous sont ajoutées
automatiquement en fin de session (plugin cwp-claude-progress).

---

## 2026-06-09 — v2.0.3
- 3 fix : inventaire conservé à la mort en donjon (snapshot au décès), écran de mort débloqué (plus de soin sur joueur mourant via level-up), portail cliquable en zone protégée (handler EntityInteract HIGHEST + receiveCanceled).
- Release : `releases/donjonmc-2.0.3.jar` + `donjonmc-latest.jar` (lien DL stable), README humanisé. Poussé sur master.
- Next : tester le portail en zone DashBoardAdmin sur le serveur ; si bloqué côté client, exempter l'entité `donjonmc:dungeon_portal` dans DashBoardAdmin.

## 2026-06-09 — v2.0.4
- Fix serveur dédié : la restauration de l'inventaire en donjon est passée de l'event Clone à `PlayerRespawnEvent` priorité LOWEST (un autre mod du modpack écrasait les items pendant le respawn). Marchait en solo, pas sur serveur. Validé via serveur dédié dev local.
- Release 2.0.4 poussée (`donjonmc-2.0.4.jar` + `latest`). Next : déployer 2.0.4 sur le vrai serveur, tous les joueurs en 2.0.4.

## 2026-06-09 — Résistance entrée punition (non release)
- Ajout Résistance V 5s à l'entrée de la dimension punition (`PunishmentManager.teleportTo`, conditionné à la dim punition). Donjon laissé à 5s. Compile OK.
- Next : décider build/release (bump 2.0.5 + jars releases/) + commit/push, ou tester d'abord en jeu.

## 2026-06-09 — XP de minage (non release)
- Nouvelle source d'XP : miner des minerais rares (diamant 50, émeraude 60, ancient debris 75, or 12, lapis 8…) via `onOreMined` dans PlayerEventHandler. XP personnel (`addXpDirect`), anti-farm Silk Touch, pas en créatif. Compile OK.
- Next : décider build/release (2.0.6) + push, ou tester en jeu et ajuster les valeurs.

## 2026-06-09 — Quêtes uniques (non release)
- Nouveau système de quêtes uniques (succès permanents, 10 quêtes : paliers niveau, boss, 500 mobs, 100 diamants, donjons rang A/S, 50 donjons) avec XP one-time. ≈8 fichiers : UniqueQuestDef/Registry/Data/Manager, SyncUniqueQuestPacket + ClientUniqueQuestCache, attachment UNIQUE_QUEST, hooks events, onglet GUI « Uniques » dans HunterScreen, lang FR/EN. Compile OK, client lancé pour test.
- Next : valider l'onglet en jeu + ajuster valeurs, puis build/release (2.0.6) + push.

## 2026-06-09 — XP succès + commandes admin (non release)
- XP de succès : `onAdvancementEarned` dans PlayerEventHandler (TASK 100 / GOAL 300 / CHALLENGE 800, ignore advancements sans display). Vanilla + mods conservés.
- 3 commandes admin (op2) : `/donjonmc punishment release <j>` (+ `PunishmentManager.releasePlayer`), `/donjonmc stats reset [j]` (rembourse les stats), `/donjonmc level set <n> [j]` (n'existait pas vraiment). Keys FR/EN ajoutées. Compile OK.
- Next : build/release 2.0.6 + push (regroupe résistance punition, minage, quêtes uniques, succès, commandes admin).

## 2026-06-09 — Respec joueur (non release)
- `/donjonmc stats respec` (joueur) : rembourse les stats sous conditions = niveau ≥ 31 ET cooldown 7j IRL, coûte -30 niveaux. Nouveau champ persistant `lastRespecMs` dans PlayerData (codec rétro-compat). `/donjonmc stats reset` reste admin-only. Keys FR/EN. Compile OK.
- Next : build/release 2.0.6 + push.

## 2026-06-09 — Respec : bouton GUI + coût ajusté (non release)
- Coût respec 30 → 5 niveaux de mod. Logique extraite dans `PlayerEventHandler.tryRespec` (partagée commande + bouton). Nouveau `RespecStatsPacket` (client→serveur) + bouton « Respec stats » dans l'onglet Stats. Sync vérifiée (barre xp/niveau OK, pas de faux popup level-up). README + lang à jour. Compile OK.
- Next : relancer le client pour voir le bouton, ou build/release 2.0.6 + push.

## 2026-06-10 — Nettoyage différé des zones de donjon (non release, post-2.0.6)
- 3 min après la fin d'un donjon, la zone est vidée en air (cube ~360×360, y58-110) progressivement (40k/tick, ignore l'air), puis l'ID est recyclé → plus de chevauchement de structures. Hooks : `scheduleZoneClear`/`tickZoneClears` dans DungeonManager, appel par tick dans DungeonEventHandler.onDungeonTick. Compile OK.
- Limite : file en mémoire, perdue si redémarrage pendant les 3 min. Next : tester (baisser le délai temporairement), éventuellement ajouter un nettoyage à la réutilisation de zone, puis build/release 2.0.7 + push.

## 2026-06-10 — Épreuve de classe v2 (non release)
- Les 5 points « Reste à faire » faits : `lastTrialFailMs` (PlayerData + sync client), `ChooseClassPacket`, onglet choix de classe HunterScreen (4 cartes + bouton cooldown), traductions FR/EN, jars Cataclysm 3.30 + Lionfish 3.0 (pas 3.29/2.x : crash AbstractMethodError), datapack 11 structure_sets vides + cataclysm-common.toml spawns à 0.
- Fix post-test : l'arène = 8 templates assemblés (offsets du bytecode du mod), TP/mobs calés sur le sol réel via `findArenaFloorY`. Serveur dédié dev lancé (`runserver/`, run séparé dans build.gradle).
- Next : valider l'épreuve complète sur le serveur local (victoire, mort, déco, 2 joueurs), puis build/release + déploiement prod (jars + config + datapack).

## 2026-06-10 — Équilibrage épreuve de classe (non release)
- Phase 1 : +50 % de mobs (8/3/4 = 15). 30 s de préparation après le TP et entre les phases (compte à rebours barre d'action, `tickPendingPhases`). Titres plein écran stylés + sons par phase (clés `donjonmc.trial.phase.title/sub.1-3`). Ignis −15 % PV (attribut MAX_HEALTH après finalizeSpawn).
- Next : valider le rythme en jeu sur le serveur local, puis dérouler les 8 points de test de CLAUDE.md avant release.

## 2026-06-10 — Équilibrage v2 + refonte GUI (non release)
- Épreuve : phase 1 à 20 mobs, phase 2 à 11 + Igris, Ignis à 76,5 % PV, TP retour différé 10 s (slot libéré après effacement complet), netherrack + structure blocks exclus du placement. `/donjonmc trial reset` (op) pour rejouer l'épreuve. Toggle Perception (Glowing) : PlayerData + packet + bouton Stats.
- GUI Hunter refondu : panneau 320×240, onglets soulignés, fadeLine, boutons unifiés (btnToggle/btnAction), mini-jauges de stats, badge de rang, cartes de classe avec hover. Incident : en_us.json corrompu par PowerShell (encodage), restauré via git + réappliqué.
- Next : valider le visuel en jeu et l'arène propre, dérouler les points de test, puis release + déploiement (jars Cataclysm 3.30 + lionfishapi-3.0, config, datapack).

## 2026-06-11 — v2.1.1 : tooltips des parchemins de sort
- `getUniqueInfo()` ajouté aux 9 sorts (rang D→S + 4 classes) : ligne de description (`spell.donjonmc.*.desc` FR/EN) + stats dynamiques via les clés `ui.irons_spellbooks.*` (dégâts, rayon, portée, durée…). Build OK, release 2.1.1 poussée (jar + latest).
- Next : dérouler les 8 points de test de l'épreuve de classe sur le serveur local, puis déploiement prod (jars Cataclysm/Lionfish, config, datapack).

## 2026-06-10 — Stats de carrière dans le profil (non release)
- Encart « Carrière » dans l'onglet Profil : mobs tués / morts / temps de jeu lus depuis les stats vanilla persistantes (`player.getStats()`, jamais reset), donjons clearés via nouveau champ `dungeonsCleared` dans PlayerData (incrémenté dans `DungeonManager.onBossKilled`). `SyncPlayerDataPacket.from(player, data)` porte les 4 valeurs.
- Next : tester l'encart en jeu, puis dérouler les points de test de l'épreuve avant release.

## 2026-06-12 — v2.1.2 : halo de portail
- Contour lumineux (Glowing, comme la Perception) sur `DungeonPortalEntity` quand un joueur est à moins de 120 blocs (100 au départ, monté à 120 après test en jeu) : check 1×/s dans `tick()`, flag synchronisé donc visible par tous les joueurs proches, à travers les murs. S'applique aussi aux portails de sortie.
- Testé sur serveur dédié local, release 2.1.2 poussée (jar + latest, README à jour).
- Next : dérouler les 8 points de test de l'épreuve de classe, puis déploiement prod (jars Cataclysm/Lionfish, config, datapack).

## 2026-06-12 — Durée de vie des portails 30 → 10 min (non release, non compilé)
- Portail non utilisé : despawn à 10 min au lieu de 30 (constructeur `DungeonPortalEntity`). Les portails déjà posés gardent leur compteur NBT. Sorties inchangées (5 min / 2 h).
- Next : inclure dans la prochaine release (2.1.3 ou groupée), compiler avant.

## 2026-06-12 — Config TOML + son de gate (non release)
- `Config.java` réécrit (boilerplate template viré) : durées de portail, intervalle de spawn 8-14 min, rayon halo, son de gate, cooldowns respec/épreuve → `config/donjonmc-common.toml`, reload à chaud. 5 classes branchées dessus ; `COOLDOWN_MS`/`RESPEC_COOLDOWN_MS` remplacés par des méthodes.
- Son de gate : `END_PORTAL_SPAWN` pitch 0.7 au spawn overworld (naturel + commande), portée ~120 blocs configurable. Compile OK.
- Next : tester en jeu (son + reload config), distribuer le même TOML aux clients si `trialCooldownHours` change (affichage GUI), puis release 2.1.3.

## 2026-06-12 — Son d'ambiance de portail (non release)
- `PORTAL_AMBIENT` (whoosh Nether) joué toutes les 4 s par le portail tant qu'un joueur est à moins de 25 blocs, pitch aléatoire 0.6-1.0. Clé `portals.ambientSoundRangeBlocks` (défaut 25, 0 = off), tous portails y compris sorties. Compile OK.
- Next : tester les deux sons en jeu, puis release 2.1.3 (regroupe config TOML + sons).

## 2026-06-12 — Persistance des zones de donjon (non release)
- Nouveau `ZoneClearSavedData` (`data/donjonmc_zones.dat` overworld) : file de nettoyage (avec curseur de reprise), freeIds et idCounter survivent au redémarrage — fin du risque de génération par-dessus une zone sale (idCounter repartait à 0). Chargé au `ServerStartedEvent`, dirty à chaque mutation. Compile OK.
- Next : tester (finir un donjon, couper pendant les 3 min, relancer → zone effacée), puis release 2.1.3 (config TOML + sons + persistance).

## 2026-06-12 — Fix : XP de mod avec les sorts (non release)
- Les kills au sort ne donnaient pas d'XP de mod : la source de dégâts n'était pas le joueur (projectile/zone/DoT). Nouveau `PlayerEventHandler.killerPlayer()` (source directe → owner TraceableEntity → getKillCredit vanilla), appliqué aussi aux compteurs de quêtes quotidiennes. Compile OK.
- Next : tester avec un sort de chaque type (projectile, zone, DoT), puis release 2.1.3 groupée.

## 2026-06-12 — Release 2.1.3
- Regroupe : config TOML + sons de gate/ambiance, portails 10 min, persistance des zones, fix XP sorts. 4 commits poussés (66b43aa, 1a03609, 0ae4562, 8452c01), jars `releases/donjonmc-2.1.3.jar` + latest, README à jour.
- Next : déployer le jar côté serveur ET clients, vérifier la génération de `config/donjonmc-common.toml`, puis dérouler les 8 points de test de l'épreuve de classe (toujours en attente).

## 2026-06-13 — XP de minage −33 % (non release)
- Table `ORE_XP` réduite d'un tiers : debris 75→50, émeraude 60→40, diamant 50→33, or 12→8, lapis 8→5, or Nether 6→4. Compile OK.
- Next : release 2.1.4 (seule modif en attente) ou regrouper avec la prochaine fournée.

## 2026-06-13 — Régén mana −33 % (non release)
- Formule de regen : `1.0 + 0.08×Intel` → `0.67 + 0.054×Intel` par seconde (intel 50 : 5,0 → 3,4/s). ×2 Guérisseur conservé, appliqué après réduction. Compile OK.
- Next : release 2.1.4 (nerfs minage + mana en attente).

## 2026-06-13 — Release 2.1.4
- Nerfs −33 % (XP minage + regen mana) buildés et poussés : commits 84ecaf9 + 5fdb2a1, jars `releases/donjonmc-2.1.4.jar` + latest, README à jour.
- Next : déployer serveur + clients, puis dérouler les 8 points de test de l'épreuve de classe (toujours en attente).

## 2026-06-13 — Double donjon (non release)
- Un portail C a 15 % de chance (config `fakeDungeonChancePercent`) de cacher un donjon B (2/3) ou A (1/3) : rang réel en champ serveur non synchronisé + NBT, conditions d'entrée et annonces = C, révélation après TP (titre « DOUBLE DONJON », son dragon, clés trap.title/sub/chat FR/EN). Instance générée au vrai rang. Compile OK.
- Next : tester avec chance à 100 + `/donjonmc dungeon spawn C`, puis release 2.1.5.

## 2026-06-13 — Release 2.1.5
- Double donjon buildé et poussé : commits 0f27263 + dce77ac, jars `releases/donjonmc-2.1.5.jar` + latest, README à jour.
- Next : déployer serveur + clients, tester le double donjon en jeu (chance à 100), et toujours les 8 points de test de l'épreuve de classe.

## 2026-06-13 — Releases 2.1.6 / 2.1.7 / 2.1.8
- 2.1.6 : fix XP des mobs tués au sort — les 6 sorts custom infligeaient des dégâts sans attaquant (`damageSources().magic()/.onFire()`, foudre sans `setCause`) ; passage à `DamageSources.applyDamage` + `getDamageSource(caster)` (SpellDamageSource porte le lanceur). 2.1.7 puis 2.1.8 : −25 % XP de minage à chaque fois (table `ORE_XP`). Tous buildés/poussés (6a9d95a, 190f93d, d5b9dd4).
- Next : déployer le dernier jar serveur + clients ; tester qu'un sort de rang donne bien l'XP au kill.

## 2026-06-13 — Buff boss rang D + audit boss (non release)
- Boss rang D (Giga Goblin) : `BOSS_SCALE[D]` 2.0/2.0 → 2.5/2.3 (PV +25 %, dégâts +15 %). Audit complet des 5 boss fait : PV/dégâts réels = base entité × BOSS_SCALE. Repéré inversion PV au rang A (Igris 240 < Golem B 280, base HP=20 trop basse) et dégâts B/A/S qui one-shot les joueurs du rang.
- Next : décider d'appliquer le lissage proposé (remonter base HP Igris à 50, aplatir multiplicateurs ATK) puis release.

## 2026-06-13 — Épreuve de classe 4 phases + buff sorts de classe (non release)
- Épreuve : P1 = 50 gobelins + 1 Giga Gobelin ; nouvelle P2 = donjon rang B (25 mobs ×3.0 PV/×2.5 ATK + Golem ×8.75/×5.5) ; ex-P2 Igris → P3, ex-P3 Ignis → P4. MAJ lang FR/EN (titres/sous-titres phase 4), seuil victoire `>= 4`.
- Sorts de classe rendus OP : Tank (Abso 32 PV + Régén + ignifuge, 28s), Healer (soin 31 + purge malus + Abso, rayon 27), Mage (54×10 cibles + brûlure + Faiblesse/Lenteur), Assassin (54, exécution ×1.5 <40 % PV, invis+célérité au repli).
- Next : compiler (`./gradlew build`) pour valider les imports ajoutés, tester équilibrage en jeu, puis release.
