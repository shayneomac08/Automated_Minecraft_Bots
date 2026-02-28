package com.shayneomac08.automated_minecraft_bots.event;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * CRITICAL FIX: Make hostile mobs target bots like they target players
 * This fixes the issue where zombies, skeletons, etc. ignore bots completely
 */
@EventBusSubscriber(modid = "automated_minecraft_bots")
public class MobTargetingHandler {

    /**
     * When a mob is looking for a target, make it consider bots as valid targets
     * This event fires when mobs use NearestAttackableTargetGoal
     */
    @SubscribeEvent
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();

        // Only handle hostile mobs (monsters)
        if (!(entity instanceof Monster mob)) {
            return;
        }

        // If the mob doesn't have a target yet, look for nearby bots
        LivingEntity currentTarget = event.getOriginalAboutToBeSetTarget();
        if (currentTarget == null) {
            // Find the nearest bot within 16 blocks
            java.util.List<AmbNpcEntity> nearbyBots = entity.level().getEntitiesOfClass(
                AmbNpcEntity.class,
                entity.getBoundingBox().inflate(16.0, 8.0, 16.0),
                bot -> bot.isAlive() && !bot.isSpectator()
            );

            if (!nearbyBots.isEmpty()) {
                // Find the closest bot
                AmbNpcEntity nearestBot = null;
                double closestDist = Double.MAX_VALUE;

                for (AmbNpcEntity bot : nearbyBots) {
                    double dist = entity.distanceToSqr(bot);
                    if (dist < closestDist) {
                        closestDist = dist;
                        nearestBot = bot;
                    }
                }

                if (nearestBot != null) {
                    // Check if there's a player closer than the bot
                    net.minecraft.world.entity.player.Player nearestPlayer = entity.level().getNearestPlayer(
                        entity,
                        16.0
                    );

                    if (nearestPlayer == null || entity.distanceToSqr(nearestPlayer) > closestDist) {
                        // Bot is closer or no player nearby, target the bot
                        if (mob instanceof Mob mobEntity) {
                            mobEntity.setTarget(nearestBot);
                            System.out.println("[AMB] " + entity.getName().getString() + " is now targeting bot: " + nearestBot.getName().getString());
                        }
                    }
                }
            }
        }
    }

    /**
     * Alternative approach: When a mob attacks, make sure it can attack bots
     * This ensures bots take damage from mob attacks
     */
    @SubscribeEvent
    public static void onLivingAttack(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        // If a bot is being attacked, make sure the damage goes through
        if (event.getEntity() instanceof AmbNpcEntity bot) {
            // Don't cancel the damage - let it through
            // This ensures bots can be hurt by mobs

            // Log the attack for debugging
            if (event.getSource().getEntity() instanceof Mob attacker) {
                System.out.println("[AMB] " + attacker.getName().getString() + " attacked bot " + bot.getName().getString() + " for " + event.getAmount() + " damage");
            }
        }
    }
}
