package net.minecraft.server;

import ee.lutsu.alpha.minecraft.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.util.LongHashSet;
import org.bukkit.craftbukkit.util.UnsafeList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
// CraftBukkit end

import com.google.common.collect.ImmutableSetMultimap;
import cpw.mods.fml.common.Side;
import cpw.mods.fml.common.asm.SideOnly;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.WorldSpecificSaveHandler;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.EntityEvent$CanUpdate;

public abstract class World implements IBlockAccess {

	public static double MAX_ENTITY_RADIUS = 2.0D;
	public final WorldMapCollection perWorldStorage;
	public boolean d = false;
	public List entityList = new TList();
	protected List f = new TList();
	public List tileEntityList = new TList();
	private List a = new TList();
	private List b = new TList();
	public List players = new TList();
	public List i = new TList();
	private long c = 16777215L;
	public int j = 0;
	protected int k = (new Random()).nextInt();
	protected final int l = 1013904223;
	public float m;
	public float n;
	public float o;
	public float p;
	protected int q = 0;
	public int r = 0;
	public boolean suppressPhysics = false;
	public int difficulty;
	public Random random = new Random();
	public WorldProvider worldProvider; // CraftBukkit - remove final
	protected List w = new TList();
	public IChunkProvider chunkProvider; // CraftBukkit - protected -> public
	protected final IDataManager dataManager;
	public WorldData worldData; // CraftBukkit - protected -> public
	public boolean isLoading;
	public WorldMapCollection worldMaps;
	public VillageCollection villages;
	protected final VillageSiege siegeManager = new VillageSiege(this);
	public final MethodProfiler methodProfiler;
	private final Vec3DPool K = new Vec3DPool(300, 2000);
	private final Calendar L = Calendar.getInstance();
	private UnsafeList M = new UnsafeList(); // CraftBukkit - ArrayList -> UnsafeList
	private boolean N;
	// CraftBukkit start - public, longhashset
	public boolean allowMonsters = true;
	public boolean allowAnimals = true;
	public LongHashSet chunkTickList = new LongHashSet();
	public long ticksPerAnimalSpawns;
	public long ticksPerMonsterSpawns;
	// CraftBukkit end
	private int O;
	int[] I;
	private List P;
	public boolean isStatic;
	private static WorldMapCollection s_mapStorage;
	private static IDataManager s_savehandler;
	
	// Alpha's stuff
	public ArrayList<BlockChangeHandler> blockChangeHandlers = new ArrayList<BlockChangeHandler>();
	public ArrayList<EntityTickHandler> entityTickHandlers = new ArrayList<EntityTickHandler>();
	
    private void handleBlockUpdate(int x, int y, int z, int type, int data)
    {
    	for(BlockChangeHandler eh : blockChangeHandlers)
    		eh.blockChanged(this, x, y, z, type, data);
    }
    
    private boolean handleTickStart()
    {
    	boolean ret = true;
    	for(EntityTickHandler th : entityTickHandlers)
    		if (!th.tickStart(this))
    			ret = false;
    	
    	return ret;
    }
    
    private boolean handleLivingTickStart(Entity e)
    {
    	boolean ret = true;
    	for(EntityTickHandler th : entityTickHandlers)
    		if (!th.livingEntityTickStart(this, e))
    			ret = false;
    	
    	return ret;
    }
    private boolean handleTileTickStart(TileEntity e)
    {
    	boolean ret = true;
    	for(EntityTickHandler th : entityTickHandlers)
    		if (!th.tileEntityTickStart(this, e))
    			ret = false;
    	
    	return ret;
    }
    
    private void handleTickEnd()
    {
    	for(EntityTickHandler th : entityTickHandlers)
    		th.tickEnd(this);
    }
    
    private void handleLivingTickEnd(Entity e)
    {
    	for(EntityTickHandler th : entityTickHandlers)
    		th.livingEntityTickEnd(this, e);
    }
    
    private void handleTileTickEnd(TileEntity e)
    {
    	for(EntityTickHandler th : entityTickHandlers)
    		th.tileEntityTickEnd(this, e);
    }
	// end alpha's methods

	public BiomeBase getBiome(int i, int j) 
	{
		return this.worldProvider.getBiomeGenForCoords(i, j);
	}

	public BiomeBase getBiomeGenForCoordsBody(int i, int j)
	{
		if (this.isLoaded(i, 0, j)) {
			Chunk chunk = this.getChunkAtWorldCoords(i, j);

			if (chunk != null) {
				return chunk.a(i & 15, j & 15, this.worldProvider.d);
			}
		}

		return this.worldProvider.d.a(i, j);
	}

	public WorldChunkManager getWorldChunkManager() {
		return this.worldProvider.d;
	}

	// CraftBukkit start
	private final CraftWorld world;
	public boolean pvpMode;
	public boolean keepSpawnInMemory = true;
	public ChunkGenerator generator;
	Chunk lastChunkAccessed;
	int lastXAccessed = Integer.MIN_VALUE;
	int lastZAccessed = Integer.MIN_VALUE;
	final Object chunkLock = new Object();

	public CraftWorld getWorld() {
		return this.world;
	}

	public CraftServer getServer() {
		return (CraftServer) Bukkit.getServer();
	}

	// Changed signature
	public World(IDataManager idatamanager, String s, WorldSettings worldsettings, WorldProvider worldprovider, MethodProfiler methodprofiler, ChunkGenerator gen, org.bukkit.World.Environment env) {
		this.generator = gen;
		this.world = new CraftWorld((WorldServer) this, gen, env);
		this.ticksPerAnimalSpawns = this.getServer().getTicksPerAnimalSpawns(); // CraftBukkit
		this.ticksPerMonsterSpawns = this.getServer().getTicksPerMonsterSpawns(); // CraftBukkit
		// CraftBukkit end

		this.O = this.random.nextInt(12000);
		this.I = new int['\u8000'];
		this.P = new UnsafeList(); // CraftBukkit - ArrayList -> UnsafeList
		this.isStatic = false;
		this.dataManager = idatamanager;
		this.methodProfiler = methodprofiler;
		this.worldMaps = this.getMapStorage(idatamanager);
		this.worldData = idatamanager.getWorldData();
		if (worldprovider != null) {
			this.worldProvider = worldprovider;
		} else if (this.worldData != null && this.worldData.j() != 0) {
			this.worldProvider = WorldProvider.byDimension(this.worldData.j());
		} else {
			this.worldProvider = WorldProvider.byDimension(0);
		}

		if (this.worldData == null) {
			this.worldData = new WorldData(worldsettings, s);
		} else {
			this.worldData.setName(s);
		}

		this.worldProvider.a(this);
		this.chunkProvider = this.j();
		if (!this.worldData.isInitialized()) {
			this.a(worldsettings);
			this.worldData.d(true);
		}

        this.perWorldStorage = new WorldMapCollection(new WorldSpecificSaveHandler((WorldServer)this, idatamanager));
        VillageCollection var6 = (VillageCollection)this.perWorldStorage.get(VillageCollection.class, "villages");

		if (var6 == null) {
			this.villages = new VillageCollection(this);
			this.perWorldStorage.a("villages", this.villages);
		} else {
			this.villages = var6;
			this.villages.a(this);
		}

		this.w();
		this.a();

		this.getServer().addWorld(this.world); // CraftBukkit
	}
	
    private WorldMapCollection getMapStorage(IDataManager var1)
    {
        if (s_savehandler != var1 || s_mapStorage == null)
        {
            s_mapStorage = new WorldMapCollection(var1);
            s_savehandler = var1;
        }

        return s_mapStorage;
    }

	protected abstract IChunkProvider j();

	protected void a(WorldSettings worldsettings) {
		this.worldData.d(true);
	}

	public int b(int i, int j) {
		int k;

		for (k = 63; !this.isEmpty(i, k + 1, j); ++k) {
			;
		}

		return this.getTypeId(i, k, j);
	}

	public int getTypeId(int i, int j, int k) {
		return i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000 ? (j < 0 ? 0 : (j >= 256 ? 0 : this.getChunkAt(i >> 4, k >> 4).getTypeId(i & 15, j, k & 15))) : 0;
	}

	public int b(int i, int j, int k) {
		return i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000 ? (j < 0 ? 0 : (j >= 256 ? 0 : this.getChunkAt(i >> 4, k >> 4).b(i & 15, j, k & 15))) : 0;
	}
	
    public boolean isEmpty(int i, int j, int k)
    {
        int var4 = this.getTypeId(i, j, k);
        return var4 == 0 || Block.byId[var4] == null || Block.byId[var4].isAirBlock(this, i, j, k);
    }

    public boolean isTileEntity(int var1, int var2, int var3)
    {
        int var4 = this.getTypeId(var1, var2, var3);
        int var5 = this.getData(var1, var2, var3);
        return Block.byId[var4] != null && Block.byId[var4].hasTileEntity(var5);
    }

	public boolean isLoaded(int i, int j, int k) {
		return j >= 0 && j < 256 ? this.isChunkLoaded(i >> 4, k >> 4) : false;
	}

	public boolean areChunksLoaded(int i, int j, int k, int l) {
		return this.d(i - l, j - l, k - l, i + l, j + l, k + l);
	}

	public boolean d(int i, int j, int k, int l, int i1, int j1) {
		if (i1 >= 0 && j < 256) {
			i >>= 4;
		k >>= 4;
		l >>= 4;
		j1 >>= 4;

		for (int k1 = i; k1 <= l; ++k1) {
			for (int l1 = k; l1 <= j1; ++l1) {
				// CraftBukkit - check unload queue too so we don't leak a chunk
				if (!this.isChunkLoaded(k1, l1) || ((WorldServer) this).chunkProviderServer.unloadQueue.contains(k1, l1)) {
					return false;
				}
			}
		}

		return true;
		} else {
			return false;
		}
	}

	protected boolean isChunkLoaded(int i, int j) {
		return this.chunkProvider.isChunkLoaded(i, j);
	}

	public Chunk getChunkAtWorldCoords(int i, int j) {
		return this.getChunkAt(i >> 4, j >> 4);
	}

	// CraftBukkit start
	public Chunk getChunkAt(int i, int j) {
		Chunk result = null;
		synchronized (this.chunkLock) {
			if (this.lastChunkAccessed == null || this.lastXAccessed != i || this.lastZAccessed != j) {
				this.lastChunkAccessed = this.chunkProvider.getOrCreateChunk(i, j);
				this.lastXAccessed = i;
				this.lastZAccessed = j;
			}
			result = this.lastChunkAccessed;
		}
		return result;
	}
	// CraftBukkit end

	public boolean setRawTypeIdAndData(int i, int j, int k, int l, int i1) {
		return this.setRawTypeIdAndData(i, j, k, l, i1, true);
	}

