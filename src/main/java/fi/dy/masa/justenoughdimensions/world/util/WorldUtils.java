package fi.dy.masa.justenoughdimensions.world.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGeneratorBonusChest;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.client.render.SkyRenderer;
import fi.dy.masa.justenoughdimensions.config.Configs;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import fi.dy.masa.justenoughdimensions.network.MessageSyncWorldProperties;
import fi.dy.masa.justenoughdimensions.network.PacketHandler;
import fi.dy.masa.justenoughdimensions.world.JEDWorldProperties;
import fi.dy.masa.justenoughdimensions.world.WorldProviderHellJED;
import fi.dy.masa.justenoughdimensions.world.WorldProviderSurfaceJED;

public class WorldUtils
{
    private static final String JED_RESPAWN_DIM_TAG = "justenoughdimensions:respawndimension";
    //private static Field field_WorldProvider_terrainType;
    //private static Field field_WorldProvider_generatorSettings;
    private static Field field_WorldProvider_biomeProvider = null;
    private static Field field_ChunkProviderServer_chunkGenerator = null;

    static
    {
        try
        {
            //field_WorldProvider_terrainType = ReflectionHelper.findField(WorldProvider.class, "field_76577_b", "terrainType");
            //field_WorldProvider_generatorSettings = ReflectionHelper.findField(WorldProvider.class, "field_82913_c", "generatorSettings");
            field_WorldProvider_biomeProvider = ReflectionHelper.findField(WorldProvider.class, "field_76578_c", "biomeProvider");
            field_ChunkProviderServer_chunkGenerator = ReflectionHelper.findField(ChunkProviderServer.class, "field_186029_c", "chunkGenerator");
        }
        catch (UnableToFindFieldException e)
        {
            JustEnoughDimensions.logger.error("WorldUtils: Reflection failed!!", e);
        }
    }

    public static int getLoadedChunkCount(WorldServer world)
    {
        return world.getChunkProvider().getLoadedChunkCount();
    }

    /**
     * Unloads all empty dimensions (with no chunks loaded)
     * @param tryUnloadChunks if true, then tries to first save and unload all non-player-loaded and non-force-loaded chunks
     * @return the number of dimensions successfully unloaded
     */
    public static int unloadEmptyDimensions(boolean tryUnloadChunks)
    {
        int count = 0;
        Integer[] dims = DimensionManager.getIDs();

        JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload empty dimensions, tryUnloadChunks = {}", tryUnloadChunks);

        for (int dim : dims)
        {
            JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload dimension {}", dim);
            WorldServer world = DimensionManager.getWorld(dim);

            if (world == null)
            {
                continue;
            }

            ChunkProviderServer chunkProviderServer = world.getChunkProvider();

            if (tryUnloadChunks && chunkProviderServer.getLoadedChunkCount() > 0)
            {
                JustEnoughDimensions.logInfo("WorldUtils.unloadEmptyDimensions(): Trying to unload chunks for dimension {}", dim);
                boolean disable = world.disableLevelSaving;
                world.disableLevelSaving = false;

                try
                {
                    // This also tries to unload all chunks that are not loaded by players
                    world.saveAllChunks(true, (IProgressUpdate) null);
                }
                catch (MinecraftException e)
                {
                    JustEnoughDimensions.logger.warn("WorldUtils.unloadEmptyDimensions(): Exception while "+
                                                     "trying to save chunks for dimension {}", world.provider.getDimension(), e);
                }

                // This would flush the chunks to disk from the AnvilChunkLoader. Probably not what we want to do.
                //world.saveChunkData();

                world.disableLevelSaving = disable;

                // This will unload the dimension, if it unloaded at least one chunk, and it has no loaded chunks anymore
                chunkProviderServer.tick();

                if (chunkProviderServer.getLoadedChunkCount() == 0)
                {
                    count++;
                }
            }
            else if (chunkProviderServer.getLoadedChunkCount() == 0 &&
                world.provider.getDimensionType().shouldLoadSpawn() == false &&
                ForgeChunkManager.getPersistentChunksFor(world).size() == 0)
            {
                DimensionManager.unloadWorld(world.provider.getDimension());
                count++;
            }
        }

        return count;
    }

