package org.xsu.magicchunkloader;

import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.FluidCollisionMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MagicChunkListener implements Listener {

    private final Random random = new Random(Double.doubleToLongBits(Math.random()));
    private final Logger logger;
    private final Plugin plugin;

    private final MagicChunkConfig config;


    public MagicChunkListener(Plugin plugin, MagicChunkConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = config.getLogger();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDiscover(ChunkPopulateEvent event) {
        if (Math.random() > config.getSpawnChance()) {
            return; // Skip processing based on the defined spawn chance
        }

        // Process asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Chunk chunk = event.getChunk();
            ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot();
            World world = event.getWorld();

            // Check for fluid biomes asynchronously
            if (!config.isAllowFluidSpawns() && containsFluidBiome(chunkSnapshot)) {
                return;
            }

            // Calculate chunk boundaries
            int minZ = chunk.getZ() * 16, maxZ = minZ + 15;
            int minX = chunk.getX() * 16, maxX = minX + 15;

            // Find a random surface block
            Block surfaceBlock = randomSurfaceBlockAt(world, minX, maxX, minZ, maxZ);

            // Process block checks and spawning on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> processBlockAndSpawn(world, surfaceBlock));
        });
    }

    /**
     * Checks if a given chunk snapshot contains any fluid biomes.
     *
     * This method is thread-safe and can be used asynchronously to determine
     * if a chunk contains biomes such as oceans or lakes.
     *
     * @param snapshot The {@link org.bukkit.ChunkSnapshot} to check.
     * @return true if any fluid biomes are present, false otherwise.
     */
    private boolean containsFluidBiome(ChunkSnapshot snapshot) {
        for (Biome biome : fluidBiomes) {
            if (snapshot.contains(biome)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a random surface block within given world coordinates.
     *
     * @param world The world to search in.
     * @param minX  Minimum X-coordinate.
     * @param maxX  Maximum X-coordinate.
     * @param minZ  Minimum Z-coordinate.
     * @param maxZ  Maximum Z-coordinate.
     * @return Random surface block or null if none found.
     */
    private Block randomSurfaceBlockAt(World world, int minX, int maxX, int minZ, int maxZ) {
        int randomZ = random.nextInt(maxZ - minZ + 1) + minZ;
        int randomX = random.nextInt(maxX - minX + 1) + minX;

        // Custom algorithm for implementation of the nether
        if (world.getEnvironment().equals(World.Environment.NETHER) && config.isAllowNetherSpawns()) {

            // Note: Ceiling begins at Y=127
            Block netherCeilingBlock = world.getBlockAt(randomX, 127, randomZ);

            // Usually, there are surface level structures from the ceiling down,
            // hence, in an oxymoronic way, we will "ray-cast" until we hit air.
            // Then, we actually ray-cast down from the netherCeilingBlock to a "surface" block
            // Note: This surface block can be lava, and will be validated by later functions
            return findViableBlockInStructures(world, netherCeilingBlock);
        }

        if (world.getEnvironment().equals(World.Environment.THE_END) && !config.isAllowEndSpawns()) {
            return null;
        }

        return world.getHighestBlockAt(randomX, randomZ, HeightMap.WORLD_SURFACE_WG);
    }

    /**
     * Processes a given block and spawns an entity if viable.
     *
     * Checks if the block is viable using {@link #isViableBlock(Block)}.
     * If viable, it triggers an entity spawn at this location.
     *
     * @param surfaceBlock The block to be checked and used for spawning.
     * @param world The world where the block is located.
     */
    private void processBlockAndSpawn(World world, Block surfaceBlock) {
        if (isViableBlock(surfaceBlock) != Viability.VIABLE) {
            surfaceBlock = findViableBlock(world, surfaceBlock);
        }

        if (surfaceBlock != null) {
            spawnItem(surfaceBlock, world);
        }
    }

    /**
     * Finds a viable block within a specified radius around a central block.
     * The method searches in a spiral pattern outward from the central block,
     * checking the highest block at each location to determine if it's viable
     * based on certain criteria defined in {@link #isViableBlock(Block)}.
     *
     * The search radius is set to 4 blocks, meaning it checks in a 9x9x9 grid
     * centered on the provided block.
     *
     * Note: This method adheres to Bukkit/Spigot's main thread requirements for
     * world and block interactions. It should only be called from the main server
     * thread.
     *
     * @param centerBlock The central block from which the search is conducted.
     * @return The first viable block found within the radius, or null if none is found.
     */
    private Block findViableBlock(World world, Block centerBlock) {
        Location loc = centerBlock.getLocation();
        int radius = 4;

        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // Check only the perimeter and faces of the cube
                        if (Math.abs(dx) == r || Math.abs(dy) == r || Math.abs(dz) == r) {
                            int x = loc.getBlockX() + dx;
                            int y = loc.getBlockY() + dy;
                            int z = loc.getBlockZ() + dz;

                            Block neighborBlock = centerBlock.getWorld().getBlockAt(x, y, z);
                            Viability viable = isViableBlock(neighborBlock);

                            if (viable == Viability.VIABLE) {
                                return neighborBlock;
                            } else if (viable == Viability.FLORA) {
                                // When it comes to trees we must do a different approach
                                // In particular we should begin going below the leaves
                                // until we reach an air block. Then ray-cast down to find
                                // the nearest surface block, or null. This is a similar approach to nether structures,
                                // and is more optimal than completing the full spiral at the 9 block height.
                                Block floralViableBlock = findViableBlockInStructures(world, neighborBlock);
                                if (floralViableBlock != null && isViableBlock(floralViableBlock) == Viability.VIABLE)
                                    return floralViableBlock;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Block findViableBlockInStructures(World world, Block startingBlock) {
        Block neighborBlock = startingBlock;

        // Descend below the leaves (or similar structures) to the first air block
        while (neighborBlock.getType() != Material.AIR && neighborBlock.getY() > 0) {
            neighborBlock = world.getBlockAt(neighborBlock.getX(), neighborBlock.getY() - 1, neighborBlock.getZ());
        }

        // Raycast downwards to find the nearest surface block
        neighborBlock = world.rayTraceBlocks(
                neighborBlock.getLocation(), new Vector(0, -1, 0), 128, FluidCollisionMode.ALWAYS
        ).getHitBlock();

        // Check if the found block is viable
        if (neighborBlock != null && isViableBlock(neighborBlock) == Viability.VIABLE) {
            return neighborBlock;
        }

        return null;
    }

    /**
     * Determines if a given block is viable for specific criteria.
     *
     * This method checks the block's type against predefined conditions
     * (e.g., non-leaf, non-wood, or non-fluid) to ascertain its viability.
     *
     * @param block The block to check for viability.
     * @return Viability an enum representing the viability of the block to spawn an item
     */
    private Viability isViableBlock(Block block) {
        // Quick edge-case
        if (block == null )
            return Viability.UNVIABLE;


        String rawBlockMaterial = block.getType().toString();

        // Check for leaves and wood (e.g., avoid trees)
        if (rawBlockMaterial.contains("LEAVES") || rawBlockMaterial.contains("WOOD"))
            return Viability.FLORA;

        // Avoid fluids if necessary
        if (!config.isAllowFluidSpawns() && (rawBlockMaterial.contains("WATER") || rawBlockMaterial.contains("LAVA")))
            return Viability.UNVIABLE;

        // Avoid non-solid blocks if fluids are allowed (e.g., vines, carpets, etc.)
        if (config.isAllowFluidSpawns() && block.isPassable())
            return Viability.UNVIABLE;

        return Viability.VIABLE;
    }

    /**
     * Spawns a specified item at a given block's location.
     *
     * This method creates an item entity at the block's location,
     * applying specified properties such as no gravity.
     *
     * Will include a Level.INFO log if requested.
     *
     * @param block The block at which to spawn the item.
     * @param world The world in which the item is spawned.
     */
    private void spawnItem(Block block, World world) {
        // Obtain the "perfect" center of the block and place a bit above it
        Location blockLocation = block.getLocation().add(0.5, 1.0, 0.5);
        new BukkitRunnable() {
            @Override
            public void run() {
                Item item = world.dropItem(blockLocation, config.getDroppedItem());

                // Removing gravity and velocity allows it to both levitate on liquids and above solid blocks
                item.setVelocity(new Vector());
                item.setGravity(false);

                // Schedule it's despawn at this point
                scheduleDespawnTask(item);

                if (config.isIncludesLogs())
                    logger.info(String.format(
                            "Generated a dropped item at %s with coordinates (%.0f, %.0f, %.0f)!",
                            world,
                            blockLocation.getX(),
                            blockLocation.getY(),
                            blockLocation.getZ()
                    ));
            }
        }.runTask(plugin);
    }

    /**
     * Schedules a task to despawn an item after a constant-set delay.
     *
     * This method creates a delayed task that will remove the item
     * if it still exists after the delay has elapsed.
     *
     * @param item The item entity to be despawned.
     */
    private void scheduleDespawnTask(Item item) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!item.isDead()) {
                    item.remove();

                    if (config.isIncludesLogs()) {
                        Location loc = item.getLocation();
                        logger.info(String.format(
                                "Despawned a generated item at %s with coordinates (%.0f, %.0f, %.0f)!",
                                loc.getWorld(),
                                loc.getX(),
                                loc.getY(),
                                loc.getZ()
                        ));
                    }
                }
            }
        }.runTaskLater(plugin, config.getDespawnTime() * 20); // 20 ticks/second
    }

    // Using set can allow for O(1) calls
    // See: https://stackoverflow.com/questions/6634816/set-time-and-speed-complexity
    final Set<Biome> fluidBiomes = Set.of(
            Biome.WARM_OCEAN,
            Biome.LUKEWARM_OCEAN,
            Biome.OCEAN,
            Biome.COLD_OCEAN,
            Biome.FROZEN_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN,
            Biome.DEEP_OCEAN,
            Biome.DEEP_COLD_OCEAN,
            Biome.DEEP_FROZEN_OCEAN
    );

    // Simple enum for representing block viability as a spawning point
    private enum Viability {
        VIABLE,
        FLORA,
        UNVIABLE
    }
}
