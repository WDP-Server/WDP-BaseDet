# WDP-BaseDet Development Log

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

## Phase 2: Testing & Refinement (NEXT)

### Planned Tasks
- [ ] Test 2-page trust menu functionality
- [ ] Verify door/bed detection scoring works
- [ ] Test permission toggles on both pages
- [ ] Test pagination navigation sounds
- [ ] Verify database migration for existing data
- [ ] Test backward compatibility with old trust entries
- [ ] Build and test core detection

---

## Phase 3: Expansion Detection

### Planned Tasks
- [ ] Detect building activity outside existing base
- [ ] Prompt to expand base bounds
- [ ] Maintain base ID on expansion
- [ ] Update trust entries automatically

---

## Phase 4: Discord Integration

### Planned Tasks
- [ ] DM notifications for base events
- [ ] Configurable messages
- [ ] Raid alerts when owner offline

---

## Phase 5: Combat Integration

### Planned Tasks
- [ ] CMI combat tag detection
- [ ] Special protection rules during combat
- [ ] Prevent abuse of base protection

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

*Last Updated: Phase 1.5 - UI & Detection Enhancements (2025-12-14)*
