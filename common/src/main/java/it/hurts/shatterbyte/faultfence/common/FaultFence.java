package it.hurts.shatterbyte.faultfence.common;

import it.hurts.shatterbyte.faultfence.common.config.FaultFenceConfig;
import it.hurts.shatterbyte.shatterlib.module.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class FaultFence {
    public static final String MODID = "faultfence";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final FaultFenceConfig CONFIG = new FaultFenceConfig();

    public static Path reportDirectory;

    public static void init(Path directory) {
        ConfigManager.register(MODID, CONFIG);
        CONFIG.load(directory);

        reportDirectory = directory.resolveSibling("logs").resolve(MODID);

        LOGGER.info("Fault Fence is preserving independent gameplay data");
    }
}
