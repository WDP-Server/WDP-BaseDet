# Smart Mining Detection Algorithm - Technical Documentation

## Quick Reference - Debug Commands

```
/base debug              → Show help menu
/base debug toggle       → Enable/disable live debug messages
/base debug clusters     → List all your active clusters  
/base debug detail <#>   → Detailed info for cluster #
/base debug clear        → Clear all clusters (admin)
```

**Console only:**
```
/base debug clusters     → View all players' clusters (admin)
```

## Overview
This document explains the exact mechanics of the multi-location base tracking system with smart mining detection implemented in WDP-BaseDet.

## Problem Statement
Players can trigger false positive base detections when:
- Mining tunnels and caves
- Strip mining for resources
- Building long structures or bridges
- Exploring while occasionally breaking blocks

Traditional single-location tracking fails when players have multiple bases or move between locations.

## Solution Architecture

### Two-Part System

1. **ClusterManager** - Manages multiple location clusters per player
2. **LocationCluster** - Individual activity clusters with mining detection

---

## Part 1: Multi-Location Cluster System

### Cluster Creation Rules

**Constants:**
```
MAX_CLUSTERS = 5               // Maximum clusters per player
NEW_CLUSTER_DISTANCE = 200     // Blocks to start new cluster
CLUSTER_ACTIVATION_THRESHOLD = 20.0  // Minimum score for active cluster
CLUSTER_EXPIRY_MS = 4 hours    // Time before cluster expires
```

**Algorithm Flow:**
```
1. Player breaks/places block at (x, y, z)
2. Get all existing clusters for player
3. Find nearest cluster in same world
4. IF (nearest cluster exists AND distance < 200 blocks)
   ├─ Use existing cluster
   └─ Update cluster center (weighted average: 70% old, 30% new)
5. ELSE IF (player has < 5 clusters)
   └─ Create new cluster at (x, y, z)
6. ELSE (player has 5 clusters already)
   ├─ Find highest scoring cluster (PROTECTED)
   ├─ Remove lowest scoring non-protected cluster
   └─ Create new cluster at (x, y, z)
```

**Center Position Update:**
```java
// Weighted average keeps center stable but follows activity
centerX = (centerX * 0.7) + (newX * 0.3)
centerY = (centerY * 0.7) + (newY * 0.3)
centerZ = (centerZ * 0.7) + (newZ * 0.3)
```

**Cluster Expiry:**
- Inactive for 4 hours → expires and removed
- **Exception:** Highest scoring cluster NEVER expires
- Cleanup runs every 5 minutes

---

## Part 2: Smart Mining Detection

### Classification Types

```java
enum ClusterType {
    UNKNOWN,  // Not enough data yet
    BASE,     // High confidence this is a base
    MINING,   // High confidence this is mining (90% score penalty)
    HYBRID    // Mixed activity (50% score penalty)
}
```

### Metrics Tracked

Every `LocationCluster` tracks:
```
blocksBroken      - Total blocks broken
blocksPlaced      - Total blocks placed
oresBroken        - Number of ores mined
belowY60Count     - Blocks broken below Y=60
linearPatternCount - Times straight-line patterns detected
recentBreakPositions - Last 50 block break locations
```

### Classification Algorithm

**Reclassification triggers after EVERY block break/place:**

```
STEP 1: Minimum Data Check
IF (blocksBroken + blocksPlaced < 10)
   └─ Type = UNKNOWN (not enough data)
   
STEP 2: Calculate Mining Score (0-100+)

A. Break-to-Place Ratio
   IF (breakRatio > 5)  +30 points
   IF (breakRatio > 2)  +15 points
   IF (only breaking, no placing)  +40 points
   
B. Ore Detection
   oreRatio = oresBroken / blocksBroken
   IF (oreRatio > 10%)  +25 points
   IF (oreRatio > 5%)   +15 points
   
C. Depth Analysis
   depthRatio = belowY60Count / blocksBroken
   IF (depthRatio > 70%)  +20 points
   IF (depthRatio > 40%)  +10 points
   
D. Linear Pattern Detection
   IF (linearPatternCount > 5)  +25 points
   IF (linearPatternCount > 2)  +10 points

STEP 3: Apply Classification
   IF (miningScore >= 60)  → Type = MINING
   IF (miningScore >= 30)  → Type = HYBRID
   IF (hasBaseIndicators OR blocksPlaced >= 10)  → Type = BASE
   ELSE  → Type = UNKNOWN
```

