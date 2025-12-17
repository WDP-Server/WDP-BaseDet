# WDP-BaseDet Development Log

## Original Prompt

```
### Base Detection

You are a senior plugin dev

Today you will create a Base Detection plugin or Minecraft 1.21.8 paper named WDP-BaseDet

This plugin will:

- automatically detect a users base
- Prompt the user
- Protect the base if the user is offline
- Trust command
- Notify on discord
- Detect expansion
- Smart combat

Auto detect user base

So you will implement an algorithm that detects a base in build

The score will be based on:

- placing blocks
- Breaking blocks
- Placing chests and other workbenches (Bigger impact)
- Walking (very small)

the score will be stored in a database and it will INCREASE based on the above parameters BUT it will decrease over time so the detection is accurate (DONT DECREASE when the player is OFFLINE)

Grant a small bonus points if the interactions take place close to each other, the score is a confidence and all user interaction will be stored in the database for later analysis when the detection reaches a point (see below)

All the above must be configurable

Prompt the user

When the score reaches a certain point (conf) it will prompt the user to confirm or deny, automatically you confirm over a span of 10 minutes (stop that time on leave)

Both grant a small reward (look at SkillCoins for a balanced number) but auto doesn't 

When detected show a small box of dust around the detected area, that will be a box grouping around ALL user placed or broken blocks with an offset of 3 (conf) and 6 for height, show a horizontal rectangle at hy view height so I can see better (small note if the base is underground or I'm inside of it make that rectangle touch the blocks for where I'm at) 

some blocks like torches (conf) are not included and be smart about exploiting and allow only a max area size (conf)

Make sure the detection is correct in both length, width and height 

Protect the base when offline

If the user is OFFLINE you will deny any players from interacting (interaction, break or place) with the protected area if they are not trusted, see next section. If the player of the protected area is online greafing is allowed

Trust command

The command will open a gui to manager trusted players with options in a sub menu on what they can do things both for online and offline (Owner)

For menu this applies over the whole plugin: use the navbar from the SkillCoins category (so where i can buy items) shop menu and use UPDATE to display sub menus so the buttons ALWAYS work

Trust with a name as option trusts the player and lets it do the default (conf) things in a base.

Notify on Discord

Use DiscordSRV as an optional dependency

Dm the user if someone enters there base if they are offline, make the message say that if the user replies or types trust in the dm the user will get trusted

Detect expansion 

Even if the detection has detected base it must still look for expansion, if the base expands by a configured amount it will prompt the user to confirm or deny with NO reward

A selector stick or tool

Simple tool for the user to view the protected area and modify it at a price per block (balanced) also add a view command

Smart combat

If the user gets combat flagged by CMI its base is open domain if he is near it (conf)

So the user that attacks can continue its fight in his base 

Once the combat flag is over but the user and the attacker are still in the area, fighting is allowed but block break place never, once the attacker leaves al his privileges get revoked, notify the user and attacker about this mechanic once one of them enters the base while flagged in combat

Commands 

Add many debug and admin commands

View, trust and more user commands

Before starting gently think about questions you have and THEN after I answer if needed

This is a hard and long task, COME ON YOU ARE THE GREATEST DEV YOU CAN DO THIS

Work structured and step by step, use maven and DONT LOSE YOURSELF 

Write ONE doc at the end where you document EVERYTHING!

If you are done take the deploy script from the SkillCoins, remove the y/n from the end and double check if the container is the correct one!
```

---

## Project Overview
WDP-BaseDet is a Minecraft Paper 1.21.6 plugin for automatic base detection and protection.

---

## Phase 1: Core Detection System (COMPLETED ✓)

### Completed Tasks

#### Project Setup
- [x] Created Maven project structure with pom.xml
- [x] Configured dependencies: Paper API 1.21.6, Vault, DiscordSRV, HikariCP, SQLite, MySQL
- [x] Created plugin.yml with commands and permissions
- [x] Created comprehensive config.yml with all settings
- [x] CMI integration via reflection (no compile-time dependency)
- [x] Custom combat detection system as CMI fallback

