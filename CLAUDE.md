# Ruflo — Claude Code Configuration

## Rules

- Do what has been asked; nothing more, nothing less
- NEVER create files unless absolutely necessary — prefer editing existing files
- NEVER create documentation files unless explicitly requested
- NEVER save working files or tests to root — use `/src`, `/tests`, `/docs`, `/config`, `/scripts`
- ALWAYS read a file before editing it
- NEVER commit secrets, credentials, .env files
- NEVER add a `Co-Authored-By` trailer to user commits unless this project's `.claude/settings.json` has `attribution.commit` set (#2078). The Claude Code Bash tool may suggest one in its default commit-message template — ignore it. `Co-Authored-By` is semantic authorship attribution under git/GitHub convention; the tool is the facilitator, not a co-author.
- Keep files under 500 lines
- Validate input at system boundaries

## Agent Comms (SendMessage-First Coordination)

Named agents coordinate via `SendMessage`, not polling or shared state.

```
Lead (you) ←→ architect ←→ developer ←→ tester ←→ reviewer
              (named agents message each other directly)
```

### Spawning a Coordinated Team

```javascript
// ALL agents in ONE message, each knows WHO to message next
Agent({ prompt: "Research the codebase. SendMessage findings to 'architect'.",
  subagent_type: "researcher", name: "researcher", run_in_background: true })
Agent({ prompt: "Wait for 'researcher'. Design solution. SendMessage to 'coder'.",
  subagent_type: "system-architect", name: "architect", run_in_background: true })
Agent({ prompt: "Wait for 'architect'. Implement it. SendMessage to 'tester'.",
  subagent_type: "coder", name: "coder", run_in_background: true })
Agent({ prompt: "Wait for 'coder'. Write tests. SendMessage results to 'reviewer'.",
  subagent_type: "tester", name: "tester", run_in_background: true })
Agent({ prompt: "Wait for 'tester'. Review code quality and security.",
  subagent_type: "reviewer", name: "reviewer", run_in_background: true })

// Kick off the pipeline
SendMessage({ to: "researcher", summary: "Start", message: "[task context]" })
```

### Patterns

| Pattern | Flow | Use When |
|---------|------|----------|
| **Pipeline** | A → B → C → D | Sequential dependencies (feature dev) |
| **Fan-out** | Lead → A, B, C → Lead | Independent parallel work (research) |
| **Supervisor** | Lead ↔ workers | Ongoing coordination (complex refactor) |

### Rules

- ALWAYS name agents — `name: "role"` makes them addressable
- ALWAYS include comms instructions in prompts — who to message, what to send
- Spawn ALL agents in ONE message with `run_in_background: true`
- After spawning: STOP, tell user what's running, wait for results
- NEVER poll status — agents message back or complete automatically

## Swarm & Routing

### Config
- **Topology**: hierarchical-mesh (anti-drift)
- **Max Agents**: 15
- **Memory**: hybrid
- **HNSW**: Enabled
- **Neural**: Enabled

```bash
npx @claude-flow/cli@latest swarm init --topology hierarchical --max-agents 8 --strategy specialized
```

### Agent Routing

| Task | Agents | Topology |
|------|--------|----------|
| Bug Fix | researcher, coder, tester | hierarchical |
| Feature | architect, coder, tester, reviewer | hierarchical |
| Refactor | architect, coder, reviewer | hierarchical |
| Performance | perf-engineer, coder | hierarchical |
| Security | security-architect, auditor | hierarchical |

### When to Swarm
- **YES**: 3+ files, new features, cross-module refactoring, API changes, security, performance
- **NO**: single file edits, 1-2 line fixes, docs updates, config changes, questions

### 3-Tier Model Routing

| Tier | Handler | Use Cases |
|------|---------|-----------|
| 1 | Agent Booster (WASM) | Simple transforms — skip LLM, use Edit directly |
| 2 | Haiku | Simple tasks, low complexity |
| 3 | Sonnet/Opus | Architecture, security, complex reasoning |

## Memory & Learning

### Before Any Task
```bash
npx @claude-flow/cli@latest memory search --query "[task keywords]" --namespace patterns
npx @claude-flow/cli@latest hooks route --task "[task description]"
```

