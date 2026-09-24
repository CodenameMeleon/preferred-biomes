package net.codenamemeleon.preferredbiomes.client;

import net.fabricmc.api.ClientModInitializer;

public class PreferredBiomesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			DebugWorldRunner.register();
		}
	}
}
