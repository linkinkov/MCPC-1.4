package net.minecraft.server;

import java.util.Random;

import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.IPlantable;

public class BlockSoil extends Block {

    protected BlockSoil(int i) {
        super(i, Material.EARTH);
        this.textureId = 87;
        this.b(true);
        this.a(0.0F, 0.0F, 0.0F, 1.0F, 0.9375F, 1.0F);
        this.h(255);
    }

    public AxisAlignedBB e(World world, int i, int j, int k) {
        return AxisAlignedBB.a().a((double) (i + 0), (double) (j + 0), (double) (k + 0), (double) (i + 1), (double) (j + 1), (double) (k + 1));
    }

    public boolean c() {
        return false;
    }

    public boolean b() {
        return false;
    }

    public int a(int i, int j) {
        return i == 1 && j > 0 ? this.textureId - 1 : (i == 1 ? this.textureId : 2);
    }

    public void b(World world, int i, int j, int k, Random random) {
        if (!this.n(world, i, j, k) && !world.B(i, j + 1, k)) {
            int l = world.getData(i, j, k);

            if (l > 0) {
                world.setData(i, j, k, l - 1);
            } else if (!this.l(world, i, j, k)) {
                world.setTypeId(i, j, k, Block.DIRT.id);
            }
        } else {
            world.setData(i, j, k, 7);
        }
    }

    public void a(World world, int i, int j, int k, Entity entity, float f) {
        if (!world.isStatic && world.random.nextFloat() < f - 0.5F) {
            // CraftBukkit start - interact soil
            org.bukkit.event.Cancellable cancellable;
            if (entity instanceof EntityHuman) {
                cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((EntityHuman) entity, org.bukkit.event.block.Action.PHYSICAL, i, j, k, -1, null);
            } else {
                cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(i, j, k));
                world.getServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
            }

            if (cancellable.isCancelled()) {
                return;
            }
            // CraftBukkit end

            world.setTypeId(i, j, k, Block.DIRT.id);
        }
    }

    /**
     * returns true if there is at least one cropblock nearby (x-1 to x+1, y+1, z-1 to z+1)
     */
    private boolean l(World var1, int var2, int var3, int var4)
    {
        byte var5 = 0;

        for (int var6 = var2 - var5; var6 <= var2 + var5; ++var6)
        {
            for (int var7 = var4 - var5; var7 <= var4 + var5; ++var7)
            {
                int var8 = var1.getTypeId(var6, var3 + 1, var7);
                Block var9 = byId[var8];

                if (var9 instanceof IPlantable && this.canSustainPlant(var1, var2, var3, var4, ForgeDirection.UP, (IPlantable)var9))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean n(World world, int i, int j, int k) {
        for (int l = i - 4; l <= i + 4; ++l) {
            for (int i1 = j; i1 <= j + 1; ++i1) {
                for (int j1 = k - 4; j1 <= k + 4; ++j1) {
                    if (world.getMaterial(l, i1, j1) == Material.WATER) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        super.doPhysics(world, i, j, k, l);
        Material material = world.getMaterial(i, j + 1, k);

        if (material.isBuildable()) {
            world.setTypeId(i, j, k, Block.DIRT.id);
        }
    }

    public int getDropType(int i, Random random, int j) {
        return Block.DIRT.getDropType(0, random, j);
    }
}
