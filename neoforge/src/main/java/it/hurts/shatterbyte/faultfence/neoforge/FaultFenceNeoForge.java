package it.hurts.shatterbyte.faultfence.neoforge;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

@Mod(FaultFence.MODID)
public class FaultFenceNeoForge {
    public FaultFenceNeoForge(IEventBus modBus) {
        modBus.addListener(FaultFenceNeoForge::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> FaultFence.init(FMLPaths.CONFIGDIR.get()));
    }
}
