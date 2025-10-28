package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Criticals extends Module {

    private final Random random = new Random();
    
    // Settings
    public Setting<Mode> mode = new Setting<>("Mode", Mode.VANILLA);
    public Setting<Boolean> strict = new Setting<>("Strict", false);
    public Setting<Boolean> onlySprint = new Setting<>("OnlySprint", true);
    public Setting<Boolean> checkPosition = new Setting<>("CheckPosition", true);
    public Setting<Integer> attackDelay = new Setting<>("AttackDelay", 5, 0, 10);
    
    // Timing
    private int lastAttackTick = 0;

    public Criticals() {
        super("Criticals", "Critical hits with selectable bypass modes", Category.COMBAT, true, false, false);
    }

    @SubscribeEvent
    public void onPacketSend(me.alpha432.oyvey.event.impl.PacketEvent.Send event) {
        if (!(event.getPacket() instanceof C02PacketUseEntity packet)) return;
        if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) return;

        Entity entity = packet.getEntityFromWorld(mc.theWorld);
        EntityPlayerSP player = mc.thePlayer;

        if (!isValidTarget(entity) || !canCritical(player)) return;
        
        // Attack delay check
        if (mc.thePlayer.ticksExisted - lastAttackTick < attackDelay.getValue()) {
            return;
        }
        lastAttackTick = mc.thePlayer.ticksExisted;

        executeCritical(player);
        player.onCriticalHit(entity);
    }

    private boolean isValidTarget(Entity entity) {
        return entity != null 
                && !(entity instanceof EntityEnderCrystal) 
                && entity instanceof EntityLivingBase
                && entity != mc.thePlayer;
    }

    private boolean canCritical(EntityPlayerSP player) {
        if (!player.onGround) return false;
        if (onlySprint.getValue() && !player.isSprinting()) return false;
        if (checkPosition.getValue() && (player.isInWater() || player.isInLava())) return false;
        return true;
    }

    private void executeCritical(EntityPlayerSP player) {
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;

        switch (mode.getValue()) {
            case VANILLA:
                sendVanillaCritical(x, y, z);
                break;
            case WATCHDOG:
                sendWatchdogCritical(x, y, z);
                break;
            case VULCAN:
                sendVulcanCritical(x, y, z);
                break;
            case INTAVE:
                sendIntaveCritical(x, y, z);
                break;
            case STRICT:
                sendStrictCritical(x, y, z);
                break;
        }
    }

    // Vanilla: basic +0.1 motion
    private void sendVanillaCritical(double x, double y, double z) {
        sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1D, z, false));
        sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
    }

    // Watchdog: randomized small offsets, sometimes skip
    private void sendWatchdogCritical(double x, double y, double z) {
        List<Double> offsets = Arrays.asList(0.0625D, 0.1D, 0.12D);
        Collections.shuffle(offsets);
        
        for (double offset : offsets) {
            if (random.nextFloat() < 0.2f) continue; // skip 20% for anti-cheat
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Vulcan: smaller offsets, very fast sequence
    private void sendVulcanCritical(double x, double y, double z) {
        double[] offsets = {0.05D, 0.06D};
        for (double offset : offsets) {
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Intave: higher offsets, randomized order
    private void sendIntaveCritical(double x, double y, double z) {
        List<Double> offsets = Arrays.asList(0.1D, 0.12D, 0.15D);
        Collections.shuffle(offsets);
        
        for (double offset : offsets) {
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Strict: minimal movement for strict servers
    private void sendStrictCritical(double x, double y, double z) {
        sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625D, z, false));
        sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
    }

    private void sendPacket(Object packet) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue((net.minecraft.network.Packet) packet);
        }
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }

    public enum Mode {
        VANILLA("Vanilla"),
        WATCHDOG("Watchdog"),
        VULCAN("Vulcan"),
        INTAVE("Intave"),
        STRICT("Strict");
        
        private final String name;
        
        Mode(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
