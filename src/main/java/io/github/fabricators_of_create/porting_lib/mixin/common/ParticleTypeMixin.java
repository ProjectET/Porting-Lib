package io.github.fabricators_of_create.porting_lib.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.fabricators_of_create.porting_lib.extensions.RegistryNameProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;

@Mixin(ParticleType.class)
public abstract class ParticleTypeMixin implements RegistryNameProvider {
	@Unique
	private ResourceLocation port_lib$registryName = null;

	@Override
	public ResourceLocation getRegistryName() {
		if (port_lib$registryName == null) {
			port_lib$registryName = Registry.PARTICLE_TYPE.getKey((ParticleType<?>) (Object) this);
		}
		return port_lib$registryName;
	}
}
