# DonjonMC

Mod NeoForge 1.21.1 inspiré de **Solo Leveling**. Les joueurs progressent en rang (E → S) en complétant des donjons procéduraux, des quêtes quotidiennes et en débloquant des sorts et classes.

---

## Fonctionnalités

### Donjons procéduraux

Des portails apparaissent aléatoirement dans l'overworld toutes les 8–14 minutes. En entrant, le groupe est téléporté dans une dimension dédiée avec un donjon généré à la volée.

| Rang | Joueurs min | Limite de temps | XP boss |
|------|-------------|-----------------|---------|
| D    | 1           | 45 min          | 800     |
| C    | 1           | 30 min          | 2 000   |
| B    | 1           | 25 min          | 5 000   |
| A    | 1           | 20 min          | 12 000  |
| S    | 4           | 15 min          | 30 000  |

- Les salles sont générées avec des thèmes variés (goblins, château, glace, orcs, fourmis…)
- Un boss de fin par rang : GigaGoblin (D), SpiderBoss (C), BossGolem (B), Igris (A), Kamish (S)
- **Mort dans un donjon : l'inventaire est conservé intégralement**
- Récompense à la victoire selon rang + bonus de carry si écart de niveau élevé

### Quêtes quotidiennes

44 quêtes réinitialisées toutes les 24h IRL. Chaque joueur reçoit 3 quêtes aléatoires (1 facile, 1 normale, 1 difficile). Le timer est mis en pause à la déconnexion et reprend à la reconnexion.

Types de quêtes : tuer des mobs, compléter des donjons, miner des minerais, survivre, pêcher, craft, se soigner…

### Système de punition

Les joueurs qui échouent ou ignorent leurs obligations sont téléportés dans une arène de punition avec un timer actif. L'arène génère des vagues d'ennemis. Le ver des sables (SandWorm) patrouille l'arène.

### Progression & Classes

- Système de niveaux avec points de stats (STR, AGI, INT, VIT, SEN, LUC)
- À partir du niveau 50, choix d'une classe :
  - **Tank** — bouclier d'équipe, résistance
  - **Assassin** — vitesse et furtivité
  - **Mage** — dégâts arcaniques, grande réserve de mana
  - **Guérisseur** — régénération et soutien

### Sorts (Iron's Spells integration)

**Par rang** (débloqués automatiquement à l'atteinte du rang) :
- Rang D : Shockwave
- Rang C : Flamme
- Rang B : Givre
- Rang A : Tonnerre
- Rang S : Voile d'Ombre

**Par classe** (débloqués au choix de classe) :
- Tank : Bouclier
- Assassin : Blink
- Mage : Déluge Arcanique
- Guérisseur : Aura de Soin

### Système de Raid

Formation de groupes via `/donjonmc raid`. Le chef active le portail pour faire entrer tout le groupe. Gestion des invitations, synchronisation HUD, rejoindre un donjon en cours avec `/donjonmc dungeon join`.

### HUD

- Barre d'XP et badge de rang
- HUD quêtes quotidiennes (déplaçable)
- HUD donjon (temps restant, kills, rang)
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

Le JAR se trouve dans `build/libs/`.

---

## Commandes

| Commande | Description |
|----------|-------------|
| `/donjonmc dungeon spawn [rang]` | Spawn un portail au pied du joueur |
| `/donjonmc dungeon exit` | Quitter le donjon de force |
| `/donjonmc dungeon join` | Rejoindre le donjon de son groupe |
| `/donjonmc raid create/invite/leave` | Gestion du groupe |
| `/donjonmc level set <n>` | Définir le niveau (op) |
| `/donjonmc punishment trigger` | Déclencher une punition (op) |

---

## Auteur

**LKDM** — Licence Apache 2.0