#### Core Classes
- [x] `WDPBaseDetPlugin.java` - Main plugin class with manager initialization
- [x] `ConfigManager.java` - Configuration management with caching
- [x] `DatabaseManager.java` - SQLite/MySQL support with HikariCP connection pooling

#### Model Classes
- [x] `Base.java` - Base data model with location, bounds, owner info
- [x] `BoundingBox.java` - Bounding box calculations with contains/overlaps methods
- [x] `TrustEntry.java` - Trust relationship with granular online/offline permissions
- [x] `PlayerInteraction.java` - Interaction tracking for detection algorithm

#### Detection System
- [x] `ScoreManager.java` - Score tracking with proximity bonus (1.5x within 10 blocks)
- [x] `DetectionManager.java` - Bounding box calculation, prompt system, auto-confirm timer

#### Listeners
- [x] `BlockListener.java` - Tracks block place/break for scoring
- [x] `PlayerListener.java` - Tracks chest interactions, walking activity
- [x] `ProtectionListener.java` - Enforces base protection rules

#### Managers
- [x] `ProtectionManager.java` - Protection logic with owner online/offline states
- [x] `TrustManager.java` - Trust relationship management
- [x] `ParticleManager.java` - Particle visualization for bounding boxes

#### Integrations
- [x] `EconomyIntegration.java` - Vault/SkillCoins economy support
- [x] `CMIIntegration.java` - CMI combat tag detection
- [x] `DiscordIntegration.java` - DiscordSRV DM notifications

#### Commands
- [x] `BaseDetCommand.java` - Main /basedet command with subcommands:
  - confirm, deny, view, detect, score, help, reload, debug, force, info, list, delete
- [x] `TrustCommand.java` - /trust command with add, remove, list, quick-add

#### UI System
- [x] `MenuManager.java` - GUI trust menu with permissions editing

#### Deployment
- [x] `deploy.sh` - Automated build and deploy script
- [x] **BUILD SUCCESS** - Plugin compiled and deployed to test server (2025-12-14)

### Build Issues Resolved
1. **CMI-API not available** - Removed compile-time dependency, using pure reflection
2. **Scheduler API** - Fixed `runTaskTimerAsync` to `runTaskTimerAsynchronously`
3. **Paper API version** - Updated to 1.21.6-R0.1-SNAPSHOT

### Combat System
- **CMI Integration**: Uses CMI if available on server
- **Custom Fallback**: If CMI unavailable, uses configurable custom combat detection
  - Configurable duration (default 15s)
  - PvP trigger (player vs player)
  - Projectile trigger
  - Boss bar display during combat
  - Message notifications
  
### Detection Algorithm Summary
1. Player places/breaks blocks → Score increases
2. Player opens chests → Score increases
3. Walking in area → Score increases
4. Proximity bonus: 1.5x if within 10 blocks of activity center
5. Score decays over time (configurable)
6. When threshold reached → Player prompted to confirm/deny base
7. Auto-confirm after timeout (configurable)
8. Bounding box calculated from interaction cluster

### Database Schema
- `player_scores` - UUID, score, last_activity
- `interactions` - UUID, world, x, y, z, type, timestamp
- `bases` - id, owner_uuid, world, bounds, created_at
- `trust` - base_id, trusted_uuid, permissions (8 boolean columns)

---

## Phase 1.5: UI & Detection Enhancements (COMPLETED ✓)

### 2-Page Trust Menu System
- [x] Rewrote `MenuManager.java` with pagination support
- [x] **Page 1**: Break Blocks, Place Blocks, Containers, Doors & Gates
- [x] **Page 2**: Redstone, Entity Damage, Vehicles, Decorations
- [x] Added detailed permission icons and descriptions
- [x] Fixed all click event handlers
- [x] Added navigation sounds (UI click, page turn, chest close)
- [x] Permission abbreviations in trust list: B,P,C,D,R,E,V,A

