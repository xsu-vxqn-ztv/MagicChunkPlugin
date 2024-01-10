# MagicChunkLoader v1.0.0
A simple plugin which generates a custom-defined item in a random location on first chunk load.
Built via Maven.

## Configuration

Below is a table describing each configuration option in `config.yml` for MyPlugin:

| Option                | Description                                                | Data Type | Default Value |
|-----------------------|------------------------------------------------------------|-----------|---------------|
| `version`             | The version of the configuration file.                     | String    | `1.0.0`       |
| `dropped-item`        | Defines the item to be spawned.                            | ItemStack | Diamond       |
| `item-glows`          | Determines if the dropped item has an enchantment glow.    | Boolean   | `true`        |
| `spawn-chance`        | The chance (0 to 1.0) that the item will spawn in a chunk. | Double    | `1.0`         |
| `despawn-time`        | Time in seconds before the item despawns.                  | Integer   | `60`          |
| `allow-fluid-spawns`  | If `true`, items can spawn "levitating" above fluids.      | Boolean   | `true`        |
| `allow-end-spawns`    | If `true`, items can spawn in the end.                     | Boolean   | `true`        |
| `allow-nether-spawns` | If `true`, items can spawn in the nether.                  | Boolean   | `true`        |
| `logs-enabled`        | Enables logging of item-related actions to the console.    | Boolean   | `true`        |

### Dropped Item Configuration

The `dropped-item` is an `ItemStack` with several properties:

- `type`: The type of item (e.g., `DIAMOND`).
- `amount`: The number of items to spawn.
- `meta`: Metadata for the item, including:
    - `display-name`: The name of the item, supporting color codes.
    - `lore`: Descriptions or lore for the item, supporting color codes.

### Additional Details

- Color codes in `display-name` and `lore` use the `&` symbol (e.g., `&c` for red).
- `spawn-chance` is a probability (0.0 = never, 1.0 = always).
- `despawn-time` is measured in seconds.

## Developer Notes
This section simply outlines interesting notes regarding specific implementation practices.

Finding the "surface" level block
This was actually trickier than expected. There were a few things to take into account:
- Trees
- Fluids (e.g., lava and water)
- The End
- The Nether

### Trees and Nether
The approach to trees and the nether were actually pretty similar. First you take the original block
(or for the nether, the bedrock ceiling block) and begin iterating down until you reach an AIR block.
From there, you can just ray-cast down until you hit a surface. Then check the "viability" of this block.

### Fluids
The approach for fluids simply involved an optional check in config.yml (same for nether and end), however,
this one requires floating the item on top of the water by removing its velocity and gravity.

### The End
The end only spawns items on the pillars (obsidian) and the island floor. None in the void.

### Viability and Algorithmic Neighbor-Blocks
Checking "viability" of the block performs a split into four* sections: `VIABLE`, `FLORAL`, `FLUID` (which isn't 
actually a part of the enum but is logically present in later checks), `UNVIABLE`, but also (when applicable) begins a 
spiral 9x9x9 algorithm, which checks neighboring blocks in a spiralling pattern until a 
viable source is found. This not only saves memory (fewer blocks to juggle for the garbage collector) but is also
increases performance while sacrificing some precision in the chunk.


