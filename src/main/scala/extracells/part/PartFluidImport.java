package extracells.part;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.AECableType;
import extracells.util.PermissionUtil;

public class PartFluidImport extends PartFluidIO implements IFluidHandler {

	@Override
	public float getCableConnectionLength(AECableType aeCableType) {
		return 5.0F;
	}

	@Override
	public boolean doWork(int rate, int TicksSinceLastCall) {
		if (getFacingTank() == null || !isActive())
			return false;
		boolean empty = true;

		List<Fluid> filter = new ArrayList<Fluid>();
		filter.add(this.filterFluids[4]);

		if (this.filterSize >= 1) {
			for (byte i = 1; i < 9; i += 2) {
				if (i != 4) {
					filter.add(this.filterFluids[i]);
				}
			}
		}

		if (this.filterSize >= 2) {
			for (byte i = 0; i < 9; i += 2) {
				if (i != 4) {
					filter.add(this.filterFluids[i]);
				}
			}
		}

		for (Fluid fluid : filter) {
			if (fluid != null) {
				empty = false;

				if (fillToNetwork(fluid, rate * TicksSinceLastCall)) {
					return true;
				}
			}
		}
		return empty && fillToNetwork(null, rate * TicksSinceLastCall);
	}

	@Override
	public FluidStack drain(FluidStack resource, boolean doDrain) {
		return null;
	}

	@Override
	public FluidStack drain(int maxDrain, boolean doDrain) {
		return null;
	}

	@Override
	public int fill(FluidStack resource, boolean doFill) {
		boolean redstonePowered = isRedstonePowered();
		if (resource == null || redstonePowered && getRedstoneMode() == RedstoneMode.LOW_SIGNAL || !redstonePowered && getRedstoneMode() == RedstoneMode.HIGH_SIGNAL)
			return 0;
		int drainAmount = Math.min(125 + this.speedState * 125, resource.amount);
		FluidStack toFill = new FluidStack(resource.getFluid(), drainAmount);
		Actionable action = doFill ? Actionable.MODULATE : Actionable.SIMULATE;
		IAEFluidStack filled = injectFluid(AEApi.instance().storage().createFluidStack(toFill), action);
		if (filled == null)
			return toFill.amount;
		return toFill.amount - (int) filled.getStackSize();
	}

	protected boolean fillToNetwork(Fluid fluid, int toDrain) {
		FluidStack drained;
		IFluidHandler facingTank = getFacingTank();
		EnumFacing side = getSide();
		if (fluid == null) {
			drained = facingTank.drain(toDrain, false);
		} else {
			drained = facingTank.drain(new FluidStack(fluid, toDrain), false);
		}

		if (drained == null || drained.amount <= 0 || drained.getFluid() == null)
			return false;

		IAEFluidStack toFill = AEApi.instance().storage()
				.createFluidStack(drained);
		IAEFluidStack notInjected = injectFluid(toFill, Actionable.MODULATE);

		if (notInjected != null) {
			int amount = (int) (toFill.getStackSize() - notInjected
					.getStackSize());
			if (amount > 0) {
				if (fluid == null)
					facingTank.drain(amount, true);
				else
					facingTank.drain(new FluidStack(toFill.getFluid(), amount), true);
				return true;
			} else {
				return false;
			}
		} else {
			if (fluid == null)
				facingTank.drain(toFill.getFluidStack().amount, true);
			else
				facingTank.drain(toFill.getFluidStack(), true);
			return true;
		}
	}

	@Override
	public void getBoxes(IPartCollisionHelper bch) {
		bch.addBox(4, 4, 14, 12, 12, 16);
		bch.addBox(5, 5, 13, 11, 11, 14);
		bch.addBox(6, 6, 12, 10, 10, 13);
		bch.addBox(6, 6, 11, 10, 10, 12);
	}

	@Override
	public double getPowerUsage() {
		return 1.0D;
	}

	@Override
	public IFluidTankProperties[] getTankProperties() {
		return new IFluidTankProperties[0];
	}

	@Override
	public boolean onActivate(EntityPlayer player, EnumHand enumHand, Vec3d pos) {
		return PermissionUtil.hasPermission(player, SecurityPermissions.BUILD, (IPart) this) && super.onActivate(player, enumHand, pos);
	}

	/*@SideOnly(Side.CLIENT)
	@Override
	public void renderInventory(IPartRenderHelper rh, RenderBlocks renderer) {
		Tessellator ts = Tessellator.instance;

		IIcon side = TextureManager.IMPORT_SIDE.getTexture();
		rh.setTexture(side, side, side,
				TextureManager.IMPORT_FRONT.getTexture(), side, side);
		rh.setBounds(4, 4, 14, 12, 12, 16);
		rh.renderInventoryBox(renderer);

		rh.setTexture(side);
		rh.setBounds(5, 5, 13, 11, 11, 14);
		rh.renderInventoryBox(renderer);
		rh.setBounds(6, 6, 12, 10, 10, 13);
		rh.renderInventoryBox(renderer);

		rh.setBounds(4, 4, 14, 12, 12, 16);
		rh.setInvColor(AEColor.Cyan.blackVariant);
		ts.setBrightness(15 << 20 | 15 << 4);
		rh.renderInventoryFace(TextureManager.IMPORT_FRONT.getTextures()[1],
				ForgeDirection.SOUTH, renderer);

		rh.setBounds(6, 6, 11, 10, 10, 12);
		renderInventoryBusLights(rh, renderer);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void renderStatic(int x, int y, int z, IPartRenderHelper rh,
			RenderBlocks renderer) {
		Tessellator ts = Tessellator.instance;

		IIcon side = TextureManager.IMPORT_SIDE.getTexture();
		rh.setTexture(side, side, side,
				TextureManager.IMPORT_FRONT.getTextures()[0], side, side);
		rh.setBounds(4, 4, 14, 12, 12, 16);
		rh.renderBlock(x, y, z, renderer);

		ts.setColorOpaque_I(getHost().getColor().blackVariant);
		if (isActive())
			ts.setBrightness(15 << 20 | 15 << 4);
		rh.renderFace(x, y, z, TextureManager.IMPORT_FRONT.getTextures()[1],
				ForgeDirection.SOUTH, renderer);

		rh.setTexture(side);
		rh.setBounds(5, 5, 13, 11, 11, 14);
		rh.renderBlock(x, y, z, renderer);
		rh.setBounds(6, 6, 12, 10, 10, 13);
		rh.renderBlock(x, y, z, renderer);

		rh.setBounds(6, 6, 11, 10, 10, 12);
		renderStaticBusLights(x, y, z, rh, renderer);
	}*/
}