### Linear Pattern Detection

**Purpose:** Detect tunnel/strip mining (straight-line breaking)

**Algorithm:**
```
1. Keep rolling window of last 50 block break positions
2. When window has >= 5 positions:
   a. Take last 10 positions
   b. Calculate average position (avgX, avgY, avgZ)
   c. Calculate variance for each axis:
      varX = Σ(x - avgX)² / count
      varY = Σ(y - avgY)² / count
      varZ = Σ(z - avgZ)² / count
   d. Check for linear patterns:
      - X-axis tunnel: varX > 20, varY < 5, varZ < 5
      - Z-axis tunnel: varZ > 20, varY < 5, varX < 5
      - Vertical shaft: varY > 10, varX < 5, varZ < 5
   e. IF linear pattern detected: linearPatternCount++
```

**Example:**
```
Mining straight tunnel east:
Positions: (100,50,64), (102,50,64), (104,50,64), (106,50,64)...
Result: High varX, low varY/varZ → Linear pattern detected
```

### Base Indicators

**Strong Evidence of Base:**
```
- BED placed       → Immediately set Type = BASE
- DOOR + CHEST     → Immediately set Type = BASE
- CRAFTING_TABLE
- FURNACE
- ANVIL
- ENCHANTING_TABLE
```

---

## Part 3: Score Modification

### Mining Penalty Application

When processing interaction:
```java
double score = interaction.getScore();  // Base score from action

// Apply penalty based on cluster type
IF (cluster.type == MINING)
   score = score * 0.1  // 90% reduction
ELSE IF (cluster.type == HYBRID)
   score = score * 0.5  // 50% reduction
// BASE and UNKNOWN get full score

cluster.addScore(score)
```

**Example Scenario:**
```
Player breaks STONE in tunnel:
- Base score: +1.0
- Cluster classified as MINING
- Applied score: 1.0 × 0.1 = 0.1
- Player builds 100 blocks in tunnel → only +10 score total
- Same 100 blocks at base → +100 score total
```

### Detection Threshold Check

```
Per-cluster detection:
1. Check if cluster.score >= config.detectionThreshold (default: 100)
2. Check if cluster.score >= CLUSTER_ACTIVATION_THRESHOLD (20)
3. Check if cluster.type != MINING
4. IF all pass → Trigger base detection for this cluster
```

---

## Part 4: Real-World Examples

### Example 1: Strip Mining
```
Action: Player digs 2×2 strip mine at Y=15
Metrics after 200 blocks:
  blocksBroken = 180
  blocksPlaced = 20 (torches, ladders)
  oresBroken = 15 (diamonds, iron)
  belowY60Count = 180
  linearPatternCount = 15+ (straight tunnels)

Mining Score Calculation:
  Break ratio (9:1) = +30
  Ore ratio (8.3%) = +15
  Depth ratio (100%) = +20
  Linear patterns (15+) = +25
  TOTAL = 90 → Type = MINING

Score Penalty: 90% reduction
Result: 200 blocks = only 20 score (won't trigger detection)
```

### Example 2: Building a Base
```
Action: Player builds house with interior
Metrics after 200 blocks:
  blocksBroken = 40 (clearing ground)
  blocksPlaced = 160 (walls, floors, roof)
  oresBroken = 0
  belowY60Count = 0
  linearPatternCount = 0
  hasBaseIndicators = true (bed, door, chest)

Mining Score Calculation:
  Break ratio (0.25:1) = 0
  Ore ratio (0%) = 0
  Depth ratio (0%) = 0
  Linear patterns (0) = 0
  TOTAL = 0 + hasBaseIndicators → Type = BASE

Score Penalty: None (100%)
Result: 200 blocks = 200 score → triggers detection
```

### Example 3: Cave Exploration
```
Action: Player explores natural cave, breaks occasional blocks
Metrics after 200 blocks:
  blocksBroken = 120
  blocksPlaced = 80 (bridges, safety blocks)
  oresBroken = 8
  belowY60Count = 60
  linearPatternCount = 3 (short paths)

Mining Score Calculation:
  Break ratio (1.5:1) = 0
  Ore ratio (6.6%) = +15
  Depth ratio (50%) = +10
  Linear patterns (3) = +10
  TOTAL = 35 → Type = HYBRID

Score Penalty: 50% reduction
Result: 200 blocks = 100 score → may trigger detection
```

