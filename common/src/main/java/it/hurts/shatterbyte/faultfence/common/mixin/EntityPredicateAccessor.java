package it.hurts.shatterbyte.faultfence.common.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.EntitySubPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(EntityPredicate.class)
public interface EntityPredicateAccessor {
    @Accessor("parts")
    Map<Codec<? extends EntitySubPredicate>, EntitySubPredicate> faultfence$parts();
}
