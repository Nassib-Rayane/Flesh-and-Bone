package com.fleshbone.item;

import java.util.function.Supplier;

import com.fleshbone.FleshBone;
import com.fleshbone.sim.BandageType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FBItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FleshBone.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FleshBone.MODID);

    // Wound care
    public static final DeferredItem<MedicalItem> RAG = medical("rag", p -> p.stacksTo(16), MedicalActions.bandage(BandageType.RAG, 1));
    public static final DeferredItem<MedicalItem> BANDAGE = medical("bandage", p -> p.durability(12), MedicalActions.bandage(BandageType.BANDAGE, 4));
    public static final DeferredItem<MedicalItem> STERILE_BANDAGE = medical("sterile_bandage", p -> p.durability(12), MedicalActions.bandage(BandageType.STERILE, 4));
    public static final DeferredItem<MedicalItem> ANTISEPTIC = medical("antiseptic", p -> p.durability(6), MedicalActions.antiseptic());
    public static final DeferredItem<MedicalItem> SUTURE_KIT = medical("suture_kit", p -> p.durability(4), MedicalActions.suture());
    public static final DeferredItem<MedicalItem> TWEEZERS = medical("tweezers", p -> p.durability(64), MedicalActions.tweezers());
    public static final DeferredItem<MedicalItem> TOURNIQUET = medical("tourniquet", p -> p.stacksTo(4), MedicalActions.tourniquet());
    public static final DeferredItem<MedicalItem> ICE_PACK = medical("ice_pack", p -> p.stacksTo(8), MedicalActions.icePack());
    // Bones
    public static final DeferredItem<MedicalItem> SPLINT = medical("splint", p -> p.stacksTo(8), MedicalActions.splint());
    // Drugs
    public static final DeferredItem<MedicalItem> PAINKILLERS = medical("painkillers", p -> p.durability(8), MedicalActions.painkillers());
    public static final DeferredItem<MedicalItem> MORPHINE = medical("morphine", p -> p.stacksTo(4), MedicalActions.morphine());
    public static final DeferredItem<MedicalItem> ANTIBIOTICS = medical("antibiotics", p -> p.durability(6), MedicalActions.antibiotics());
    // Surgery
    public static final DeferredItem<MedicalItem> BONE_SAW = medical("bone_saw", p -> p.durability(16), MedicalActions.amputation());
    // Heart, lungs and blood
    public static final DeferredItem<MedicalItem> DEFIBRILLATOR = medical("defibrillator", p -> p.durability(20), MedicalActions.defibrillator());
    public static final DeferredItem<MedicalItem> EPINEPHRINE = medical("epinephrine", p -> p.stacksTo(4), MedicalActions.epinephrine());
    public static final DeferredItem<MedicalItem> ANTIARRHYTHMIC = medical("antiarrhythmic", p -> p.stacksTo(4), MedicalActions.antiarrhythmic());
    public static final DeferredItem<MedicalItem> NALOXONE = medical("naloxone", p -> p.stacksTo(4), MedicalActions.naloxone());
    public static final DeferredItem<MedicalItem> PROCOAGULANT = medical("procoagulant", p -> p.stacksTo(4), MedicalActions.procoagulant());
    public static final DeferredItem<MedicalItem> THROMBOLYTIC = medical("thrombolytic", p -> p.stacksTo(4), MedicalActions.thrombolytic());
    public static final DeferredItem<MedicalItem> EMPTY_BLOOD_BAG = medical("empty_blood_bag", p -> p.stacksTo(8), MedicalActions.emptyBloodBag());
    public static final DeferredItem<BloodBagItem> BLOOD_BAG = ITEMS.registerItem("blood_bag",
            p -> new BloodBagItem(p, MedicalActions.bloodBag()), p -> p.stacksTo(1));
    public static final DeferredItem<MedicalItem> SALINE_BAG = medical("saline_bag", p -> p.stacksTo(4), MedicalActions.saline());
    public static final DeferredItem<MedicalItem> CHEST_DRAIN = medical("chest_drain", p -> p.stacksTo(4), MedicalActions.chestDrain());
    /** Carried anywhere in the inventory: shows exact vital signs and an ECG. */
    public static final DeferredItem<Item> VITALS_MONITOR = ITEMS.registerSimpleItem("vitals_monitor", p -> p.stacksTo(1));
    // Needs
    public static final DeferredItem<CanteenItem> CANTEEN = ITEMS.registerItem("canteen", CanteenItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> PURIFIED_WATER = ITEMS.registerSimpleItem("purified_water", p -> p.stacksTo(16)
            .component(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumables.defaultDrink().build())
            .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE));
    public static final DeferredItem<Item> COFFEE = ITEMS.registerSimpleItem("coffee", p -> p.stacksTo(16)
            .component(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumables.defaultDrink().build())
            .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE));
    public static final DeferredItem<Item> EMPTY_SYRINGE = ITEMS.registerSimpleItem("empty_syringe", p -> p.stacksTo(16));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fleshbone"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BANDAGE.get().getDefaultInstance())
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(entry -> output.accept(entry.get())))
            .build());

    private FBItems() {}

    private static DeferredItem<MedicalItem> medical(String name, java.util.function.UnaryOperator<Item.Properties> properties, MedicalAction action) {
        return ITEMS.registerItem(name, p -> new MedicalItem(p, action), properties);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** All registered items, for data generation scripts and tests. */
    public static Iterable<? extends Supplier<? extends Item>> all() {
        return ITEMS.getEntries();
    }
}
