# Debug System Visual Guide

## Command Outputs - What You'll See

### 1. `/base debug toggle` (First Time)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BaseDet] ✓ Debug Mode ENABLED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Live messages will show:
  ● Block interactions & score changes
  ● Cluster type classification
  ● Mining detection analysis
  ● Cluster creation & removal
  ● Score penalties & bonuses

Commands:
  /base debug clusters - View all clusters
  /base debug detail <#> - Detailed info
  /base debug toggle - Disable
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Your Active Clusters (0/5)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
No active clusters yet.
Start building to create a cluster!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 2. Live Debug Messages (While Building)

```
[BaseDet] [Cluster] Started new activity cluster at world at 1000, 64, 1000
You now have 1/5 active clusters

[BaseDet] [Score] BLOCK_PLACE +2.00 at [BASE] (total: 2.0)

[BaseDet] [Score] BLOCK_PLACE +2.00 at [BASE] (total: 4.0)

[BaseDet] [Score] BLOCK_BREAK +1.50 at [BASE] (total: 5.5)

[BaseDet] [Score] BLOCK_PLACE +10.00 at [BASE] (total: 15.5)
  (Placed BED - base indicator!)

[BaseDet] [Score] BLOCK_PLACE +12.00 at [BASE] (total: 27.5)
  (Placed DOOR - base indicator!)
```

### 3. Live Debug Messages (While Mining)

```
[BaseDet] [Cluster] Started new activity cluster at world at 2000, 15, 2500
You now have 2/5 active clusters

[BaseDet] [Score] BLOCK_BREAK +1.50 at [UNKNOWN] (total: 1.5)

[BaseDet] [Score] BLOCK_BREAK +1.50 at [UNKNOWN] (total: 3.0)

[BaseDet] [Cluster] Cluster reclassified: UNKNOWN → MINING
  Reason: High break/place ratio (15.0), ores detected (3)

[BaseDet] [Score] BLOCK_BREAK +0.15 at [MINING] (total: 3.2)
  Mining stats: broken=25, placed=2, ores=3

[BaseDet] [Score] BLOCK_BREAK +0.15 at [MINING] (total: 3.4)
  Mining stats: broken=26, placed=2, ores=4
```

### 4. `/base debug clusters` (With Active Clusters)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Your Active Clusters (3/5)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

#1 ⌂ BASE
  Location: world at 1000, 64, 1000
  Score: 127.5 / 100 (threshold)
  Blocks: 82 broken, 95 placed
  Break/Place Ratio: 0.86
  Age: 2.5 hours

#2 ⛏ MINING
  Location: world at 2000, 15, 2500
  Score: 12.3 / 100 (threshold)
  Blocks: 215 broken, 8 placed
  Break/Place Ratio: 26.88
  Ores: 18 ⛏
  Age: 45 minutes

#3 ⚒ HYBRID
  Location: world at 1500, 50, 1800
  Score: 68.5 / 100 (threshold)
  Blocks: 75 broken, 55 placed
  Break/Place Ratio: 1.36
  Ores: 5 ⛏
  Age: 1.8 hours

Use /base debug detail <#> for more info
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 5. `/base debug detail 1` (BASE Cluster)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Cluster #1 Details
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Type: ⌂ BASE
Center: 1000, 64, 1000 in world
Score: 127.50 / 100
Progress: 127.5%

⚡ Activity Stats:
  Blocks Broken: 82
  Blocks Placed: 95
  Total Blocks: 177
  Break/Place Ratio: 0.86

⛏ Mining Indicators:
  Ores Broken: 2
  Ore Percentage: 2.4%

⌂ Base Indicators:
  Bed: ✓
  Door: ✓
  Chest: ✓

⏱ Time Info:
  Created: 2 hours 30 minutes ago
  Last Activity: just now

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 6. `/base debug detail 2` (MINING Cluster)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Cluster #2 Details
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Type: ⛏ MINING
Center: 2000, 15, 2500 in world
Score: 12.30 / 100
Progress: 12.3%

⚡ Activity Stats:
  Blocks Broken: 215
  Blocks Placed: 8
  Total Blocks: 223
  Break/Place Ratio: 26.88

⛏ Mining Indicators:
  Ores Broken: 18
  Ore Percentage: 8.4%

⌂ Base Indicators:
  Bed: ✗
  Door: ✗
  Chest: ✗

⏱ Time Info:
  Created: 45 minutes ago
  Last Activity: 2 minutes ago

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 7. `/base debug detail 3` (HYBRID Cluster)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Cluster #3 Details
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Type: ⚒ HYBRID
Center: 1500, 50, 1800 in world
Score: 68.50 / 100
Progress: 68.5%

