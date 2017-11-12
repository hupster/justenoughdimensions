package fi.dy.masa.justenoughdimensions.world;

import net.minecraft.nbt.NBTTagCompound;

public interface IWorldProviderJED
{
    /**
     *  Set JED-specific WorldProvider properties on the client side from a synced NBT tag
     * @param tag
     */
    public void setJEDPropertiesFromNBT(NBTTagCompound tag);

    /**
     * Set server-side required properties from WorldInfoJED
     * @param worldInfo
     */
    public void setJEDPropertiesFromWorldProperties(JEDWorldProperties properties);

    /**
     * Returns true if the WorldInfo values have already been set for this WorldProvider
     * @return
     */
    public boolean getWorldInfoHasBeenSet();
}
