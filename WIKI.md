# WDP-BaseDet Wiki

## Complete User Guide for Automatic Base Detection & Protection

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Commands](#commands)
4. [Base Detection System](#base-detection-system)
5. [Base Protection](#base-protection)
6. [Trust System](#trust-system)
7. [Menu System](#menu-system)
8. [Teleportation](#teleportation)
9. [Base Expansion](#base-expansion)
10. [Combat System](#combat-system)
11. [Permissions](#permissions)
12. [Configuration](#configuration)
13. [PlaceholderAPI Placeholders](#placeholderapi-placeholders)
14. [Integrations](#integrations)
15. [FAQ](#faq)

---

## Overview

WDP-BaseDet is an automatic base detection and protection plugin designed for survival Minecraft servers. Unlike traditional claim plugins, WDP-BaseDet analyzes your building activity and automatically detects when you've established a base, then offers protection when you're offline.

### Key Features

- **Automatic Detection**: No commands needed to claim - just build your base!
- **Smart Scoring**: The plugin tracks your activity and learns where you live
- **Offline Protection**: Your base is automatically protected when you log out
- **Trust System**: Fine-grained control over who can access what
- **Combat Integration**: Smart combat system allows PvP near bases during fights
- **Discord Notifications**: Get DMs when someone enters your base
- **Multi-Base Support**: Own up to 3 bases in different locations
- **Economy Integration**: Works with SkillCoins/Vault

---

## Getting Started

### How It Works

1. **Build Naturally**: Start building your base - place chests, beds, doors, furnaces, etc.
2. **Score Accumulation**: Every action adds to your "home score" for that area
3. **Detection Trigger**: Once you reach the detection threshold, you'll be prompted
4. **Confirm or Deny**: Click to confirm your base or deny if it's wrong
5. **Protection Active**: Your base is now protected when you go offline!

### First Base Checklist

For fastest detection, place:
- ✅ A bed (highest score value)
- ✅ 2-3 doors (high score value)
- ✅ Chests and storage
- ✅ Crafting table and furnaces
- ✅ Walk around your building area

**Tip**: Detection score builds up with proximity - building in one area scores faster than spread out!

---

## Commands

### Main Command: `/base` (aliases: `/bd`, `/basedet`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/base` | Opens the base menu (or base selector if multiple) | `basedet.user.menu` |
| `/base confirm` | Confirm a detected base | `basedet.user.confirm` |
| `/base deny` | Deny/cancel a base detection | `basedet.user.deny` |
| `/base view` | Toggle particle visualization of your base | `basedet.user.view` |
| `/base detect` | Manually trigger detection check | `basedet.user.detect` |
| `/base score` | View your current detection score | `basedet.user.score` |
| `/base tool` | Get the boundary modification tool | `basedet.user.tool` |
| `/base menu` | Open the base management menu | `basedet.user.menu` |
| `/base expand <confirm/deny>` | Confirm or deny base expansion | `basedet.user.expand` |
| `/base help` | Show command help | `basedet.user.help` |

### Trust Command: `/trust`

| Command | Description | Permission |
|---------|-------------|------------|
| `/trust <player>` | Trust a player in your base | `basedet.user.trust` |
| `/trust list` | List all trusted players | `basedet.user.trust` |
| `/trust remove <player>` | Remove trust from a player | `basedet.user.trust` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/base reload` | Reload plugin configuration | `basedet.admin.reload` |
| `/base debug` | Toggle debug mode | `basedet.admin.debug` |
| `/base force <player>` | Force detection for a player | `basedet.admin.force` |
| `/base info <player>` | View player's base info | `basedet.admin.view` |
| `/base list` | List all bases | `basedet.admin.view` |
| `/base delete <id>` | Delete a base by ID | `basedet.admin.delete` |

---

## Base Detection System

### Score Values

Different actions contribute different amounts to your detection score:

| Action | Score | Notes |
|--------|-------|-------|
| Place bed | 20.0 | Counts only once per player |
| Place door/gate | 12.0 | Counts up to 3 times |
| Place enchanting table | 12.0 | |
| Place chest | 10.0 | |
| Place crafting table | 8.0 | |
| Place furnace | 8.0 | |
| Place shulker box | 8.0 | |
| Place anvil | 7.0 | |
| Place barrel | 6.0 | |
| Place smoker | 6.0 | |
| Place blast furnace | 6.0 | |
| Place other blocks | 2.0 | |
| Break blocks | 1.5 | |
| Walking (per 10 blocks) | 0.1 | |

### Score Limits

To prevent exploitation, certain blocks have score limits:
- **Beds**: Only the first bed counts (limit: 1)
- **Doors/Gates**: Only first 3 doors count (limit: 3)

### Proximity Bonus

Building blocks close together increases your score faster:
- Blocks within 10 blocks of previous activity get a bonus
- Maximum 2.0x multiplier for concentrated building
- Encourages building in one area

### Detection Threshold

- Default threshold: 100 points
- Once reached, you'll see a detection prompt
- 10-minute auto-confirm timer starts
- Cooldown of 5 minutes between detection checks

### Excluded Blocks

These blocks don't count for detection:
- Torches (all types)
- Lanterns
- Chains
- Ladders
- Vines
- Scaffolding
- Cobwebs

### Dimension Exclusions

Beds in Nether/End don't count (they're used as weapons there!)

---

## Base Protection

### How Protection Works

When a base owner is **offline**, their base is protected:

| Action | Protected? |
|--------|------------|
| Block breaking | ✅ Blocked |
| Block placing | ✅ Blocked |
| Chest access | ✅ Blocked |
| Door interaction | ✅ Blocked |
| Button/lever use | ✅ Blocked |
| Entity damage | ✅ Blocked |
| Item pickup | ❌ Allowed |

### When Owner is Online

When you're online, your base is **not** protected - anyone can interact normally. This allows natural gameplay and PvP.

### Combat Override

If combat occurs near a base, protection is temporarily lifted to allow fair fights. See [Combat System](#combat-system) for details.

---

## Trust System

### Overview

Trust players to allow them access to your base when you're offline. Each trusted player has two permission sets:

1. **Online Permissions**: What they can do when you're online
2. **Offline Permissions**: What they can do when you're offline

### Available Permissions

| Permission | Description |
|------------|-------------|
| Break Blocks | Mine and break blocks |
| Place Blocks | Place and build |
| Containers | Open chests, barrels, hoppers |
| Doors & Gates | Use doors, trapdoors, fence gates |
| Redstone | Use buttons, levers, pressure plates |
| Entities | Damage animals and mobs |
| Vehicles | Break/place boats, minecarts, armor stands |
| Decorations | Item frames, paintings, leads |

### Default Trust Permissions

**When Owner Online:**
- All permissions granted by default

**When Owner Offline:**
- Only containers, doors, and redstone allowed by default
- Breaking/placing blocked by default

### Using the Trust Menu

1. Open `/base` → Click "Trust Manager"
2. View all trusted players
3. Click a player to edit their permissions
4. Toggle individual permissions on/off
5. Use Page 1 for basic perms, Page 2 for advanced

### Quick Trust via Command

```
/trust PlayerName        - Trust with default permissions
/trust remove PlayerName - Remove all trust
/trust list             - See all trusted players
```

---

## Menu System

### Base Menu (Main)

Access with `/base` command. Contains:

- **Trust Manager** - Manage trusted players
- **Protection Settings** - View protection status
- **Modify Boundaries** - Get selector tool (if enabled)
- **Boundary Particles** - Toggle visual boundaries
- **Teleport to Base** - Teleport home
- **Base Statistics** - Detailed base info
- **Abandon Base** - Delete your base (shift-click)

### Base Selector (Multiple Bases)

If you have multiple bases, `/base` shows a selector:
- Each base shown with dimension icon (Overworld/Nether/End)
- Shows world name, size, volume
- Click to manage that specific base

### Trust Manager

Lists all trusted players with their permission summary:
- `B` = Break, `P` = Place, `C` = Containers
- `D` = Doors, `R` = Redstone, `E` = Entities
- `V` = Vehicles, `A` = Decorations (Art)

### Permission Editor

Two pages of permissions:
- **Page 1**: Basic (Break, Place, Containers, Doors)
- **Page 2**: Advanced (Redstone, Entities, Vehicles, Decorations)

Green sections = Online permissions
Yellow sections = Offline permissions

---

## Teleportation

### Using Teleport

From the Base Menu, click "Teleport to Base" to teleport home.

### Requirements

- Teleportation must be enabled in config
- Must not be in combat (if `block-in-combat` enabled)
- Must not be on cooldown
- Must have enough SkillCoins (if cost configured)

### Configuration Options

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | true | Enable teleport feature |
| `cost` | 100 | SkillCoins cost per teleport |
| `cooldown` | 300 | Seconds between teleports |
| `delay` | 5 | Seconds before teleporting |
| `cancel-on-move` | true | Cancel if you move during delay |
| `cancel-on-damage` | true | Cancel if you take damage |
| `block-in-combat` | true | Block teleport while combat tagged |

### Teleport States

- **Ready** (Ender Pearl icon) - Click to teleport
- **On Cooldown** (Clock icon) - Shows remaining time
- **Combat Blocked** (Red Dye) - Wait for combat to end
- **Disabled** (Gray Dye) - Server has disabled teleports

---

## Base Expansion

### Automatic Expansion Detection

If you build outside your base but nearby:
1. The plugin tracks your new activity
2. Once you've placed 25+ blocks outside
3. An expansion prompt appears
4. Confirm to grow your base, deny to keep current size

### Expansion Prompt

```
⬢ Base Expansion Detected!
  Your base has grown by X blocks
  New size: 50x50x20
  
  [✓ Expand]  [✗ Keep Current]
```

### Expansion Limits

- Must be within distance threshold of existing base
- Cannot exceed maximum base dimensions
- Auto-denied after 1 minute if no response

---

## Combat System

### Smart Combat Detection

The plugin includes a smart combat system that:
- Tracks when players engage in combat
- Temporarily lifts protection during fights
- Integrates with CMI combat tags if available

### Combat Tag Duration

Default: 15 seconds after last hit

### Combat Triggers

- Player damages another player
- Projectile hits (arrows, tridents)
- Optional: Player damages any entity

### Combat Indicators

- Boss bar shows combat status
- Chat message when combat starts/ends
- Teleport blocked during combat

### CMI Integration

If CMI is installed, the plugin will use CMI's combat system instead of its own.

---

## Permissions

### User Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `basedet.user.confirm` | Confirm detected bases | true |
| `basedet.user.deny` | Deny detected bases | true |
| `basedet.user.view` | View base boundaries | true |
| `basedet.user.detect` | Manually trigger detection | true |
| `basedet.user.score` | View detection score | true |
| `basedet.user.tool` | Use boundary selector tool | true |
| `basedet.user.expand` | Confirm/deny expansions | true |
| `basedet.user.help` | View help | true |
| `basedet.user.menu` | Open base menu | true |
| `basedet.user.trust` | Manage trusted players | true |

### Admin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `basedet.admin.*` | All admin permissions | op |
| `basedet.admin.reload` | Reload configuration | op |
| `basedet.admin.debug` | Toggle debug mode | op |
| `basedet.admin.bypass` | Bypass all protection | op |
| `basedet.admin.view` | View any player's base | op |
| `basedet.admin.force` | Force detection for players | op |
| `basedet.admin.delete` | Delete bases | op |

---

## Configuration

### Key Configuration Sections

#### Detection Settings
```yaml
detection:
  detection-threshold: 100     # Score needed to trigger
  detection-cooldown: 300      # Seconds between checks
  score-limits:
    bed: 1                     # Beds only count once
    door: 3                    # Doors count up to 3 times
```

#### Base Size Limits
```yaml
limits:
  max:
    width: 200
    length: 200
    height: 64
  min:
    width: 10
    length: 10
    height: 3
```

#### Multiple Bases
```yaml
bases:
  allow-multiple: true
  max-per-player: 3
  min-distance: 500           # Blocks between bases
  auto-abandon-old: true      # Auto-delete oldest when at limit
```

#### Teleport Settings
```yaml
teleport:
  enabled: true
  cost: 100
  cooldown: 300
  delay: 5
  cancel-on-move: true
  cancel-on-damage: true
  block-in-combat: true
```

#### Selector Tool
```yaml
selector:
  enabled: false              # Disabled by default
  material: BLAZE_ROD
  cost-per-block: 5
  shrink-free: true
```

---

## PlaceholderAPI Placeholders

### Player Stats
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_score%` | Current detection score | `85.5` |
| `%basedet_base_count%` | Number of confirmed bases | `2` |
| `%basedet_has_base%` | Has at least one base | `true` |
| `%basedet_trusted_count%` | Total trusted players | `5` |

### Primary Base Info
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_base_world%` | World name | `world` |
| `%basedet_base_dimension%` | Dimension name | `Overworld` |
| `%basedet_base_width%` | Base width | `50` |
| `%basedet_base_length%` | Base length | `50` |
| `%basedet_base_height%` | Base height | `20` |
| `%basedet_base_volume%` | Total volume | `50000` |
| `%basedet_base_volume_formatted%` | Formatted volume | `50,000` |
| `%basedet_base_size%` | Size as WxLxH | `50×50×20` |
| `%basedet_base_center%` | Center coordinates | `100, 64, -200` |
| `%basedet_base_center_x%` | Center X | `100` |
| `%basedet_base_center_y%` | Center Y | `64` |
| `%basedet_base_center_z%` | Center Z | `-200` |
| `%basedet_base_min_x%` | Min X coordinate | `75` |
| `%basedet_base_min_y%` | Min Y coordinate | `54` |
| `%basedet_base_min_z%` | Min Z coordinate | `-225` |
| `%basedet_base_max_x%` | Max X coordinate | `125` |
| `%basedet_base_max_y%` | Max Y coordinate | `74` |
| `%basedet_base_max_z%` | Max Z coordinate | `-175` |
| `%basedet_base_created%` | Creation date | `Jan 15, 2024` |
| `%basedet_base_confirmed%` | Is confirmed | `true` |
| `%basedet_base_trusted%` | Trusted player count | `3` |
| `%basedet_base_id%` | Internal base ID | `42` |

### Combat Status
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_combat%` | Is in combat | `true` |
| `%basedet_combat_time%` | Combat time remaining | `12` |

### Detection Status
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_pending%` | Has pending detection | `false` |
| `%basedet_threshold%` | Detection threshold | `100` |
| `%basedet_score_percent%` | Score as % of threshold | `85` |

### Location Context
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_in_base%` | Is in own base | `true` |
| `%basedet_in_any_base%` | Is in any base | `true` |
| `%basedet_base_owner%` | Owner of current base | `Steve` |

### Protection Status
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_protection_active%` | Is protection active | `true` |
| `%basedet_owner_online%` | Is base owner online | `false` |

### Config Values
| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%basedet_config_threshold%` | Detection threshold | `100` |
| `%basedet_config_max_bases%` | Max bases allowed | `3` |
| `%basedet_config_teleport_cost%` | Teleport cost | `100` |
| `%basedet_config_teleport_cooldown%` | Teleport cooldown | `300` |

---

## Integrations

### Vault/SkillCoins

Required for economy features:
- Base confirmation rewards
- Teleport costs
- Boundary modification costs

### DiscordSRV

Enables Discord notifications:
- DM when someone enters your base
- Trust players via Discord reply
- Combat alerts

### CMI

If installed, uses CMI's combat tag system instead of custom combat.

### PlaceholderAPI

Enables 40+ placeholders for use in other plugins (scoreboards, chat, etc.)

---

## FAQ

### Q: My base wasn't detected, why?

**A**: You need to reach the detection threshold (default 100 points). Focus on:
- Placing a bed (20 points)
- Adding doors (12 points each, up to 3)
- Adding chests and workstations
- Building in a concentrated area

### Q: Can I have multiple bases?

**A**: Yes! By default you can have up to 3 bases, minimum 500 blocks apart.

### Q: How do I remove a trusted player?

**A**: Use `/trust remove PlayerName` or go to Trust Manager → Click player → Click "Remove" button.

### Q: Why can't I teleport?

**A**: Check if:
- You're in combat (wait for combat to end)
- Teleport is on cooldown
- You have enough SkillCoins
- Teleport feature is enabled on the server

### Q: Someone broke into my base while I was offline!

**A**: Check if:
- They were a trusted player
- They attacked you before you logged off (combat exemption)
- They have the `basedet.admin.bypass` permission
- Contact server staff if unexplained

### Q: How do I expand my base?

**A**: Just build naturally outside your base boundaries. After placing 25+ blocks nearby, you'll get an expansion prompt.

### Q: My base seems too small/big

**A**: The boundary selector tool (if enabled) lets you adjust boundaries. Use `/base tool` to get it.

### Q: What happens if I die and respawn at my bed?

**A**: Respawning at your bed is fine and counts positively toward detection!

### Q: Can I unclaim my base?

**A**: Yes, in the Base Menu click "Abandon Base" (shift-click to confirm). Warning: This cannot be undone!

### Q: Do I need to do anything to protect my base?

**A**: No! Just confirm when prompted. Protection is automatic when you log out.

---

## Support

For issues or suggestions, contact server staff or visit our Discord.

**Plugin Version**: Check with `/base debug`

---

*WDP-BaseDet - Building Your Home, Protecting Your Legacy*
