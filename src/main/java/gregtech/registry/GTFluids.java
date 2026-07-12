package gregtech.registry;

import gregtech.GT6UOU;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Consumer;

public final class GTFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, GT6UOU.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, GT6UOU.MODID);

    public static final DeferredHolder<FluidType, FluidType> RAW_OIL_TYPE = FLUID_TYPES.register(
            "raw_oil",
            () -> new FluidType(FluidType.Properties.create()
                    .density(900)
                    .viscosity(12000)
                    .motionScale(0.004D)
                    .canSwim(false)
                    .canDrown(true)
                    .supportsBoating(false)
            ) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        private static final ResourceLocation STILL_TEXTURE =
                                ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/raw_oil_still");
                        private static final ResourceLocation FLOWING_TEXTURE =
                                ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/raw_oil_flow");

                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOWING_TEXTURE;
                        }

                        @Override
                        public int getTintColor() {
                            return 0xFF1A1208;
                        }
                    });
                }

                @Override
                public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
                    entity.moveRelative(0.014F, movementVector);

                    Vec3 velocity = entity.getDeltaMovement();
                    double horizontalDrag = entity.onGround() ? 0.42D : 0.30D;
                    double sinkingVelocity = Math.min(velocity.y - 0.075D, -0.045D);

                    entity.setDeltaMovement(
                            velocity.x * horizontalDrag,
                            Math.max(sinkingVelocity, -0.42D),
                            velocity.z * horizontalDrag
                    );
                    entity.move(MoverType.SELF, entity.getDeltaMovement());

                    Vec3 movedVelocity = entity.getDeltaMovement();
                    entity.setDeltaMovement(
                            movedVelocity.x * 0.55D,
                            Math.max(Math.min(movedVelocity.y - 0.03D, -0.035D), -0.42D),
                            movedVelocity.z * 0.55D
                    );
                    return true;
                }

                @Override
                public void setItemMovement(ItemEntity entity) {
                    Vec3 velocity = entity.getDeltaMovement();
                    entity.setDeltaMovement(velocity.x * 0.18D, Math.max(velocity.y - 0.06D, -0.35D), velocity.z * 0.18D);
                }
            }
    );

    public static final DeferredHolder<FluidType, FluidType> OIL_TYPE = FLUID_TYPES.register(
            "oil",
            () -> simpleLiquidType(0xFF201407, "oil", 900, 8000)
    );

    public static final DeferredHolder<FluidType, FluidType> LIGHT_OIL_TYPE = FLUID_TYPES.register(
            "light_oil",
            () -> simpleLiquidType(0xFF3A2610, "light_oil", 820, 5000)
    );

    public static final DeferredHolder<FluidType, FluidType> HEAVY_OIL_TYPE = FLUID_TYPES.register(
            "heavy_oil",
            () -> simpleLiquidType(0xFF100B05, "heavy_oil", 960, 14000)
    );

    public static final DeferredHolder<FluidType, FluidType> NATURAL_GAS_TYPE = FLUID_TYPES.register(
            "natural_gas",
            () -> new FluidType(FluidType.Properties.create()
                    .density(-100)
                    .viscosity(100)
                    .canSwim(false)
                    .canDrown(false)
                    .canPushEntity(false)
            )
            {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        private static final ResourceLocation STILL_TEXTURE =
                                ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
                        private static final ResourceLocation FLOWING_TEXTURE =
                                ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");

                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOWING_TEXTURE;
                        }

                        @Override
                        public int getTintColor() {
                            return 0xCCF4F4EA;
                        }
                    });
                }
            }
    );

    public static final DeferredHolder<FluidType, FluidType> SALT_WATER_TYPE = FLUID_TYPES.register(
            "salt_water",
            () -> simpleLiquidType(0xCCB9D8E8, "salt_water", 1030, 1200)
    );

    public static final DeferredHolder<Fluid, FlowingFluid> RAW_OIL = FLUIDS.register(
            "raw_oil",
            () -> new BaseFlowingFluid.Source(rawOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_RAW_OIL = FLUIDS.register(
            "flowing_raw_oil",
            () -> new BaseFlowingFluid.Flowing(rawOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> OIL = FLUIDS.register(
            "oil",
            () -> new BaseFlowingFluid.Source(oilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_OIL = FLUIDS.register(
            "flowing_oil",
            () -> new BaseFlowingFluid.Flowing(oilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> LIGHT_OIL = FLUIDS.register(
            "light_oil",
            () -> new BaseFlowingFluid.Source(lightOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_LIGHT_OIL = FLUIDS.register(
            "flowing_light_oil",
            () -> new BaseFlowingFluid.Flowing(lightOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> HEAVY_OIL = FLUIDS.register(
            "heavy_oil",
            () -> new BaseFlowingFluid.Source(heavyOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HEAVY_OIL = FLUIDS.register(
            "flowing_heavy_oil",
            () -> new BaseFlowingFluid.Flowing(heavyOilProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> NATURAL_GAS = FLUIDS.register(
            "natural_gas",
            () -> new BaseFlowingFluid.Source(naturalGasProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_NATURAL_GAS = FLUIDS.register(
            "flowing_natural_gas",
            () -> new BaseFlowingFluid.Flowing(naturalGasProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> SALT_WATER = FLUIDS.register(
            "salt_water",
            () -> new BaseFlowingFluid.Source(saltWaterProperties())
    );

    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_SALT_WATER = FLUIDS.register(
            "flowing_salt_water",
            () -> new BaseFlowingFluid.Flowing(saltWaterProperties())
    );

    private GTFluids() {
    }

    private static FluidType simpleLiquidType(int tintColor, String texturePrefix, int density, int viscosity) {
        return new FluidType(FluidType.Properties.create()
                .density(density)
                .viscosity(viscosity)
                .motionScale(0.004D)
                .canSwim(false)
                .canDrown(true)
                .supportsBoating(false)
        ) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    private static final ResourceLocation STILL_TEXTURE =
                            ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/oil_still");
                    private static final ResourceLocation FLOWING_TEXTURE =
                            ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/oil_flow");

                    @Override
                    public ResourceLocation getStillTexture() {
                        return ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/" + texturePrefix + "_still");
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return ResourceLocation.fromNamespaceAndPath(GT6UOU.MODID, "block/" + texturePrefix + "_flow");
                    }

                    @Override
                    public int getTintColor() {
                        return tintColor;
                    }
                });
            }
        };
    }

    private static BaseFlowingFluid.Properties rawOilProperties() {
        return new BaseFlowingFluid.Properties(RAW_OIL_TYPE, RAW_OIL, FLOWING_RAW_OIL)
                .block(GTBlocks.RAW_OIL_BLOCK)
                .bucket(GTItems.RAW_OIL_BUCKET)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(60);
    }

    private static BaseFlowingFluid.Properties oilProperties() {
        return simpleFluidProperties(OIL_TYPE, OIL, FLOWING_OIL);
    }

    private static BaseFlowingFluid.Properties lightOilProperties() {
        return simpleFluidProperties(LIGHT_OIL_TYPE, LIGHT_OIL, FLOWING_LIGHT_OIL);
    }

    private static BaseFlowingFluid.Properties heavyOilProperties() {
        return simpleFluidProperties(HEAVY_OIL_TYPE, HEAVY_OIL, FLOWING_HEAVY_OIL);
    }

    private static BaseFlowingFluid.Properties naturalGasProperties() {
        return simpleFluidProperties(NATURAL_GAS_TYPE, NATURAL_GAS, FLOWING_NATURAL_GAS);
    }

    private static BaseFlowingFluid.Properties saltWaterProperties() {
        return simpleFluidProperties(SALT_WATER_TYPE, SALT_WATER, FLOWING_SALT_WATER);
    }

    private static BaseFlowingFluid.Properties simpleFluidProperties(
            DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, FlowingFluid> source,
            DeferredHolder<Fluid, FlowingFluid> flowing
    ) {
        return new BaseFlowingFluid.Properties(type, source, flowing)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(60);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
