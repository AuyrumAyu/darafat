package com.spellcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class SpellcraftMod implements ModInitializer {
    public static final String MOD_ID = "spellcraft";

    public static final Item WAND = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "wand"),
            new WandItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(WAND));
    }
}