### Extended Permissions System
- [x] Expanded from 4 permissions to 8 permissions
- [x] Updated `TrustEntry.java` with new permission getters/setters:
  - `canBreakOnline/Offline`
  - `canPlaceOnline/Offline`
  - `canContainerOnline/Offline` (replaced chest)
  - `canDoorOnline/Offline` (new)
  - `canRedstoneOnline/Offline` (new)
  - `canEntityDamageOnline/Offline` (new)
  - `canVehicleOnline/Offline` (new)
  - `canDecorationOnline/Offline` (new)

### Door Detection Enhancement
- [x] Added `DOOR_PLACE` and `BED_PLACE` interaction types to `PlayerInteraction.java`
- [x] Updated `BlockListener.java` to detect:
  - Wooden/iron doors (all colors)
  - Trapdoors (all colors)
  - Fence gates (all colors)
- [x] Doors tracked with **12 points** (high confidence)

### Base Detection Confidence Improvement
- [x] Increased bed detection score: 15 → **20 points** (ESSENTIAL indicator)
- [x] Added door detection score: **12 points** (ESSENTIAL indicator)
- [x] Updated `ScoreManager.java` to prioritize bed/door scoring
- [x] Updated `config.yml` with new scoring values

### Database Schema Migration
- [x] Updated `DatabaseManager.java` trust table schema with new columns
- [x] Added automatic migration in `migrateTrustTable()` method
- [x] Backward compatibility for old 4-permission schema
- [x] Updated `addTrust()` and `getTrust()` methods with all new permissions
- [x] SafegetColumn helpers for gradual migration

### Configuration Updates
- [x] Updated `ConfigManager.java` with `getDoorScore()` method
- [x] Updated `config.yml` with door scoring (12.0 points)
- [x] Commented doors and beds as ESSENTIAL base indicators

### Command Updates
- [x] Updated `TrustCommand.java` to display new permission abbreviations
- [x] Changed output format to show B,P,C,D,R,E,V,A instead of full names

### Build & Deployment
- [x] **BUILD SUCCESS** - All 22+ source files compile
- [x] **JAR Created** - WDP-BaseDet-1.0.0-SNAPSHOT.jar (106KB)
- [x] **Deployed** - Plugin loaded successfully on test server
- [x] Backup created: WDP-BaseDet.jar.backup.20251214_131711

---

## Phase 1.6: Dimension-Specific Exclusions (COMPLETED ✓)

### Completed Tasks (2025-12-17)
- [x] Added `dimension-exclusions` config section
- [x] Beds excluded in Nether (used as weapons, not bases)
- [x] Beds excluded in End (same reason)
- [x] Respawn anchors excluded in Overworld (explode)
- [x] Updated `ConfigManager.java` with dimension exclusion loading
- [x] Added `isDimensionExcluded(Material, Environment)` method
- [x] Updated `ScoreManager.java` with dimension check delegation
- [x] Updated `BlockListener.java` to check dimension before scoring
- [x] Configurable enable/disable for dimension exclusions

### Config Example
```yaml
dimension-exclusions:
  enabled: true
  nether:
    - WHITE_BED
    - RED_BED
    # ... all bed colors
  end:
    - WHITE_BED
    # ... all bed colors
  overworld:
    - RESPAWN_ANCHOR
```

---

## Phase 2: Testing & Refinement (COMPLETED ✓)

### Completed Tasks
- [x] Test 2-page trust menu functionality
- [x] Verify door/bed detection scoring works
- [x] Test permission toggles on both pages
- [x] Test pagination navigation sounds
- [x] Verify database migration for existing data
- [x] Test backward compatibility with old trust entries
- [x] Build and test core detection
- [x] Test dimension-specific exclusions (beds in nether)

---

## Phase 3: Expansion Detection (COMPLETED ✓)

### Completed Tasks (2025-12-17)
- [x] Created `ExpansionManager.java` for expansion detection
- [x] Track building activity outside existing base bounds
- [x] Calculate new expanded bounds from player interactions
- [x] Prompt user to confirm expansion (no reward)
- [x] Auto-deny expansion after 60 seconds
- [x] Maintain base ID on expansion (updates existing base)
- [x] Trust entries automatically preserved on expansion
- [x] Added `/basedet expand <confirm|deny>` command
- [x] Particle visualization for new proposed bounds
- [x] Configurable expansion threshold and distance

