package com.fleshbone.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fleshbone.client.ClientBody;
import com.fleshbone.client.ClientPatients;
import com.fleshbone.client.FBKeys;
import com.fleshbone.client.hud.BodyDoll;
import com.fleshbone.client.hud.MoodleLayer;
import com.fleshbone.client.minigame.BandageMinigame;
import com.fleshbone.client.minigame.CprMinigame;
import com.fleshbone.client.minigame.DefibMinigame;
import com.fleshbone.client.minigame.ExtractMinigame;
import com.fleshbone.client.minigame.MinigameScreen;
import com.fleshbone.client.minigame.ResetMinigame;
import com.fleshbone.client.minigame.SawMinigame;
import com.fleshbone.client.minigame.SutureMinigame;
import com.fleshbone.config.FBConfig;
import com.fleshbone.item.MedicalAction;
import com.fleshbone.item.MedicalItem;
import com.fleshbone.network.FBPayloads;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Moodle;
import com.fleshbone.sim.Moodles;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.Vitals;
import com.fleshbone.sim.system.ConsciousnessSystem;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Health panel (key H, or right-click another player with a medical item). Tab "Body": clickable silhouette, details
 * of the selected part and the treatments available in your inventory. Tab "Report": vital signs, needs and every
 * moodle. Time keeps running while it is open, as in Casualties: Unknown.
 */
public class HealthPanelScreen extends Screen {
    private static final int TEXT = 0xFFE0D8CC;
    private static final int DIM = 0xFF8C8478;
    private static final int GOOD = 0xFF7FC060;
    private static final int WARN = 0xFFE0B040;
    private static final int BAD = 0xFFE05040;
    private static final int HEADER = 0xFFFFD27A;
    private static final int HOVER = 0x30FFFFFF;
    /** Applying from the panel is careful work: better than a rushed quick use. */
    private static final float PANEL_QUALITY = 0.85f;
    private static final float HANDS_QUALITY = 0.5f;

    private enum Tab { BODY, REPORT }

    /** A clickable row in the treatment list. */
    private record Option(Component label, ItemStack icon, int color, Runnable action, int ticks) {}

    /** Treatment being performed: sent to the server when the time is up. */
    private record Pending(Component label, CustomPacketPayload payload, long startTick, int ticks) {}

    private final int patientId;
    private boolean patientLost;
    /** A minigame is being opened on top: keep the examination going. */
    private boolean openingChild;
    /** Item of an irreversible action waiting for a second click. */
    private net.minecraft.world.item.Item confirming;
    private Tab tab = Tab.BODY;
    private BodyPart selected;
    private Pending pending;
    private final List<Option> options = new ArrayList<>();
    private final List<int[]> optionBounds = new ArrayList<>();
    private int x0;
    private int y0;
    private int panelW;
    private int panelH;
    private int dollX;
    private int dollY;
    private float dollScale;

    /** Your own body. */
    public HealthPanelScreen() {
        this(-1);
    }

    /** Another player's body ({@code patientId} = their entity id), or yours with -1. */
    public HealthPanelScreen(int patientId) {
        super(Component.translatable("fleshbone.panel.title"));
        this.patientId = patientId;
    }

    public boolean isExamining() {
        return patientId >= 0;
    }

    public void patientLost() {
        patientLost = true;
        onClose();
    }

    @Override
    protected void init() {
        openingChild = false;
        panelW = Math.min(470, width - 10);
        panelH = Math.min(272, height - 10);
        x0 = (width - panelW) / 2;
        y0 = (height - panelH) / 2;
        dollScale = Math.max(2f, Math.min(4f, (panelH - 64) / (float) BodyDoll.GRID_H));
        dollX = x0 + 10;
        dollY = y0 + 22;
    }