### Example 4: Multiple Bases
```
Player builds base at (1000, 64, 1000):
- Cluster A created
- Builds 100 blocks → 100 score
- Type = BASE (placed bed, chest)

Player travels 300 blocks away to (1300, 64, 1000):
- Distance > 200 → Cluster B created
- Builds 100 blocks → 100 score
- Type = BASE

Player returns to first base:
- Distance to Cluster A < 200 → uses Cluster A
- Continues building → Cluster A score increases

Both clusters independently tracked, both can trigger detection
```

---

## Part 5: Debug Mode

### Debug Command Usage
```
/base debug                  - Show debug command help
/base debug toggle          - Toggle live debug messages on/off
/base debug clusters        - View all your active clusters
/base debug detail <#>      - Detailed info for specific cluster
/base debug clear           - Clear all clusters (admin only)
```

### Live Debug Messages (when toggled on)
When debug mode is enabled, you see real-time messages:
```
[BaseDet] [Score] BLOCK_PLACE +1.50 at [BASE] (total: 45.2)
[BaseDet] [Score] BLOCK_BREAK +0.10 at [MINING] (total: 12.3)
  Mining stats: broken=120, placed=15, ores=8
[BaseDet] [Cluster] Started new activity cluster at world at 1500, 64, 2000
[BaseDet] You now have 3/5 active clusters
```

### Cluster List Output
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Your Active Clusters (3/5)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

#1 ⌂ BASE
  Location: world at 1000, 64, 1000
  Score: 125.5 / 100 (threshold)
  Blocks: 80 broken, 95 placed
  Break/Place Ratio: 0.84
  Age: 2.5 hours

#2 ⛏ MINING
  Location: world at 1500, 15, 2000
  Score: 8.2 / 100 (threshold)
  Blocks: 200 broken, 5 placed
  Break/Place Ratio: 40.00
  Ores: 15 ⛏
  Age: 35 minutes

#3 ⚒ HYBRID
  Location: world at 800, 45, 1200
  Score: 45.0 / 100 (threshold)
  Blocks: 60 broken, 40 placed
  Break/Place Ratio: 1.50
  Ores: 3 ⛏
  Age: 1.2 hours

Use /base debug detail <#> for more info
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Detailed Cluster Info
```
/base debug detail 1

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⬡ Cluster #1 Details
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Type: ⌂ BASE
Center: 1000, 64, 1000 in world
Score: 125.50 / 100
Progress: 125.5%

⚡ Activity Stats:
  Blocks Broken: 80
  Blocks Placed: 95
  Total Blocks: 175
  Break/Place Ratio: 0.84

⌂ Base Indicators:
  Bed: ✓
  Door: ✓
  Chest: ✓

⏱ Time Info:
  Created: 2 hours 30 minutes ago
  Last Activity: just now

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Console Debug (Admin)
Console can view all players' clusters:
```
/base debug clusters

=== All Active Clusters ===
PlayerName: 3 clusters
  [BASE] world at 1000, 64, 1000 - Score: 125.5
  [MINING] world at 1500, 15, 2000 - Score: 8.2
  [HYBRID] world at 800, 45, 1200 - Score: 45.0
```

### Debug Icons
- ⌂ = BASE cluster (green)
- ⛏ = MINING cluster (red)
- ⚒ = HYBRID cluster (yellow)
- ? = UNKNOWN cluster (gray)

### Using Debug for Testing

**Test Mining Detection:**
```
1. /base debug toggle
2. Go to Y=15 and mine in straight line
3. Watch messages show MINING classification
4. /base debug clusters - see high break/place ratio
5. /base debug detail 1 - see ore percentage and ratios
```

**Test Base Building:**
```
1. /base debug toggle
2. Build a house with bed, door, chest
3. Watch messages show BASE classification
4. /base debug clusters - see balanced ratios
5. /base debug detail 1 - see base indicators
```

**Test Multi-Location:**
```
1. /base debug toggle
2. Build at location A until score is high
3. Travel 300+ blocks away
4. Build at location B
5. /base debug clusters - see 2 separate clusters
6. Return to location A
7. Continue building - goes to cluster A (not new)
```

### Complete Testing Workflow

**Comprehensive Test Session:**
```bash
# 1. Enable debug mode
/base debug toggle

# 2. Start building a house
Build house at (1000, 64, 1000)
Place bed, door, chest
→ Should see: "Started new activity cluster"
→ Should see: Score increases with BASE classification

