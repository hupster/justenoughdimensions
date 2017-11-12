package fi.dy.masa.justenoughdimensions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.client.FMLClientHandler;
import fi.dy.masa.justenoughdimensions.JustEnoughDimensions;
import fi.dy.masa.justenoughdimensions.config.DimensionConfigEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DimensionSyncPacket
{
    private ByteBuf buffer = Unpooled.buffer();
    private List<DimensionConfigEntry> dimensions = new ArrayList<DimensionConfigEntry>();

    public void addDimensionData(List<DimensionConfigEntry> entries)
    {
        this.buffer.writeInt(entries.size());

        for (DimensionConfigEntry entry : entries)
        {
            entry.writeToByteBuf(this.buffer);
        }
    }

    public void consumePacket(ByteBuf buf)
    {
        int count = buf.readInt();
        this.dimensions.clear();

        for (int i = 0 ; i < count ; i++)
        {
            DimensionConfigEntry entry = DimensionConfigEntry.fromByteBuf(buf);

            if (entry != null)
            {
                this.dimensions.add(entry);
            }
        }
    }

    public ByteBuf getData()
    {
        return this.buffer;
    }

    public void execute()
    {
        Minecraft mc = FMLClientHandler.instance().getClient();

        if (mc == null)
        {
            JustEnoughDimensions.logger.error("Minecraft was null in DimensionSyncPacket");
            return;
        }

        mc.addScheduledTask(new Runnable()
        {
            public void run()
            {
                processMessage();
            }
        });
    }

    protected void processMessage()
    {
        String str = registerDimensions(this.dimensions);
        JustEnoughDimensions.logInfo("DimensionSyncPacket: Registered dimensions: '" + str + "'");
    }

    /**
     * Registers all the dimensions on the provided list.
     * @param dimensions
     * @return a string for logging purposes of all the registered dimensions
     */
    public static String registerDimensions(List<DimensionConfigEntry> dimensions)
    {
        List<String> ids = new ArrayList<String>();

        for (DimensionConfigEntry entry : dimensions)
        {
            registerDimension(entry.getId(), entry);

            if (entry.getUnregister() == false && entry.hasDimensionTypeEntry())
            {
                ids.add(String.valueOf(entry.getId()));
            }
        }

        return String.join(", ", ids);
    }

    private static void registerDimension(int id, DimensionConfigEntry entry)
    {
        if (entry.getUnregister() || entry.hasDimensionTypeEntry() == false)
        {
            return;
        }

        if (DimensionManager.isDimensionRegistered(id))
        {
            DimensionType type = DimensionManager.getProviderType(id);

            if (type.createDimension().getClass() != entry.getDimensionTypeEntry().getProviderClass())
            {
                JustEnoughDimensions.logInfo("DimensionSyncPacket.registerDimension: Dimension {} already registered, unregistering the old one", id);
                DimensionManager.unregisterDimension(id);
            }
        }

        if (DimensionManager.isDimensionRegistered(id) == false)
        {
            JustEnoughDimensions.logInfo("DimensionSyncPacket.registerDimension: Registering dimension {}", id);
            DimensionManager.registerDimension(id, entry.getDimensionTypeEntry().registerDimensionType());
        }
    }
}
