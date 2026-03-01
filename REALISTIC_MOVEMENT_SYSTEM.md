# Realistic Movement & Action System

## Overview

The bots now use a fully functional realistic movement and action system that makes them behave like real Minecraft players. This system replaces the old teleport-based movement with proper physics, pathfinding, and environmental awareness.

## Key Features

### 1. Realistic Movement (`RealisticMovement.java`)

#### Player-Like Physics
- **Gravity**: Bots fall naturally and take fall damage
- **Jumping**: Automatically jump over 1-block obstacles
- **Swimming**: Proper water navigation with upward movement when underwater
- **Sprinting**: Dynamic speed based on terrain and conditions

#### Intelligent Pathfinding
- **Obstacle Detection**: Uses raycasting to detect blocks in the path
- **Alternative Routes**: Finds paths around obstacles at 45° and 90° angles
- **Stuck Detection**: Detects when bot hasn't moved for 3 seconds and finds new goal
- **Cliff Avoidance**: Won't walk off edges > 3 blocks high

#### Environmental Awareness
- **Lava Avoidance**: Detects and avoids lava blocks
- **Void Protection**: Won't fall into the void (y < 0)
- **Water Handling**: Swims properly and surfaces when underwater
- **Terrain Speed**: Moves slower in water, faster when sprinting

#### Natural Movement
- **Smooth Rotation**: Realistic head and body rotation towards movement direction
- **Speed Calculation**: Dynamic speed based on sprinting, water, sneaking
- **Reach Detection**: Checks if blocks are within interaction range (5 blocks)

### 2. Realistic Actions (`RealisticActions.java`)

#### Progressive Mining
- **Mining State Tracking**: Tracks current mining operation with progress
- **Tool-Based Speed**: Mining time calculated from block hardness and tool efficiency
- **Breaking Animation**: Shows progressive block-breaking stages (0-9)
- **Arm Swinging**: Visual feedback every 4 ticks while mining
- **Proper Drops**: Uses gameMode.destroyBlock() for correct item drops

#### Automatic Tool Management
- **Best Tool Selection**: Automatically equips the best tool for each block type
- **Tool Hierarchy**: Diamond > Iron > Stone > Wood
- **Task-Based Switching**:
  - Axes for wood gathering
  - Pickaxes for stone/ore mining
  - Swords for combat
  - Shovels for dirt/sand

#### Realistic Eating
- **Hunger Detection**: Eats when hunger < 14 (like real players)
- **Food Priority**: Cooked meat > Raw meat > Bread > Apples
- **Hunger Restoration**: Proper hunger values for each food type
- **Inventory Management**: Automatically finds and consumes food

#### Combat System
- **Attack Cooldown**: Respects attack strength scale (waits for 90%+ charge)
- **Weapon Selection**: Automatically equips best weapon from inventory
- **Target Tracking**: Looks at target while attacking
- **Realistic Timing**: Matches player combat mechanics

#### Building & Interaction
- **Block Placement**: Realistic block placing with proper positioning
- **Block Interaction**: Can use crafting tables, furnaces, chests
- **Inventory Checks**: Verifies items exist before using them
- **Item Dropping**: Can drop items from inventory

### 3. Survival AI Integration

#### Health Management
- **Critical Health (< 5 HP)**: Stops all actions and retreats
- **Low Health (< 10 HP)**: Prioritizes eating and avoiding combat
- **Health Monitoring**: Continuous health checks every tick

#### Hunger Management
- **Automatic Eating**: Eats when hunger < 14
- **Food Finding**: Searches inventory for best available food
- **Hunger Tracking**: Uses Minecraft's FoodData system

#### Task Execution
- **Goal-Based Movement**: Moves to specific blocks based on current task
- **Block Finding**: Searches for nearest relevant blocks (trees, stone, ores)
- **Task Completion**: Mines blocks when reached, then finds new goal
- **Stuck Recovery**: Abandons unreachable goals after 3 seconds

## How It Works

### Movement Flow

1. **Goal Setting**: LLM or task system sets a BlockPos goal
2. **Path Calculation**: RealisticMovement.moveTowards() calculates direction
3. **Obstacle Check**: Raycasting detects blocks in the path
4. **Jump/Navigate**: Either jumps over obstacle or finds alternative path
5. **Danger Check**: Verifies path is safe (no lava, cliffs, void)
6. **Apply Movement**: Sets entity velocity with proper physics
7. **Update Rotation**: Rotates head/body to face movement direction
8. **Stuck Detection**: Monitors position changes, resets goal if stuck

### Mining Flow

