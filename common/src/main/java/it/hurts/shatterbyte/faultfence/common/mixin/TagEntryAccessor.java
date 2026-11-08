package it.hurts.shatterbyte.faultfence.common.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {
    @Accessor("id")
    Identifier faultfence$id();

    @Accessor("tag")
    boolean faultfence$tag();
}