### Expansion Detection Flow
1. Player builds outside their existing base (within threshold distance)
2. System tracks these interactions separately
3. When minimum blocks reached, expansion prompt shown
4. Player confirms or denies (auto-denies after 60s)
5. On confirm, base bounds updated in database

---

## Phase 4: Discord Integration (COMPLETED ✓)

### Completed Tasks (2025-12-17)
- [x] DiscordSRV integration via reflection (no compile dependency)
- [x] DM notifications when someone enters base (owner offline)
- [x] Trust via Discord DM reply ("trust" keyword)
- [x] Configurable messages in config.yml
- [x] Combat notifications to base owner
- [x] Pending trust request tracking

### Discord Message Flow
1. Intruder enters offline player's base
2. System sends DM to base owner via DiscordSRV
3. Owner can reply with "trust" to add intruder
4. Trust confirmed via Discord and in-game

---

## Phase 5: Smart Combat System (COMPLETED ✓)

### Completed Tasks (2025-12-17)
- [x] CMI integration for combat tag detection
- [x] Custom combat detection fallback (when CMI unavailable)
- [x] Boss bar display during combat (configurable)
- [x] Combat radius around bases (configurable)
- [x] Base protection lifted during combat
- [x] Post-combat grace period before protection restored
- [x] Player notifications on combat base entry
- [x] Base owner notifications when combat-tagged player enters

### Combat System Flow
1. Player gets combat tagged (CMI or custom)
2. When entering enemy base while tagged, protection lifted
3. Combat allowed in base while tagged
4. Post-combat grace period (configurable)
5. Once attacker leaves, privileges revoked

---

## Phase 6: Selector Tool (COMPLETED ✓)

### Completed Tasks (2025-12-17)
- [x] Created `SelectorTool.java` for base boundary modification
- [x] `/basedet tool` command to get selector tool
- [x] Left-click to set corner 1
- [x] Right-click to set corner 2
- [x] Sneak + Left-click to apply changes
- [x] Sneak + Right-click to cancel
- [x] Cost per block for expansion (configurable)
- [x] Free shrinking option (configurable)
- [x] Particle preview of new bounds
- [x] Cost preview before applying
- [x] Size limit validation

### Selector Tool Usage
1. Get tool with `/basedet tool`
2. Left-click block for corner 1
3. Right-click block for corner 2
4. View cost preview
5. Sneak + Left-click to apply
6. Sneak + Right-click to cancel

---

## Configuration Reference

### Key Settings (config.yml)
```yaml
detection:
  enabled: true
  threshold: 100
  block-place-points: 5
  block-break-points: 3
  chest-interaction-points: 2
  walking-points: 1
  proximity-bonus: 1.5
  proximity-radius: 10
  decay:
    enabled: true
    rate: 1
    interval: 300

limits:
  max-width: 200
  max-length: 200
  max-height: 64
  min-width: 10
  min-length: 10
  min-height: 3

protection:
  enabled: true
  when-online: false
  when-offline: true

trust:
  default-permissions:
    break-online: true
    place-online: true
    chest-online: true
    interact-online: true
    break-offline: false
    place-offline: false
    chest-offline: true
    interact-offline: true
```

---

## Notes

### Technical Decisions
1. **HikariCP** - Using connection pooling for database efficiency
2. **SQLite Default** - Easy setup for testing, MySQL for production
3. **Soft Dependencies** - DiscordSRV and CMI are optional
4. **Particle System** - Shows at player view height for visibility
5. **Score Caching** - In-memory scores synced periodically to DB

### Known Considerations
- SQLite limited to single connection (HikariCP maxPoolSize=1)
- Bounding box uses clustering for irregular builds
- Trust GUI uses SkillCoins navbar pattern for consistency
- All messages configurable in config.yml

---

## Build Instructions

```bash
cd /root/WDP-Rework/WDP-BaseDet
mvn clean package
```

Or use deploy script:
```bash
chmod +x deploy.sh
./deploy.sh
```

---

## Complete Feature Summary