    public static void syncWorldProviderProperties(EntityPlayer player)
    {
        if (player instanceof EntityPlayerMP)
        {
            JustEnoughDimensions.logInfo("WorldUtils.syncWorldProviderProperties: Syncing WorldProvider properties " +
                                         "of dimension {} to player '{}'", player.getEntityWorld().provider.getDimension(), player.getName());
            PacketHandler.INSTANCE.sendTo(new MessageSyncWorldProperties(player.getEntityWorld()), (EntityPlayerMP) player);
        }
    }

    public static boolean setRenderersOnNonJEDWorld(World world, NBTTagCompound tag)
    {
        int skyRenderType = 0;
        int skyDisableFlags = 0;

        if (tag.hasKey("SkyRenderType", Constants.NBT.TAG_BYTE))   { skyRenderType = tag.getByte("SkyRenderType"); }
        if (tag.hasKey("SkyDisableFlags", Constants.NBT.TAG_BYTE)) { skyDisableFlags = tag.getByte("SkyDisableFlags"); }

        if (skyRenderType != 0)
        {
            world.provider.setSkyRenderer(new SkyRenderer(skyRenderType, skyDisableFlags));
            return true;
        }

        return false;
    }

    public static void findAndSetWorldSpawnIfApplicable(World world)
    {
        final int dimension = world.provider.getDimension();

        if (Configs.enableSeparateWorldInfo && DimensionConfig.instance().useCustomWorldInfoFor(dimension))
        {
            final boolean isDimensionInit = WorldFileUtils.jedLevelFileExists(world) == false;

            if (isDimensionInit)
            {
                findAndSetWorldSpawn(world);
            }
        }
    }

    /*
    public static void overrideWorldProviderSettings(World world, WorldProvider provider)
    {
        WorldInfo info = world.getWorldInfo();

        if (info instanceof WorldInfoJED)
        {
            try
            {
                JustEnoughDimensions.logInfo("WorldUtils.overrideWorldProviderSettings(): Trying to override the "+
                                             "WorldType and generatorSettings for dimension {}", provider.getDimension());
                field_WorldProvider_terrainType.set(provider, info.getTerrainType());
                field_WorldProvider_generatorSettings.set(provider, info.getGeneratorOptions());
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.error("WorldUtils.overrideWorldProviderSettings(): Failed to override " +
                                                  "WorldProvider settings for dimension {}", provider.getDimension());
            }
        }
    }
    */

    public static void overrideBiomeProvider(World world)
    {
        // For WorldProviderSurfaceJED the BiomeProvider has already been set in WorldProviderSurfaceJED#init()
        if ((world.provider instanceof WorldProviderSurfaceJED) == false)
        {
            int dimension = world.provider.getDimension();
            String biomeName = DimensionConfig.instance().getBiomeFor(dimension);
            Biome biome = biomeName != null ? Biome.REGISTRY.getObject(new ResourceLocation(biomeName)) : null;

            if (biome != null && ((world.provider.getBiomeProvider() instanceof BiomeProviderSingle) == false ||
                world.provider.getBiomeProvider().getBiome(BlockPos.ORIGIN) != biome))
            {
                BiomeProvider biomeProvider = new BiomeProviderSingle(biome);

                JustEnoughDimensions.logInfo("WorldUtils.overrideBiomeProvider: Overriding the BiomeProvider for dimension {} with {}" +
                    " using the biome '{}'", dimension, biomeProvider.getClass().getName(), biomeName);

                try
                {
                    field_WorldProvider_biomeProvider.set(world.provider, biomeProvider);
                }
                catch (Exception e)
                {
                    JustEnoughDimensions.logger.error("Failed to override the BiomeProvider of dimension {}", dimension);
                }
            }
        }
    }

