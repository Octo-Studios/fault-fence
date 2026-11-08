package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import it.hurts.shatterbyte.faultfence.common.recovery.MissingLootItemCodec;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootPoolEntries.class)
public abstract class LootPoolEntriesMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<LootPoolEntryContainer> faultfence$keepValidLoot(Codec<LootPoolEntryContainer> original) {
        return new MissingLootItemCodec(original);
    }
}
