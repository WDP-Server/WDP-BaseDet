# WDP-BaseDet Detection System - Complete Logic Report

## How Cluster Detection Works (Step-by-Step)

### 1. When You Place a Block

**What happens:**
1. System records your action (block type, location, world)
2. System calculates a score value for that block (e.g., bed = 15, chest = 10, stone = 0.1)
3. System looks for the **closest existing cluster** to this block location

---

### 2. Cluster Assignment Logic

**The system checks:**
- "Do I have any existing clusters in this world?"
- "What is the distance to each cluster?"

**Decision made:**

| Condition | Action |
|-----------|--------|
| **Closest cluster is < 100 blocks away** | Add score to THAT cluster (you're building near existing location) |
| **All clusters are > 100 blocks away** | Create a NEW cluster at this location (you moved far away) |

**Critical number: `new-cluster-distance: 100`**
- Within 100 blocks → Use existing cluster
- Beyond 100 blocks → Start new cluster

---

### 3. Score Accumulation (Per Cluster)

**Each cluster has its own independent score.**

Example scenario:
```
Cluster A (at spawn)     → Score: 45
Cluster B (at mountain)  → Score: 85
Cluster C (at ocean)     → Score: 12
```

When you place blocks:
- **At spawn** → Adds to Cluster A only
- **At mountain** → Adds to Cluster B only
- **At ocean** → Adds to Cluster C only
- **500 blocks away** → Creates Cluster D (new)

**Important:** Scores do NOT add up across clusters. Each cluster is separate.

---

### 4. Detection Triggering

**Detection triggers when:**
1. A single cluster reaches score ≥ 150 (`detection-threshold`)
2. That cluster is NOT classified as "MINING"
3. That cluster has score ≥ 20 (`activation-threshold`)

**Example scenarios:**

| Situation | Result |
|-----------|--------|
| You build at spawn, Cluster A reaches 150 | ✅ Detection triggers for Cluster A |
| You build at mountain, Cluster B reaches 150 | ✅ Detection triggers for Cluster B |
| Cluster A = 80, Cluster B = 60 (total 140) | ❌ No detection (neither reaches 150 individually) |
| You mine at Cluster C, reaches 150 | ❌ No detection (mining clusters ignored) |
| Cluster D reaches 15 | ❌ No detection (below activation threshold) |

---

### 5. Far Away Building Verification

**Scenario:** You have Cluster A at spawn (score 80), then fly 1000 blocks away and start building.

**What happens:**
1. First block placed 1000 blocks away
2. System checks: "Nearest cluster is 1000 blocks away"
3. System sees: 1000 > 100 (new-cluster-distance)
4. **Creates Cluster B at new location** (score starts at 0)
5. Continue building at new location
6. Score adds to Cluster B only
7. When Cluster B reaches 150 → Detection triggers for Cluster B

**Cluster A at spawn:**
- Still exists with score 80
- Does NOT interfere with Cluster B
- Does NOT trigger detection (only at 80/100)

---

### 6. Multiple Clusters Management

**Maximum clusters per player: 5**

When you try to create a 6th cluster:
1. System finds the LOWEST scoring cluster
2. Removes that cluster (unless it's the highest scoring and protection enabled)
3. Creates the new cluster

**Protection:** The highest scoring cluster is never removed (prevents losing your best base)

---

### 7. Cluster Types & Penalties

Clusters are automatically classified:

| Type | When | Score Multiplier |
|------|------|------------------|
| **BASE** | Placing blocks (chests, beds, crafting tables) | 1.0x (normal) |
| **MINING** | Breaking lots of ore/stone | 0.1x (90% penalty) |
| **HYBRID** | Mix of placing and mining | 0.5x (50% penalty) |
| **UNKNOWN** | Initial state, not enough data | 1.0x (normal) |

**Why this matters:**
- Mining doesn't trigger detection (even at high scores)
- Strip mining won't cause false positives
- Actual base building gets full score

---

### 8. Commands & What They Show

**`/base score`** (default)
- Shows ONLY the cluster with highest score
- This is your "active detection" cluster
- The one most likely to trigger next

**`/base score all`**
- Shows ALL your clusters
- Lists each with location, type, and score
- Good for debugging multiple locations

---

## Critical Configuration Values

```yaml
detection-threshold: 150          # Individual cluster must reach this
activation-threshold: 20.0        # Cluster must be "established" (prevents tiny clusters)
new-cluster-distance: 100         # How far to start new cluster
max-per-player: 5                 # Maximum separate clusters
mining-penalty: 0.1               # Mining gives 10% score
hybrid-penalty: 0.5               # Mixed activity gives 50% score
```

---

## Verification Checklist

✅ **Far away building creates new cluster?**
- YES - If distance > 200 blocks, always creates new cluster

✅ **New cluster starts at score 0?**
- YES - Each new cluster begins fresh

✅ **Old clusters don't interfere?**
- YES - Each cluster score is independent

✅ **Detection triggers on active cluster only?**
- YES - Only the cluster you're building in can trigger

✅ **Global score sum ignored?**
- YES - Fixed in latest deployment, sum is only for stats

✅ **Mining clusters ignored for detection?**
- YES - MINING type never triggers detection

✅ **Multiple locations supported?**
- YES - Up to 5 simultaneous clusters per player

---

## Example Full Scenario

**Timeline:**
1. Start at spawn (0, 0), place chest → Creates Cluster A, score 10
2. Place bed → Cluster A score 25
3. Place door → Cluster A score 30
4. Continue building → Cluster A score 95
5. Fly to coordinates (5000, 5000)
6. Place chest → Creates Cluster B (5000 > 100), score 10
7. Build house → Cluster B score 160
8. **Detection triggers for Cluster B** (reached 150)
9. Confirm/deny prompt appears for Cluster B location
10. Cluster A still exists at score 95 (untouched)

**Result:** System correctly detected your NEW base at (5000, 5000), ignoring the incomplete Cluster A at spawn.

---

## What Was Fixed

**Before:** Detection used sum of all cluster scores (global total)
- Cluster A (45) + Cluster B (60) = 105 → Would trigger ❌

**After:** Detection checks individual cluster scores
- Cluster A (45) → No trigger ✅
- Cluster B (60) → No trigger ✅
- Only when one cluster reaches 150 individually → Triggers ✅

---

## Summary

The system works like having **separate "base detection zones"** that track independently:
- Build within 200 blocks → Same zone
- Build beyond 200 blocks → New zone
- Each zone must reach 100 score individually
- Zones don't add together
- System detects where you're actively building
