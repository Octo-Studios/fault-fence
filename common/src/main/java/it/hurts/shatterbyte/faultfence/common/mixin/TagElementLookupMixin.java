package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TagLoader.ElementLookup.class)
public interface TagElementLookupMixin {
    @WrapMethod(method = "fromGetters")
    private static <T> TagLoader.ElementLookup<Holder<T>> faultfence$useRegisteredElements(ResourceKey<? extends Registry<T>> key, HolderGetter<T> writable, HolderGetter<T> immutable, Operation<TagLoader.ElementLookup<Holder<T>>> original) {
        if (!RecoveryCategory.TAGS.enabled())
            return original.call(key, writable, immutable);

        return (id, required) -> {
            var elementKey = ResourceKey.create(key, id);

            return immutable.get(elementKey).filter(holder -> !(immutable instanceof Registry<T> registry) || registry.registrationInfo(elementKey).isPresent());
        };
    }
}
