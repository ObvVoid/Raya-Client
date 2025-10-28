package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class KillAura extends Module {
    // Settings
    public Setting<Mode> mode = new Setting<>("Mode", Mode.VANILLA);
    public Setting<BlockMode> blockMode = new Setting<>("BlockMode", BlockMode.LEGIT);
    public Setting<Double> range = new Setting<>("Range", 4.2, 1.0, 6.0);
    public Setting<Boolean> players = new Setting<>("Players", true);
    public Setting<Boolean> mobs = new Setting<>("Mobs", false);
    public Setting<Boolean> animals = new Setting<>("Animals", false);
    public Setting<Boolean> invisibles = new Setting<>("Invisibles", false);
    public Setting<Integer> apsMin = new Setting<>("APS-Min", 8, 1, 20);
    public Setting<Integer> apsMax = new Setting<>("APS-Max", 12, 1, 20);
    public Setting<Boolean> noSwing = new Setting<>("NoSwing", false);
    public Setting<Boolean> fovCheck = new Setting<>("FOVCheck", true);
    public Setting<Boolean> rayTrace = new Setting<>("RayTrace", false);

    // Runtime
    private EntityLivingBase target;
    private boolean isBlocking;
    private final me.alpha432.oyvey.util.Timer attackTimer = new me.alpha432.oyvey.util.Timer();
    private final me.alpha432.oyvey.util.Timer blockTimer = new me.alpha432.oyvey.util.Timer();

    public KillAura() {
        super("KillAura", "Automatically attacks entities around you", Category.COMBAT, true, false, false);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        updateTarget();
        handleAutoblock();
        handleAttacks();
    }

    private void updateTarget() {
        List<Entity> entities = mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityLivingBase)
                .filter(entity -> entity != mc.thePlayer)
                .filter(this::isValidTarget)
                .filter(this::inRange)
                .filter(this::inFOV)
                .sorted(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSqToEntity(e)))
                .collect(Collectors.toList());

        target = entities.isEmpty() ? null : (EntityLivingBase) entities.get(0);
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        if (entity.isInvisible() && !invisibles.getValue()) return false;

        if (entity instanceof EntityPlayer && players.getValue()) return true;
        if (entity instanceof EntityMob && mobs.getValue()) return true;
        return entity instanceof EntityAnimal && animals.getValue();
    }

    private boolean inRange(Entity entity) {
        return mc.thePlayer.getDistanceSqToEntity(entity) <= (range.getValue() * range.getValue());
    }

    private boolean inFOV(Entity entity) {
        if (!fovCheck.getValue()) return true;
        // Basic FOV check implementation
        return true;
    }

    private void handleAutoblock() {
        if (!hasSword()) {
            if (isBlocking) unblock();
            return;
        }

        switch (blockMode.getValue()) {
            case NONE:
                if (isBlocking) unblock();
                break;
            case LEGIT:
                handleLegitBlock();
                break;
            case INTAVE:
                handleIntaveBlock();
                break;
            case VULCAN:
                handleVulcanBlock();
                break;
        }
    }

    private void handleLegitBlock() {
        if (target != null) {
            if (!isBlocking) block();
        } else {
            if (isBlocking) unblock();
        }
    }

    private void handleIntaveBlock() {
        if (target != null && mc.thePlayer.ticksExisted % 20 < 15) {
            if (!isBlocking) block();
        } else {
            if (isBlocking) unblock();
        }
    }

    private void handleVulcanBlock() {
        if (target != null && attackTimer.passedMs(200)) {
            if (!isBlocking) block();
            blockTimer.reset();
        }
        if (isBlocking && blockTimer.passedMs(100)) {
            unblock();
        }
    }

    private void handleAttacks() {
        if (target == null || !attackTimer.passedMs(getAttackDelay())) return;

        switch (mode.getValue()) {
            case VANILLA:
            case NCP:
                attackVanilla();
                break;
            case WATCHDOG_18:
            case WATCHDOG_112:
                attackWatchdog();
                break;
            case VULCAN:
                attackVulcan();
                break;
            case INTAVE:
                attackIntave();
                break;
        }
        attackTimer.reset();
    }

    private void attackVanilla() {
        if (blockMode.getValue() == BlockMode.NCP) {
            if (isBlocking) unblock();
            attackTarget();
            block();
        } else {
            attackTarget();
        }
    }

    private void attackWatchdog() {
        if (isBlocking) unblock();
        attackTarget();
        if (hasSword()) block();
    }

    private void attackVulcan() {
        for (int i = 0; i < 2; i++) {
            attackTarget();
        }
    }

    private void attackIntave() {
        if (isBlocking) unblock();
        attackTarget();
        if (hasSword()) block();
    }

    private void attackTarget() {
        if (target == null || target.isDead) return;

        mc.thePlayer.swingItem();
        mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

        if (noSwing.getValue()) {
            // Reset swing to prevent client-side animation
            mc.thePlayer.swingProgressInt = 0;
        }
    }

    private void block() {
        if (!hasSword() || isBlocking) return;
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        isBlocking = true;
    }

    private void unblock() {
        if (!isBlocking) return;
        mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        isBlocking = false;
    }

    private boolean hasSword() {
        return mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private int getAttackDelay() {
        int min = apsMin.getValue();
        int max = apsMax.getValue();
        int delay = 1000 / (min + (int)(Math.random() * ((max - min) + 1)));
        return Math.max(delay, 50); // Cap at 20 APS for safety
    }

    @Override
    public void onDisable() {
        if (isBlocking) unblock();
        target = null;
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name() + (target != null ? "✓" : "");
    }

    public enum Mode {
        VANILLA, NCP, WATCHDOG_18, WATCHDOG_112, VULCAN, INTAVE
    }

    public enum BlockMode {
        NONE, LEGIT, NCP, INTAVE, VULCAN
    }
}