    public static void reCreateChunkProvider(World world)
    {
        if (world instanceof WorldServer && world.getChunkProvider() instanceof ChunkProviderServer)
        {
            int dimension = world.provider.getDimension();
            WorldInfo info = world.getWorldInfo();
            WorldInfo infoOverWorld = world.getMinecraftServer().getWorld(0).getWorldInfo();

            if (infoOverWorld.getTerrainType() == info.getTerrainType() &&
                infoOverWorld.getGeneratorOptions().equals(info.getGeneratorOptions()))
            {
                JustEnoughDimensions.logInfo("No need to re-create the ChunkProvider in dimension {}", dimension);
                return;
            }

            // This sets the new WorldType and generatorOptions to the WorldProvider
            world.provider.setWorld(world);

            ChunkProviderServer chunkProviderServer = (ChunkProviderServer) world.getChunkProvider();
            IChunkGenerator newChunkGenerator = world.provider.createChunkGenerator();

            if (newChunkGenerator == null)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider for dimension {}", dimension);
                return;
            }

            JustEnoughDimensions.logInfo("WorldUtils.reCreateChunkProvider: Attempting to override/re-create the ChunkProvider (of type {}) in dimension {} with {}",
                    chunkProviderServer.chunkGenerator.getClass().getName(), dimension, newChunkGenerator.getClass().getName());

            try
            {
                field_ChunkProviderServer_chunkGenerator.set(chunkProviderServer, newChunkGenerator);
            }
            catch (Exception e)
            {
                JustEnoughDimensions.logger.warn("Failed to re-create the ChunkProvider for dimension {} with {}",
                        dimension, newChunkGenerator.getClass().getName(), e);
            }
        }
    }

    public static void findAndSetWorldSpawn(World world)
    {
        WorldProvider provider = world.provider;
        NBTTagCompound nbt = WorldInfoUtils.getWorldInfoTag(world, provider.getDimension(), false, false);
        BlockPos pos = world.getSpawnPoint();

        if (nbt.hasKey("SpawnX") && nbt.hasKey("SpawnY") && nbt.hasKey("SpawnZ"))
        {
            JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Spawn point defined in dimension config " +
                                         "for dimension {}, skipping the search", provider.getDimension());
            pos = new BlockPos(nbt.getInteger("SpawnX"), nbt.getInteger("SpawnY"), nbt.getInteger("SpawnZ"));
        }
        else
        {
            JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Trying to find a world spawn for dimension {}...", provider.getDimension());
            pos = findSuitableSpawnpoint(world);
        }

        world.setSpawnPoint(pos);
        JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Set the world spawnpoint of dimension {} to {}", provider.getDimension(), pos);

        WorldBorder border = world.getWorldBorder();

        if (border.contains(pos) == false)
        {
            border.setCenter(pos.getX(), pos.getZ());
            JustEnoughDimensions.logInfo("WorldUtils.findAndSetWorldSpawn: Moved the WorldBorder of dimension {} " +
                                         "to the world's spawn, because the spawn was outside the border", provider.getDimension());
        }
    }

    @Nonnull
    public static BlockPos findSuitableSpawnpoint(World world)
    {
        WorldProvider provider = world.provider;
        BlockPos pos;

        // Likely end type dimensions
        if (provider.getDimensionType() == DimensionType.THE_END ||
            provider instanceof WorldProviderEnd)
        {
            pos = provider.getSpawnCoordinate();

            if (pos == null)
            {
                pos = getSuitableSpawnBlockInColumn(world, BlockPos.ORIGIN);
            }
        }
        // Likely nether type dimensions
        else if (provider.getDimensionType() == DimensionType.NETHER ||
                 provider instanceof WorldProviderHell ||
                 provider instanceof WorldProviderHellJED)
        {
            pos = findNetherSpawnpoint(world);
        }
        else if (world.getWorldInfo().getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES)
        {
            pos = BlockPos.ORIGIN.up(64);
        }
        // Mostly overworld type dimensions
        else
        {
            pos = findOverworldSpawnpoint(world);
        }

        return pos;
    }

    @Nonnull
    private static BlockPos findNetherSpawnpoint(World world)
    {
        Random random = new Random(world.getSeed());
        int x = 0;
        int z = 0;
        int iterations = 0;
        BlockPos pos = new BlockPos(x, 120, z);

        while (iterations < 100)
        {
            while (pos.getY() >= 30)
            {
                if (world.isAirBlock(pos) && world.isAirBlock(pos.down(1)) && world.getBlockState(pos.down(2)).getMaterial().blocksMovement())
                {
                    return pos.down();
                }

                pos = pos.down();
            }

            x += random.nextInt(32) - random.nextInt(32);
            z += random.nextInt(32) - random.nextInt(32);
            pos = new BlockPos(x, 120, z);
            iterations++;
        }

        JustEnoughDimensions.logger.warn("Unable to find a nether spawn point for dimension {}", world.provider.getDimension());

        return new BlockPos(0, 70, 0);
    }

    @Nonnull
    private static BlockPos findOverworldSpawnpoint(World world)
    {
        WorldProvider provider = world.provider;
        BiomeProvider biomeProvider = provider.getBiomeProvider();
        List<Biome> list = biomeProvider.getBiomesToSpawnIn();
        Random random = new Random(world.getSeed());
        int x = 8;
        int z = 8;

        // This will not generate chunks, but only check the biome ID from the genBiomes.getInts() output
        BlockPos pos = biomeProvider.findBiomePosition(0, 0, 512, list, random);

        if (pos != null)
        {
            x = pos.getX();
            z = pos.getZ();
        }
        else
        {
            JustEnoughDimensions.logger.warn("Unable to find spawn biome for dimension {}", provider.getDimension());
        }

        int iterations = 0;

        // Note: This will generate chunks! Also note that the returned position might
        // still end up inside a tree or something, since decoration hasn't necessarily been done yet.
        while (iterations < 1000)
        {
            Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
            pos = new BlockPos(x, chunk.getTopFilledSegment() + 15, z);

            while (pos.getY() >= 0)
            {
                if (isSuitableSpawnBlock(world, pos))
                {
                    return pos.up();
                }

                pos = pos.down();
            }

            x += random.nextInt(32) - random.nextInt(32);
            z += random.nextInt(32) - random.nextInt(32);
            iterations++;
        }

        return getSuitableSpawnBlockInColumn(world, new BlockPos(x, 70, z)).up();
    }

    @Nonnull
    private static BlockPos getSuitableSpawnBlockInColumn(World world, BlockPos posIn)
    {
        Chunk chunk = world.getChunkFromBlockCoords(posIn);
        BlockPos pos = new BlockPos(posIn.getX(), chunk.getTopFilledSegment() + 15, posIn.getZ());

        while (pos.getY() >= 0)
        {
            if (isSuitableSpawnBlock(world, pos))
            {
                return pos;
            }

            pos = pos.down();
        }

        return posIn;
    }

    private static boolean isSuitableSpawnBlock(World world, BlockPos pos)
    {
        IBlockState state = world.getBlockState(pos);
        Material materialUp1 = world.getBlockState(pos.up(1)).getMaterial();
        Material materialUp2 = world.getBlockState(pos.up(2)).getMaterial();

        return state.getMaterial().blocksMovement() &&
               state.getBlock().isLeaves(state, world, pos) == false && state.getBlock().isFoliage(world, pos) == false &&
               materialUp1.blocksMovement() == false && materialUp1.isLiquid() == false &&
               materialUp2.blocksMovement() == false && materialUp2.isLiquid() == false;
    }

    public static void createBonusChest(World world)
    {
        WorldInfo info = world.getWorldInfo();
        WorldGeneratorBonusChest gen = new WorldGeneratorBonusChest();

        for (int i = 0; i < 10; ++i)
        {
            int x = info.getSpawnX() + world.rand.nextInt(6) - world.rand.nextInt(6);
            int z = info.getSpawnZ() + world.rand.nextInt(6) - world.rand.nextInt(6);
            BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).up();

            if (gen.generate(world, world.rand, pos))
            {
                break;
            }
        }
    }

    /**
     * This will set the spawnDimension field on the player, if the WorldInfo on the current
     * world is WorldInfoJED, and it says that respawning there should be allowed, but the
     * WorldProvider says it's not. This should support respawning even without a JED WorldProvider.
     * @param player
     */
    public static void setupRespawnDimension(EntityPlayer player)
    {
        World world = player.getEntityWorld();
        JEDWorldProperties props = JEDWorldProperties.getProperties(world);

        if (props != null)
        {
            Boolean canRespawnHere = props.canRespawnHere();

            if (canRespawnHere != null && canRespawnHere.booleanValue() && world.provider.canRespawnHere() == false)
            {
                final int dim = world.provider.getDimension();
                JustEnoughDimensions.logInfo("WorldUtils.setupRespawnDimension: Setting the respawn dimension of player '{}' to: {}", player.getName(), dim);
                player.setSpawnDimension(dim);
                player.addTag(JED_RESPAWN_DIM_TAG);
                return;
            }
        }

        if (player.getTags().contains(JED_RESPAWN_DIM_TAG))
        {
            JustEnoughDimensions.logInfo("WorldUtils.setupRespawnDimension: Removing the respawn dimension data from player '{}'", player.getName());
            player.setSpawnDimension(null);
            player.removeTag(JED_RESPAWN_DIM_TAG);
        }
    }
}
