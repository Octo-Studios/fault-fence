package it.hurts.shatterbyte.faultfence.common.report;

import net.minecraft.server.packs.resources.ResourceManager;

public interface ReportedResource {
    ResourceOrigin faultfence$origin();

    void faultfence$origin(ResourceOrigin origin);

    ResourceManager faultfence$manager();

    void faultfence$manager(ResourceManager manager);
}