# 3. Check cluster status
/base debug clusters
→ Should show: 1 cluster, type BASE, low break/place ratio

# 4. Get detailed info
/base debug detail 1
→ Should show: Base indicators (bed ✓, door ✓, chest ✓)
→ Should show: Low ore percentage

# 5. Go mining far away
Teleport to (2000, 15, 2000)
Strip mine in straight line, break 100 blocks
→ Should see: "Started new activity cluster"
→ Should see: Score increases very slowly (90% penalty)
→ Should see: MINING classification after ~20 blocks

# 6. Check clusters again
/base debug clusters
→ Should show: 2 clusters
→ Cluster 1: BASE with high score
→ Cluster 2: MINING with low score despite many blocks

# 7. Compare details
/base debug detail 1
→ BASE: balanced ratios, base indicators
/base debug detail 2
→ MINING: high break/place ratio (20:1), ores detected

# 8. Return to base
Teleport back to (1000, 64, 1000)
Place 10 more blocks
→ Should see: Score added to cluster #1 (not new cluster)
→ Distance < 200 blocks, reuses existing

# 9. Build another base far away
Teleport to (1500, 70, 1500)
Build small shelter with bed
→ Should see: "Started new activity cluster"
→ Should see: #3 cluster created

# 10. Final cluster list
/base debug clusters
→ Should show: 3 clusters
→ All with different locations, types, scores

# Success! Multi-location tracking working perfectly
```

### Visual Debug Output Example

**When you place a block at BASE:**
```
§8[BaseDet] §7[Score] §aBLOCK_PLACE §7+2.00 at §a[BASE] §7(total: 47.5)
```

**When you break ore while MINING:**
```
§8[BaseDet] §7[Score] §cBLOCK_BREAK §7+0.15 at §c[MINING] §7(total: 5.8)
§7  Mining stats: broken=120, placed=15, ores=8
```

**When cluster type changes:**
```
§8[BaseDet] §7[Cluster] §eCluster reclassified: §7UNKNOWN §7→ §cMINING
§7  Reason: High break/place ratio (15.0), ores detected (8)
```

**When you move to new area:**
```
§8[BaseDet] §a[Cluster] Started new activity cluster at world at 1500, 64, 2000
§7You now have 2/5 active clusters
```

---

## Key Design Decisions

### Why 200 Block Threshold?
- Large enough to separate distinct bases
- Small enough to keep related activity together
- Players building mega-bases stay in one cluster

### Why 4-Hour Expiry?
- Allows temporary mining/building excursions
- Cleans up abandoned locations
- Keeps data manageable

### Why Never Remove Highest Cluster?
- Main base should always be tracked
- Prevents loss of primary location data
- Ensures at least one permanent cluster

### Why 90% Mining Penalty?
- Enough to prevent false positives
- Still allows score accumulation if player persists
- Distinguishes sustained base-building from mining

### Why Weighted Center Update?
- Prevents cluster from jumping around
- Gradual shift follows actual activity
- More stable than simple average

---

## Performance Considerations

### Memory Usage
```
Per Player:
  - Max 5 clusters
  - Each cluster: ~50 positions × 3 ints = 600 bytes
  - Total: ~3KB per player
  
1000 players = ~3MB memory (negligible)
```

### CPU Usage
```
Per Block Break/Place:
  1. Distance calculation (all clusters)  O(n) n=5 max
  2. Linear pattern variance              O(m) m=10
  3. Reclassification                     O(1)
  
Total: Constant time per interaction
```

### Cleanup Task
```
Runs every 5 minutes
Iterates all players' clusters
Removes expired (except highest)
Async-safe (ConcurrentHashMap)
```

---

## Configuration Integration

### Config Options Used
```yaml
detection:
  threshold: 100.0        # Score needed for detection
  decay-amount: 5.0       # Optional periodic decay
  decay-interval: 3600    # Optional decay frequency
