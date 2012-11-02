package net.minecraft.server;

import forge.ISpecialResistance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.PluginManager;

public class Explosion
{
  public boolean a = false;
  private Random h = new Random();
  private World world;
  public double posX;
  public double posY;
  public double posZ;
  public Entity source;
  public float size;
  public Set blocks = new HashSet();

  public boolean wasCanceled = false;

  public Explosion(World world, Entity entity, double d0, double d1, double d2, float f) {
    this.world = world;
    this.source = entity;
    this.size = (float)Math.max(f, 0.0D);
    this.posX = d0;
    this.posY = d1;
    this.posZ = d2;
  }

  public void a()
  {
    if (this.size < 0.1F) {
      return;
    }

    float f = this.size;
    byte b0 = 16;

    for (int i = 0; i < b0; i++) {
      for (int j = 0; j < b0; j++) {
        for (int k = 0; k < b0; k++) {
          if ((i == 0) || (i == b0 - 1) || (j == 0) || (j == b0 - 1) || (k == 0) || (k == b0 - 1)) {
            double d3 = i / (b0 - 1.0F) * 2.0F - 1.0F;
            double d4 = j / (b0 - 1.0F) * 2.0F - 1.0F;
            double d5 = k / (b0 - 1.0F) * 2.0F - 1.0F;
            double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);

            d3 /= d6;
            d4 /= d6;
            d5 /= d6;
            float f1 = this.size * (0.7F + this.world.random.nextFloat() * 0.6F);

            double d0 = this.posX;
            double d1 = this.posY;
            double d2 = this.posZ;

            for (float f2 = 0.3F; f1 > 0.0F; f1 -= f2 * 0.75F) {
              int l = MathHelper.floor(d0);
              int i1 = MathHelper.floor(d1);
              int j1 = MathHelper.floor(d2);
              int k1 = this.world.getTypeId(l, i1, j1);

              if (k1 > 0)
              {
                if ((Block.byId[k1] instanceof ISpecialResistance)) {
                  ISpecialResistance isr = (ISpecialResistance)Block.byId[k1];
                  f1 -= (isr.getSpecialExplosionResistance(this.world, l, i1, j1, this.posX, this.posY, this.posZ, this.source) + 0.3F) * f2;
                } else {
                  f1 -= (Block.byId[k1].a(this.source) + 0.3F) * f2;
                }
              }

              if ((f1 > 0.0F) && (i1 < 256) && (i1 >= 0)) {
                this.blocks.add(new ChunkPosition(l, i1, j1));
              }

              d0 += d3 * f2;
              d1 += d4 * f2;
              d2 += d5 * f2;
            }
          }
        }
      }
    }

    this.size *= 2.0F;
    i = MathHelper.floor(this.posX - this.size - 1.0D);
    int j = MathHelper.floor(this.posX + this.size + 1.0D);
    int k = MathHelper.floor(this.posY - this.size - 1.0D);
    int l1 = MathHelper.floor(this.posY + this.size + 1.0D);
    int i2 = MathHelper.floor(this.posZ - this.size - 1.0D);
    int j2 = MathHelper.floor(this.posZ + this.size + 1.0D);
    List list = this.world.getEntities(this.source, AxisAlignedBB.b(i, k, i2, j, l1, j2));
    Vec3D vec3d = Vec3D.create(this.posX, this.posY, this.posZ);