    @Override
    public boolean isPauseScreen() {
        return FBConfig.PANEL_PAUSES_GAME.get() && !isExamining();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    private Body body() {
        return isExamining() ? ClientPatients.body(patientId) : ClientBody.get();
    }

    private int targetId() {
        return isExamining() ? patientId : minecraft.player.getId();
    }

    @Override
    public void tick() {
        if (pending != null && minecraft != null && minecraft.level != null
                && minecraft.level.getGameTime() - pending.startTick() >= pending.ticks()) {
            ClientPacketDistributor.sendToServer(pending.payload());
            pending = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Body body = body();
        if (body == null || minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        g.fill(x0, y0, x0 + panelW, y0 + panelH, 0xEC121010);
        g.outline(x0, y0, panelW, panelH, 0xFF5E4A36);
        Component heading = title;
        if (isExamining()) {
            Entity patient = minecraft.level.getEntity(patientId);
            heading = Component.translatable("fleshbone.panel.patient", patient != null ? patient.getDisplayName() : Component.literal("?"));
        }
        g.text(font, heading, x0 + 8, y0 + 7, HEADER, true);
        drawTabs(g, mouseX, mouseY);

        if (tab == Tab.BODY) {
            drawBodyTab(g, body, mouseX, mouseY);
        } else {
            drawReportTab(g, body);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    // ------------------------------------------------------------------------------------------------ tabs

    private int tabX(Tab t) {
        return x0 + panelW - 8 - (Tab.values().length - t.ordinal()) * 70;
    }

    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Tab t : Tab.values()) {
            int x = tabX(t);
            boolean active = t == tab;
            boolean hovered = mouseX >= x && mouseX < x + 66 && mouseY >= y0 + 4 && mouseY < y0 + 17;
            g.fill(x, y0 + 4, x + 66, y0 + 17, active ? 0xFF3A2E24 : hovered ? 0xFF2A221C : 0xFF1C1814);
            g.centeredText(font, Component.translatable("fleshbone.panel.tab." + t.name().toLowerCase(Locale.ROOT)), x + 33, y0 + 7, active ? HEADER : DIM);
        }
    }

    // ------------------------------------------------------------------------------------------------ body tab

    private void drawBodyTab(GuiGraphicsExtractor g, Body body, int mouseX, int mouseY) {
        BodyPart hovered = BodyDoll.partAt(mouseX, mouseY, dollX, dollY, dollScale);
        BodyDoll.draw(g, body, dollX, dollY, dollScale, selected != null ? selected : hovered, ClientBody.time());
        if (hovered != null) g.setTooltipForNextFrame(font, partName(hovered), mouseX, mouseY);
        int dollBottom = dollY + Math.round(BodyDoll.GRID_H * dollScale);
        g.text(font, Component.translatable("fleshbone.panel.blood_type", body.vitals.bloodType.display()), x0 + 8, dollBottom + 6, DIM, false);

        int detailsX = dollX + Math.round(BodyDoll.GRID_W * dollScale) + 12;
        int treatX = x0 + panelW - 196;
        drawPartDetails(g, body, detailsX, y0 + 24, treatX - detailsX - 8);
        drawTreatments(g, body, treatX, y0 + 24, 188, mouseX, mouseY);

        List<Moodle> moodles = Moodles.compute(body, isExamining() ? 20 : minecraft.player.getFoodData().getFoodLevel());
        int iconsX = detailsX;
        int iconsY = y0 + panelH - 22;
        float time = ClientBody.time();
        int maxIcons = Math.max(1, (treatX - detailsX - 8) / 18);
        for (int i = 0; i < moodles.size() && i < maxIcons; i++) {
            int ix = iconsX + i * 18;
            MoodleLayer.draw(g, font, moodles.get(i), ix, iconsY, time);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iconsY && mouseY < iconsY + 16) {
                g.setTooltipForNextFrame(font, Component.translatable(moodles.get(i).translationKey()), mouseX, mouseY);
            }
        }
    }

    private void drawPartDetails(GuiGraphicsExtractor g, Body body, int x, int y, int width) {
        if (selected == null) {
            g.textWithWordWrap(font, Component.translatable("fleshbone.panel.select_part"), x, y, width, DIM);
            return;
        }
        PartState s = body.part(selected);
        g.text(font, partName(selected), x, y, HEADER, false);
        y += 12;
        if (s.amputated) {
            g.text(font, Component.translatable("fleshbone.panel.amputated"), x, y, BAD, false);
            return;
        }
        y = bar(g, x, y, width, Component.translatable("fleshbone.panel.skin"), s.skin);
        y = bar(g, x, y, width, Component.translatable("fleshbone.panel.muscle"), s.muscle);

        List<Line> lines = new ArrayList<>();
        int i = selected.ordinal();
        float bleed = body.derived.partBleed[i];
        if (bleed > 0.0005f) lines.add(new Line(Component.translatable("fleshbone.panel.bleeding", format(bleed, 3)), bleed > 0.225f ? BAD : WARN));
        float internal = body.derived.partInternal[i];
        if (internal > 0.0005f) lines.add(new Line(Component.translatable("fleshbone.panel.internal_bleeding", format(internal, 3)), BAD));
        float pain = body.derived.partPain[i];
        if (pain >= 1f) lines.add(new Line(Component.translatable("fleshbone.panel.pain", Math.round(pain)), pain > 55f ? BAD : pain > 30f ? WARN : TEXT));
        if (s.isFractured()) {
            float speed = s.splinted ? 2.5f : 1f;
            float seconds = s.fracture * (float) (FBConfig.FRACTURE_HEAL_MINUTES.get() * 60.0) / speed;
            lines.add(new Line(Component.translatable("fleshbone.panel.fracture", Math.round((1f - s.fracture) * 100f), duration(seconds)), BAD));
        }
        if (s.isDislocated()) lines.add(new Line(Component.translatable("fleshbone.panel.dislocation"), BAD));
        if (s.shrapnel > 0) lines.add(new Line(Component.translatable("fleshbone.panel.shrapnel", s.shrapnel), WARN));
        if (s.infection > 25f) {
            lines.add(new Line(Component.translatable("fleshbone.panel.infection", Math.round(s.infection)), s.infection > 60f ? BAD : WARN));
        } else if (s.hasOpenWound() && s.contamination > 20f) {
            lines.add(new Line(Component.translatable("fleshbone.panel.dirty_wound"), WARN));
        }
        if (s.isBandaged()) {
            lines.add(new Line(Component.translatable("fleshbone.panel.bandage",
                    Component.translatable("fleshbone.bandage." + s.bandage.name().toLowerCase(Locale.ROOT)),
                    Math.round(s.bandageQuality * 100f), Math.round(s.bandageSoak * 100f)), s.bandageSoak > 0.6f ? WARN : GOOD));
        }
        if (s.sutured) lines.add(new Line(Component.translatable("fleshbone.panel.sutured"), GOOD));
        if (s.splinted) lines.add(new Line(Component.translatable("fleshbone.panel.splint"), GOOD));
        if (s.tourniquet) lines.add(new Line(Component.translatable("fleshbone.panel.tourniquet", duration(s.tourniquetTime)), s.tourniquetTime > 120f ? BAD : WARN));
        if (s.chilled > 0f) lines.add(new Line(Component.translatable("fleshbone.panel.chilled", duration(s.chilled)), GOOD));
        if (s.disinfected > 0f) lines.add(new Line(Component.translatable("fleshbone.panel.disinfected", duration(s.disinfected)), GOOD));
        if (lines.isEmpty()) lines.add(new Line(Component.translatable("fleshbone.panel.healthy"), GOOD));
        for (Line line : lines) {
            g.text(font, line.text(), x, y, line.color(), false);
            y += 10;
        }
    }

    private int bar(GuiGraphicsExtractor g, int x, int y, int width, Component label, float value) {
        g.text(font, label, x, y, TEXT, false);
        int barX = x + 42;
        int barW = Math.max(20, width - 42 - 26);
        g.fill(barX, y + 1, barX + barW, y + 7, 0xFF2A2420);
        g.fill(barX, y + 1, barX + Math.round(barW * Math.max(0f, Math.min(100f, value)) / 100f), y + 7, BodyDoll.tissueColor(value));
        g.text(font, Math.round(value) + "%", barX + barW + 4, y, DIM, false);
        return y + 11;
    }

    // ------------------------------------------------------------------------------------------------ treatments

    private void drawTreatments(GuiGraphicsExtractor g, Body body, int x, int y, int width, int mouseX, int mouseY) {
        Component target = selected != null ? partName(selected) : Component.translatable("fleshbone.panel.whole_body");
        g.text(font, Component.translatable("fleshbone.panel.treatments", target), x, y, HEADER, false);
        y += 12;
        if (pending != null) {
            float progress = Math.min(1f, (minecraft.level.getGameTime() - pending.startTick()) / (float) pending.ticks());
            g.text(font, pending.label(), x, y, TEXT, false);
            g.fill(x, y + 10, x + width, y + 14, 0xFF2A2420);
            g.fill(x, y + 10, x + Math.round(width * progress), y + 14, 0xFF7FC060);
            y += 20;
        }
        buildOptions(body);
        optionBounds.clear();
        if (options.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable(selected != null ? "fleshbone.panel.no_treatment" : "fleshbone.panel.no_drugs"), x, y, width, DIM);
            return;
        }
        for (Option option : options) {
            if (option == null) {
                // Separator before whole-body drugs
                y += 3;
                g.fill(x, y, x + width, y + 1, 0xFF3A2E24);
                y += 4;
                continue;
            }
            boolean hovered = pending == null && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 17;
            if (hovered) g.fill(x - 2, y, x + width, y + 17, HOVER);
            if (!option.icon().isEmpty()) g.item(option.icon(), x, y);
            int textX = option.icon().isEmpty() ? x + 2 : x + 19;
            g.text(font, option.label(), textX, y + 4, option.color(), false);
            optionBounds.add(new int[] {x, y, x + width, y + 17, options.indexOf(option)});
            y += 18;
            if (y > y0 + panelH - 26) break;
        }
    }

    /** Medical items in the medic's inventory that can be used now, procedures and removable devices. */
    private void buildOptions(Body body) {
        options.clear();
        Inventory inventory = minecraft.player.getInventory();
        int target = targetId();
        if (selected != null) {
            PartState s = body.part(selected);
            int part = selected.ordinal();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!(stack.getItem() instanceof MedicalItem item) || item.action().target() != MedicalAction.Target.PART) continue;
                if (!item.action().canApply(body, selected) || hasOption(stack)) continue;
                int ticks = item.action().useTicks();
                Component label = stack.getHoverName();
                MedicalAction.Minigame minigame = item.action().minigame();
                if (item.action().dangerous() && confirming != stack.getItem()) {
                    options.add(new Option(label, stack, BAD, () -> confirming = stack.getItem(), ticks));
                } else if (item.action().dangerous()) {
                    Component confirm = Component.translatable("fleshbone.panel.confirm", label);
                    options.add(new Option(confirm, stack, BAD, () -> {
                        confirming = null;
                        if (FBConfig.MINIGAMES.get()) openMinigame(minigame, stack, s, target, part);
                        else start(label, new FBPayloads.Treat(target, part, slotOf(stack), HANDS_QUALITY, 0), ticks);
                    }, ticks));
                } else if (minigame != MedicalAction.Minigame.NONE && FBConfig.MINIGAMES.get()) {
                    options.add(new Option(label, stack, TEXT, () -> openMinigame(minigame, stack, s, target, part), ticks));
                } else {
                    options.add(new Option(label, stack, TEXT,
                            () -> start(label, new FBPayloads.Treat(target, part, slotOf(stack), PANEL_QUALITY, 0), ticks), ticks));
                }
            }
            if (Treatments.canReset(s)) {
                Component label = Component.translatable("fleshbone.panel.action.reset");
                int procedure = FBPayloads.Procedure.RESET_DISLOCATION.ordinal();
                options.add(new Option(label, ItemStack.EMPTY, WARN, () -> {
                    if (FBConfig.MINIGAMES.get()) {
                        openChild(new ResetMinigame(this, (quality, amount) -> send(new FBPayloads.DoProcedure(target, part, procedure, quality)),
                                body.derived.totalPain));
                    } else {
                        start(label, new FBPayloads.DoProcedure(target, part, procedure, HANDS_QUALITY), 40);
                    }
                }, 40));
            }
            if (Treatments.canExtract(s)) {
                Component label = Component.translatable("fleshbone.panel.action.pull");
                int procedure = FBPayloads.Procedure.PULL_SHRAPNEL.ordinal();
                options.add(new Option(label, ItemStack.EMPTY, WARN, () -> {
                    if (FBConfig.MINIGAMES.get()) {
                        openChild(new ExtractMinigame(this, (quality, amount) -> send(new FBPayloads.DoProcedure(target, part, procedure, quality)), false));
                    } else {
                        start(label, new FBPayloads.DoProcedure(target, part, procedure, HANDS_QUALITY), 40);
                    }
                }, 40));
            }
            if (s.isBandaged()) options.add(removal("bandage", FBPayloads.Device.BANDAGE, target, part));
            if (isExamining() && selected == BodyPart.THORAX && (body.vitals.cardiacArrest || body.vitals.respRate < 2.5f)) {
                options.add(new Option(Component.translatable("fleshbone.panel.action.cpr"), ItemStack.EMPTY, BAD,
                        () -> openChild(new CprMinigame(this, patientId)), 0));
            }
            if (s.splinted) options.add(removal("splint", FBPayloads.Device.SPLINT, target, part));
            if (s.tourniquet) options.add(removal("tourniquet", FBPayloads.Device.TOURNIQUET, target, part));
        }
        boolean separated = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof MedicalItem item) || item.action().target() != MedicalAction.Target.BODY || hasOption(stack)) continue;
            if (!separated && !options.isEmpty()) {
                options.add(null);
                separated = true;
            }
            if (!item.action().canApply(body, null)) continue;
            int ticks = item.action().useTicks();
            Component label = stack.getHoverName();
            if (item.action().minigame() == MedicalAction.Minigame.DEFIB && FBConfig.MINIGAMES.get()) {
                int itemSlot = slotOf(stack);
                options.add(new Option(label, stack, TEXT, () -> openChild(new DefibMinigame(this,
                        (quality, amount) -> send(new FBPayloads.Treat(target, -1, itemSlot, quality, 0)))), ticks));
            } else {
                options.add(new Option(label, stack, TEXT, () -> start(label, new FBPayloads.Treat(target, -1, slotOf(stack), PANEL_QUALITY, 0), ticks), ticks));
            }
        }
    }

    private Option removal(String device, FBPayloads.Device type, int target, int part) {
        Component label = Component.translatable("fleshbone.panel.action.remove_" + device);
        return new Option(label, ItemStack.EMPTY, DIM, () -> start(label, new FBPayloads.RemoveDevice(target, part, type.ordinal()), 20), 20);
    }

    /** One row per item type, using the first matching stack. */
    private boolean hasOption(ItemStack stack) {
        for (Option option : options) {
            if (option != null && !option.icon().isEmpty() && option.icon().getItem() == stack.getItem()) return true;
        }
        return false;
    }

    private int slotOf(ItemStack stack) {
        Inventory inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == stack) return slot;
        }
        return -1;
    }

    /** Opens the hands-on minigame for an item; the result is applied with the quality achieved. */
    private void openMinigame(MedicalAction.Minigame minigame, ItemStack stack, PartState s, int target, int part) {
        int slot = slotOf(stack);
        MinigameScreen.Completion done = (quality, amount) -> send(new FBPayloads.Treat(target, part, slot, quality, amount));
        switch (minigame) {
            case BANDAGE -> {
                int length = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 8;
                openChild(new BandageMinigame(this, done, s.skin, length));
            }
            case SUTURE -> openChild(new SutureMinigame(this, done));
            case EXTRACT -> openChild(new ExtractMinigame(this, done, true));
            case SAW -> openChild(new SawMinigame(this, done, s.skin));
            case NONE -> { }
        }
    }

    private void openChild(Screen child) {
        openingChild = true;
        minecraft.setScreen(child);
    }

    private static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    private void start(Component label, CustomPacketPayload payload, int ticks) {
        if (pending != null || minecraft.level == null) return;
        pending = new Pending(label, payload, minecraft.level.getGameTime(), ticks);
    }

    // ------------------------------------------------------------------------------------------------ report tab

    private void drawReportTab(GuiGraphicsExtractor g, Body body) {
        int columnX = x0 + 10;
        int y = y0 + 24;
        Vitals v = body.vitals;
        boolean exact = ClientBody.vitalsVisible(minecraft.player);
        g.text(font, Component.translatable("fleshbone.panel.vitals"), columnX, y, HEADER, false);
        y += 11;
        y = row(g, columnX, y, "blood", exact ? format(v.bloodVolume, 2) + " L" : word("blood", bloodWord(v.bloodVolume)), bloodColor(v.bloodVolume));
        if (exact) {
            y = row(g, columnX, y, "pressure", Math.round(v.systolic) + "/" + Math.round(v.diastolic), v.systolic < 90f || v.systolic > 160f ? BAD : TEXT);
            y = row(g, columnX, y, "pulse", v.cardiacArrest ? "0" : Math.round(v.heartRate) + " bpm", v.cardiacArrest || v.heartRate > 140f ? BAD : TEXT);
            y = row(g, columnX, y, "breathing", format(v.respRate, 0) + "/min", v.respRate < 8f ? BAD : TEXT);
            y = row(g, columnX, y, "spo2", Math.round(v.spo2) + "%", v.spo2 < 85f ? BAD : TEXT);
            y = row(g, columnX, y, "temperature", format(v.bodyTemp, 1) + "°C", v.bodyTemp < 35f || v.bodyTemp > 38.5f ? WARN : TEXT);
        } else {
            y = row(g, columnX, y, "pulse", word("pulse", pulseWord(v)), v.cardiacArrest || v.heartRate > 140f ? BAD : TEXT);
            y = row(g, columnX, y, "breathing", word("breathing", breathingWord(v)), v.respRate < 8f ? BAD : TEXT);
            y = row(g, columnX, y, "temperature", word("temperature", temperatureWord(v.bodyTemp)), v.bodyTemp < 35f || v.bodyTemp > 38.5f ? WARN : TEXT);
        }
        y = row(g, columnX, y, "consciousness", word("consciousness", consciousnessWord(v.consciousness)), v.consciousness < 55f ? BAD : v.consciousness < 90f ? WARN : TEXT);
        y = row(g, columnX, y, "pain", Math.round(body.derived.totalPain) + "", body.derived.totalPain > 55f ? BAD : body.derived.totalPain > 30f ? WARN : TEXT);

        y += 6;
        g.text(font, Component.translatable("fleshbone.panel.needs"), columnX, y, HEADER, false);
        y += 11;
        if (!isExamining()) {
            int food = minecraft.player.getFoodData().getFoodLevel();
            y = row(g, columnX, y, "food", food * 5 + "%", food < 7 ? BAD : food < 14 ? WARN : TEXT);
        }
        y = row(g, columnX, y, "thirst", Math.round(Math.min(100f, body.needs.thirst)) + "%", body.needs.thirst < 35f ? BAD : body.needs.thirst < 55f ? WARN : TEXT);
        y = row(g, columnX, y, "energy", Math.round(body.needs.energy) + "%", body.needs.energy < 25f ? BAD : TEXT);
        y = row(g, columnX, y, "stamina", Math.round(body.needs.stamina) + "%", body.needs.stamina < 35f ? WARN : TEXT);
        row(g, columnX, y, "mood", word("mood", moodWord(body.mind.mood)), body.mind.mood < -30f ? BAD : TEXT);

        int listX = x0 + 160;
        int listY = y0 + 24;
        g.text(font, Component.translatable("fleshbone.panel.moodles"), listX, listY, HEADER, false);
        listY += 12;
        List<Moodle> moodles = Moodles.compute(body, isExamining() ? 20 : minecraft.player.getFoodData().getFoodLevel());
        if (moodles.isEmpty()) {
            g.text(font, Component.translatable("fleshbone.panel.no_moodles"), listX, listY, DIM, false);
            return;
        }
        int columnWidth = 150;
        int columns = Math.max(1, (x0 + panelW - 8 - listX) / columnWidth);
        int rows = Math.max(1, (y0 + panelH - 8 - listY) / 19);
        float time = ClientBody.time();
        for (int i = 0; i < moodles.size() && i < columns * rows; i++) {
            int cx = listX + (i / rows) * columnWidth;
            int cy = listY + (i % rows) * 19;
            Moodle moodle = moodles.get(i);
            MoodleLayer.draw(g, font, moodle, cx, cy, time);
            g.text(font, Component.translatable(moodle.translationKey()), cx + 19, cy + 4, moodle.type().positive ? GOOD : TEXT, false);
        }
    }

    private int row(GuiGraphicsExtractor g, int x, int y, String key, Object value, int color) {
        g.text(font, Component.translatable("fleshbone.panel.vital." + key), x, y, DIM, false);
        Component text = value instanceof Component c ? c : Component.literal(String.valueOf(value));
        g.text(font, text, x + 138 - font.width(text), y, color, false);
        return y + 10;
    }

    private static Component word(String group, String word) {
        return Component.translatable("fleshbone.panel.word." + group + "." + word);
    }

    private static String bloodWord(float litres) {
        if (litres >= 4.6f) return "none";
        if (litres >= 4.0f) return "light";
        if (litres >= 3.3f) return "heavy";
        return "critical";
    }

    private static int bloodColor(float litres) {
        return litres >= 4.6f ? TEXT : litres >= 4.0f ? WARN : BAD;
    }

    private static String pulseWord(Vitals v) {
        if (v.cardiacArrest || v.heartRate <= 0f) return "absent";
        if (v.fibrillation > 50f) return "chaotic";
        if (v.heartRate > 120f) return v.systolic < 90f ? "weak_fast" : "fast";
        if (v.heartRate < 50f) return "slow";
        return "normal";
    }

    private static String breathingWord(Vitals v) {
        if (v.respRate < 2.5f) return "absent";
        if (v.respRate < 10f) return "shallow";
        if (v.respRate > 24f) return "fast";
        return "normal";
    }

    private static String temperatureWord(float temp) {
        if (temp < 32.5f) return "hypothermia";
        if (temp < 35.5f) return "cold";
        if (temp > 40f) return "high_fever";
        if (temp > 38f) return "fever";
        return "normal";
    }

    private static String consciousnessWord(float c) {
        if (c < ConsciousnessSystem.UNCONSCIOUS) return "unconscious";
        if (c < ConsciousnessSystem.INCAPACITATED) return "incapacitated";
        if (c < ConsciousnessSystem.FAINTING) return "fainting";
        if (c < ConsciousnessSystem.VERY_CONFUSED) return "very_confused";
        if (c < ConsciousnessSystem.CONFUSED) return "confused";
        return "alert";
    }

    private static String moodWord(float mood) {
        if (mood < -50f) return "depressed";
        if (mood < -10f) return "low";
        if (mood > 30f) return "happy";
        return "neutral";
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (Tab t : Tab.values()) {
            int x = tabX(t);
            if (mx >= x && mx < x + 66 && my >= y0 + 4 && my < y0 + 17) {
                tab = t;
                return true;
            }
        }
        if (tab == Tab.BODY) {
            BodyPart part = BodyDoll.partAt(mx, my, dollX, dollY, dollScale);
            if (part != null) {
                selected = part == selected ? null : part;
                confirming = null;
                return true;
            }
            if (pending == null) {
                for (int[] bounds : optionBounds) {
                    if (mx >= bounds[0] && mx < bounds[2] && my >= bounds[1] && my < bounds[3]) {
                        Option option = options.get(bounds[4]);
                        if (option != null) option.action().run();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (FBKeys.HEALTH_PANEL.get().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (isExamining() && !patientLost && !openingChild) ClientPacketDistributor.sendToServer(new FBPayloads.StopExamining());
        super.removed();
    }

    // ------------------------------------------------------------------------------------------------ formatting

    private static Component partName(BodyPart part) {
        return Component.translatable("fleshbone.part." + part.serializedName());
    }

    private static String format(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String duration(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private record Line(Component text, int color) {}
}