### After Success
```bash
npx @claude-flow/cli@latest memory store --namespace patterns --key "[name]" --value "[what worked]"
npx @claude-flow/cli@latest hooks post-task --task-id "[id]" --success true --store-results true
```

### MCP Tools (use `ToolSearch("keyword")` to discover)

| Category | Key Tools |
|----------|-----------|
| **Memory** | `memory_store`, `memory_search`, `memory_search_unified` |
| **Bridge** | `memory_import_claude`, `memory_bridge_status` |
| **Swarm** | `swarm_init`, `swarm_status`, `swarm_health` |
| **Agents** | `agent_spawn`, `agent_list`, `agent_status` |
| **Hooks** | `hooks_route`, `hooks_post-task`, `hooks_worker-dispatch` |
| **Security** | `aidefence_scan`, `aidefence_is_safe`, `aidefence_has_pii` |
| **Hive-Mind** | `hive-mind_init`, `hive-mind_consensus`, `hive-mind_spawn` |

### Background Workers

| Worker | When |
|--------|------|
| `audit` | After security changes |
| `optimize` | After performance work |
| `testgaps` | After adding features |
| `map` | Every 5+ file changes |
| `document` | After API changes |

```bash
npx @claude-flow/cli@latest hooks worker dispatch --trigger audit
```

## Agents

**Core**: `coder`, `reviewer`, `tester`, `planner`, `researcher`
**Architecture**: `system-architect`, `backend-dev`, `mobile-dev`
**Security**: `security-architect`, `security-auditor`
**Performance**: `performance-engineer`, `perf-analyzer`
**Coordination**: `hierarchical-coordinator`, `mesh-coordinator`, `adaptive-coordinator`
**GitHub**: `pr-manager`, `code-review-swarm`, `issue-tracker`, `release-manager`

Any string works as a custom agent type.

## Build & Test

- ALWAYS run tests after code changes
- ALWAYS verify build succeeds before committing

```bash
npm run build && npm test
```

## CLI Quick Reference

```bash
npx @claude-flow/cli@latest init --wizard           # Setup
npx @claude-flow/cli@latest swarm init --v3-mode     # Start swarm
npx @claude-flow/cli@latest memory search --query "" # Vector search
npx @claude-flow/cli@latest hooks route --task ""    # Route to agent
npx @claude-flow/cli@latest doctor --fix             # Diagnostics
npx @claude-flow/cli@latest security scan            # Security scan
npx @claude-flow/cli@latest performance benchmark    # Benchmarks
```

26 commands, 140+ subcommands. Use `--help` on any command for details.

## Setup

```bash
claude mcp add claude-flow -- npx -y @claude-flow/cli@latest
npx @claude-flow/cli@latest daemon start
npx @claude-flow/cli@latest doctor --fix
```

**Agent tool** handles execution (agents, files, code, git). **MCP tools** handle coordination (swarm, memory, hooks). **CLI** is the same via Bash.

---

# Feature en cours : Épreuve de classe v2 (donjon 3 phases)

## Contexte décidé (ne pas remettre en question sans demander)

- Au niveau 50, le joueur CHOISIT sa classe dans le menu Hunter (Tank /
  Assassin / Mage / Guérisseur), puis valide par une épreuve de combat.
  `determineClass()` (déduction par stats) est SUPPRIMÉ.
- Épreuve = donjon instancié dans la dimension `donjonmc:class_trial`,
  arène posée depuis le template `cataclysm:burning_arena1` (mod L_Ender's
  Cataclysm utilisé comme dépendance runtime, PAS de code/assets copiés,
  licence CC BY-NC-ND). Fallback arène en briques si Cataclysm absent.
- 3 phases : vague de mobs donjonmc → vague + Igris (donjonmc:igris) →
  vague + Ignis (`cataclysm:ignis`, stats intactes, fallback Wither Skeleton).
- Victoire → classe choisie appliquée + sort de classe + retour overworld.
- Mort OU déconnexion → échec + cooldown 24h IRL (champ `lastTrialFailMs`
  dans PlayerData, même pattern que `lastRespecMs`).
- L'arène est RETIRÉE à chaque fin d'épreuve (victoire comme défaite),
  effacement budgété sur plusieurs ticks (file `cleanupQueue`).
