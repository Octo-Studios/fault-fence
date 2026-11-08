package it.hurts.shatterbyte.faultfence.common.mixin;

import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(MultiPackResourceManager.class)
public abstract class MultiPackResourceManagerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void faultfence$beginReport(PackType type, List<PackResources> packs, CallbackInfo ci) {
        Reports.begin((ResourceManager) this, type);
    }

    @Inject(method = "getResource", at = @At("RETURN"))
    private void faultfence$trackResource(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
        cir.getReturnValue().ifPresent(resource -> Reports.attach((ResourceManager) this, id.toString(), resource));
    }

    @Inject(method = "getResourceStack", at = @At("RETURN"))
    private void faultfence$trackStack(Identifier id, CallbackInfoReturnable<List<Resource>> cir) {
        cir.getReturnValue().forEach(resource -> Reports.attach((ResourceManager) this, id.toString(), resource));
    }

    @Inject(method = "listResources", at = @At("RETURN"))
    private void faultfence$trackResources(String directory, ResourceManager.Selector selector, CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
        cir.getReturnValue().forEach((id, resource) -> Reports.attach((ResourceManager) this, id.toString(), resource));
    }

    @Inject(method = "listResourceStacks", at = @At("RETURN"))
    private void faultfence$trackStacks(String directory, ResourceManager.Selector selector, CallbackInfoReturnable<Map<Identifier, List<Resource>>> cir) {
        cir.getReturnValue().forEach((id, resources) -> resources.forEach(resource -> Reports.attach((ResourceManager) this, id.toString(), resource)));
    }
}
