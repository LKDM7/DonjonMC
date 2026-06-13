# DonjonMC

Mod NeoForge 1.21.1 inspiré de **Solo Leveling**. Les joueurs montent en rang (E vers S) en complétant des donjons procéduraux et des quêtes quotidiennes, et en débloquant des sorts et des classes.

---

## Téléchargement

Lien de DL direct (toujours à jour) :

```
https://github.com/LKDM7/DonjonMC/raw/master/releases/donjonmc-latest.jar
```

Version figée : `releases/donjonmc-2.1.5.jar`.

---

## Fonctionnalités

### Donjons procéduraux

Des portails apparaissent aléatoirement dans l'overworld toutes les 8 à 14 minutes. Quand un joueur active le portail, son groupe est téléporté dans une dimension dédiée où le donjon est généré à la volée.

| Rang | Joueurs min | Limite de temps | XP boss |
|------|-------------|-----------------|---------|
| D    | 1           | 45 min          | 800     |
| C    | 1           | 30 min          | 2 000   |
| B    | 1           | 25 min          | 5 000   |
| A    | 1           | 20 min          | 12 000  |
| S    | 4           | 15 min          | 30 000  |

- Les salles utilisent des thèmes variés (goblins, château, glace, orcs, fourmis…)
- Un boss de fin par rang : GigaGoblin (D), SpiderBoss (C), BossGolem (B), Igris (A), Kamish (S)
- En cas de mort dans un donjon, le joueur garde tout son inventaire et est renvoyé dans l'overworld
- Récompense à la victoire selon le rang, avec un bonus de carry quand l'écart de niveau est élevé

### Quêtes quotidiennes

44 quêtes réinitialisées toutes les 24h IRL. Chaque joueur reçoit 3 quêtes aléatoires : une facile, une normale et une difficile. Le timer se met en pause à la déconnexion et reprend à la reconnexion.

Types de quêtes : tuer des mobs, compléter des donjons, miner des minerais, survivre, pêcher, craft, se soigner…

### Quêtes uniques

Des succès permanents, complétables une seule fois, qui rapportent un gros bonus d'XP : paliers de niveau (10 / 25 / 50), premier boss et 25 boss, 500 monstres tués, 100 diamants minés, donjons de rang A et S, et 50 donjons complétés. La progression est visible dans l'onglet « Uniques » du menu Hunter.

### Système de punition

Les joueurs qui échouent ou ignorent leurs obligations sont téléportés dans une arène de punition avec un timer actif. L'arène génère des vagues d'ennemis, et le ver des sables (SandWorm) y patrouille.

### Progression et classes

- Système de niveaux avec points de compétence à répartir dans 5 stats : Force, Agilité, Vitalité, Intelligence, Perception
- Respec des stats possible avec `/donjonmc stats respec` ou le bouton de l'onglet Stats (une fois tous les 7 jours, coûte 5 niveaux de mod)
- À partir du niveau 50, le joueur choisit une classe :
  - **Tank** : bouclier d'équipe, résistance
  - **Assassin** : vitesse et furtivité
  - **Mage** : dégâts arcaniques, grande réserve de mana
  - **Guérisseur** : régénération et soutien

### Sources d'XP

- Mobs et boss de donjon, avec un bonus de speed-clear à la victoire
- Quêtes quotidiennes et quêtes uniques
- Minage de minerais rares (diamant, émeraude, ancient debris, or, lapis)
- Succès / advancements vanilla et des autres mods (tâche / objectif / défi)

### Sorts (intégration Iron's Spells)

Par rang, débloqués automatiquement quand le joueur atteint le rang :
- Rang D : Shockwave
- Rang C : Flamme
- Rang B : Givre
- Rang A : Tonnerre
- Rang S : Voile d'Ombre

Par classe, débloqués au choix de la classe :
- Tank : Bouclier
- Assassin : Blink
- Mage : Déluge Arcanique
- Guérisseur : Aura de Soin

### Système de raid

Les groupes se forment via `/donjonmc raid`. Le chef active le portail pour faire entrer tout le groupe. Le système gère les invitations, la synchronisation du HUD, et l'arrivée dans un donjon déjà en cours avec `/donjonmc dungeon join`.

### HUD

- Barre d'XP et badge de rang
- HUD des quêtes quotidiennes (déplaçable)
- HUD de donjon (temps restant, kills, rang)
- Timer de punition
- Popup de level up

---

## Dépendances

| Mod | Version |
|-----|---------|
| NeoForge | 21.1.230 (MC 1.21.1) |
| GeckoLib | 4.x |
| Iron's Spells 'n Spellbooks | 3.x |

---

## Build

```bash
./gradlew build
```

Le JAR se trouve dans `build/libs/`. Pour publier une version, copiez-le dans `releases/` sous les noms `donjonmc-<version>.jar` et `donjonmc-latest.jar`.

---

## Commandes

| Commande | Description |
|----------|-------------|
| `/donjonmc dungeon spawn [rang]` | Spawn un portail au pied du joueur (op) |
| `/donjonmc dungeon exit` / `join` | Quitter / rejoindre un donjon |
| `/donjonmc raid create/invite/leave` | Gestion du groupe |
| `/donjonmc stats respec` | Respec ses stats (cooldown 7 jours, coûte 5 niveaux de mod) |
| `/donjonmc punishment` | Se déclencher une punition |
| `/donjonmc punishment <joueur>` | Punir un joueur (op) |
| `/donjonmc punishment release <joueur>` | Sortir un joueur de la dimension punition (op) |
| `/donjonmc stats reset [joueur]` | Réinitialiser et rembourser les stats (op) |
| `/donjonmc level set <n> [joueur]` | Définir le niveau d'un joueur (op) |

---

## Auteur

**LKDM**, licence Apache 2.0
