package dev.distortion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;

public class DistortionMod implements ModInitializer {
    public static final String MOD_ID = "distortion";
    private static final String TAG_PREFIX = "distortion_stress_";
    private static final int MAX_STRESS = 100;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(this::onWorldTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("distortion")
                .then(literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    int stress = getStress(player);
                    player.sendMessage(Text.literal(
                        "Напряжение: " + stress + "/100 — " + stageName(stress)
                    ), false);
                    return 1;
                }))
                .then(literal("reset").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    setStress(player, 0);
                    player.sendMessage(Text.literal("Ваше напряжение сброшено."), false);
                    return 1;
                }))
            )
        );
    }

    private void onWorldTick(ServerWorld world) {
        if (world.getTime() % 20 != 0) return;

        for (ServerPlayerEntity player : world.getPlayers()) {
            int oldStress = getStress(player);
            int stress = calculateStress(player, oldStress);
            setStress(player, stress);

            int oldStage = stage(oldStress);
            int newStage = stage(stress);

            if (newStage > oldStage) {
                player.sendMessage(Text.literal(stageMessage(newStage)), false);
                world.playSound(null, player.getBlockPos(), SoundEvents.AMBIENT_CAVE,
                    SoundCategory.AMBIENT, 0.75f, 0.65f + (newStage * 0.08f));
            }

            applyStageEffects(player, newStage, world);
        }
    }

    private int calculateStress(ServerPlayerEntity player, int current) {
        BlockPos pos = player.getBlockPos();
        boolean darkness = player.getWorld().getLightLevel(pos) <= 4;
        boolean injured = player.getHealth() <= 6.0f;
        boolean hungry = player.getHungerManager().getFoodLevel() <= 6;

        int next = current;
        if (darkness) next += 2;
        if (injured) next += 2;
        if (hungry) next += 1;

        if (!darkness && !injured && !hungry
                && player.getWorld().getLightLevel(pos) >= 9
                && player.getHealth() > 10.0f
                && player.getHungerManager().getFoodLevel() >= 12) {
            next -= 2;
        }

        return Math.max(0, Math.min(MAX_STRESS, next));
    }

    private void applyStageEffects(ServerPlayerEntity player, int stage, ServerWorld world) {
        if (stage >= 1 && world.getTime() % 100 == 0) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, 45, 0, true, false, true
            ));
        }
        if (stage >= 2 && world.getTime() % 160 == 0) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA, 70, 0, true, false, true
            ));
        }
        if (stage >= 3 && world.getTime() % 220 == 0) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, 25, 0, true, false, true
            ));
        }
        if (stage == 4 && world.getTime() % 300 == 0) {
            player.sendMessage(Text.literal("§5Что-то внутри вас отвечает на зов..."), true);
        }
    }

    private int getStress(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                try {
                    return Math.max(0, Math.min(MAX_STRESS,
                        Integer.parseInt(tag.substring(TAG_PREFIX.length()))));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void setStress(ServerPlayerEntity player, int value) {
        player.getCommandTags().removeIf(tag -> tag.startsWith(TAG_PREFIX));
        player.addCommandTag(TAG_PREFIX + Math.max(0, Math.min(MAX_STRESS, value)));
    }

    private int stage(int stress) {
        if (stress >= 100) return 4;
        if (stress >= 75) return 3;
        if (stress >= 50) return 2;
        if (stress >= 25) return 1;
        return 0;
    }

    private String stageName(int stress) {
        return switch (stage(stress)) {
            case 1 -> "Инкубационный период";
            case 2 -> "Ранняя стадия";
            case 3 -> "Средняя стадия";
            case 4 -> "Терминальная стадия";
            default -> "Стабильное состояние";
        };
    }

    private String stageMessage(int stage) {
        return switch (stage) {
            case 1 -> "§7В краю зрения мелькает что-то неестественное...";
            case 2 -> "§8Вы начинаете слышать тихий шёпот.";
            case 3 -> "§5Мир вокруг будто теряет привычную форму.";
            case 4 -> "§4ГРАНИЦА ПРЕОДОЛЕНА. Искажение близко.";
            default -> "";
        };
    }
}
