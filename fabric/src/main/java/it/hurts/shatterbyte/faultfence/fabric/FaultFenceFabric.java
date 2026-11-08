package it.hurts.shatterbyte.faultfence.fabric;

import it.hurts.shatterbyte.faultfence.common.FaultFence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class FaultFenceFabric implements ClientModInitializer, DedicatedServerModInitializer {
    @Override
    public void onInitializeClient() {
        initialize();
    }

    @Override
    public void onInitializeServer() {
        initialize();
    }

    private static void initialize() {
        FaultFence.init(FabricLoader.getInstance().getConfigDir());
    }
}
