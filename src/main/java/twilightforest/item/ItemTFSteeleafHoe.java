package twilightforest.item;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.IItemTier;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class ItemTFSteeleafHoe extends HoeItem {

	public ItemTFSteeleafHoe(IItemTier material, Properties props) {
		super(material, 1, material.getEfficiency(), props);
	}

	@Override
	public void fillItemGroup(ItemGroup tab, NonNullList<ItemStack> list) {
		if (isInGroup(tab)) {
			ItemStack istack = new ItemStack(this);
			istack.addEnchantment(Enchantments.EFFICIENCY, 1);
			list.add(istack);
		}
	}
}