- Instances simultanées : 1 slot par joueur, grille espacée de 1024 blocs.
- Drops + XP des mobs d'épreuve annulés (tag NBT `donjonmc_trial_owner`,
  events LivingDropsEvent / LivingExperienceDropEvent).

## Fichiers modifiés

- `src/main/java/neoforge/donjonmc/player/ClassTrialHandler.java` : RÉÉCRIT
  (machine à phases, sessions, placement/retrait d'arène, spawn Ignis par
  lookup `EntityType.byString`). Fichier fourni séparément.

## Reste à faire (à traiter dans cet ordre)

### 1. PlayerData.java — ajouter le champ cooldown 24h

```java
private long lastTrialFailMs; // timestamp ms du dernier échec ; 0 = jamais

public long getLastTrialFailMs()       { return lastTrialFailMs; }
public void setLastTrialFailMs(long v) { this.lastTrialFailMs = v; }
```

Dans le CODEC, après `lastRespecMs` :
```java
Codec.LONG.optionalFieldOf("lastTrialFailMs", 0L).forGetter(d -> d.lastTrialFailMs)
```
Mettre à jour le constructeur complet + le constructeur vide (`..., 0L)`).
Si un STREAM_CODEC réseau existe, y ajouter aussi (le menu Hunter en a besoin).

### 2. Branchement du packet de choix de classe

L'ancienne signature `startTrial(player)` n'existe plus. Nouvelle :
```java
ClassTrialHandler.startTrial(player, PlayerClass.fromOrdinal(packet.classOrdinal()));
```
Le packet client→serveur doit porter l'ordinal de la classe choisie.

### 3. HunterScreen — onglet de choix de classe

- 4 cartes : nom de la classe + description + aperçu du sort de classe.
- Bouton "Commencer l'épreuve" grisé si `ClassTrialHandler.cooldownRemainingMs(data) > 0`.
- Si grisé, afficher "Disponible dans Xh Ymin".
- Cliquer envoie le packet du point 2.

### 4. Traductions (fr_fr.json et en_us.json)

```json
"donjonmc.trial.level_too_low":    "Vous devez être niveau 50 pour passer l'épreuve de classe.",
"donjonmc.trial.already_has_class":"Vous avez déjà une classe.",
"donjonmc.trial.cooldown":         "Épreuve échouée récemment. Réessayez dans %sh%smin.",
"donjonmc.trial.start":            "Épreuve de classe : %s. Survivez aux trois phases !",
"donjonmc.trial.phase":            "Phase %s !",
"donjonmc.trial.complete":         "Classe %s débloquée. Félicitations, Hunter.",
"donjonmc.trial.fail":             "Épreuve échouée. Revenez dans 24h.",
"donjonmc.trial.error":            "Erreur : dimension d'épreuve introuvable.",
"donjonmc.trial.already_started":  "Une épreuve est déjà en cours.",
"donjonmc.trial.boss":             "Gardien de l'Épreuve"
```

### 5. Serveur — jars et config

- Ajouter côté serveur ET client : `L_Ender's Cataclysm 1.21.1-3.29` +
  `Lionfish API 3.0 NeoForge 1.21.1`. Curios déjà présent.
- `config/cataclysm-common.toml` : tous les `*_spawn_weight` à 0.
- Datapack qui vide les 11 structure_sets de Cataclysm (worldgen coupé,
  le mod ne sert qu'au boss).

## Points de test

1. Boot serveur complet (385 mods + 2 nouveaux), vérifier aucun crash.
2. `/place template cataclysm:burning_arena1` en créatif — si le morceau
   n'est pas la plateforme centrale, tester burning_arena2 à 8 et changer
   `ARENA_TEMPLATE` dans ClassTrialHandler.
3. Épreuve gagnée → classe attribuée + sort + retour overworld.
4. Mort en phase 3 → cooldown 24h dans PlayerData, bouton grisé dans le menu.
5. Déconnexion en phase 2 → même résultat que mort.
6. Deux joueurs en simultané → slots distincts, arènes séparées.
7. Arène effacée dans les secondes suivant la fin (victoire ou défaite).
8. Ignis ne drop rien, pas d'XP vanilla.
