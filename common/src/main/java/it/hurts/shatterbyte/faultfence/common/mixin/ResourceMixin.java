package it.hurts.shatterbyte.faultfence.common.mixin;

import it.hurts.shatterbyte.faultfence.common.report.ReportedResource;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Resource.class)
public abstract class ResourceMixin implements ReportedResource {
    @Unique
    private ResourceOrigin faultfence$origin;

    @Unique
    private ResourceManager faultfence$manager;

    @Override
    public ResourceOrigin faultfence$origin() {
        return faultfence$origin;
    }

    @Override
    public void faultfence$origin(ResourceOrigin origin) {
        faultfence$origin = origin;
    }

    @Override
    public ResourceManager faultfence$manager() {
        return faultfence$manager;
    }

    @Override
    public void faultfence$manager(ResourceManager manager) {
        faultfence$manager = manager;
    }
}