	public boolean setRawTypeIdAndData(int i, int j, int k, int l, int i1, boolean flag) {
		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			if (j < 0) {
				return false;
			} else if (j >= 256) {
				return false;
			} else {
				handleBlockUpdate(i, j, k, l, i1);
				
				Chunk chunk = this.getChunkAt(i >> 4, k >> 4);
				boolean flag1 = chunk.a(i & 15, j, k & 15, l, i1);

				this.methodProfiler.a("checkLight");
				this.x(i, j, k);
				this.methodProfiler.b();
				if (flag && flag1 && (this.isStatic || chunk.seenByPlayer)) {
					this.notify(i, j, k);
				}

				return flag1;
			}
		} else {
			return false;
		}
	}

	public boolean setRawTypeId(int i, int j, int k, int l) {
		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			if (j < 0) {
				return false;
			} else if (j >= 256) {
				return false;
			} else {
				handleBlockUpdate(i, j, k, l, 0);
				
				Chunk chunk = this.getChunkAt(i >> 4, k >> 4);
				boolean flag = chunk.a(i & 15, j, k & 15, l);

				this.methodProfiler.a("checkLight");
				this.x(i, j, k);
				this.methodProfiler.b();
				if (flag && (this.isStatic || chunk.seenByPlayer)) {
					this.notify(i, j, k);
				}

				return flag;
			}
		} else {
			return false;
		}
	}

	public Material getMaterial(int i, int j, int k) {
		int l = this.getTypeId(i, j, k);

		return l == 0 ? Material.AIR : Block.byId[l].material;
	}

	public int getData(int i, int j, int k) {
		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			if (j < 0) {
				return 0;
			} else if (j >= 256) {
				return 0;
			} else {
				Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

				i &= 15;
				k &= 15;
				return chunk.getData(i, j, k);
			}
		} else {
			return 0;
		}
	}

	public void setData(int i, int j, int k, int l) {
		if (this.setRawData(i, j, k, l)) {
			this.update(i, j, k, this.getTypeId(i, j, k));
		}
	}

	public boolean setRawData(int i, int j, int k, int l) {
		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			if (j < 0) {
				return false;
			} else if (j >= 256) {
				return false;
			} else {
				Chunk chunk = this.getChunkAt(i >> 4, k >> 4);
				int i1 = i & 15;
				int j1 = k & 15;
				boolean flag = chunk.b(i1, j, j1, l);

				if (flag && (this.isStatic || chunk.seenByPlayer && Block.u[chunk.getTypeId(i1, j, j1) & 4095])) {
					this.notify(i, j, k);
				}

				return flag;
			}
		} else {
			return false;
		}
	}

	public boolean setTypeId(int i, int j, int k, int l) {
		// CraftBukkit start
		int old = this.getTypeId(i, j, k);
		if (this.setRawTypeId(i, j, k, l)) {
			this.update(i, j, k, l == 0 ? old : l);
			// CraftBukkit end
			return true;
		} else {
			return false;
		}
	}

	public boolean setTypeIdAndData(int i, int j, int k, int l, int i1) {
		if (this.setRawTypeIdAndData(i, j, k, l, i1)) {
			this.update(i, j, k, l);
			return true;
		} else {
			return false;
		}
	}

	public void notify(int i, int j, int k) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(i, j, k);
		}
	}

	public void update(int i, int j, int k, int l) {
		this.applyPhysics(i, j, k, l);
	}

	public void g(int i, int j, int k, int l) {
		int i1;

		if (k > l) {
			i1 = l;
			l = k;
			k = i1;
		}

		if (!this.worldProvider.f) {
			for (i1 = k; i1 <= l; ++i1) {
				this.c(EnumSkyBlock.SKY, i, i1, j);
			}
		}

		this.e(i, k, j, i, l, j);
	}

	public void i(int i, int j, int k) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(i, j, k, i, j, k);
		}
	}

	public void e(int i, int j, int k, int l, int i1, int j1) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(i, j, k, l, i1, j1);
		}
	}

	public void applyPhysics(int i, int j, int k, int l) {
		this.m(i - 1, j, k, l);
		this.m(i + 1, j, k, l);
		this.m(i, j - 1, k, l);
		this.m(i, j + 1, k, l);
		this.m(i, j, k - 1, l);
		this.m(i, j, k + 1, l);
	}

	private void m(int i, int j, int k, int l) {
		if (!this.suppressPhysics && !this.isStatic) {
			Block block = Block.byId[this.getTypeId(i, j, k)];

			if (block != null) {
				// CraftBukkit start
				CraftWorld world = ((WorldServer) this).getWorld();
				if (world != null) {
					BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(i, j, k), l);
					this.getServer().getPluginManager().callEvent(event);

					if (event.isCancelled()) {
						return;
					}
				}
				// CraftBukkit end

				block.doPhysics(this, i, j, k, l);
			}
		}
	}

	public boolean j(int i, int j, int k) {
		return this.getChunkAt(i >> 4, k >> 4).d(i & 15, j, k & 15);
	}

	public int k(int i, int j, int k) {
		if (j < 0) {
			return 0;
		} else {
			if (j >= 256) {
				j = 255;
			}

			return this.getChunkAt(i >> 4, k >> 4).c(i & 15, j, k & 15, 0);
		}
	}

	public int getLightLevel(int i, int j, int k) {
		return this.a(i, j, k, true);
	}

	public int a(int i, int j, int k, boolean flag) {
		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			if (flag) {
				int l = this.getTypeId(i, j, k);

				if (l == Block.STEP.id || l == Block.WOOD_STEP.id || l == Block.SOIL.id || l == Block.COBBLESTONE_STAIRS.id || l == Block.WOOD_STAIRS.id) {
					int i1 = this.a(i, j + 1, k, false);
					int j1 = this.a(i + 1, j, k, false);
					int k1 = this.a(i - 1, j, k, false);
					int l1 = this.a(i, j, k + 1, false);
					int i2 = this.a(i, j, k - 1, false);

					if (j1 > i1) {
						i1 = j1;
					}

					if (k1 > i1) {
						i1 = k1;
					}

					if (l1 > i1) {
						i1 = l1;
					}

					if (i2 > i1) {
						i1 = i2;
					}

					return i1;
				}
			}

			if (j < 0) {
				return 0;
			} else {
				if (j >= 256) {
					j = 255;
				}

				Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

				i &= 15;
				k &= 15;
				return chunk.c(i, j, k, this.j);
			}
		} else {
			return 15;
		}
	}

	public int getHighestBlockYAt(int i, int j) {
		if (i >= -30000000 && j >= -30000000 && i < 30000000 && j < 30000000) {
			if (!this.isChunkLoaded(i >> 4, j >> 4)) {
				return 0;
			} else {
				Chunk chunk = this.getChunkAt(i >> 4, j >> 4);

				return chunk.b(i & 15, j & 15);
			}
		} else {
			return 0;
		}
	}

	public int g(int i, int j) {
		if (i >= -30000000 && j >= -30000000 && i < 30000000 && j < 30000000) {
			if (!this.isChunkLoaded(i >> 4, j >> 4)) {
				return 0;
			} else {
				Chunk chunk = this.getChunkAt(i >> 4, j >> 4);

				return chunk.p;
			}
		} else {
			return 0;
		}
	}

	public int b(EnumSkyBlock enumskyblock, int i, int j, int k) {
		if (j < 0) {
			j = 0;
		}

		if (j >= 256) {
			j = 255;
		}

		if (i >= -30000000 && k >= -30000000 && i < 30000000 && k < 30000000) {
			int l = i >> 4;
		int i1 = k >> 4;

		if (!this.isChunkLoaded(l, i1)) {
			return enumskyblock.c;
		} else {
			Chunk chunk = this.getChunkAt(l, i1);

			return chunk.getBrightness(enumskyblock, i & 15, j, k & 15);
		}
		} else {
			return enumskyblock.c;
		}
	}

    public void b(EnumSkyBlock var1, int var2, int var3, int var4, int var5)
    {
        if (var2 >= -30000000 && var4 >= -30000000 && var2 < 30000000 && var4 < 30000000 && var3 >= 0 && var3 < 256 && this.isChunkLoaded(var2 >> 4, var4 >> 4))
        {
            Chunk var6 = this.getChunkAt(var2 >> 4, var4 >> 4);
            var6.a(var1, var2 & 15, var3, var4 & 15, var5);
            Iterator var7 = this.w.iterator();

            while (var7.hasNext())
            {
                IWorldAccess var8 = (IWorldAccess)var7.next();
                var8.b(var2, var3, var4);
            }
        }
    }

	public void n(int i, int j, int k) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.b(i, j, k);
		}
	}

	public float o(int i, int j, int k) {
		return this.worldProvider.g[this.getLightLevel(i, j, k)];
	}

	public boolean t() {
		return this.worldProvider.isDaytime();
	}

	public MovingObjectPosition a(Vec3D vec3d, Vec3D vec3d1) {
		return this.rayTrace(vec3d, vec3d1, false, false);
	}

	public MovingObjectPosition rayTrace(Vec3D vec3d, Vec3D vec3d1, boolean flag) {
		return this.rayTrace(vec3d, vec3d1, flag, false);
	}

	public MovingObjectPosition rayTrace(Vec3D vec3d, Vec3D vec3d1, boolean flag, boolean flag1) {
		if (!Double.isNaN(vec3d.c) && !Double.isNaN(vec3d.d) && !Double.isNaN(vec3d.e)) {
			if (!Double.isNaN(vec3d1.c) && !Double.isNaN(vec3d1.d) && !Double.isNaN(vec3d1.e)) {
				int i = MathHelper.floor(vec3d1.c);
				int j = MathHelper.floor(vec3d1.d);
				int k = MathHelper.floor(vec3d1.e);
				int l = MathHelper.floor(vec3d.c);
				int i1 = MathHelper.floor(vec3d.d);
				int j1 = MathHelper.floor(vec3d.e);
				int k1 = this.getTypeId(l, i1, j1);
				int l1 = this.getData(l, i1, j1);
				Block block = Block.byId[k1];

				if (block != null && (!flag1 || block == null || block.e(this, l, i1, j1) != null) && k1 > 0 && block.a(l1, flag)) {
					MovingObjectPosition movingobjectposition = block.a(this, l, i1, j1, vec3d, vec3d1);

					if (movingobjectposition != null) {
						return movingobjectposition;
					}
				}

				k1 = 200;

				while (k1-- >= 0) {
					if (Double.isNaN(vec3d.c) || Double.isNaN(vec3d.d) || Double.isNaN(vec3d.e)) {
						return null;
					}

					if (l == i && i1 == j && j1 == k) {
						return null;
					}

					boolean flag2 = true;
					boolean flag3 = true;
					boolean flag4 = true;
					double d0 = 999.0D;
					double d1 = 999.0D;
					double d2 = 999.0D;

					if (i > l) {
						d0 = (double) l + 1.0D;
					} else if (i < l) {
						d0 = (double) l + 0.0D;
					} else {
						flag2 = false;
					}

					if (j > i1) {
						d1 = (double) i1 + 1.0D;
					} else if (j < i1) {
						d1 = (double) i1 + 0.0D;
					} else {
						flag3 = false;
					}

					if (k > j1) {
						d2 = (double) j1 + 1.0D;
					} else if (k < j1) {
						d2 = (double) j1 + 0.0D;
					} else {
						flag4 = false;
					}

					double d3 = 999.0D;
					double d4 = 999.0D;
					double d5 = 999.0D;
					double d6 = vec3d1.c - vec3d.c;
					double d7 = vec3d1.d - vec3d.d;
					double d8 = vec3d1.e - vec3d.e;

					if (flag2) {
						d3 = (d0 - vec3d.c) / d6;
					}

					if (flag3) {
						d4 = (d1 - vec3d.d) / d7;
					}

					if (flag4) {
						d5 = (d2 - vec3d.e) / d8;
					}

					boolean flag5 = false;
					byte b0;

					if (d3 < d4 && d3 < d5) {
						if (i > l) {
							b0 = 4;
						} else {
							b0 = 5;
						}

						vec3d.c = d0;
						vec3d.d += d7 * d3;
						vec3d.e += d8 * d3;
					} else if (d4 < d5) {
						if (j > i1) {
							b0 = 0;
						} else {
							b0 = 1;
						}

						vec3d.c += d6 * d4;
						vec3d.d = d1;
						vec3d.e += d8 * d4;
					} else {
						if (k > j1) {
							b0 = 2;
						} else {
							b0 = 3;
						}

						vec3d.c += d6 * d5;
						vec3d.d += d7 * d5;
						vec3d.e = d2;
					}

					Vec3D vec3d2 = this.getVec3DPool().create(vec3d.c, vec3d.d, vec3d.e);

					l = (int) (vec3d2.c = (double) MathHelper.floor(vec3d.c));
					if (b0 == 5) {
						--l;
						++vec3d2.c;
					}

					i1 = (int) (vec3d2.d = (double) MathHelper.floor(vec3d.d));
					if (b0 == 1) {
						--i1;
						++vec3d2.d;
					}

					j1 = (int) (vec3d2.e = (double) MathHelper.floor(vec3d.e));
					if (b0 == 3) {
						--j1;
						++vec3d2.e;
					}

					int i2 = this.getTypeId(l, i1, j1);
					int j2 = this.getData(l, i1, j1);
					Block block1 = Block.byId[i2];

					if ((!flag1 || block1 == null || block1.e(this, l, i1, j1) != null) && i2 > 0 && block1.a(j2, flag)) {
						MovingObjectPosition movingobjectposition1 = block1.a(this, l, i1, j1, vec3d, vec3d1);

						if (movingobjectposition1 != null) {
							vec3d2.b.release(vec3d2); // CraftBukkit
							return movingobjectposition1;
						}
					}
					vec3d2.b.release(vec3d2); // CraftBukkit
				}

				return null;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

    /**
     * Plays a sound at the entity's position. Args: entity, sound, volume (relative to 1.0), and frequency (or pitch,
     * also relative to 1.0).
     */
    public void makeSound(Entity var1, String var2, float var3, float var4)
    {
        PlaySoundAtEntityEvent var5 = new PlaySoundAtEntityEvent(var1, var2, var3, var4);

        if (!MinecraftForge.EVENT_BUS.post(var5))
        {
            var2 = var5.name;

            if (var1 != null && var2 != null)
            {
                Iterator var6 = this.w.iterator();

                while (var6.hasNext())
                {
                    IWorldAccess var7 = (IWorldAccess)var6.next();
                    var7.a(var2, var1.locX, var1.locY - (double)var1.height, var1.locZ, var3, var4);
                }
            }
        }
    }

	public void makeSound(double d0, double d1, double d2, String s, float f, float f1) {
		if (s != null) {
			Iterator iterator = this.w.iterator();

			while (iterator.hasNext()) {
				IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

				iworldaccess.a(s, d0, d1, d2, f, f1);
			}
		}
	}

	public void b(double d0, double d1, double d2, String s, float f, float f1) {}

	public void a(String s, int i, int j, int k) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(s, i, j, k);
		}
	}

	public void addParticle(String s, double d0, double d1, double d2, double d3, double d4, double d5) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(s, d0, d1, d2, d3, d4, d5);
		}
	}

	public boolean strikeLightning(Entity entity) {
		this.i.add(entity);
		return true;
	}

	// CraftBukkit start - used for entities other than creatures
	public boolean addEntity(Entity entity) {
		return this.addEntity(entity, SpawnReason.DEFAULT); // Set reason as DEFAULT
	}

	public boolean addEntity(Entity entity, SpawnReason spawnReason) { // Changed signature, added SpawnReason
		if (entity == null) return false;
	// CraftBukkit end

	int i = MathHelper.floor(entity.locX / 16.0D);
	int j = MathHelper.floor(entity.locZ / 16.0D);
	boolean flag = false;

	if (entity instanceof EntityHuman) {
		flag = true;
	}

	// CraftBukkit start
	org.bukkit.event.Cancellable event = null;
	if (entity instanceof EntityLiving && !(entity instanceof EntityPlayer)) {
		boolean isAnimal = entity instanceof EntityAnimal || entity instanceof EntityWaterAnimal || entity instanceof EntityGolem;
		boolean isMonster = entity instanceof EntityMonster || entity instanceof EntityGhast || entity instanceof EntitySlime;

		if (spawnReason != SpawnReason.CUSTOM) {
			if (isAnimal && !allowAnimals || isMonster && !allowMonsters)  {
				entity.dead = true;
				return false;
			}
		}

		event = CraftEventFactory.callCreatureSpawnEvent((EntityLiving) entity, spawnReason);
	} else if (entity instanceof EntityItem) {
		event = CraftEventFactory.callItemSpawnEvent((EntityItem) entity);
	} else if (entity.getBukkitEntity() instanceof org.bukkit.entity.Projectile) {
		// Not all projectiles extend EntityProjectile, so check for Bukkit interface instead
		event = CraftEventFactory.callProjectileLaunchEvent(entity);
	}

	if (event != null && (event.isCancelled() || entity.dead)) {
		entity.dead = true;
		return false;
	}
	// CraftBukkit end

	if (!flag && !this.isChunkLoaded(i, j)) {
		entity.dead = true; // CraftBukkit
		return false;
	} else {
		if (entity instanceof EntityHuman) {
			EntityHuman entityhuman = (EntityHuman) entity;

			this.players.add(entityhuman);
			this.everyoneSleeping();
		}

        if (!flag && MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, this)))
        {
            return false;
        }
        else
        {
            this.getChunkAt(i, j).a(entity);
            this.entityList.add(entity);
            this.a(entity);
            return true;
        }
	}
	}

	protected void a(Entity entity) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.a(entity);
		}

		entity.valid = true; // CraftBukkit
	}

	protected void b(Entity entity) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.b(entity);
		}

		entity.valid = false; // CraftBukkit
	}

	public void kill(Entity entity) {
		if (entity.passenger != null) {
			entity.passenger.mount((Entity) null);
		}

		if (entity.vehicle != null) {
			entity.mount((Entity) null);
		}

		entity.die();
		if (entity instanceof EntityHuman) {
			this.players.remove(entity);
			this.everyoneSleeping();
		}
	}

	public void removeEntity(Entity entity) {
		entity.die();
		if (entity instanceof EntityHuman) {
			this.players.remove(entity);
			this.everyoneSleeping();
		}

		int i = entity.ai;
		int j = entity.ak;

		if (entity.ah && this.isChunkLoaded(i, j)) {
			this.getChunkAt(i, j).b(entity);
		}

		this.entityList.remove(entity);
		this.b(entity);
	}

	public void addIWorldAccess(IWorldAccess iworldaccess) {
		this.w.add(iworldaccess);
	}

	public List getCubes(Entity entity, AxisAlignedBB axisalignedbb) {
		this.M.clear();
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = i1; l1 < j1; ++l1) {
				if (this.isLoaded(k1, 64, l1)) {
					for (int i2 = k - 1; i2 < l; ++i2) {
						Block block = Block.byId[this.getTypeId(k1, i2, l1)];

						if (block != null) {
							block.a(this, k1, i2, l1, axisalignedbb, this.M, entity);
						}
					}
				}
			}
		}

		double d0 = 0.25D;
		List list = this.getEntities(entity, axisalignedbb.grow(d0, d0, d0));
		Iterator iterator = list.iterator();

		while (iterator.hasNext()) {
			Entity entity1 = (Entity) iterator.next();
			AxisAlignedBB axisalignedbb1 = entity1.E();

			if (axisalignedbb1 != null && axisalignedbb1.a(axisalignedbb)) {
				this.M.add(axisalignedbb1);
			}

			axisalignedbb1 = entity.g(entity1);
			if (axisalignedbb1 != null && axisalignedbb1.a(axisalignedbb)) {
				this.M.add(axisalignedbb1);
			}
		}

		return this.M;
	}

	public List a(AxisAlignedBB axisalignedbb) {
		this.M.clear();
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = i1; l1 < j1; ++l1) {
				if (this.isLoaded(k1, 64, l1)) {
					for (int i2 = k - 1; i2 < l; ++i2) {
						Block block = Block.byId[this.getTypeId(k1, i2, l1)];

						if (block != null) {
							block.a(this, k1, i2, l1, axisalignedbb, this.M, (Entity) null);
						}
					}
				}
			}
		}

		return this.M;
	}

	public int a(float f) {
		float f1 = this.c(f);
		float f2 = 1.0F - (MathHelper.cos(f1 * 3.1415927F * 2.0F) * 2.0F + 0.5F);

		if (f2 < 0.0F) {
			f2 = 0.0F;
		}

		if (f2 > 1.0F) {
			f2 = 1.0F;
		}

		f2 = 1.0F - f2;
		f2 = (float) ((double) f2 * (1.0D - (double) (this.j(f) * 5.0F) / 16.0D));
		f2 = (float) ((double) f2 * (1.0D - (double) (this.i(f) * 5.0F) / 16.0D));
		f2 = 1.0F - f2;
		return (int) (f2 * 11.0F);
	}

	public float c(float f) {
		return this.worldProvider.a(this.worldData.g(), f);
	}

	public int h(int i, int j) {
		return this.getChunkAtWorldCoords(i, j).d(i & 15, j & 15);
	}

	public int i(int i, int j) {
		Chunk chunk = this.getChunkAtWorldCoords(i, j);
		int k = chunk.h() + 15;

		i &= 15;

		for (j &= 15; k > 0; --k) {
			int l = chunk.getTypeId(i, k, j);

			if (l != 0 && Block.byId[l].material.isSolid() && Block.byId[l].material != Material.LEAVES && !Block.byId[l].isBlockFoliage(this, i, k, j)) {
				return k + 1;
			}
		}

		return -1;
	}

	public void a(int i, int j, int k, int l, int i1) {}

	public void a(int i, int j, int k, int l, int i1, int j1) {}

	public void b(int i, int j, int k, int l, int i1) {}

	public void tickEntities() {
		
		if (!handleTickStart())
			return;
		
		this.methodProfiler.a("entities");
		this.methodProfiler.a("global");

		int i;
		Entity entity;

		for (i = 0; i < this.i.size(); ++i) {
			entity = (Entity) this.i.get(i);
			// CraftBukkit start - fixed an NPE, don't process entities in chunks queued for unload
			if (entity == null) {
				continue;
			}

			ChunkProviderServer chunkProviderServer = ((WorldServer) this).chunkProviderServer;
			if (chunkProviderServer.unloadQueue.contains(MathHelper.floor(entity.locX) >> 4, MathHelper.floor(entity.locZ) >> 4)) {
				continue;
			}
			// CraftBukkit end

			entity.j_();
			if (entity.dead) {
				this.i.remove(i--);
			}
		}

		this.methodProfiler.c("remove");
		this.entityList.removeAll(this.f);
		Iterator iterator = this.f.iterator();

		int j;
		int k;

		while (iterator.hasNext()) {
			entity = (Entity) iterator.next();
			j = entity.ai;
			k = entity.ak;
			if (entity.ah && this.isChunkLoaded(j, k)) {
				this.getChunkAt(j, k).b(entity);
			}
		}

		iterator = this.f.iterator();

		while (iterator.hasNext()) {
			entity = (Entity) iterator.next();
			this.b(entity);
		}

		this.f.clear();
		this.methodProfiler.c("regular");

		for (i = 0; i < this.entityList.size(); ++i) {
			entity = (Entity) this.entityList.get(i);

			// CraftBukkit start - don't tick entities in chunks queued for unload
			ChunkProviderServer chunkProviderServer = ((WorldServer) this).chunkProviderServer;
			if (chunkProviderServer.unloadQueue.contains(MathHelper.floor(entity.locX) >> 4, MathHelper.floor(entity.locZ) >> 4)) {
				continue;
			}
			// CraftBukkit end

			if (entity.vehicle != null) {
				if (!entity.vehicle.dead && entity.vehicle.passenger == entity) {
					continue;
				}

				entity.vehicle.passenger = null;
				entity.vehicle = null;
			}

			this.methodProfiler.a("tick");
			if (!entity.dead) {
				this.playerJoinedWorld(entity);
			}

			this.methodProfiler.b();
			this.methodProfiler.a("remove");
			if (entity.dead) {
				j = entity.ai;
				k = entity.ak;
				if (entity.ah && this.isChunkLoaded(j, k)) {
					this.getChunkAt(j, k).b(entity);
				}

				this.entityList.remove(i--);
				this.b(entity);
			}

			this.methodProfiler.b();
		}

		this.methodProfiler.c("tileEntities");
		this.N = true;
		iterator = this.tileEntityList.iterator();

		while (iterator.hasNext()) {
			TileEntity tileentity = (TileEntity) iterator.next();
			// CraftBukkit start - don't tick entities in chunks queued for unload
			ChunkProviderServer chunkProviderServer = ((WorldServer) this).chunkProviderServer;
			if (chunkProviderServer.unloadQueue.contains(tileentity.x >> 4, tileentity.z >> 4)) {
				continue;
			}
			// CraftBukkit end

			if (!tileentity.r() && tileentity.o() && this.isLoaded(tileentity.x, tileentity.y, tileentity.z)) {
				
				if (handleTileTickStart(tileentity))
					tileentity.g();
				
				handleTileTickEnd(tileentity);
			}

			if (tileentity.r()) {
				iterator.remove();
				if (this.isChunkLoaded(tileentity.x >> 4, tileentity.z >> 4)) {
					Chunk chunk = this.getChunkAt(tileentity.x >> 4, tileentity.z >> 4);

					if (chunk != null) {
						chunk.cleanChunkBlockTileEntity(tileentity.x & 15, tileentity.y, tileentity.z & 15);
					}
				}
			}
		}

		this.N = false;
		if (!this.b.isEmpty()) {
			
			Iterator var9 = this.b.iterator();

            while (var9.hasNext())
            {
                Object var10 = var9.next();
                ((TileEntity)var10).onChunkUnload();
            }
            
			this.tileEntityList.removeAll(this.b);
			this.b.clear();
		}

		this.methodProfiler.c("pendingTileEntities");
		if (!this.a.isEmpty()) {
			Iterator iterator1 = this.a.iterator();

			while (iterator1.hasNext()) {
				TileEntity tileentity1 = (TileEntity) iterator1.next();

				if (!tileentity1.r()) {
					/* CraftBukkit start - order matters, moved down
                    if (!this.tileEntityList.contains(tileentity1)) {
                        this.tileEntityList.add(tileentity1);
                    }
                    // CraftBukkit end */

					if (this.isChunkLoaded(tileentity1.x >> 4, tileentity1.z >> 4)) {
						Chunk chunk1 = this.getChunkAt(tileentity1.x >> 4, tileentity1.z >> 4);

						if (chunk1 != null) {
							chunk1.a(tileentity1.x & 15, tileentity1.y, tileentity1.z & 15, tileentity1);
							// CraftBukkit start - moved down from above
							if (!this.tileEntityList.contains(tileentity1)) {
								this.tileEntityList.add(tileentity1);
							}
							// CraftBukkit end
						}
					}

					//this.notify(tileentity1.x, tileentity1.y, tileentity1.z);
				}
			}

			this.a.clear();
		}

		this.methodProfiler.b();
		this.methodProfiler.b();
		
		handleTickEnd();
	}

    public void a(Collection var1)
    {
        List var2 = this.N ? this.a : this.tileEntityList;
        Iterator var3 = var1.iterator();

        while (var3.hasNext())
        {
            Object var4 = var3.next();

            if (((TileEntity)var4).canUpdate())
            {
                var2.add(var4);
            }
        }
    }

	public void playerJoinedWorld(Entity entity) {
		this.entityJoinedWorld(entity, true);
	}

    /**
     * Will update the entity in the world if the chunk the entity is in is currently loaded or its forced to update.
     * Args: entity, forceUpdate
     */
    public void entityJoinedWorld(Entity var1, boolean var2)
    {
        int var3 = MathHelper.floor(var1.locX);
        int var4 = MathHelper.floor(var1.locZ);
        boolean var5 = this.getPersistentChunks().containsKey(new ChunkCoordIntPair(var3 >> 4, var4 >> 4));
        int var6 = var5 ? 0 : 32;
        boolean var7 = !var2 || this.d(var3 - var6, 0, var4 - var6, var3 + var6, 0, var4 + var6);

        if (!var7)
        {
            EntityEvent$CanUpdate var8 = new EntityEvent$CanUpdate(var1);
            MinecraftForge.EVENT_BUS.post(var8);
            var7 = var8.canUpdate;
        }

        if (var7)
        {
            var1.T = var1.locX;
            var1.U = var1.locY;
            var1.V = var1.locZ;
            var1.lastYaw = var1.yaw;
            var1.lastPitch = var1.pitch;

            if (var2 && var1.ah)
            {
            	if (handleLivingTickStart(var1))
            	{
	                if (var1.vehicle != null)
	                {
	                    var1.U();
	                }
	                else
	                {
	                    var1.j_();
	                }
            	}
            	handleLivingTickEnd(var1);
            }

            this.methodProfiler.a("chunkCheck");

            if (Double.isNaN(var1.locX) || Double.isInfinite(var1.locX))
            {
                var1.locX = var1.T;
            }

            if (Double.isNaN(var1.locY) || Double.isInfinite(var1.locY))
            {
                var1.locY = var1.U;
            }

            if (Double.isNaN(var1.locZ) || Double.isInfinite(var1.locZ))
            {
                var1.locZ = var1.V;
            }

            if (Double.isNaN((double)var1.pitch) || Double.isInfinite((double)var1.pitch))
            {
                var1.pitch = var1.lastPitch;
            }

            if (Double.isNaN((double)var1.yaw) || Double.isInfinite((double)var1.yaw))
            {
                var1.yaw = var1.lastYaw;
            }

            int var11 = MathHelper.floor(var1.locX / 16.0D);
            int var9 = MathHelper.floor(var1.locY / 16.0D);
            int var10 = MathHelper.floor(var1.locZ / 16.0D);

            if (!var1.ah || var1.ai != var11 || var1.aj != var9 || var1.ak != var10)
            {
                if (var1.ah && this.isChunkLoaded(var1.ai, var1.ak))
                {
                    this.getChunkAt(var1.ai, var1.ak).a(var1, var1.aj);
                }

                if (this.isChunkLoaded(var11, var10))
                {
                    var1.ah = true;
                    this.getChunkAt(var11, var10).a(var1);
                }
                else
                {
                    var1.ah = false;
                }
            }

            this.methodProfiler.b();

            if (var2 && var1.ah && var1.passenger != null)
            {
                if (!var1.passenger.dead && var1.passenger.vehicle == var1)
                {
                    this.playerJoinedWorld(var1.passenger);
                }
                else
                {
                    var1.passenger.vehicle = null;
                    var1.passenger = null;
                }
            }
        }
    }

	public boolean b(AxisAlignedBB axisalignedbb) {
		return this.a(axisalignedbb, (Entity) null);
	}

	public boolean a(AxisAlignedBB axisalignedbb, Entity entity) {
		List list = this.getEntities((Entity) null, axisalignedbb);
		Iterator iterator = list.iterator();

		Entity entity1;

		do {
			if (!iterator.hasNext()) {
				return true;
			}

			entity1 = (Entity) iterator.next();
		} while (entity1.dead || !entity1.m || entity1 == entity);

		return false;
	}

	public boolean c(AxisAlignedBB axisalignedbb) {
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		if (axisalignedbb.a < 0.0D) {
			--i;
		}

		if (axisalignedbb.b < 0.0D) {
			--k;
		}

		if (axisalignedbb.c < 0.0D) {
			--i1;
		}

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = k; l1 < l; ++l1) {
				for (int i2 = i1; i2 < j1; ++i2) {
					Block block = Block.byId[this.getTypeId(k1, l1, i2)];

					if (block != null) {
						return true;
					}
				}
			}
		}

		return false;
	}

	public boolean containsLiquid(AxisAlignedBB axisalignedbb) {
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		if (axisalignedbb.a < 0.0D) {
			--i;
		}

		if (axisalignedbb.b < 0.0D) {
			--k;
		}

		if (axisalignedbb.c < 0.0D) {
			--i1;
		}

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = k; l1 < l; ++l1) {
				for (int i2 = i1; i2 < j1; ++i2) {
					Block block = Block.byId[this.getTypeId(k1, l1, i2)];

					if (block != null && block.material.isLiquid()) {
						return true;
					}
				}
			}
		}

		return false;
	}

    /**
     * Returns whether or not the given bounding box is on fire or not
     */
    public boolean e(AxisAlignedBB var1)
    {
        int var2 = MathHelper.floor(var1.a);
        int var3 = MathHelper.floor(var1.d + 1.0D);
        int var4 = MathHelper.floor(var1.b);
        int var5 = MathHelper.floor(var1.e + 1.0D);
        int var6 = MathHelper.floor(var1.c);
        int var7 = MathHelper.floor(var1.f + 1.0D);

        if (this.d(var2, var4, var6, var3, var5, var7))
        {
            for (int var8 = var2; var8 < var3; ++var8)
            {
                for (int var9 = var4; var9 < var5; ++var9)
                {
                    for (int var10 = var6; var10 < var7; ++var10)
                    {
                        int var11 = this.getTypeId(var8, var9, var10);

                        if (var11 == Block.FIRE.id || var11 == Block.LAVA.id || var11 == Block.STATIONARY_LAVA.id)
                        {
                            return true;
                        }

                        Block var12 = Block.byId[var11];

                        if (var12 != null && var12.isBlockBurning(this, var8, var9, var10))
                        {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

	public boolean a(AxisAlignedBB axisalignedbb, Material material, Entity entity) {
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		if (!this.d(i, k, i1, j, l, j1)) {
			return false;
		} else {
			boolean flag = false;
			Vec3D vec3d = this.getVec3DPool().create(0.0D, 0.0D, 0.0D);

			for (int k1 = i; k1 < j; ++k1) {
				for (int l1 = k; l1 < l; ++l1) {
					for (int i2 = i1; i2 < j1; ++i2) {
						Block block = Block.byId[this.getTypeId(k1, l1, i2)];

						if (block != null && block.material == material) {
							double d0 = (double) ((float) (l1 + 1) - BlockFluids.d(this.getData(k1, l1, i2)));

							if ((double) l >= d0) {
								flag = true;
								block.a(this, k1, l1, i2, entity, vec3d);
							}
						}
					}
				}
			}

			if (vec3d.b() > 0.0D) {
				vec3d = vec3d.a();
				double d1 = 0.014D;

				entity.motX += vec3d.c * d1;
				entity.motY += vec3d.d * d1;
				entity.motZ += vec3d.e * d1;
			}
			vec3d.b.release(vec3d); // CraftBukkit - pop it - we're done

			return flag;
		}
	}

	public boolean a(AxisAlignedBB axisalignedbb, Material material) {
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = k; l1 < l; ++l1) {
				for (int i2 = i1; i2 < j1; ++i2) {
					Block block = Block.byId[this.getTypeId(k1, l1, i2)];

					if (block != null && block.material == material) {
						return true;
					}
				}
			}
		}

		return false;
	}

	public boolean b(AxisAlignedBB axisalignedbb, Material material) {
		int i = MathHelper.floor(axisalignedbb.a);
		int j = MathHelper.floor(axisalignedbb.d + 1.0D);
		int k = MathHelper.floor(axisalignedbb.b);
		int l = MathHelper.floor(axisalignedbb.e + 1.0D);
		int i1 = MathHelper.floor(axisalignedbb.c);
		int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

		for (int k1 = i; k1 < j; ++k1) {
			for (int l1 = k; l1 < l; ++l1) {
				for (int i2 = i1; i2 < j1; ++i2) {
					Block block = Block.byId[this.getTypeId(k1, l1, i2)];

					if (block != null && block.material == material) {
						int j2 = this.getData(k1, l1, i2);
						double d0 = (double) (l1 + 1);

						if (j2 < 8) {
							d0 = (double) (l1 + 1) - (double) j2 / 8.0D;
						}

						if (d0 >= axisalignedbb.b) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	public Explosion explode(Entity entity, double d0, double d1, double d2, float f, boolean flag) {
		return this.createExplosion(entity, d0, d1, d2, f, false, flag);
	}

	public Explosion createExplosion(Entity entity, double d0, double d1, double d2, float f, boolean flag, boolean flag1) {
		Explosion explosion = new Explosion(this, entity, d0, d1, d2, f);

		explosion.a = flag;
		explosion.b = flag1;
		explosion.a();
		explosion.a(true);
		return explosion;
	}

	public float a(Vec3D vec3d, AxisAlignedBB axisalignedbb) {
		double d0 = 1.0D / ((axisalignedbb.d - axisalignedbb.a) * 2.0D + 1.0D);
		double d1 = 1.0D / ((axisalignedbb.e - axisalignedbb.b) * 2.0D + 1.0D);
		double d2 = 1.0D / ((axisalignedbb.f - axisalignedbb.c) * 2.0D + 1.0D);
		int i = 0;
		int j = 0;

		Vec3D vec3d2 = vec3d.b.create(0, 0, 0); // CraftBukkit
		for (float f = 0.0F; f <= 1.0F; f = (float) ((double) f + d0)) {
			for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) ((double) f1 + d1)) {
				for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) ((double) f2 + d2)) {
					double d3 = axisalignedbb.a + (axisalignedbb.d - axisalignedbb.a) * (double) f;
					double d4 = axisalignedbb.b + (axisalignedbb.e - axisalignedbb.b) * (double) f1;
					double d5 = axisalignedbb.c + (axisalignedbb.f - axisalignedbb.c) * (double) f2;

					if (this.a(vec3d2.b(d3, d4, d5), vec3d) == null) { // CraftBukkit
						++i;
					}

					++j;
				}
			}
		}
		vec3d2.b.release(vec3d2); // CraftBukkit

		return (float) i / (float) j;
	}

	public boolean douseFire(EntityHuman entityhuman, int i, int j, int k, int l) {
		if (l == 0) {
			--j;
		}

		if (l == 1) {
			++j;
		}

		if (l == 2) {
			--k;
		}

		if (l == 3) {
			++k;
		}

		if (l == 4) {
			--i;
		}

		if (l == 5) {
			++i;
		}

		if (this.getTypeId(i, j, k) == Block.FIRE.id) {
			this.a(entityhuman, 1004, i, j, k, 0);
			this.setTypeId(i, j, k, 0);
			return true;
		} else {
			return false;
		}
	}

	public TileEntity getTileEntity(int i, int j, int k) {
		if (j >= 256) {
			return null;
		} else {
			Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

			if (chunk == null) {
				return null;
			} else {
				TileEntity tileentity = chunk.e(i & 15, j, k & 15);

				if (tileentity == null) {
					Iterator iterator = this.a.iterator();

					while (iterator.hasNext()) {
						TileEntity tileentity1 = (TileEntity) iterator.next();

						if (!tileentity1.r() && tileentity1.x == i && tileentity1.y == j && tileentity1.z == k) {
							tileentity = tileentity1;
							break;
						}
					}
				}

				return tileentity;
			}
		}
	}
	
    /**
     * Sets the TileEntity for a given block in X, Y, Z coordinates
     */
    public void setTileEntity(int var1, int var2, int var3, TileEntity var4)
    {
        if (var4 != null && !var4.r())
        {
            if (var4.canUpdate())
            {
                List var5 = this.N ? this.a : this.tileEntityList;
                var5.add(var4);
            }

            Chunk var6 = this.getChunkAt(var1 >> 4, var3 >> 4);

            if (var6 != null)
            {
                var6.a(var1 & 15, var2, var3 & 15, var4);
            }
        }
    }

    /**
     * Removes the TileEntity for a given block in X,Y,Z coordinates
     */
    public void q(int var1, int var2, int var3)
    {
        Chunk var4 = this.getChunkAt(var1 >> 4, var3 >> 4);

        if (var4 != null)
        {
            var4.f(var1 & 15, var2, var3 & 15);
        }
    }

	public void a(TileEntity tileentity) {
		this.b.add(tileentity);
	}

	public boolean r(int i, int j, int k) {
		Block block = Block.byId[this.getTypeId(i, j, k)];

		return block == null ? false : block.c();
	}

    /**
     * Returns true if the block at the specified coordinates is an opaque cube. Args: x, y, z
     */
    public boolean s(int var1, int var2, int var3)
    {
        Block var4 = Block.byId[this.getTypeId(var1, var2, var3)];
        return var4 != null && var4.isBlockNormalCube(this, var1, var2, var3);
    }

    /**
     * Returns true if the block at the given coordinate has a solid (buildable) top surface.
     */
    public boolean t(int var1, int var2, int var3)
    {
        return this.isBlockSolidOnSide(var1, var2, var3, ForgeDirection.UP);
    }

    /**
     * Checks if the block is a solid, normal cube. If the chunk does not exist, or is not loaded, it returns the
     * boolean parameter.
     */
    public boolean b(int var1, int var2, int var3, boolean var4)
    {
        if (var1 >= -30000000 && var3 >= -30000000 && var1 < 30000000 && var3 < 30000000)
        {
            Chunk var5 = this.chunkProvider.getOrCreateChunk(var1 >> 4, var3 >> 4);

            if (var5 != null && !var5.isEmpty())
            {
                Block var6 = Block.byId[this.getTypeId(var1, var2, var3)];
                return var6 == null ? false : this.s(var1, var2, var3);
            }
            else
            {
                return var4;
            }
        }
        else
        {
            return var4;
        }
    }

	public void w() {
		int i = this.a(1.0F);

		if (i != this.j) {
			this.j = i;
		}
	}

    /**
     * first boolean for hostile mobs and second for peaceful mobs
     */
    public void setSpawnFlags(boolean var1, boolean var2)
    {
        this.worldProvider.setAllowedSpawnTypes(var1, var2);
    }

	public void doTick() {
		this.n();
	}

	private void a() {
        this.worldProvider.calculateInitialWeather();
    }

    public void calculateInitialWeatherBody()
    {
		if (this.worldData.hasStorm()) {
			this.n = 1.0F;
			if (this.worldData.isThundering()) {
				this.p = 1.0F;
			}
		}
	}

	protected void n() {
        this.worldProvider.updateWeather();
    }

    public void updateWeatherBody()
    {
		if (!this.worldProvider.f) {
			if (this.q > 0) {
				--this.q;
			}

			int i = this.worldData.getThunderDuration();

			if (i <= 0) {
				if (this.worldData.isThundering()) {
					this.worldData.setThunderDuration(this.random.nextInt(12000) + 3600);
				} else {
					this.worldData.setThunderDuration(this.random.nextInt(168000) + 12000);
				}
			} else {
				--i;
				this.worldData.setThunderDuration(i);
				if (i <= 0) {
					// CraftBukkit start
					ThunderChangeEvent thunder = new ThunderChangeEvent(this.getWorld(), !this.worldData.isThundering());
					this.getServer().getPluginManager().callEvent(thunder);
					if (!thunder.isCancelled()) {
						this.worldData.setThundering(!this.worldData.isThundering());
					}
					// CraftBukkit end
				}
			}

			int j = this.worldData.getWeatherDuration();

			if (j <= 0) {
				if (this.worldData.hasStorm()) {
					this.worldData.setWeatherDuration(this.random.nextInt(12000) + 12000);
				} else {
					this.worldData.setWeatherDuration(this.random.nextInt(168000) + 12000);
				}
			} else {
				--j;
				this.worldData.setWeatherDuration(j);
				if (j <= 0) {
					// CraftBukkit start
					WeatherChangeEvent weather = new WeatherChangeEvent(this.getWorld(), !this.worldData.hasStorm());
					this.getServer().getPluginManager().callEvent(weather);

					if (!weather.isCancelled()) {
						this.worldData.setStorm(!this.worldData.hasStorm());
					}
					// CraftBukkit end
				}
			}

			this.m = this.n;
			if (this.worldData.hasStorm()) {
				this.n = (float) ((double) this.n + 0.01D);
			} else {
				this.n = (float) ((double) this.n - 0.01D);
			}

			if (this.n < 0.0F) {
				this.n = 0.0F;
			}

			if (this.n > 1.0F) {
				this.n = 1.0F;
			}

			this.o = this.p;
			if (this.worldData.isThundering()) {
				this.p = (float) ((double) this.p + 0.01D);
			} else {
				this.p = (float) ((double) this.p - 0.01D);
			}

			if (this.p < 0.0F) {
				this.p = 0.0F;
			}

			if (this.p > 1.0F) {
				this.p = 1.0F;
			}
		}
	}

	public void x() {
		this.worldProvider.toggleRain();
	}

	protected void y() {
		// this.chunkTickList.clear(); // CraftBukkit - removed
		//this.chunkTickList.addAll(this.getPersistentChunks().keySet()); - TODO: FIX
		this.methodProfiler.a("buildList");

		int i;
		EntityHuman entityhuman;
		int j;
		int k;

		for (i = 0; i < this.players.size(); ++i) {
			entityhuman = (EntityHuman) this.players.get(i);
			j = MathHelper.floor(entityhuman.locX / 16.0D);
			k = MathHelper.floor(entityhuman.locZ / 16.0D);
			byte b0 = 7;

			for (int l = -b0; l <= b0; ++l) {
				for (int i1 = -b0; i1 <= b0; ++i1) {
					// CraftBukkit start - don't tick chunks queued for unload
					ChunkProviderServer chunkProviderServer = ((WorldServer) entityhuman.world).chunkProviderServer;
					if (chunkProviderServer.unloadQueue.contains(l + j, i1 + k)) {
						continue;
					}
					// CraftBukkit end

					this.chunkTickList.add(org.bukkit.craftbukkit.util.LongHash.toLong(l + j, i1 + k)); // CraftBukkit
				}
			}
		}

		this.methodProfiler.b();
		if (this.O > 0) {
			--this.O;
		}

		this.methodProfiler.a("playerCheckLight");
		if (!this.players.isEmpty()) {
			i = this.random.nextInt(this.players.size());
			entityhuman = (EntityHuman) this.players.get(i);
			j = MathHelper.floor(entityhuman.locX) + this.random.nextInt(11) - 5;
			k = MathHelper.floor(entityhuman.locY) + this.random.nextInt(11) - 5;
			int j1 = MathHelper.floor(entityhuman.locZ) + this.random.nextInt(11) - 5;

			this.x(j, k, j1);
		}

		this.methodProfiler.b();
	}

	protected void a(int i, int j, Chunk chunk) {
		this.methodProfiler.c("moodSound");
		if (this.O == 0 && !this.isStatic) {
			this.k = this.k * 3 + 1013904223;
			int k = this.k >> 2;
			int l = k & 15;
			int i1 = k >> 8 & 15;
				int j1 = k >> 16 & 255; // CraftBukkit - 127 -> 255
				int k1 = chunk.getTypeId(l, j1, i1);

				l += i;
				i1 += j;
				if (k1 == 0 && this.k(l, j1, i1) <= this.random.nextInt(8) && this.b(EnumSkyBlock.SKY, l, j1, i1) <= 0) {
					EntityHuman entityhuman = this.findNearbyPlayer((double) l + 0.5D, (double) j1 + 0.5D, (double) i1 + 0.5D, 8.0D);

					if (entityhuman != null && entityhuman.e((double) l + 0.5D, (double) j1 + 0.5D, (double) i1 + 0.5D) > 4.0D) {
						this.makeSound((double) l + 0.5D, (double) j1 + 0.5D, (double) i1 + 0.5D, "ambient.cave.cave", 0.7F, 0.8F + this.random.nextFloat() * 0.2F);
						this.O = this.random.nextInt(12000) + 6000;
					}
				}
		}

		this.methodProfiler.c("checkLight");
		chunk.o();
	}

	protected void g() {
		this.y();
	}

	public boolean u(int i, int j, int k) {
		return this.c(i, j, k, false);
	}

	public boolean v(int i, int j, int k) {
		return this.c(i, j, k, true);
	}
    /**
     * checks to see if a given block is both water, and cold enough to freeze - if the par4 boolean is set, this will
     * only return true if there is a non-water block immediately adjacent to the specified block
     */
    public boolean c(int var1, int var2, int var3, boolean var4)
    {
        return this.worldProvider.canBlockFreeze(var1, var2, var3, var4);
    }

    public boolean canBlockFreezeBody(int i, int j, int k, boolean flag)
    {
		BiomeBase biomebase = this.getBiome(i, k);
		float f = biomebase.j();

		if (f > 0.15F) {
			return false;
		} else {
			if (j >= 0 && j < 256 && this.b(EnumSkyBlock.BLOCK, i, j, k) < 10) {
				int l = this.getTypeId(i, j, k);

				if ((l == Block.STATIONARY_WATER.id || l == Block.WATER.id) && this.getData(i, j, k) == 0) {
					if (!flag) {
						return true;
					}

					boolean flag1 = true;

					if (flag1 && this.getMaterial(i - 1, j, k) != Material.WATER) {
						flag1 = false;
					}

					if (flag1 && this.getMaterial(i + 1, j, k) != Material.WATER) {
						flag1 = false;
					}

					if (flag1 && this.getMaterial(i, j, k - 1) != Material.WATER) {
						flag1 = false;
					}

					if (flag1 && this.getMaterial(i, j, k + 1) != Material.WATER) {
						flag1 = false;
					}

					if (!flag1) {
						return true;
					}
				}
			}

			return false;
		}
	}
    public boolean w(int var1, int var2, int var3)
    {
        return this.worldProvider.canSnowAt(var1, var2, var3);
    }

    public boolean canSnowAtBody(int i, int j, int k)
    {
		BiomeBase biomebase = this.getBiome(i, k);
		float f = biomebase.j();

		if (f > 0.15F) {
			return false;
		} else {
			if (j >= 0 && j < 256 && this.b(EnumSkyBlock.BLOCK, i, j, k) < 10) {
				int l = this.getTypeId(i, j - 1, k);
				int i1 = this.getTypeId(i, j, k);

				if (i1 == 0 && Block.SNOW.canPlace(this, i, j, k) && l != 0 && l != Block.ICE.id && Block.byId[l].material.isSolid()) {
					return true;
				}
			}

			return false;
		}
	}

	public void x(int i, int j, int k) {
		if (!this.worldProvider.f) {
			this.c(EnumSkyBlock.SKY, i, j, k);
		}

		this.c(EnumSkyBlock.BLOCK, i, j, k);
	}

	private int b(int i, int j, int k, int l, int i1, int j1) {
		int k1 = 0;

		if (this.j(j, k, l)) {
			k1 = 15;
		} else {
			if (j1 == 0) {
				j1 = 1;
			}

			int l1 = this.b(EnumSkyBlock.SKY, j - 1, k, l) - j1;
			int i2 = this.b(EnumSkyBlock.SKY, j + 1, k, l) - j1;
			int j2 = this.b(EnumSkyBlock.SKY, j, k - 1, l) - j1;
			int k2 = this.b(EnumSkyBlock.SKY, j, k + 1, l) - j1;
			int l2 = this.b(EnumSkyBlock.SKY, j, k, l - 1) - j1;
			int i3 = this.b(EnumSkyBlock.SKY, j, k, l + 1) - j1;

			if (l1 > k1) {
				k1 = l1;
			}

			if (i2 > k1) {
				k1 = i2;
			}

			if (j2 > k1) {
				k1 = j2;
			}

			if (k2 > k1) {
				k1 = k2;
			}

			if (l2 > k1) {
				k1 = l2;
			}

			if (i3 > k1) {
				k1 = i3;
			}
		}

		return k1;
	}

	private int g(int i, int j, int k, int l, int i1, int j1) {
		int k1 = i1 != 0 && Block.byId[i1] != null ? Block.byId[i1].getLightValue(this, j, k, l) : 0;
		int l1 = this.b(EnumSkyBlock.BLOCK, j - 1, k, l) - j1;
		int i2 = this.b(EnumSkyBlock.BLOCK, j + 1, k, l) - j1;
		int j2 = this.b(EnumSkyBlock.BLOCK, j, k - 1, l) - j1;
		int k2 = this.b(EnumSkyBlock.BLOCK, j, k + 1, l) - j1;
		int l2 = this.b(EnumSkyBlock.BLOCK, j, k, l - 1) - j1;
		int i3 = this.b(EnumSkyBlock.BLOCK, j, k, l + 1) - j1;

		if (l1 > k1) {
			k1 = l1;
		}

		if (i2 > k1) {
			k1 = i2;
		}

		if (j2 > k1) {
			k1 = j2;
		}

		if (k2 > k1) {
			k1 = k2;
		}

		if (l2 > k1) {
			k1 = l2;
		}

		if (i3 > k1) {
			k1 = i3;
		}

		return k1;
	}

	public void c(EnumSkyBlock enumskyblock, int i, int j, int k) {
		if (this.areChunksLoaded(i, j, k, 17)) {
			int l = 0;
			int i1 = 0;

			this.methodProfiler.a("getBrightness");
			int j1 = this.b(enumskyblock, i, j, k);
			boolean flag = false;
			int k1 = this.getTypeId(i, j, k);
			int l1 = this.b(i, j, k);

			if (l1 == 0) {
				l1 = 1;
			}

			boolean flag1 = false;
			int i2;

			if (enumskyblock == EnumSkyBlock.SKY) {
				i2 = this.b(j1, i, j, k, k1, l1);
			} else {
				i2 = this.g(j1, i, j, k, k1, l1);
			}

			int j2;
			int k2;
			int l2;
			int i3;
			int j3;
			int k3;
			int l3;
			int i4;

			if (i2 > j1) {
				this.I[i1++] = 133152;
			} else if (i2 < j1) {
				if (enumskyblock != EnumSkyBlock.BLOCK) {
					;
				}

				this.I[i1++] = 133152 + (j1 << 18);

				while (l < i1) {
					k1 = this.I[l++];
					l1 = (k1 & 63) - 32 + i;
					i2 = (k1 >> 6 & 63) - 32 + j;
					j2 = (k1 >> 12 & 63) - 32 + k;
					k2 = k1 >> 18 & 15;
					l2 = this.b(enumskyblock, l1, i2, j2);
					if (l2 == k2) {
						this.b(enumskyblock, l1, i2, j2, 0);
						if (k2 > 0) {
							i3 = l1 - i;
							k3 = i2 - j;
							j3 = j2 - k;
							if (i3 < 0) {
								i3 = -i3;
							}

							if (k3 < 0) {
								k3 = -k3;
							}

							if (j3 < 0) {
								j3 = -j3;
							}

							if (i3 + k3 + j3 < 17) {
								for (i4 = 0; i4 < 6; ++i4) {
									l3 = i4 % 2 * 2 - 1;
									int j4 = l1 + i4 / 2 % 3 / 2 * l3;
									int k4 = i2 + (i4 / 2 + 1) % 3 / 2 * l3;
									int l4 = j2 + (i4 / 2 + 2) % 3 / 2 * l3;

									l2 = this.b(enumskyblock, j4, k4, l4);
									int i5 = this.b(j4, k4, l4);

									if (i5 == 0) {
										i5 = 1;
									}

									if (l2 == k2 - i5 && i1 < this.I.length) {
										this.I[i1++] = j4 - i + 32 + (k4 - j + 32 << 6) + (l4 - k + 32 << 12) + (k2 - i5 << 18);
									}
								}
							}
						}
					}
				}

				l = 0;
			}

			this.methodProfiler.b();
			this.methodProfiler.a("checkedPosition < toCheckCount");

			while (l < i1) {
				k1 = this.I[l++];
				l1 = (k1 & 63) - 32 + i;
				i2 = (k1 >> 6 & 63) - 32 + j;
				j2 = (k1 >> 12 & 63) - 32 + k;
				k2 = this.b(enumskyblock, l1, i2, j2);
				l2 = this.getTypeId(l1, i2, j2);
				i3 = this.b(l1, i2, j2);
				if (i3 == 0) {
					i3 = 1;
				}

				boolean flag2 = false;

				if (enumskyblock == EnumSkyBlock.SKY) {
					k3 = this.b(k2, l1, i2, j2, l2, i3);
				} else {
					k3 = this.g(k2, l1, i2, j2, l2, i3);
				}

				if (k3 != k2) {
					this.b(enumskyblock, l1, i2, j2, k3);
					if (k3 > k2) {
						j3 = l1 - i;
						i4 = i2 - j;
						l3 = j2 - k;
						if (j3 < 0) {
							j3 = -j3;
						}

						if (i4 < 0) {
							i4 = -i4;
						}

						if (l3 < 0) {
							l3 = -l3;
						}

						if (j3 + i4 + l3 < 17 && i1 < this.I.length - 6) {
							if (this.b(enumskyblock, l1 - 1, i2, j2) < k3) {
								this.I[i1++] = l1 - 1 - i + 32 + (i2 - j + 32 << 6) + (j2 - k + 32 << 12);
							}

							if (this.b(enumskyblock, l1 + 1, i2, j2) < k3) {
								this.I[i1++] = l1 + 1 - i + 32 + (i2 - j + 32 << 6) + (j2 - k + 32 << 12);
							}

							if (this.b(enumskyblock, l1, i2 - 1, j2) < k3) {
								this.I[i1++] = l1 - i + 32 + (i2 - 1 - j + 32 << 6) + (j2 - k + 32 << 12);
							}

							if (this.b(enumskyblock, l1, i2 + 1, j2) < k3) {
								this.I[i1++] = l1 - i + 32 + (i2 + 1 - j + 32 << 6) + (j2 - k + 32 << 12);
							}

							if (this.b(enumskyblock, l1, i2, j2 - 1) < k3) {
								this.I[i1++] = l1 - i + 32 + (i2 - j + 32 << 6) + (j2 - 1 - k + 32 << 12);
							}

							if (this.b(enumskyblock, l1, i2, j2 + 1) < k3) {
								this.I[i1++] = l1 - i + 32 + (i2 - j + 32 << 6) + (j2 + 1 - k + 32 << 12);
							}
						}
					}
				}
			}

			this.methodProfiler.b();
		}
	}

	public boolean a(boolean flag) {
		return false;
	}

	public List a(Chunk chunk, boolean flag) {
		return null;
	}

	public List getEntities(Entity entity, AxisAlignedBB axisalignedbb) {
		this.P.clear();
		int i = MathHelper.floor((axisalignedbb.a - MAX_ENTITY_RADIUS) / 16.0D);
		int j = MathHelper.floor((axisalignedbb.d + MAX_ENTITY_RADIUS) / 16.0D);
		int k = MathHelper.floor((axisalignedbb.c - MAX_ENTITY_RADIUS) / 16.0D);
		int l = MathHelper.floor((axisalignedbb.f + MAX_ENTITY_RADIUS) / 16.0D);

		for (int i1 = i; i1 <= j; ++i1) {
			for (int j1 = k; j1 <= l; ++j1) {
				if (this.isChunkLoaded(i1, j1)) {
					this.getChunkAt(i1, j1).a(entity, axisalignedbb, this.P);
				}
			}
		}

		return this.P;
	}

	public List a(Class oclass, AxisAlignedBB axisalignedbb) {
		return this.a(oclass, axisalignedbb, (IEntitySelector) null);
	}

	public List a(Class oclass, AxisAlignedBB axisalignedbb, IEntitySelector ientityselector) {
		int i = MathHelper.floor((axisalignedbb.a - MAX_ENTITY_RADIUS) / 16.0D);
		int j = MathHelper.floor((axisalignedbb.d + MAX_ENTITY_RADIUS) / 16.0D);
		int k = MathHelper.floor((axisalignedbb.c - MAX_ENTITY_RADIUS) / 16.0D);
		int l = MathHelper.floor((axisalignedbb.f + MAX_ENTITY_RADIUS) / 16.0D);
		ArrayList arraylist = new ArrayList();

		for (int i1 = i; i1 <= j; ++i1) {
			for (int j1 = k; j1 <= l; ++j1) {
				if (this.isChunkLoaded(i1, j1)) {
					this.getChunkAt(i1, j1).a(oclass, axisalignedbb, arraylist, ientityselector);
				}
			}
		}

		return arraylist;
	}

	public Entity a(Class oclass, AxisAlignedBB axisalignedbb, Entity entity) {
		List list = this.a(oclass, axisalignedbb);
		Entity entity1 = null;
		double d0 = Double.MAX_VALUE;
		Iterator iterator = list.iterator();

		while (iterator.hasNext()) {
			Entity entity2 = (Entity) iterator.next();

			if (entity2 != entity) {
				double d1 = entity.e(entity2);

				if (d1 <= d0) {
					entity1 = entity2;
					d0 = d1;
				}
			}
		}

		return entity1;
	}

	public abstract Entity getEntity(int i);

	public void b(int i, int j, int k, TileEntity tileentity) {
		if (this.isLoaded(i, j, k)) {
			this.getChunkAtWorldCoords(i, k).e();
		}
	}

	public int a(Class oclass) {
		int i = 0;

		for (int j = 0; j < this.entityList.size(); ++j) {
			Entity entity = (Entity) this.entityList.get(j);

			if (oclass.isAssignableFrom(entity.getClass())) {
				++i;
			}
		}

		return i;
	}

	public void a(List list) {
		// CraftBukkit start
		Entity entity = null;
		for (int i = 0; i < list.size(); ++i) {
			entity = (Entity) list.get(i);
			if (entity == null) {
				continue;
			}
            if (!MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, this)))
            {
				this.entityList.add(entity);
				// CraftBukkit end
				this.a(entity);
            }
		}
	}

	public void b(List list) {
		this.f.addAll(list);
	}

	public boolean mayPlace(int i, int j, int k, int l, boolean flag, int i1, Entity entity) {
		int j1 = this.getTypeId(j, k, l);
		Block block = Block.byId[j1];
		Block block1 = Block.byId[i];
		AxisAlignedBB axisalignedbb = block1.e(this, j, k, l);

		if (flag) {
			axisalignedbb = null;
		}

		boolean defaultReturn; // CraftBukkit - store the default action

		if (axisalignedbb != null && !this.a(axisalignedbb, entity)) {
			defaultReturn = false; // CraftBukkit
		} else {
			if (block != null && (block == Block.WATER || block == Block.STATIONARY_WATER || block == Block.LAVA || block == Block.STATIONARY_LAVA || block == Block.FIRE || block.material.isReplaceable())) {
				block = null;
			}
			
            if (block != null && block.isBlockReplaceable(this, j, k, l))
            {
            	block = null;
            }

			// CraftBukkit
			defaultReturn = block != null && block.material == Material.ORIENTABLE && block1 == Block.ANVIL ? true : i > 0 && block == null && block1.canPlace(this, j, k, l, i1);
		}

		// CraftBukkit start
		BlockCanBuildEvent event = new BlockCanBuildEvent(this.getWorld().getBlockAt(j, k, l), i, defaultReturn);
		this.getServer().getPluginManager().callEvent(event);
		
		return event.isBuildable();
		// CraftBukkit end
	}

	public PathEntity findPath(Entity entity, Entity entity1, float f, boolean flag, boolean flag1, boolean flag2, boolean flag3) {
		this.methodProfiler.a("pathfind");
		int i = MathHelper.floor(entity.locX);
		int j = MathHelper.floor(entity.locY + 1.0D);
		int k = MathHelper.floor(entity.locZ);
		int l = (int) (f + 16.0F);
		int i1 = i - l;
		int j1 = j - l;
		int k1 = k - l;
		int l1 = i + l;
		int i2 = j + l;
		int j2 = k + l;
		ChunkCache chunkcache = new ChunkCache(this, i1, j1, k1, l1, i2, j2);
		PathEntity pathentity = (new Pathfinder(chunkcache, flag, flag1, flag2, flag3)).a(entity, entity1, f);

		this.methodProfiler.b();
		return pathentity;
	}

	public PathEntity a(Entity entity, int i, int j, int k, float f, boolean flag, boolean flag1, boolean flag2, boolean flag3) {
		this.methodProfiler.a("pathfind");
		int l = MathHelper.floor(entity.locX);
		int i1 = MathHelper.floor(entity.locY);
		int j1 = MathHelper.floor(entity.locZ);
		int k1 = (int) (f + 8.0F);
		int l1 = l - k1;
		int i2 = i1 - k1;
		int j2 = j1 - k1;
		int k2 = l + k1;
		int l2 = i1 + k1;
		int i3 = j1 + k1;
		ChunkCache chunkcache = new ChunkCache(this, l1, i2, j2, k2, l2, i3);
		PathEntity pathentity = (new Pathfinder(chunkcache, flag, flag1, flag2, flag3)).a(entity, i, j, k, f);

		this.methodProfiler.b();
		return pathentity;
	}

	public boolean isBlockFacePowered(int i, int j, int k, int l) {
		int i1 = this.getTypeId(i, j, k);

		return i1 == 0 ? false : Block.byId[i1].c(this, i, j, k, l);
	}

	public boolean isBlockPowered(int i, int j, int k) {
		return this.isBlockFacePowered(i, j - 1, k, 0) ? true : (this.isBlockFacePowered(i, j + 1, k, 1) ? true : (this.isBlockFacePowered(i, j, k - 1, 2) ? true : (this.isBlockFacePowered(i, j, k + 1, 3) ? true : (this.isBlockFacePowered(i - 1, j, k, 4) ? true : this.isBlockFacePowered(i + 1, j, k, 5)))));
	}

	public boolean isBlockFaceIndirectlyPowered(int i, int j, int k, int l) {
		if (this.s(i, j, k)) {
			return this.isBlockPowered(i, j, k);
		} else {
			int i1 = this.getTypeId(i, j, k);

			return i1 == 0 ? false : Block.byId[i1].b(this, i, j, k, l);
		}
	}

	public boolean isBlockIndirectlyPowered(int i, int j, int k) {
		return this.isBlockFaceIndirectlyPowered(i, j - 1, k, 0) ? true : (this.isBlockFaceIndirectlyPowered(i, j + 1, k, 1) ? true : (this.isBlockFaceIndirectlyPowered(i, j, k - 1, 2) ? true : (this.isBlockFaceIndirectlyPowered(i, j, k + 1, 3) ? true : (this.isBlockFaceIndirectlyPowered(i - 1, j, k, 4) ? true : this.isBlockFaceIndirectlyPowered(i + 1, j, k, 5)))));
	}

	public EntityHuman findNearbyPlayer(Entity entity, double d0) {
		return this.findNearbyPlayer(entity.locX, entity.locY, entity.locZ, d0);
	}

	public EntityHuman findNearbyPlayer(double d0, double d1, double d2, double d3) {
		double d4 = -1.0D;
		EntityHuman entityhuman = null;

		for (int i = 0; i < this.players.size(); ++i) {
			EntityHuman entityhuman1 = (EntityHuman) this.players.get(i);
			// CraftBukkit start - fixed an NPE
			if (entityhuman1 == null || entityhuman1.dead) {
				continue;
			}
			// CraftBukkit end
			double d5 = entityhuman1.e(d0, d1, d2);

			if ((d3 < 0.0D || d5 < d3 * d3) && (d4 == -1.0D || d5 < d4)) {
				d4 = d5;
				entityhuman = entityhuman1;
			}
		}

		return entityhuman;
	}

	public EntityHuman findNearbyVulnerablePlayer(Entity entity, double d0) {
		return this.findNearbyVulnerablePlayer(entity.locX, entity.locY, entity.locZ, d0);
	}

	public EntityHuman findNearbyVulnerablePlayer(double d0, double d1, double d2, double d3) {
		double d4 = -1.0D;
		EntityHuman entityhuman = null;

		for (int i = 0; i < this.players.size(); ++i) {
			EntityHuman entityhuman1 = (EntityHuman) this.players.get(i);
			// CraftBukkit start - fixed an NPE
			if (entityhuman1 == null || entityhuman1.dead) {
				continue;
			}
			// CraftBukkit end

			if (!entityhuman1.abilities.isInvulnerable) {
				double d5 = entityhuman1.e(d0, d1, d2);
				double d6 = d3;

				if (entityhuman1.isSneaking()) {
					d6 = d3 * 0.800000011920929D;
				}

				if (entityhuman1.isInvisible()) {
					float f = entityhuman1.bO();

					if (f < 0.1F) {
						f = 0.1F;
					}

					d6 *= (double) (0.7F * f);
				}

				if ((d3 < 0.0D || d5 < d6 * d6) && (d4 == -1.0D || d5 < d4)) {
					d4 = d5;
					entityhuman = entityhuman1;
				}
			}
		}

		return entityhuman;
	}

	public EntityHuman a(String s) {
		for (int i = 0; i < this.players.size(); ++i) {
			if (s.equals(((EntityHuman) this.players.get(i)).name)) {
				return (EntityHuman) this.players.get(i);
			}
		}

		return null;
	}

	public void C() throws ExceptionWorldConflict { // CraftBukkit - added throws
		this.dataManager.checkSession();
	}

    /**
     * gets the random world seed
     */
    public long getSeed()
    {
        return this.worldProvider.getSeed();
    }

    public long getTime()
    {
        return this.worldData.getTime();
    }

    public long F()
    {
        return this.worldProvider.getWorldTime();
    }

    /**
     * Sets the world time.
     */
    public void setTime(long var1)
    {
        this.worldProvider.setWorldTime(var1);
    }

    public ChunkCoordinates getSpawn()
    {
        return this.worldProvider.getSpawnPoint();
    }

    /**
     * Called when checking if a certain block can be mined or not. The 'spawn safe zone' check is located here.
     */
    public boolean a(EntityHuman var1, int var2, int var3, int var4)
    {
        return this.worldProvider.canMineBlock(var1, var2, var3, var4);
    }

    public boolean canMineBlockBody(EntityHuman var1, int var2, int var3, int var4)
    {
        return true;
    }

	public void broadcastEntityEffect(Entity entity, byte b0) {}

	public IChunkProvider H() {
		return this.chunkProvider;
	}

	public void playNote(int i, int j, int k, int l, int i1, int j1) {
		if (l > 0) {
			Block.byId[l].b(this, i, j, k, i1, j1);
		}
	}

	public IDataManager getDataManager() {
		return this.dataManager;
	}

	public WorldData getWorldData() {
		return this.worldData;
	}

	public GameRules getGameRules() {
		return this.worldData.getGameRules();
	}

	public void everyoneSleeping() {}

	// CraftBukkit start
	// Calls the method that checks to see if players are sleeping
	// Called by CraftPlayer.setPermanentSleeping()
	public void checkSleepStatus() {
		if (!this.isStatic) {
			this.everyoneSleeping();
		}
	}
	// CraftBukkit end

	public float i(float f) {
		return (this.o + (this.p - this.o) * f) * this.j(f);
	}

	public float j(float f) {
		return this.m + (this.n - this.m) * f;
	}

	public boolean L() {
		return (double) this.i(1.0F) > 0.9D;
	}

	public boolean M() {
		return (double) this.j(1.0F) > 0.2D;
	}

	public boolean B(int i, int j, int k) {
		if (!this.M()) {
			return false;
		} else if (!this.j(i, j, k)) {
			return false;
		} else if (this.h(i, k) > j) {
			return false;
		} else {
			BiomeBase biomebase = this.getBiome(i, k);

			return biomebase.c() ? false : biomebase.d();
		}
	}

    /**
     * Checks to see if the biome rainfall values for a given x,y,z coordinate set are extremely high
     */
    public boolean C(int var1, int var2, int var3)
    {
        return this.worldProvider.isBlockHighHumidity(var1, var2, var3);
    }

	public void a(String s, WorldMapBase worldmapbase) {
		this.worldMaps.a(s, worldmapbase);
	}

	public WorldMapBase a(Class oclass, String s) {
		return this.worldMaps.get(oclass, s);
	}

	public int b(String s) {
		return this.worldMaps.a(s);
	}

	public void e(int i, int j, int k, int l, int i1) {
		for (int j1 = 0; j1 < this.w.size(); ++j1) {
			((IWorldAccess) this.w.get(j1)).a(i, j, k, l, i1);
		}
	}

	public void triggerEffect(int i, int j, int k, int l, int i1) {
		this.a((EntityHuman) null, i, j, k, l, i1);
	}

	public void a(EntityHuman entityhuman, int i, int j, int k, int l, int i1) {
		for (int j1 = 0; j1 < this.w.size(); ++j1) {
			((IWorldAccess) this.w.get(j1)).a(entityhuman, i, j, k, l, i1);
		}
	}

    /**
     * Returns maximum world height.
     */
    public int getHeight()
    {
        return this.worldProvider.getHeight();
    }

    /**
     * Returns current world height.
     */
    public int O()
    {
        return this.worldProvider.getActualHeight();
    }

	public IUpdatePlayerListBox a(EntityMinecart entityminecart) {
		return null;
	}

	public Random D(int i, int j, int k) {
		long l = (long) i * 341873128712L + (long) j * 132897987541L + this.getWorldData().getSeed() + (long) k;

		this.random.setSeed(l);
		return this.random;
	}

	public ChunkPosition b(String s, int i, int j, int k) {
		return this.H().findNearestMapFeature(this, s, i, j, k);
	}

	public CrashReport a(CrashReport crashreport) {
		crashreport.a("World " + this.worldData.getName() + " Entities", (Callable) (new CrashReportEntities(this)));
		crashreport.a("World " + this.worldData.getName() + " Players", (Callable) (new CrashReportPlayers(this)));
		crashreport.a("World " + this.worldData.getName() + " Chunk Stats", (Callable) (new CrashReportChunkStats(this)));
		return crashreport;
	}

	public void g(int i, int j, int k, int l, int i1) {
		Iterator iterator = this.w.iterator();

		while (iterator.hasNext()) {
			IWorldAccess iworldaccess = (IWorldAccess) iterator.next();

			iworldaccess.b(i, j, k, l, i1);
		}
	}

	public Vec3DPool getVec3DPool() {
		return this.K;
	}

	public Calendar S() {
		this.L.setTimeInMillis(System.currentTimeMillis());
		return this.L;
	}
	

    public void addTileEntity(TileEntity var1)
    {
        List var2 = this.N ? this.a : this.tileEntityList;

        if (var1.canUpdate())
        {
            var2.add(var1);
        }
    }

    public boolean isBlockSolidOnSide(int var1, int var2, int var3, ForgeDirection var4)
    {
        return this.isBlockSolidOnSide(var1, var2, var3, var4, false);
    }

    public boolean isBlockSolidOnSide(int var1, int var2, int var3, ForgeDirection var4, boolean var5)
    {
        if (var1 >= -30000000 && var3 >= -30000000 && var1 < 30000000 && var3 < 30000000)
        {
            Chunk var6 = this.chunkProvider.getOrCreateChunk(var1 >> 4, var3 >> 4);

            if (var6 != null && !var6.isEmpty())
            {
                Block var7 = Block.byId[this.getTypeId(var1, var2, var3)];
                return var7 == null ? false : var7.isBlockSolidOnSide(this, var1, var2, var3, var4);
            }
            else
            {
                return var5;
            }
        }
        else
        {
            return var5;
        }
    }

    public ImmutableSetMultimap getPersistentChunks()
    {
        return ForgeChunkManager.getPersistentChunksFor(this);
    }
}