```

### Cluster Manager Constants
```java
MAX_CLUSTERS = 5                          // Hardcoded
NEW_CLUSTER_DISTANCE = 200                // Hardcoded
CLUSTER_ACTIVATION_THRESHOLD = 20.0       // Hardcoded
CLUSTER_EXPIRY_MS = 4 hours               // Hardcoded
```

---

## Future Enhancements

### Potential Improvements
1. **Persistence:** Save clusters to database for server restarts
2. **Configurable Constants:** Make thresholds configurable
3. **Machine Learning:** Train model on player behavior patterns
4. **Ore Types:** Weight different ores differently (diamond > coal)
5. **Time-Based Analysis:** Track time spent in cluster
6. **Biome Detection:** Different rules for nether/end

### Already Implemented Features
✅ Multi-location tracking (5 clusters)
✅ Smart mining detection
✅ Linear pattern detection
✅ Score penalties for mining
✅ Base indicator recognition
✅ Debug mode for testing
✅ Cluster expiry with protection
✅ Weighted center updates

---

## Testing Recommendations

### Test Cases
1. **Strip Mining Test:** Mine 200 blocks in straight line → should NOT detect
2. **Base Building Test:** Build 100 block house with bed → should detect
3. **Multi-Base Test:** Build bases 300 blocks apart → both should detect independently
4. **Cave Exploration Test:** Break 150 random cave blocks → should not detect or heavily penalized
5. **Mining Then Building Test:** Mine 200 blocks (MINING type), then place 100 at same spot → should reclassify to HYBRID/BASE

### Debug Testing
```
1. Player enables debug: /base debug
2. Perform test actions
3. Watch console output for:
   - Cluster creation messages
   - Score modifications
   - Type classifications
   - Mining statistics
```

---

## Summary

The algorithm works through **two independent systems**:

1. **Spatial Clustering:** Automatically groups nearby activity into clusters, creates new clusters when player moves far away, manages up to 5 locations per player

2. **Pattern Classification:** Analyzes break/place ratios, ore mining, depth, and linear patterns to distinguish mining from base-building, applies 90% score penalty to mining activity

**Result:** Players can mine extensively without false positives, while legitimate bases are reliably detected across multiple locations.

---

## Debug Command Quick Reference Card

```
╔════════════════════════════════════════════════════════════════════╗
║                  WDP-BaseDet Debug Commands                        ║
╠════════════════════════════════════════════════════════════════════╣
║                                                                    ║
║  BASIC COMMANDS                                                    ║
║  ─────────────────────────────────────────────────────────────    ║
║  /base debug              Show debug command help                 ║
║  /base debug toggle       Toggle live debug messages              ║
║  /base debug clusters     List all your clusters                  ║
║  /base debug detail <#>   Detailed cluster information            ║
║                                                                    ║
║  ADMIN COMMANDS                                                    ║
║  ─────────────────────────────────────────────────────────────    ║
║  /base debug clear        Clear all clusters (admin)              ║
║  /base debug clusters     View all players' clusters (console)    ║
║                                                                    ║
║  CLUSTER TYPES & ICONS                                             ║
║  ─────────────────────────────────────────────────────────────    ║
║  ⌂ BASE    - Building activity (green)                            ║
║  ⛏ MINING  - Mining activity (red) - 90% score penalty           ║
║  ⚒ HYBRID  - Mixed activity (yellow) - 50% score penalty         ║
║  ? UNKNOWN - Not classified yet (gray)                            ║
║                                                                    ║
║  KEY METRICS TO WATCH                                              ║
║  ─────────────────────────────────────────────────────────────    ║
║  • Break/Place Ratio: < 2.0 = BASE, > 5.0 = MINING               ║
║  • Ore Percentage: > 10% = likely mining                          ║
║  • Score Progress: Shows distance to detection threshold          ║
║  • Cluster Age: Shows when cluster was created                    ║
║  • Last Activity: Shows recent activity timestamp                 ║
║                                                                    ║
║  TESTING TIPS                                                      ║
║  ─────────────────────────────────────────────────────────────    ║
║  1. Toggle debug on before testing                                ║
║  2. Watch live messages as you build/mine                         ║
║  3. Check clusters list to see classification                     ║
║  4. Use detail command for full statistics                        ║
║  5. Test at different Y-levels (mining usually < Y:60)            ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝

CLUSTER LIMITS:
  • Maximum 5 clusters per player
  • New cluster created when > 200 blocks from existing
  • Clusters expire after 4 hours of inactivity
  • Highest scoring cluster NEVER expires

SCORE PENALTIES:
  • MINING type: 90% reduction (0.1x multiplier)
  • HYBRID type: 50% reduction (0.5x multiplier)
  • BASE type: No penalty (1.0x multiplier)
  • UNKNOWN type: No penalty (1.0x multiplier)

DETECTION TRIGGERS:
  • Cluster score >= 100 (configurable)
  • Cluster type != MINING
  • Cluster age > activation threshold (20 score)
```
