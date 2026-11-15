package com.spellcraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class WandItem extends Item {
    // Per-player, per-spell cooldowns (kept outside the ItemStack so the hand doesn't "re-equip" on every cast)
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    public WandItem(Settings settings) {
        super(settings);
    }

    public static Spell getSpell(ItemStack stack) {
        return Spell.byIndex(stack.hasNbt() ? stack.getNbt().getInt("Spell") : 0);
    }

    public static void setSpell(ItemStack stack, Spell spell) {
        stack.getOrCreateNbt().putInt("Spell", spell.ordinal());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
            return TypedActionResult.success(stack);
        }

        Spell current = getSpell(stack);

        // Sneak + right click = next spell
        if (sp.isSneaking()) {
            Spell next = current.next();
            setSpell(stack, next);
            sp.sendMessage(Text.translatable("message.spellcraft.selected",
                    Text.translatable(next.translationKey())).formatted(Formatting.AQUA), true);
            sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.4f);
            return TypedActionResult.success(stack);
        }

        long now = sw.getTime();
        long[] cds = COOLDOWNS.computeIfAbsent(sp.getUuid(), k -> new long[Spell.values().length]);
        long left = cds[current.ordinal()] - now;
        if (left > 0) {
            sp.sendMessage(Text.translatable("message.spellcraft.cooldown",
                    String.format("%.1f", left / 20.0)).formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        cds[current.ordinal()] = now + current.cooldown;
        current.cast(sw, sp);
        sp.sendMessage(Text.translatable(current.translationKey()).formatted(Formatting.GOLD), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.spellcraft.spell",
                Text.translatable(getSpell(stack).translationKey())).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.spellcraft.hint").formatted(Formatting.GRAY));
    }
}