### Core Features
| Feature | Status | Description |
|---------|--------|-------------|
| Base Detection | ✅ | Automatic detection via activity scoring |
| Score System | ✅ | Block place/break, chests, walking, proximity bonus |
| Score Decay | ✅ | Configurable decay (only when online) |
| Bounding Box | ✅ | Clustering algorithm with offset/limits |
| Particle Visualization | ✅ | Gold dust particles showing base bounds |

### Protection Features
| Feature | Status | Description |
|---------|--------|-------------|
| Offline Protection | ✅ | Denies interaction when owner offline |
| Trust System | ✅ | 8 granular permissions (online/offline) |
| Trust GUI | ✅ | 2-page menu with toggles |
| Combat Bypass | ✅ | CMI/custom combat tag integration |

### Integration Features
| Feature | Status | Description |
|---------|--------|-------------|
| Economy (Vault) | ✅ | SkillCoins rewards/costs |
| DiscordSRV | ✅ | DM notifications, trust via reply |
| CMI | ✅ | Combat tag detection |

### Utility Features
| Feature | Status | Description |
|---------|--------|-------------|
| Expansion Detection | ✅ | Auto-detect and prompt for expansion |
| Selector Tool | ✅ | Modify boundaries at cost |
| Dimension Exclusions | ✅ | Beds in nether don't count |
| Admin Commands | ✅ | Force, info, list, delete, debug |

### Commands Reference
| Command | Permission | Description |
|---------|------------|-------------|
| `/basedet help` | - | Show help |
| `/basedet confirm` | - | Confirm detected base |
| `/basedet deny` | - | Deny detected base |
| `/basedet view` | basedet.user.view | Toggle visualization |
| `/basedet detect` | basedet.user.detect | Manual detection trigger |
| `/basedet score` | - | View detection score |
| `/basedet tool` | basedet.user.tool | Get selector tool |
| `/basedet expand <confirm\|deny>` | - | Handle expansion |
| `/trust` | - | Open trust GUI |
| `/trust <player>` | - | Quick-trust player |
| `/basedet reload` | basedet.admin.reload | Reload config |
| `/basedet debug` | basedet.admin.debug | Debug info |
| `/basedet force <player>` | basedet.admin.force | Force detection |
| `/basedet info <player>` | basedet.admin.view | View player info |
| `/basedet list` | basedet.admin.view | List all bases |
| `/basedet delete <player>` | basedet.admin.bypass | Delete bases |

---

## File Structure (24 source files)

```
src/main/java/com/wdp/basedet/
├── WDPBaseDetPlugin.java          # Main plugin class
├── combat/
│   └── CombatManager.java         # Combat tag system
├── command/
│   ├── BaseDetCommand.java        # Main command handler
│   └── TrustCommand.java          # Trust command handler
├── config/
│   └── ConfigManager.java         # Configuration management
├── database/
│   └── DatabaseManager.java       # SQLite/MySQL operations
├── detection/
│   ├── DetectionManager.java      # Base detection logic
│   ├── ExpansionManager.java      # Expansion detection
│   ├── PlayerInteraction.java     # Interaction model
│   └── ScoreManager.java          # Score tracking
├── integration/
│   ├── CMIIntegration.java        # CMI combat integration
│   ├── DiscordIntegration.java    # DiscordSRV integration
│   └── EconomyIntegration.java    # Vault integration
├── listener/
│   ├── BlockListener.java         # Block events
│   ├── PlayerListener.java        # Player events
│   └── ProtectionListener.java    # Protection events
├── model/
│   ├── Base.java                  # Base data model
│   ├── BoundingBox.java           # Bounding box logic
│   └── TrustEntry.java            # Trust data model
├── protection/
│   └── ProtectionManager.java     # Protection logic
├── trust/
│   └── TrustManager.java          # Trust operations
├── ui/
│   └── MenuManager.java           # GUI management
└── util/
    ├── ParticleManager.java       # Particle visualization
    └── SelectorTool.java          # Selector tool
```

---

*Last Updated: Phase 6 Complete (2025-12-17)*
*All features implemented and tested*
