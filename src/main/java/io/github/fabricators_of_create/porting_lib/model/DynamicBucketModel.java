package io.github.fabricators_of_create.porting_lib.model;

import com.google.common.collect.*;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;

import io.github.fabricators_of_create.porting_lib.extensions.FluidExtensions;
import io.github.fabricators_of_create.porting_lib.extensions.RegistryNameProvider;
import io.github.fabricators_of_create.porting_lib.extensions.TransformationExtensions;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.math.Quaternion;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;

public final class DynamicBucketModel implements IModelGeometry<DynamicBucketModel>
{
	private static final Logger LOGGER = LogManager.getLogger();

	// minimal Z offset to prevent depth-fighting
	private static final float NORTH_Z_COVER = 7.496f / 16f;
	private static final float SOUTH_Z_COVER = 8.504f / 16f;
	private static final float NORTH_Z_FLUID = 7.498f / 16f;
	private static final float SOUTH_Z_FLUID = 8.502f / 16f;

	@Nonnull
	private final Fluid fluid;

	private final boolean flipGas;
	private final boolean tint;
	private final boolean coverIsMask;
	private final boolean applyFluidLuminosity;

	@Deprecated
	public DynamicBucketModel(Fluid fluid, boolean flipGas, boolean tint, boolean coverIsMask)
	{
		this(fluid, flipGas, tint, coverIsMask, true);
	}

	public DynamicBucketModel(Fluid fluid, boolean flipGas, boolean tint, boolean coverIsMask, boolean applyFluidLuminosity)
	{
		this.fluid = fluid;
		this.flipGas = flipGas;
		this.tint = tint;
		this.coverIsMask = coverIsMask;
		this.applyFluidLuminosity = applyFluidLuminosity;
	}

	/**
	 * Returns a new ModelDynBucket representing the given fluid, but with the same
	 * other properties (flipGas, tint, coverIsMask).
	 */
	public DynamicBucketModel withFluid(Fluid newFluid)
	{
		return new DynamicBucketModel(newFluid, flipGas, tint, coverIsMask, applyFluidLuminosity);
	}

