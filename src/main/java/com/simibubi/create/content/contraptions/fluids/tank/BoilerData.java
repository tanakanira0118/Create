package com.simibubi.create.content.contraptions.fluids.tank;

import java.util.Arrays;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.components.steam.SteamEngineBlock;
import com.simibubi.create.content.contraptions.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.block.BlockStressValues;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import com.simibubi.create.foundation.utility.animation.LerpedFloat.Chaser;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BoilerData {

	static final int SAMPLE_RATE = 5;

	private static final int waterSupplyPerLevel = 10;
	private static final float passiveEngineEfficiency = 1 / 32f;

	// pooled water supply
	int gatheredSupply;
	float[] supplyOverTime = new float[10];
	int ticksUntilNextSample;
	int currentIndex;

	// heat score
	public boolean needsHeatLevelUpdate;
	public boolean passiveHeat;
	public int activeHeat;

	public float waterSupply;
	public int attachedEngines;

	public LerpedFloat gauge = LerpedFloat.linear();

	public void tick(FluidTankTileEntity controller) {
		if (!isActive())
			return;
		if (controller.getLevel().isClientSide) {
			gauge.tickChaser();
			float current = gauge.getValue(1);
			if (current > 1 && Create.RANDOM.nextFloat() < 1 / 2f)
				gauge.setValueNoUpdate(current + Math.min(-(current - 1) * Create.RANDOM.nextFloat(), 0));
			return;
		}
		if (needsHeatLevelUpdate && updateTemperature(controller))
			controller.notifyUpdate();
		ticksUntilNextSample--;
		if (ticksUntilNextSample > 0)
			return;
		int capacity = controller.tankInventory.getCapacity();
		if (capacity == 0)
			return;

		ticksUntilNextSample = SAMPLE_RATE;
		supplyOverTime[currentIndex] = gatheredSupply / (float) SAMPLE_RATE;
		waterSupply = Math.max(waterSupply, supplyOverTime[currentIndex]);
		currentIndex = (currentIndex + 1) % supplyOverTime.length;
		gatheredSupply = 0;

		if (currentIndex == 0) {
			waterSupply = 0;
			for (float i : supplyOverTime)
				waterSupply = Math.max(i, waterSupply);
		}

		controller.notifyUpdate();
	}

	public int getTheoreticalHeatLevel() {
		return activeHeat;
	}

	public int getMaxHeatLevelForBoilerSize(int boilerSize) {
		return (int) Math.min(18, boilerSize / 4);
	}

	public int getMaxHeatLevelForWaterSupply() {
		return (int) Math.min(18, Mth.ceil(waterSupply) / waterSupplyPerLevel);
	}

	public boolean isPassive(int boilerSize) {
		return passiveHeat && getMaxHeatLevelForBoilerSize(boilerSize) > 0 && getMaxHeatLevelForWaterSupply() > 0;
	}

	public float getEngineEfficiency(int boilerSize) {
		if (isPassive(boilerSize))
			return passiveEngineEfficiency / attachedEngines;
		if (activeHeat == 0)
			return 0;
		int actualHeat = getActualHeat(boilerSize);
		return attachedEngines <= actualHeat ? 1 : (float) actualHeat / attachedEngines;
	}

	private int getActualHeat(int boilerSize) {
		int forBoilerSize = getMaxHeatLevelForBoilerSize(boilerSize);
		int forWaterSupply = getMaxHeatLevelForWaterSupply();
		int actualHeat = Math.min(activeHeat, Math.min(forWaterSupply, forBoilerSize));
		return actualHeat;
	}

	String spacing = "    ";
	Component componentSpacing = new TextComponent(spacing);

	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking, int boilerSize) {
		if (!isActive())
			return false;

		int forBoilerSize = getMaxHeatLevelForBoilerSize(boilerSize);
		int forWaterSupply = getMaxHeatLevelForWaterSupply();
		int boilerLevel = Math.min(activeHeat, Math.min(forWaterSupply, forBoilerSize));

		int minValue = Math.min(passiveHeat ? 1 : activeHeat, Math.min(forWaterSupply, forBoilerSize));
		int maxValue = Math.max(passiveHeat ? 1 : activeHeat, Math.max(forWaterSupply, forBoilerSize));

		TextComponent heatLevel = isPassive(boilerSize) ? new TextComponent("Passive")
			: (boilerLevel == 0 ? new TextComponent("Idle")
				: boilerLevel == 18 ? new TextComponent("Max")
					: new TextComponent("Lvl " + IHaveGoggleInformation.format(boilerLevel)));
		tooltip.add(componentSpacing.plainCopy()
			.append(new TextComponent("Boiler Status:  ").append(heatLevel.withStyle(ChatFormatting.GREEN))));

		Component h = levelComponent("Heat ...... ", passiveHeat ? 1 : activeHeat, minValue, maxValue);
		Component w = levelComponent("Water ... ", forWaterSupply, minValue, maxValue);
		Component s = levelComponent("Size ....... ", forBoilerSize, minValue, maxValue);

		double totalSU = getEngineEfficiency(boilerSize) * 16 * Math.max(boilerLevel, attachedEngines)
			* BlockStressValues.getCapacity(AllBlocks.STEAM_ENGINE.get());
		Component capacity =
			new TextComponent(IHaveGoggleInformation.format(totalSU)).append(Lang.translate("generic.unit.stress"))
				.withStyle(ChatFormatting.AQUA);
		Component engines =
			new TextComponent(" via " + attachedEngines + " engine(s)").withStyle(ChatFormatting.DARK_GRAY);

		Component indent = new TextComponent(spacing);
		Component indent2 = new TextComponent(spacing + " ");

		tooltip.add(indent2.plainCopy()
			.append(s));
		tooltip.add(indent2.plainCopy()
			.append(w));
		tooltip.add(indent2.plainCopy()
			.append(h));

		tooltip.add(indent);
		tooltip.add(indent.plainCopy()
			.append(Lang.translate("tooltip.capacityProvided")
				.withStyle(ChatFormatting.GRAY)));
		tooltip.add(indent2.plainCopy()
			.append(capacity)
			.append(engines));

		return true;
	}

	private MutableComponent levelComponent(String text, int level, int min, int max) {
		int indexOf = text.indexOf(".");
		String label = text.substring(0, indexOf);
		String dots = text.substring(indexOf);
		MutableComponent c = new TextComponent(label).withStyle(ChatFormatting.GRAY)
			.append(new TextComponent(dots).withStyle(ChatFormatting.DARK_GRAY));

		c.append(bars(Math.max(0, min - 1), ChatFormatting.DARK_GREEN));
		c.append(bars(min > 0 ? 1 : 0, ChatFormatting.GREEN));
		c.append(bars(Math.max(0, level - min), ChatFormatting.DARK_GREEN));
		c.append(bars(Math.max(0, max - level), ChatFormatting.DARK_RED));
		c.append(bars(Math.max(0, Math.min(18 - max, ((max / 5 + 1) * 5) - max)), ChatFormatting.DARK_GRAY));

		return c;
	}

	private MutableComponent bars(int level, ChatFormatting format) {
		return new TextComponent(Strings.repeat('|', level)).withStyle(format);
	}

	public boolean evaluate(FluidTankTileEntity controller) {
		BlockPos controllerPos = controller.getBlockPos();
		Level level = controller.getLevel();
		int prev = attachedEngines;
		attachedEngines = 0;

		for (int yOffset = 0; yOffset < controller.height; yOffset++) {
			for (int xOffset = 0; xOffset < controller.width; xOffset++) {
				for (int zOffset = 0; zOffset < controller.width; zOffset++) {

					BlockPos pos = controllerPos.offset(xOffset, yOffset, zOffset);
					BlockState blockState = level.getBlockState(pos);
					if (!FluidTankBlock.isTank(blockState))
						continue;
					for (Direction d : Iterate.directions) {
						BlockPos enginePos = pos.relative(d);
						BlockState engineState = level.getBlockState(enginePos);
						if (!AllBlocks.STEAM_ENGINE.has(engineState))
							continue;
						if (SteamEngineBlock.getFacing(engineState) != d)
							continue;
						attachedEngines++;
					}
				}
			}
		}

		needsHeatLevelUpdate = true;
		return prev != attachedEngines;
	}

	public boolean updateTemperature(FluidTankTileEntity controller) {
		BlockPos controllerPos = controller.getBlockPos();
		Level level = controller.getLevel();
		needsHeatLevelUpdate = false;

		boolean prevPassive = passiveHeat;
		int prevActive = activeHeat;
		passiveHeat = false;
		activeHeat = 0;

		for (int xOffset = 0; xOffset < controller.width; xOffset++) {
			for (int zOffset = 0; zOffset < controller.width; zOffset++) {
				BlockPos pos = controllerPos.offset(xOffset, -1, zOffset);
				BlockState blockState = level.getBlockState(pos);
				float heat = BoilerHeaters.getActiveHeatOf(blockState);
				if (heat == 0) {
					passiveHeat |= BoilerHeaters.canHeatPassively(blockState);
					continue;
				}
				activeHeat += heat;
			}
		}

		passiveHeat &= activeHeat == 0;

		return prevActive != activeHeat || prevPassive != passiveHeat;
	}

	public boolean isActive() {
		return attachedEngines > 0;
	}

	public void clear() {
		waterSupply = 0;
		activeHeat = 0;
		passiveHeat = false;
		attachedEngines = 0;
		Arrays.fill(supplyOverTime, 0);
	}

	public CompoundTag write() {
		CompoundTag nbt = new CompoundTag();
		nbt.putFloat("Supply", waterSupply);
		nbt.putInt("ActiveHeat", activeHeat);
		nbt.putBoolean("PassiveHeat", passiveHeat);
		nbt.putInt("Engines", attachedEngines);
		nbt.putBoolean("Update", needsHeatLevelUpdate);
		return nbt;
	}

	public void read(CompoundTag nbt, int boilerSize) {
		waterSupply = nbt.getFloat("Supply");
		activeHeat = nbt.getInt("ActiveHeat");
		passiveHeat = nbt.getBoolean("PassiveHeat");
		attachedEngines = nbt.getInt("Engines");
		needsHeatLevelUpdate = nbt.getBoolean("Update");
		Arrays.fill(supplyOverTime, (int) waterSupply);

		int forBoilerSize = getMaxHeatLevelForBoilerSize(boilerSize);
		int forWaterSupply = getMaxHeatLevelForWaterSupply();
		int actualHeat = Math.min(activeHeat, Math.min(forWaterSupply, forBoilerSize));
		float target = isPassive(boilerSize) ? 1 / 8f : forBoilerSize == 0 ? 0 : actualHeat / (forBoilerSize * 1f);
		gauge.chase(target, 0.125f, Chaser.EXP);
	}

	public BoilerFluidHandler createHandler() {
		return new BoilerFluidHandler();
	}

	public class BoilerFluidHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return 10000;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return FluidHelper.isWater(stack.getFluid());
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!isFluidValid(0, resource))
				return 0;
			int amount = resource.getAmount();
			if (action.execute())
				gatheredSupply += amount;
			return amount;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}

	}

}