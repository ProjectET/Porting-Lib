package io.github.fabricators_of_create.porting_lib.util;

import net.minecraft.nbt.CompoundTag;

public class NBTSerializer {
	public static void deserializeNBT(Object o, CompoundTag nbt) {
		((NBTSerializable) o).deserializeNBT(nbt);
	}

	public static CompoundTag serializeNBT(Object o) {
		return ((NBTSerializable) o).serializeNBT();
	}
}
