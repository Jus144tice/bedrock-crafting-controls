package com.bedrockcraftingcontrols;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * Entry point for Bedrock Crafting Controls.
 *
 * <p>This is a <strong>client-only</strong> mod ({@code dist = Dist.CLIENT}). It registers a
 * single client config and bakes its values into plain fields on {@link Config} whenever the
 * config loads or reloads. All of the actual behavior lives in the Mixin
 * {@code com.bedrockcraftingcontrols.mixin.MultiPlayerGameModeMixin}.
 */
@Mod(value = BedrockCraftingControls.MOD_ID, dist = Dist.CLIENT)
public final class BedrockCraftingControls {

    public static final String MOD_ID = "bedrockcraftingcontrols";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Flip to {@code true} (and run with debug logging enabled) to get verbose traces around the
     * recipe-click interception. Kept {@code false} so runtime logging stays quiet by default.
     */
    public static final boolean DEBUG = false;

    public BedrockCraftingControls(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // ModConfigEvent is abstract and never posted directly, so we listen for both concrete
        // subtypes. Each fires once the config file is read so we can cache its values.
        modBus.addListener((ModConfigEvent.Loading event) -> Config.onConfigEvent(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> Config.onConfigEvent(event));

        LOGGER.info("Bedrock Crafting Controls loaded (client-only).");
    }
}