⚡ Activity Stats:
  Blocks Broken: 75
  Blocks Placed: 55
  Total Blocks: 130
  Break/Place Ratio: 1.36

⛏ Mining Indicators:
  Ores Broken: 5
  Ore Percentage: 6.7%

⌂ Base Indicators:
  Bed: ✗
  Door: ✓
  Chest: ✓

⏱ Time Info:
  Created: 1 hours 48 minutes ago
  Last Activity: 10 minutes ago

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 8. Console: `/base debug clusters` (Admin View)

```
=== All Active Clusters ===
Steve: 3 clusters
  [BASE] world at 1000, 64, 1000 - Score: 127.5
  [MINING] world at 2000, 15, 2500 - Score: 12.3
  [HYBRID] world at 1500, 50, 1800 - Score: 68.5
Alex: 1 clusters
  [BASE] world at -500, 75, -800 - Score: 95.2
Notch: 2 clusters
  [MINING] world at 3000, 12, 4000 - Score: 5.8
  [BASE] world at 3200, 68, 4150 - Score: 142.0
```

### 9. Tab Completion Examples

```
> /base debug <TAB>
toggle  clusters  detail  clear

> /base debug det<TAB>
detail

> /base debug detail <TAB>
1  2  3

> /base debug detail 1<TAB>
[Executes command]
```

## Color Coding Reference

### Cluster Types (in list view)
- `⌂ BASE` = **Green text** (§a)
- `⛏ MINING` = **Red text** (§c)
- `⚒ HYBRID` = **Yellow text** (§e)
- `? UNKNOWN` = **Gray text** (§7)

### Break/Place Ratios
- **Green** (§a) = Ratio < 2.0 (building-focused)
- **Yellow** (§e) = Ratio 2.0-5.0 (mixed activity)
- **Red** (§c) = Ratio > 5.0 (mining-focused)

### Ore Percentages
- **Gray** (§7) = < 5% (normal)
- **Yellow** (§e) = 5-10% (moderate)
- **Red** (§c) = > 10% (heavy mining)

### Labels and Values
- **Gray** (§7) = Labels and descriptions
- **White** (§f) = Numbers and values
- **Gold** (§6) = Headers and titles
- **Blue** (§b) = Time information

## Icon Reference

### Cluster Type Icons
- ⌂ (U+2302) = House/HOME = BASE cluster
- ⛏ (U+26CF) = PICK = MINING cluster
- ⚒ (U+2692) = HAMMER AND PICK = HYBRID cluster
- ? (U+003F) = QUESTION MARK = UNKNOWN cluster

### Status Icons
- ✓ (U+2713) = CHECK MARK = Present/Yes
- ✗ (U+2717) = BALLOT X = Absent/No
- ● (U+25CF) = BLACK CIRCLE = Bullet point
- ⚡ (U+26A1) = HIGH VOLTAGE = Activity
- ⏱ (U+23F1) = STOPWATCH = Time
- ⚠ (U+26A0) = WARNING SIGN = Warning
- ⬡ (U+2B21) = WHITE HEXAGON = Section marker

### Box Drawing
- ━ (U+2501) = BOX DRAWINGS HEAVY HORIZONTAL
- ═ (U+2550) = BOX DRAWINGS DOUBLE HORIZONTAL
- ╗ (U+2557) = BOX DRAWINGS DOUBLE DOWN AND LEFT
- ╚ (U+255A) = BOX DRAWINGS DOUBLE UP AND RIGHT
- ║ (U+2551) = BOX DRAWINGS DOUBLE VERTICAL

## Terminal Compatibility

### Tested and Working
- ✅ Windows Terminal
- ✅ Git Bash
- ✅ PowerShell
- ✅ macOS Terminal
- ✅ Linux Terminal (GNOME, KDE)
- ✅ PuTTY
- ✅ VS Code integrated terminal
- ✅ IntelliJ IDEA terminal
- ✅ Minecraft server console

### Fallback Display
If icons don't render:
- ⌂ → [BASE]
- ⛏ → [MINING]
- ⚒ → [HYBRID]
- ? → [UNKNOWN]
- ✓ → [YES]
- ✗ → [NO]

All functionality remains the same, just visual representation differs.

---

**Tip:** For best experience, use a terminal with Unicode support and a monospace font with good Unicode coverage (e.g., Cascadia Code, JetBrains Mono, Fira Code).
