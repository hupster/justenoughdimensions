package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.WorldInfo;

public class WorldInfoJED extends WorldInfo
{
    public WorldInfoJED(NBTTagCompound tag)
    {
        super(tag);
    }
    @Override
    public void setDifficulty(EnumDifficulty newDifficulty)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    @Override
    public void setGameType(GameType type)
    {
        // NO-OP to prevent the MinecraftServer from reseting this
    }

    public void setDifficultyJED(EnumDifficulty newDifficulty)
    {
        super.setDifficulty(newDifficulty);
    }

    public void setGameTypeJED(GameType type)
    {
        super.setGameType(type);
    }
}