1. **Reach Goal**: Bot arrives at target block position
2. **Check Block Type**: Verifies block matches current task
3. **Equip Tool**: Automatically equips best tool for block type
4. **Start Mining**: Initializes MiningState with calculated mining time
5. **Progressive Breaking**: Updates breaking animation every tick
6. **Arm Swing**: Swings arm every 4 ticks for visual feedback
7. **Complete Mining**: Breaks block when mining time reached
8. **Find Next Goal**: Executes task to find next block to mine

### Task Execution Flow

1. **Task Assignment**: LLM sets task (gather_wood, mine_stone, etc.)
2. **Block Search**: Searches 32-block radius for relevant blocks
3. **Goal Setting**: Sets nearest matching block as goal
4. **Movement**: Uses realistic movement to navigate to block
5. **Mining**: Mines block when reached
6. **Repeat**: Finds next block and continues until task expires

## Integration with LLM System

The realistic movement system is fully integrated with the LLM control:

- **LLM Sets Goals**: BotBrain sends "set_goal" actions with task names
- **ActionExecutor Maps Tasks**: Converts LLM goals to task strings
- **AmbNpcEntity Executes**: Uses realistic movement to complete tasks
- **Feedback Loop**: Bot reports progress and completion to LLM

## Performance Optimizations

- **Cached Entity Searches**: Entity searches cached for 2 seconds
- **Tick-Based Updates**: Movement updates every tick, tool switching every 2 seconds
- **Efficient Pathfinding**: Limited to 20 attempts for alternative paths
- **Radius Limits**: Block searches limited to 32-block radius

## Configuration

### Movement Speeds
- **Walking**: 0.1 blocks/tick
- **Sprinting**: 0.13 blocks/tick
- **Swimming**: 0.05 blocks/tick (50% slower)
- **Sneaking**: 0.03 blocks/tick (30% of walking)

### Mining Times
- **Base Time**: Block hardness × 20 ticks
- **Tool Multiplier**: Divided by tool destroy speed
- **Example**: Stone (hardness 1.5) with wooden pickaxe (speed 2.0) = 15 ticks

### Detection Ranges
- **Block Search**: 32 blocks radius
- **Interaction Range**: 5 blocks
- **Stuck Detection**: 60 ticks (3 seconds) without movement
- **Cliff Detection**: Falls > 3 blocks considered dangerous

## Testing

To test the realistic movement system:

1. **Spawn a bot**: `/amb spawn TestBot openai`
2. **Enable autonomous mode**: `/amb autonomous TestBot true`
3. **Watch the bot**: It should move naturally, jump over obstacles, mine blocks progressively
4. **Check console**: Look for movement debug messages

Expected behaviors:
- ✅ Bot walks smoothly towards goals
- ✅ Bot jumps over 1-block obstacles
- ✅ Bot avoids lava and cliffs
- ✅ Bot mines blocks with progressive breaking animation
- ✅ Bot automatically switches tools based on task
- ✅ Bot eats when hungry
- ✅ Bot retreats when low on health

## Troubleshooting

### Bot Not Moving
- Check if goal is set: Look for "currentGoal" in debug output
- Verify task is assigned: Check "currentTask" value
- Check for stuck detection: Bot should reset goal after 3 seconds

### Bot Mining Instantly
- Verify RealisticActions.continueMining() is being called
- Check miningState.isMining flag
- Ensure mining ticks are incrementing

### Bot Walking Through Walls
- Check obstacle detection in RealisticMovement.isPathBlocked()
- Verify raycasting is working correctly
- Check entity collision settings

### Bot Falling Off Cliffs
- Verify isDangerousPath() is being called
- Check cliff detection threshold (currently 3 blocks)
- Ensure fall distance calculation is correct

## Future Enhancements

Potential improvements to the system:

1. **Advanced Pathfinding**: A* algorithm for complex navigation
2. **Block Breaking**: Break blocks that are in the way
3. **Ladder Climbing**: Navigate vertical structures
4. **Boat Usage**: Use boats for water travel
5. **Elytra Flight**: Use elytra for long-distance travel
6. **Parkour**: Jump across gaps and navigate complex terrain
7. **Building**: Place blocks to create paths and bridges
8. **Redstone**: Interact with redstone mechanisms

## Code Structure

```
movement/
├── RealisticMovement.java    - Physics, pathfinding, navigation
└── RealisticActions.java     - Mining, eating, combat, interaction

entity/
└── AmbNpcEntity.java         - Main bot entity, integrates movement system

bot/
├── BotBrain.java             - LLM decision-making
└── BotTicker.java            - Server tick handler

agent/
└── ActionExecutor.java       - Converts LLM actions to bot commands
```

## Summary

The realistic movement and action system transforms bots from teleporting entities into believable Minecraft players. They navigate the world naturally, interact with blocks realistically, and manage their survival needs autonomously. This creates a much more immersive and human-like experience for LLM-controlled bots.
