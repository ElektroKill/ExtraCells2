package extracells.inventory;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;

import com.google.common.collect.Lists;

import extracells.api.ECApi;
import extracells.api.IFluidStorageCell;
import extracells.api.IHandlerFluidStorage;
import extracells.container.ContainerFluidStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class HandlerItemStorageFluid implements IMEInventoryHandler<IAEFluidStack>, IHandlerFluidStorage {

    private NBTTagCompound stackTag;
    protected ArrayList<FluidStack> fluidStacks = new ArrayList<FluidStack>();
    private ArrayList<Fluid> prioritizedFluids = new ArrayList<Fluid>();
    private int totalTypes;
    private int totalBytes;
    private List<ContainerFluidStorage> containers = new ArrayList<ContainerFluidStorage>();
    private ISaveProvider saveProvider;

    public HandlerItemStorageFluid(ItemStack _storageStack, ISaveProvider _saveProvider) {
        if (!_storageStack.hasTagCompound())
            _storageStack.setTagCompound(new NBTTagCompound());
        stackTag = _storageStack.getTagCompound();
        totalTypes = ((IFluidStorageCell) _storageStack.getItem()).getMaxTypes(_storageStack);
        totalBytes = ((IFluidStorageCell) _storageStack.getItem()).getMaxBytes(_storageStack) * 250;

        for (int i = 0; i < totalTypes; i++)
            fluidStacks.add(FluidStack.loadFluidStackFromNBT(stackTag.getCompoundTag("Fluid#" + i)));

        saveProvider = _saveProvider;
    }
    
    public HandlerItemStorageFluid(ItemStack _storageStack, ISaveProvider _saveProvider, ArrayList<Fluid> _filter) {
    	this(_storageStack, _saveProvider);
    	if(_filter != null)
    		prioritizedFluids = _filter;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEFluidStack input) {
        return input != null && prioritizedFluids.contains(input.getFluid());
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        if (input == null)
            return false;
        if(!ECApi.instance().canStoreFluid(input.getFluid()))
        	return false;
        for (FluidStack fluidStack : fluidStacks) {
            if (fluidStack == null || fluidStack.getFluid().getID() == input.getFluid().getID())
                return allowedByFormat(input.getFluid());
        }
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true; //TODO
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable mode, BaseActionSource src) {
        if (input == null || !allowedByFormat(input.getFluid()))
            return input;
        IAEFluidStack notAdded = input.copy();
        List<FluidStack> currentFluids = Lists.newArrayList(fluidStacks);
        for (int i = 0; i < currentFluids.size(); i++) {
            FluidStack currentStack = currentFluids.get(i);
            if (notAdded != null && currentStack != null && input.getFluid().getID() == currentStack.fluidID) {
                if (notAdded.getStackSize() <= freeBytes()) {
                    FluidStack toWrite = new FluidStack(currentStack.fluidID, currentStack.amount + (int) notAdded.getStackSize());
                    currentFluids.set(i, toWrite);
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, toWrite);
                    }
                    notAdded = null;
                } else {
                    FluidStack toWrite = new FluidStack(currentStack.fluidID, currentStack.amount + freeBytes());
                    currentFluids.set(i, toWrite);
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, toWrite);
                    }
                    notAdded.setStackSize(notAdded.getStackSize() - freeBytes());
                }
            }
        }
        for (int i = 0; i < currentFluids.size(); i++) {
            FluidStack currentStack = currentFluids.get(i);
            if (notAdded != null && currentStack == null) {
                if (input.getStackSize() <= freeBytes()) {
                    FluidStack toWrite = notAdded.getFluidStack();
                    currentFluids.set(i, toWrite);
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, toWrite);
                    }
                    notAdded = null;
                } else {
                    FluidStack toWrite = new FluidStack(notAdded.getFluid().getID(), freeBytes());
                    currentFluids.set(i, toWrite);
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, toWrite);
                    }
                    notAdded.setStackSize(notAdded.getStackSize() - freeBytes());
                }
            }
        }
        if (notAdded == null || !notAdded.equals(input))
            requestSave();
        return notAdded;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable mode, BaseActionSource src) {
        if (request == null || !allowedByFormat(request.getFluid()))
            return null;

        IAEFluidStack removedStack;
        List<FluidStack> currentFluids = Lists.newArrayList(fluidStacks);
        for (int i = 0; i < fluidStacks.size(); i++) {
            FluidStack currentStack = fluidStacks.get(i);
            if (currentStack != null && currentStack.fluidID == request.getFluid().getID()) {
                long endAmount = currentStack.amount - request.getStackSize();
                if (endAmount >= 0) {
                    removedStack = request.copy();
                    FluidStack toWrite = new FluidStack(currentStack.fluidID, (int) endAmount);
                    currentFluids.set(i, toWrite);
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, toWrite);
                    }
                } else {
                    removedStack = AEApi.instance().storage().createFluidStack(currentStack.copy());
                    if (mode == Actionable.MODULATE) {
                        writeFluidToSlot(i, null);
                    }
                }
                if (removedStack != null && removedStack.getStackSize() > 0)
                    requestSave();
                return removedStack;
            }
        }

        return null;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (FluidStack fluidStack : fluidStacks)
            if (fluidStack != null)
                out.add(AEApi.instance().storage().createFluidStack(fluidStack));
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    private boolean allowedByFormat(Fluid fluid) {
        return !isFormatted() || prioritizedFluids.contains(fluid);
    }

    public int freeBytes() {
        int i = 0;
        for (FluidStack stack : fluidStacks)
            if (stack != null)
                i += stack.amount;
        return totalBytes - i;
    }

    public int usedBytes() {
        return totalBytes - freeBytes();
    }

    public int totalBytes() {
        return totalBytes;
    }

    public int usedTypes() {
        int i = 0;
        for (FluidStack stack : fluidStacks)
            if (stack != null)
                i++;
        return i;
    }

    public int totalTypes() {
        return totalTypes;
    }

    public boolean isFormatted() {
        // Common case
        if (prioritizedFluids.isEmpty()) {
            return false;
        }

        for (Fluid currentFluid : prioritizedFluids) {
            if (currentFluid != null)
                return true;
        }

        return false;
    }

    protected void writeFluidToSlot(int i, FluidStack fluidStack) {
        NBTTagCompound fluidTag = new NBTTagCompound();
        if (fluidStack != null && fluidStack.fluidID > 0 && fluidStack.amount > 0) {
            fluidStack.writeToNBT(fluidTag);
            stackTag.setTag("Fluid#" + i, fluidTag);
        } else {
            stackTag.removeTag("Fluid#" + i);
        }
        fluidStacks.set(i, fluidStack);
    }

    private void requestSave() {
        if (saveProvider != null)
            saveProvider.saveChanges(this);
    }
}