	@Override
	public BakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides overrides, ResourceLocation modelLocation) {
		Material particleLocation = owner.isTexturePresent("particle") ? owner.resolveTexture("particle") : null;
		Material baseLocation = owner.isTexturePresent("base") ? owner.resolveTexture("base") : null;
		Material fluidMaskLocation = owner.isTexturePresent("fluid") ? owner.resolveTexture("fluid") : null;
		Material coverLocation = owner.isTexturePresent("cover") ? owner.resolveTexture("cover") : null;

		ModelState transformsFromModel = owner.getCombinedTransform();

		TextureAtlasSprite fluidSprite = fluid != Fluids.EMPTY ? spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, ((FluidExtensions)fluid).getAttributes().getStillTexture())) : null;
		TextureAtlasSprite coverSprite = (coverLocation != null && (!coverIsMask || baseLocation != null)) ? spriteGetter.apply(coverLocation) : null;

		ImmutableMap<TransformType, Transformation> transformMap =
				PerspectiveMapWrapper.getTransforms(new CompositeModelState(transformsFromModel, modelTransform));

		TextureAtlasSprite particleSprite = particleLocation != null ? spriteGetter.apply(particleLocation) : null;

		if (particleSprite == null) particleSprite = fluidSprite;
		if (particleSprite == null && !coverIsMask) particleSprite = coverSprite;

		// if the fluid is lighter than air, will manipulate the initial state to be rotated 180deg to turn it upside down
		if (flipGas && fluid != Fluids.EMPTY && ((FluidExtensions)fluid).getAttributes().isLighterThanAir()) {
			modelTransform = new SimpleModelState(
					((TransformationExtensions)(Object)modelTransform.getRotation()).blockCornerToCenter().compose(
							((TransformationExtensions)(Object)new Transformation(null, new Quaternion(0, 0, 1, 0), null, null)).blockCenterToCorner()));
		}

		Transformation transform = modelTransform.getRotation();

		ItemMultiLayerBakedModel.Builder builder = ItemMultiLayerBakedModel.builder(owner, particleSprite, new ContainedFluidOverrideHandler(overrides, bakery, owner, this), transformMap);

		if (baseLocation != null) {
			// build base (insidest)
			builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(baseLocation), transform, spriteGetter));
		}

		if (fluidMaskLocation != null && fluidSprite != null) {
			TextureAtlasSprite templateSprite = spriteGetter.apply(fluidMaskLocation);
			if (templateSprite != null) {
				// build liquid layer (inside)
				int luminosity = applyFluidLuminosity ? ((FluidExtensions)fluid).getAttributes().getLuminosity() : 0;
				int color = tint ? ((FluidExtensions)fluid).getAttributes().getColor() : 0xFFFFFFFF;
				builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, NORTH_Z_FLUID, Direction.NORTH, color, 1, luminosity));
				builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, SOUTH_Z_FLUID, Direction.SOUTH, color, 1, luminosity));
			}
		}

		if (coverIsMask) {
			if (coverSprite != null && baseLocation != null) {
				TextureAtlasSprite baseSprite = spriteGetter.apply(baseLocation);
				builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.convertTexture(transform, coverSprite, baseSprite, NORTH_Z_COVER, Direction.NORTH, 0xFFFFFFFF, 2));
				builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.convertTexture(transform, coverSprite, baseSprite, SOUTH_Z_COVER, Direction.SOUTH, 0xFFFFFFFF, 2));
			}
		}
		else
		{
			if (coverSprite != null)
			{
				builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.genQuad(transform, 0, 0, 16, 16, NORTH_Z_COVER, coverSprite, Direction.NORTH, 0xFFFFFFFF, 2));
				builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.genQuad(transform, 0, 0, 16, 16, SOUTH_Z_COVER, coverSprite, Direction.SOUTH, 0xFFFFFFFF, 2));
			}
		}

		builder.setParticle(particleSprite);

		return builder.build();
	}

	@Override
	public Collection<Material> getTextures(IModelConfiguration owner, Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors)
	{
		Set<Material> texs = Sets.newHashSet();

		if (owner.isTexturePresent("particle")) texs.add(owner.resolveTexture("particle"));
		if (owner.isTexturePresent("base")) texs.add(owner.resolveTexture("base"));
		if (owner.isTexturePresent("fluid")) texs.add(owner.resolveTexture("fluid"));
		if (owner.isTexturePresent("cover")) texs.add(owner.resolveTexture("cover"));

		return texs;
	}

	public enum Loader implements IModelLoader<DynamicBucketModel>
	{
		INSTANCE;

		@Override
		public void onResourceManagerReload(ResourceManager resourceManager)
		{
			// no need to clear cache since we create a new model instance
		}

		@Override
		public DynamicBucketModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
		{
			if (!modelContents.has("fluid"))
				throw new RuntimeException("Bucket model requires 'fluid' value.");

			ResourceLocation fluidName = new ResourceLocation(modelContents.get("fluid").getAsString());

			Fluid fluid = Registry.FLUID.get(fluidName);

			boolean flip = false;
			if (modelContents.has("flipGas"))
			{
				flip = modelContents.get("flipGas").getAsBoolean();
			}

			boolean tint = true;
			if (modelContents.has("applyTint"))
			{
				tint = modelContents.get("applyTint").getAsBoolean();
			}

			boolean coverIsMask = true;
			if (modelContents.has("coverIsMask"))
			{
				coverIsMask = modelContents.get("coverIsMask").getAsBoolean();
			}

			boolean applyFluidLuminosity = true;
			if (modelContents.has("applyFluidLuminosity"))
			{
				applyFluidLuminosity = modelContents.get("applyFluidLuminosity").getAsBoolean();
			}

			// create new model with correct liquid
			return new DynamicBucketModel(fluid, flip, tint, coverIsMask, applyFluidLuminosity);
		}
	}

	private static final class ContainedFluidOverrideHandler extends ItemOverrides
	{
		private final Map<String, BakedModel> cache = Maps.newHashMap(); // contains all the baked models since they'll never change
		private final ItemOverrides nested;
		private final ModelBakery bakery;
		private final IModelConfiguration owner;
		private final DynamicBucketModel parent;

		private ContainedFluidOverrideHandler(ItemOverrides nested, ModelBakery bakery, IModelConfiguration owner, DynamicBucketModel parent)
		{
			this.nested = nested;
			this.bakery = bakery;
			this.owner = owner;
			this.parent = parent;
		}

		@Override
		public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed)
		{
			BakedModel overriden = nested.resolve(originalModel, stack, world, entity, seed);
			if (overriden != originalModel) return overriden;
			return TransferUtil.getFluidContained(stack)
					.map(fluidStack -> {
						Fluid fluid = fluidStack.getFluid();
						String name = ((RegistryNameProvider)fluid).getRegistryName().toString();

						if (!cache.containsKey(name))
						{
							DynamicBucketModel unbaked = this.parent.withFluid(fluid);
							BakedModel bakedModel = unbaked.bake(owner, bakery, Material::sprite, BlockModelRotation.X0_Y0, this, new ResourceLocation("forge:bucket_override"));
							cache.put(name, bakedModel);
							return bakedModel;
						}

						return cache.get(name);
					})
					// not a fluid item apparently
					.orElse(originalModel); // empty bucket
		}
	}
}
