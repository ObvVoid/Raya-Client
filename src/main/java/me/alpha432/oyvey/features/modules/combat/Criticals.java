package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

public class Criticals extends Module {

    public enum Mode {
        VANILLA,
        WATCHDOG,
        VULCAN,
        INTAVE
    }

    private final Random random = new Random();

    private Mode mode = Mode.VANILLA; // Default mode

    public Criticals() {
        super("Criticals", "Critical hits with selectable bypass modes", Category.COMBAT, true, false, false);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @SubscribeEvent
    public void onPacketSend(me.alpha432.oyvey.event.impl.PacketEvent.Send event) {
        if (!(event.getPacket() instanceof C02PacketUseEntity packet)) return;
        if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) return;

        Entity entity = packet.getEntityFromWorld(mc.theWorld);
        EntityPlayerSP player = mc.thePlayer;

        if (entity == null
                || entity instanceof EntityEnderCrystal
                || !(entity instanceof EntityLivingBase)
                || !player.onGround) return;

        switch (mode) {
            case VANILLA -> sendVanillaCritical(player);
            case WATCHDOG -> sendWatchdogCritical(player);
            case VULCAN -> sendVulcanCritical(player);
            case INTAVE -> sendIntaveCritical(player);
        }

        player.onCriticalHit(entity);
    }

    // Vanilla: basic +0.1 motion
    private void sendVanillaCritical(EntityPlayerSP player) {
        double x = player.posX, y = player.posY, z = player.posZ;
        player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.1D, z, false));
        player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
    }

    // Watchdog: randomized small offsets, sometimes skip
    private void sendWatchdogCritical(EntityPlayerSP player) {
        double[] offsets = {0.0625D, 0.1D, 0.12D};
        shuffleArray(offsets);
        double x = player.posX, y = player.posY, z = player.posZ;

        for (double offset : offsets) {
            if (random.nextFloat() < 0.2f) continue; // skip 20% for anti-cheat
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Vulcan: smaller offsets, very fast sequence
    private void sendVulcanCritical(EntityPlayerSP player) {
        double[] offsets = {0.05D, 0.06D};
        double x = player.posX, y = player.posY, z = player.posZ;
        for (double offset : offsets) {
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Intave: higher offsets, randomized order
    private void sendIntaveCritical(EntityPlayerSP player) {
        double[] offsets = {0.1D, 0.12D, 0.15D};
        shuffleArray(offsets);
        double x = player.posX, y = player.posY, z = player.posZ;
        for (double offset : offsets) {
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + offset, z, false));
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    // Utility: shuffle array
    private void shuffleArray(double[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            double temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    @Override
    public String getDisplayInfo() {
        return mode.name();
    }
}