    for (int k2 = 0; k2 < list.size(); k2++) {
      Entity entity = (Entity)list.get(k2);

      double d0 = entity.locX - this.posX;
      double d1 = entity.locY - this.posY;
      double d2 = entity.locZ - this.posZ;
      double d8 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

      double d7 = d8 / this.size;

      if (d7 <= 1.0D)
      {
        if (d8 != 0.0D) {
          d0 /= d8;
          d1 /= d8;
          d2 /= d8;
        } else {
          d0 = 0.0D;
          d1 = 0.0D;
          d2 = 0.0D;
        }

        double d9 = this.world.a(vec3d, entity.boundingBox);
        double d10 = (1.0D - d7) * d9;

        org.bukkit.entity.Entity damagee = entity == null ? null : entity.getBukkitEntity();
        int damageDone = (int)((d10 * d10 + d10) / 2.0D * 8.0D * this.size + 1.0D);

        if (damagee != null)
        {
          if (this.source == null) {
            EntityDamageByBlockEvent event = new EntityDamageByBlockEvent(null, damagee, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, damageDone);
            Bukkit.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
              damagee.setLastDamageCause(event);
              entity.damageEntity(DamageSource.EXPLOSION, event.getDamage());
              entity.motX += d0 * d10;
              entity.motY += d1 * d10;
              entity.motZ += d2 * d10;
            }
          } else {
            org.bukkit.entity.Entity damager = this.source.getBukkitEntity();
            EntityDamageEvent.DamageCause damageCause;
            EntityDamageEvent.DamageCause damageCause;
            if ((damager instanceof TNTPrimed))
              damageCause = EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
            else {
              damageCause = EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
            }
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, damagee, damageCause, damageDone);
            Bukkit.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
              entity.getBukkitEntity().setLastDamageCause(event);
              entity.damageEntity(DamageSource.EXPLOSION, event.getDamage());

              entity.motX += d0 * d10;
              entity.motY += d1 * d10;
              entity.motZ += d2 * d10;
            }
          }
        }
      }
    }

    this.size = f;
    ArrayList arraylist = new ArrayList();

    arraylist.addAll(this.blocks);
  }

  public void a(boolean flag) {
    this.world.makeSound(this.posX, this.posY, this.posZ, "random.explode", 4.0F, (1.0F + (this.world.random.nextFloat() - this.world.random.nextFloat()) * 0.2F) * 0.7F);
    this.world.a("hugeexplosion", this.posX, this.posY, this.posZ, 0.0D, 0.0D, 0.0D);
    ArrayList arraylist = new ArrayList();

    arraylist.addAll(this.blocks);

    org.bukkit.World bworld = this.world.getWorld();
    org.bukkit.entity.Entity explode = this.source == null ? null : this.source.getBukkitEntity();
    Location location = new Location(bworld, this.posX, this.posY, this.posZ);

    List blockList = new ArrayList();
    for (int j = arraylist.size() - 1; j >= 0; j--) {
      ChunkPosition cpos = (ChunkPosition)arraylist.get(j);
      org.bukkit.block.Block block = bworld.getBlockAt(cpos.x, cpos.y, cpos.z);
      if (block.getType() != Material.AIR) {
        blockList.add(block);
      }
    }

    EntityExplodeEvent event = new EntityExplodeEvent(explode, location, blockList, 0.3F);
    this.world.getServer().getPluginManager().callEvent(event);

    arraylist.clear();
    this.blocks.clear();

    for (org.bukkit.block.Block block : event.blockList()) {
      ChunkPosition coords = new ChunkPosition(block.getX(), block.getY(), block.getZ());
      arraylist.add(coords);
      this.blocks.add(coords);
    }

    if (event.isCancelled()) {
      this.wasCanceled = true;
      return;
    }

    for (int i = arraylist.size() - 1; i >= 0; i--) {
      ChunkPosition chunkposition = (ChunkPosition)arraylist.get(i);
      int j = chunkposition.x;
      int k = chunkposition.y;
      int l = chunkposition.z;
      int i1 = this.world.getTypeId(j, k, l);
      if (flag) {
        double d0 = j + this.world.random.nextFloat();
        double d1 = k + this.world.random.nextFloat();
        double d2 = l + this.world.random.nextFloat();
        double d3 = d0 - this.posX;
        double d4 = d1 - this.posY;
        double d5 = d2 - this.posZ;
        double d6 = MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);

        d3 /= d6;
        d4 /= d6;
        d5 /= d6;
        double d7 = 0.5D / (d6 / this.size + 0.1D);

        d7 *= this.world.random.nextFloat() * this.world.random.nextFloat() + 0.3F;
        d3 *= d7;
        d4 *= d7;
        d5 *= d7;
        this.world.a("explode", (d0 + this.posX * 1.0D) / 2.0D, (d1 + this.posY * 1.0D) / 2.0D, (d2 + this.posZ * 1.0D) / 2.0D, d3, d4, d5);
        this.world.a("smoke", d0, d1, d2, d3, d4, d5);
      }

      if ((i1 > 0) && (i1 != Block.FIRE.id))
      {
        Block.byId[i1].dropNaturally(this.world, j, k, l, this.world.getData(j, k, l), event.getYield(), 0);
        this.world.setTypeId(j, k, l, 0);
        Block.byId[i1].wasExploded(this.world, j, k, l);
      }
    }

    if (this.a)
      for (i = arraylist.size() - 1; i >= 0; i--) {
        ChunkPosition chunkposition = (ChunkPosition)arraylist.get(i);
        int j = chunkposition.x;
        int k = chunkposition.y;
        int l = chunkposition.z;
        int i1 = this.world.getTypeId(j, k, l);
        int j1 = this.world.getTypeId(j, k - 1, l);

        if ((i1 == 0) && (Block.n[j1] != 0) && (this.h.nextInt(3) == 0))
          this.world.setTypeId(j, k, l, Block.FIRE.id);
      }
  }
}
