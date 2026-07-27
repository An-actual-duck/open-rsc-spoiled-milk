package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sector;
import com.openrsc.client.model.Tile;
import com.openrsc.data.DataConversions;
import orsc.Config;
import orsc.NativeLayeredTerrainSnapshot;
import orsc.RenderTelemetry;
import orsc.graphics.two.GraphicsController;
import orsc.util.FastMath;
import orsc.util.GenUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class World {
	private static final int RENDERER_3D_WALL_SHADOW_MAX_FOOTPRINT = 512;

	public static final int SECTION_SIZE = 48;
	public static final int ACTIVE_SECTION_GRID = 3;
	public static final int LOCAL_TILE_COUNT = SECTION_SIZE * ACTIVE_SECTION_GRID;
	public static final int LOCAL_TILE_MAX = LOCAL_TILE_COUNT - 1;
	public static final int LOCAL_FACE_TILE_COUNT = LOCAL_TILE_COUNT - 1;
	public static final int LEGACY_MINIMAP_TILE_COUNT = SECTION_SIZE * 2;
	public static final int LEGACY_MINIMAP_FACE_TILE_COUNT = LEGACY_MINIMAP_TILE_COUNT - 1;
	private static final int ACTIVE_SECTION_ORIGIN_OFFSET = ACTIVE_SECTION_GRID / 2;
	private static final int ACTIVE_SECTION_COUNT = ACTIVE_SECTION_GRID * ACTIVE_SECTION_GRID;
	private static final int MODEL_GRID_TILE_SIZE = 12;
	private static final int MODEL_GRID_AXIS = LOCAL_TILE_COUNT / MODEL_GRID_TILE_SIZE;
	private static final int MODEL_GRID_COUNT = MODEL_GRID_AXIS * MODEL_GRID_AXIS;
	private static final int MODEL_GRID_WORLD_SIZE = MODEL_GRID_TILE_SIZE * 128;
	private static final int MODEL_BUFFER_CAPACITY = LOCAL_TILE_COUNT * LOCAL_TILE_COUNT * 4;
	private static final int MODEL_SPLIT_VERTEX_LIMIT = 512;
	private static final int MINIMAP_PIXEL_SIZE = LEGACY_MINIMAP_FACE_TILE_COUNT * 3;
	private static final int SECTOR_CACHE_LIMIT = 256;
	private static final int CPU_SECTION_WINDOW_CACHE_LIMIT = 24;
	private static final int TERRAIN_MODEL_INPUT_CACHE_LIMIT = 24;
	private static final int WALL_MODEL_INPUT_CACHE_LIMIT = 24;
	private static final int ROOF_MODEL_INPUT_CACHE_LIMIT = 24;
	private static final int WORLD_MODEL_PRODUCT_CACHE_LIMIT = 48;
	private static final int LAVA_GLOW_OVERLAY_ID = 11;
	private static final int LAVA_GLOW_COLOR = 0xff5a18;
	private static final int LAVA_GLOW_RADIUS = 384;
	private static final int LAVA_GLOW_INTENSITY = 96;
	private static final int LAVA_GLOW_NON_OVERWORLD_INTENSITY = 72;
	private static final int SYNTHETIC_DEEP_MIN_X = 440;
	private static final int SYNTHETIC_DEEP_MAX_X = 460;
	private static final int SYNTHETIC_DEEP_MIN_Z = 590;
	private static final int SYNTHETIC_DEEP_MAX_Z = 610;
	private static final int SECTOR_PRELOAD_LOW_OFFSET = -ACTIVE_SECTION_ORIGIN_OFFSET - 1;
	private static final int SECTOR_PRELOAD_HIGH_OFFSET = ACTIVE_SECTION_GRID - ACTIVE_SECTION_ORIGIN_OFFSET;
	private final int[] colorToResource = new int[256];
	private final int[][] tileElevationCache = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
	private final int[][] pathFindSource = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
	private final int[][] tileDirection = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
	private final Object sectorCacheLock = new Object();
	private final Object cpuSectionWindowCacheLock = new Object();
	private final Object terrainModelInputCacheLock = new Object();
	private final Object wallModelInputCacheLock = new Object();
	private final Object roofModelInputCacheLock = new Object();
	private final Object worldModelProductCacheLock = new Object();
	private final Object tileArchiveLock = new Object();
	private final Object worldEditorTerrainPatchLock = new Object();
	private final Map<String, Map<Integer, TerrainPatch>> worldEditorTerrainPatches =
		new HashMap<String, Map<Integer, TerrainPatch>>();
	private volatile long worldEditorTerrainRevision = 0L;
	private volatile boolean syntheticDeepFixtureTerrain;
	private volatile int syntheticDeepFixtureOffsetX;
	private volatile int syntheticDeepFixtureOffsetZ;
	private volatile NativeLayeredTerrainSnapshot nativeLayeredTerrainSnapshot;
	private final Map<String, Sector> sectorTemplateCache = Collections.synchronizedMap(
		new LinkedHashMap<String, Sector>(SECTOR_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Sector> eldest) {
				return size() > SECTOR_CACHE_LIMIT;
			}
		}
	);
	private final Set<String> sectorPreloadsInFlight = Collections.synchronizedSet(new HashSet<String>());
	private final Map<String, CpuSectionWindow> cpuSectionWindowCache =
		new LinkedHashMap<String, CpuSectionWindow>(CPU_SECTION_WINDOW_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, CpuSectionWindow> eldest) {
				return size() > CPU_SECTION_WINDOW_CACHE_LIMIT;
			}
		};
	private final Map<String, TerrainModelInput> terrainModelInputCache =
		new LinkedHashMap<String, TerrainModelInput>(TERRAIN_MODEL_INPUT_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, TerrainModelInput> eldest) {
				return size() > TERRAIN_MODEL_INPUT_CACHE_LIMIT;
			}
		};
	private final Map<String, WallModelInput> wallModelInputCache =
		new LinkedHashMap<String, WallModelInput>(WALL_MODEL_INPUT_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, WallModelInput> eldest) {
				return size() > WALL_MODEL_INPUT_CACHE_LIMIT;
			}
		};
	private final Map<String, RoofModelInput> roofModelInputCache =
		new LinkedHashMap<String, RoofModelInput>(ROOF_MODEL_INPUT_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, RoofModelInput> eldest) {
				return size() > ROOF_MODEL_INPUT_CACHE_LIMIT;
			}
		};
	private final Map<String, WorldModelProduct> worldModelProductCache =
		new LinkedHashMap<String, WorldModelProduct>(WORLD_MODEL_PRODUCT_CACHE_LIMIT, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, WorldModelProduct> eldest) {
				return size() > WORLD_MODEL_PRODUCT_CACHE_LIMIT;
			}
		};
	private final Set<String> cpuSectionWindowBuildsInFlight = new HashSet<String>();
	private final Set<String> terrainModelInputBuildsInFlight = new HashSet<String>();
	private final Set<String> wallModelInputBuildsInFlight = new HashSet<String>();
	private final Set<String> roofModelInputBuildsInFlight = new HashSet<String>();
	private final Set<String> worldModelProductBuildsInFlight = new HashSet<String>();
	private final ExecutorService sectorPreloadExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "world-sector-preload");
			thread.setDaemon(true);
			return thread;
		}
	});
	private final boolean showInvisibleWalls = false;
	public int baseMediaSprite = 750;
	public int[][] collisionFlags = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
	public int[] faceTileX = new int[MODEL_BUFFER_CAPACITY];
	public int[] faceTileZ = new int[MODEL_BUFFER_CAPACITY];
	public byte[] landscapePack;
	public byte[] mapPack;
	public byte[] memberLandscapePack;
	public boolean playerAlive = false;
	public RSModel[][] modelWallGrid = new RSModel[4][MODEL_GRID_COUNT];
	public RSModel[][] modelRoofGrid = new RSModel[4][MODEL_GRID_COUNT];
	// private final byte[][] elevation = new byte[4][2304];
	// private final byte[][] terrainColour = new byte[4][2304];
	// private final byte[][] tileDecoration = new byte[4][2304];
	//
	//
	// private final int[][] wallsDiagonal = new int[4][2304];
	// private final byte[][] wallsEastWest = new byte[4][2304];
	// private final byte[][] wallsNorthSouth = new byte[4][2304];
	// private final byte[][] wallsRoof = new byte[4][2304];
	private GraphicsController minimapGraphics;
	private Scene scene;
	private RSModel modelAccumulate;
	private RSModel[] modelLandscapeGrid = new RSModel[MODEL_GRID_COUNT];
	private Sector[] worldMapSector = new Sector[ACTIVE_SECTION_COUNT];
	private int mapPointX = 0;
	private int mapPointZ = 0;
	private ZipFile tileArchive;
	private Sector[] sectors;
	private final WorldStreamManager worldStreamManager = new WorldStreamManager();
	private Renderer3DWorldChunkFrame renderer3DWorldChunkFrame = Renderer3DWorldChunkFrame.EMPTY;
	public String mapHash;

	public World(Scene var1, GraphicsController var2) {
		try {
			this.minimapGraphics = var2;
			this.scene = var1;

			int var3;
			for (var3 = 0; var3 < 64; ++var3)
				this.colorToResource[var3] = GenUtil.colorToResource(255 - var3 * 4,
					255 - (int) ((double) var3 * 1.75D), 255 - var3 * 4);

			for (var3 = 0; var3 < 64; ++var3)
				this.colorToResource[64 + var3] = GenUtil.colorToResource(var3 * 3, 144, 0);

			for (var3 = 0; var3 < 64; ++var3)
				this.colorToResource[128 + var3] = GenUtil.colorToResource(192 - (int) ((double) var3 * 1.5D),
					144 - (int) ((double) var3 * 1.5D), 0);

			for (var3 = 0; var3 < 64; ++var3)
				this.colorToResource[192 + var3] = GenUtil.colorToResource(96 - (int) ((double) var3 * 1.5D),
					(int) ((double) var3 * 1.5D) + 48, 0);

			sectors = new Sector[ACTIVE_SECTION_COUNT];

			try {
				String path;
				if (Config.S_WANT_CUSTOM_LANDSCAPE)
					path = Config.F_CACHE_DIR + File.separator + "video" + File.separator + "Custom_Landscape.orsc";
				else
					path = Config.F_CACHE_DIR + File.separator + "video" + File.separator + "Authentic_Landscape.orsc";
				tileArchive = new ZipFile(new File(path));
				mapHash = generateMapHash(path);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4,
				"k.<init>(" + (var1 != null ? "{...}" : "null") + ',' + (var2 != null ? "{...}" : "null") + ')');
		}
	}

	public void setSyntheticDeepFixtureTerrain(
		final boolean enabled,
		final int worldOffsetX,
		final int worldOffsetZ) {
		setLayeredTerrainScope(enabled, null, worldOffsetX, worldOffsetZ);
	}

	public void setLayeredTerrainScope(
		final boolean syntheticDeepFixture,
		final NativeLayeredTerrainSnapshot nativeTerrainSnapshot,
		final int worldOffsetX,
		final int worldOffsetZ) {
		final boolean layeredTerrain =
			syntheticDeepFixture || nativeTerrainSnapshot != null;
		if (syntheticDeepFixtureTerrain != layeredTerrain
			|| !Objects.equals(
				nativeLayeredTerrainSnapshot, nativeTerrainSnapshot)
			|| layeredTerrain
				&& (syntheticDeepFixtureOffsetX != worldOffsetX
					|| syntheticDeepFixtureOffsetZ != worldOffsetZ)) {
			syntheticDeepFixtureTerrain = layeredTerrain;
			nativeLayeredTerrainSnapshot = nativeTerrainSnapshot;
			syntheticDeepFixtureOffsetX = worldOffsetX;
			syntheticDeepFixtureOffsetZ = worldOffsetZ;
			worldEditorTerrainRevision++;
		}
	}

	private static void tagRenderer3DModels(RSModel[] models, Renderer3DModelKind kind) {
		if (models == null) {
			return;
		}
		for (RSModel model : models) {
			if (model != null) {
				model.setRenderer3DModelKind(kind);
			}
		}
	}

	private String generateMapHash(String path) {
		try {
			return GenUtil.getMD5Checksum(path);
		} catch (Exception e) {
			return "failed-" + path;
		}
	}

	public static int worldTileToSection(int worldTile) {
		return (SECTION_SIZE / 2 + worldTile) / SECTION_SIZE;
	}

	public static int sectionToLocalBaseTile(int section) {
		return (section - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE;
	}

	public static boolean isLocalTile(int tileX, int tileZ) {
		return tileX >= 0 && tileZ >= 0 && tileX < LOCAL_TILE_COUNT && tileZ < LOCAL_TILE_COUNT;
	}

	public static boolean isLocalFaceTile(int tileX, int tileZ) {
		return tileX >= 0 && tileZ >= 0 && tileX < LOCAL_FACE_TILE_COUNT && tileZ < LOCAL_FACE_TILE_COUNT;
	}

	public static int localTileKey(int tileX, int tileZ) {
		return tileZ * LOCAL_TILE_COUNT + tileX;
	}

	private static int sectorIndexForLocalTile(int tileX, int tileZ) {
		if (!isLocalTile(tileX, tileZ)) {
			return -1;
		}
		return tileZ / SECTION_SIZE * ACTIVE_SECTION_GRID + tileX / SECTION_SIZE;
	}

	private Sector sectorForLocalTile(int tileX, int tileZ) {
		int sectorIndex = sectorIndexForLocalTile(tileX, tileZ);
		return sectorIndex < 0 ? null : sectors[sectorIndex];
	}

	public boolean isTerrainLoadedAtLocalTile(int tileX, int tileZ) {
		Sector sector = sectorForLocalTile(tileX, tileZ);
		return sector != null && sector.isLoaded();
	}

	public boolean isTerrainLoadedAtLocalPixel(int pixelX, int pixelZ) {
		return isTerrainLoadedAtLocalTile(pixelX >> 7, pixelZ >> 7);
	}

	private static int tileInSector(int tile) {
		return tile % SECTION_SIZE;
	}

	private boolean loadSectionWindow(Sector[] target, int height, int sectionX, int sectionY) {
		CpuSectionWindow window = loadCpuSectionWindow(height, sectionX, sectionY);
		window.copyInto(target);
		return window.bridgeDecorationsApplied;
	}

	public final void addGameObject_UpdateCollisionMap(int xTile, int zTile, int objectID, boolean var3) {
		try {
			if (!var3) {

				if (isLocalFaceTile(xTile, zTile))
					if (Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getType() == 1
						|| Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getType() == 2) {
						int dir = this.getTileDirection((int) xTile, zTile);
						int xSize;
						int zSize;
						if (dir == 0 || dir == 4) {
							xSize = Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getWidth();
							zSize = Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getHeight();
						} else {
							xSize = Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getHeight();
							zSize = Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getWidth();
						}

						for (int x = xTile; x < xSize + xTile; ++x)
							for (int z = zTile; zTile + zSize > z; ++z)
								if (Objects.requireNonNull(EntityHandler.getObjectDef(objectID)).getType() == 1)
									this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
										CollisionFlag.FULL_BLOCK_C);
								else if (dir != 0) {
									if (dir == 2) {
										this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
											CollisionFlag.WALL_SOUTH);
										if (z < LOCAL_FACE_TILE_COUNT)
											this.collisionFlagBitwiseOr(x, (int) (1 + z), CollisionFlag.WALL_NORTH);
									} else if (dir != 4) {
										if (dir == 6) {
											this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
												CollisionFlag.WALL_NORTH);
											if (z > 0)
												this.collisionFlagBitwiseOr(x, (int) (z - 1), CollisionFlag.WALL_SOUTH);
										}
									} else {
										this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
											CollisionFlag.WALL_WEST);
										if (x < LOCAL_FACE_TILE_COUNT)
											this.collisionFlagBitwiseOr(x + 1, (int) z, CollisionFlag.WALL_EAST);
									}
								} else {
									this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
										CollisionFlag.WALL_EAST);
									if (x > 0)
										this.collisionFlagBitwiseOr(x - 1, (int) z, CollisionFlag.WALL_WEST);
								}

						this.setVertexLightArea(xTile, zTile, xSize, zSize);
					}
			}
		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10, "k.CA(" + xTile + ',' + objectID + ',' + var3 + ',' + zTile + ')');
		}
	}

	public final void addLoginScreenModels(RSModel[] modelTable) {
		try {


			for (int x = 0; x < LOCAL_FACE_TILE_COUNT - 1; ++x)
				for (int z = 0; z < LOCAL_FACE_TILE_COUNT - 1; ++z)
					if (this.getWallDiagonal(x, z) > 48000 && this.getWallDiagonal(x, z) < 60000) {
						int diagWall = this.getWallDiagonal(x, z) - 48001;
						int dir = this.getTileDirection((int) x, z);
						int xSize;
						int zSize;
						if (dir == 0 || dir == 4) {
							zSize = Objects.requireNonNull(EntityHandler.getObjectDef(diagWall)).getHeight();
							xSize = Objects.requireNonNull(EntityHandler.getObjectDef(diagWall)).getWidth();
						} else {
							xSize = Objects.requireNonNull(EntityHandler.getObjectDef(diagWall)).getHeight();
							zSize = Objects.requireNonNull(EntityHandler.getObjectDef(diagWall)).getWidth();
						}

						this.addGameObject_UpdateCollisionMap(x, z, diagWall, false);
						RSModel copy = modelTable[Objects.requireNonNull(EntityHandler.getObjectDef(diagWall)).modelID].copyModel(false, -120,
							false, false, true);
						int xTranslate = (xSize + x + x) * 128 / 2;
						int zTranslate = (zSize + z + z) * 128 / 2;
						copy.translate2(xTranslate, -this.getElevation(xTranslate, zTranslate), zTranslate);
						copy.setRot256(0, this.getTileDirection(x, z) * 32, 0);
						this.scene.addModel(copy);
						copy.setDiffuseLight(48, 48, -10, -122, -50, -50);
						if (xSize > 1 || zSize > 1)
							for (int xi = x; x + xSize > xi; ++xi)
								for (int zi = z; zSize + z > zi; ++zi)
									if ((x < xi || z < zi) && diagWall == this.getWallDiagonal(xi, zi) - 48001) {
										int sectorIndex = sectorIndexForLocalTile(xi, zi);
										if (sectorIndex >= 0) {
											xTranslate = tileInSector(xi);
											zTranslate = tileInSector(zi);
											sectors[sectorIndex].getTile(xTranslate, zTranslate).diagonalWalls = 0;
										}
										// this.wallsDiagonal[var14][xTranslate
										// * 48 + zTranslate] = 0;
									}
					}
		} catch (RuntimeException var15) {
			throw GenUtil.makeThrowable(var15, "k.FA(" + (modelTable != null ? "{...}" : "null") + ',' + "dummy" + ')');
		}
	}

	public final void applyWallToCollisionFlags(int wallID, int x, int z, int dir) {
		try {

			if (isLocalFaceTile(x, z))
				if (Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getDoorType() == 1) {
					if (dir == 0) {
						this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
							CollisionFlag.WALL_NORTH);
						if (z > 0)
							this.collisionFlagBitwiseOr(x, (int) (z - 1), CollisionFlag.WALL_SOUTH);
					} else if (dir == 1) {
						this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
							CollisionFlag.WALL_EAST);
						if (x > 0)
							this.collisionFlagBitwiseOr(x - 1, (int) z, CollisionFlag.WALL_WEST);
					} else if (dir != 2) {
						if (dir == 3)
							this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
								CollisionFlag.FULL_BLOCK_B);
					} else
						this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
							CollisionFlag.FULL_BLOCK_A);

					this.setVertexLightArea(x, z, 1, 1);
				}
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "k.W(" + z + ',' + wallID + ',' + dir + ',' + x + ',' + 11715 + ')');
		}
	}

	private void applyWallToElevationCache(RoofElevationWorkspace elevations, int wallID, int x1, int z1, int x2, int z2) {
		try {

			int height = Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getWallObjectHeight();

			if (elevations.get(x1, z1) < 80000)
				elevations.set(x1, z1, elevations.get(x1, z1) + height + 80000);

			if (elevations.get(x2, z2) < 80000)
				elevations.set(x2, z2, elevations.get(x2, z2) + height + 80000);

		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8,
				"k.AA(" + wallID + ',' + x2 + ',' + z1 + ',' + z2 + ',' + "dummy" + ',' + x1 + ')');
		}
	}

	private void collisionFlagBitwiseOr(int x, int z, int val) {
		try {

			this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z], val);
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.O(" + val + ',' + z + ',' + "dummy" + ',' + x + ')');
		}
	}

	private void collisionFlagModify(int x, int z, int and, int or) {
		try {

			this.collisionFlags[x][z] = FastMath.bitwiseAnd(this.collisionFlags[x][z], and - or);
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.E(" + z + ',' + and + ',' + x + ',' + or + ')');
		}
	}

	private int collisionFlagSafe(int x, int z) {
		try {

			return isLocalTile(x, z) ? this.collisionFlags[x][z] : 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.JA(" + -38 + ',' + z + ',' + x + ')');
		}
	}

	private void drawMinimapTile(int tileX, int tileZ, int bridge00_11, int res01, int res10) {
		try {
			if (!isLegacyMinimapFaceTile(tileX, tileZ)) {
				return;
			}

			int mx = tileX * 3;
			int my = tileZ * 3;
			int a = this.scene.resourceToColor(res10, true);
			a = a >> 1 & 0x7F7F7F;
			int b = this.scene.resourceToColor(res01, true);
			b = (0xFEFEFF & b) >> 1;
			if (bridge00_11 == 0) {
				// AAA
				// AAB
				// ABB
				this.minimapGraphics.drawLineHoriz(mx, my, 3, a);
				this.minimapGraphics.drawLineHoriz(mx, 1 + my, 2, a);
				this.minimapGraphics.drawLineHoriz(mx, my + 2, 1, a);
				this.minimapGraphics.drawLineHoriz(2 + mx, my + 1, 1, b);
				this.minimapGraphics.drawLineHoriz(mx + 1, my + 2, 2, b);
			} else if (bridge00_11 == 1) {
				// BBB
				// ABB
				// AAB
				this.minimapGraphics.drawLineHoriz(mx, my, 3, b);
				this.minimapGraphics.drawLineHoriz(1 + mx, 1 + my, 2, b);
				this.minimapGraphics.drawLineHoriz(mx + 2, my + 2, 1, b);
				this.minimapGraphics.drawLineHoriz(mx, my + 1, 1, a);
				this.minimapGraphics.drawLineHoriz(mx, 2 + my, 2, a);
			}

		} catch (RuntimeException var12) {
			throw GenUtil.makeThrowable(var12,
				"k.C(" + bridge00_11 + ',' + "dummy" + ',' + res01 + ',' + tileX + ',' + tileZ + ',' + res10 + ')');
		}
	}

	private static boolean isLegacyMinimapFaceTile(int tileX, int tileZ) {
		return tileX >= 0 && tileZ >= 0 && tileX < LEGACY_MINIMAP_FACE_TILE_COUNT
			&& tileZ < LEGACY_MINIMAP_FACE_TILE_COUNT;
	}

	/**
	 * @param pathX
	 * @param pathZ
	 * @param startX
	 * @param startZ
	 * @param xLow
	 * @param xHigh
	 * @param zLow
	 * @param zHigh
	 * @param reachBorder
	 * @return the number of nodes in the path
	 */
	public final int findPath(int[] pathX, int[] pathZ, int startX, int startZ, int xLow, int xHigh, int zLow,
							  int zHigh, boolean reachBorder) {
		// System.out.println("Find path: " + startX + "," + startZ + " -> [" +
		// xLow + "-" + xHigh + "," + zLow + "-"
		// + zHigh + "] Border good: " + reachBorder);
		try {
			for (int x = 0; x < LOCAL_TILE_COUNT; ++x)
				for (int y = 0; y < LOCAL_TILE_COUNT; ++y)
					this.pathFindSource[x][y] = 0;


			byte var20 = 0;
			int openListRead = 0;
			int x = startX;
			int z = startZ;
			this.pathFindSource[startX][startZ] = 99;
			pathX[var20] = startX;
			pathZ[var20] = startZ;
			int openListWrite = var20 + 1;
			int openListSize = pathX.length;
			boolean complete = false;

			while (openListRead != openListWrite) {
				x = pathX[openListRead];
				z = pathZ[openListRead];
				openListRead = (1 + openListRead) % openListSize;
				if (x >= xLow && x <= xHigh && z >= zLow && z <= zHigh) {
					// System.out.println("complete");
					complete = true;
					break;
				}

				if (reachBorder) {
					if (x > 0 && xLow <= x - 1 && xHigh >= x - 1 && zLow <= z && zHigh >= z
						&& (this.collisionFlags[x - 1][z] & CollisionFlag.WALL_WEST) == 0) {
						complete = true;
						break;
					}

					if (x < LOCAL_FACE_TILE_COUNT && 1 + x >= xLow && x + 1 <= xHigh && z >= zLow && zHigh >= z
						&& (CollisionFlag.WALL_EAST & this.collisionFlags[x + 1][z]) == 0) {
						complete = true;
						break;
					}

					if (z > 0 && xLow <= x && xHigh >= x && z - 1 >= zLow && zHigh >= z - 1
						&& (CollisionFlag.WALL_SOUTH & this.collisionFlags[x][z - 1]) == 0) {
						complete = true;
						break;
					}

					if (z < LOCAL_FACE_TILE_COUNT && xLow <= x && x <= xHigh && zLow <= z + 1 && zHigh >= z + 1
						&& (CollisionFlag.WALL_NORTH & this.collisionFlags[x][z + 1]) == 0) {
						complete = true;
						break;
					}
				}

				if (x > 0 && this.pathFindSource[x - 1][z] == 0
					&& (this.collisionFlags[x - 1][z] & CollisionFlag.WEST_BLOCKED) == 0) {
					pathX[openListWrite] = x - 1;
					pathZ[openListWrite] = z;
					this.pathFindSource[x - 1][z] = CollisionFlag.SOURCE_WEST;
					openListWrite = (openListWrite + 1) % openListSize;
				}

				if (x < LOCAL_FACE_TILE_COUNT && this.pathFindSource[1 + x][z] == 0
					&& (this.collisionFlags[1 + x][z] & CollisionFlag.EAST_BLOCKED) == 0) {
					pathX[openListWrite] = 1 + x;
					pathZ[openListWrite] = z;
					this.pathFindSource[x + 1][z] = CollisionFlag.SOURCE_EAST;
					openListWrite = (1 + openListWrite) % openListSize;
				}

				if (z > 0 && this.pathFindSource[x][z - 1] == 0
					&& (CollisionFlag.SOUTH_BLOCKED & this.collisionFlags[x][z - 1]) == 0) {
					pathX[openListWrite] = x;
					pathZ[openListWrite] = z - 1;
					this.pathFindSource[x][z - 1] = CollisionFlag.SOURCE_SOUTH;
					openListWrite = (openListWrite + 1) % openListSize;
				}

				if (z < LOCAL_FACE_TILE_COUNT && this.pathFindSource[x][1 + z] == 0
					&& (CollisionFlag.NORTH_BLOCKED & this.collisionFlags[x][1 + z]) == 0) {
					pathX[openListWrite] = x;
					pathZ[openListWrite] = z + 1;
					this.pathFindSource[x][z + 1] = CollisionFlag.SOURCE_NORTH;
					openListWrite = (openListWrite + 1) % openListSize;
				}

				if (x > 0 && z > 0 && (CollisionFlag.SOUTH_BLOCKED & this.collisionFlags[x][z - 1]) == 0
					&& (CollisionFlag.WEST_BLOCKED & this.collisionFlags[x - 1][z]) == 0
					&& (CollisionFlag.SOUTH_WEST_BLOCKED & this.collisionFlags[x - 1][z - 1]) == 0
					&& this.pathFindSource[x - 1][z - 1] == 0) {
					pathX[openListWrite] = x - 1;
					pathZ[openListWrite] = z - 1;
					this.pathFindSource[x - 1][z - 1] = CollisionFlag.SOURCE_SOUTH_WEST;
					openListWrite = (1 + openListWrite) % openListSize;
				}

				if (x < LOCAL_FACE_TILE_COUNT && z > 0 && (this.collisionFlags[x][z - 1] & CollisionFlag.SOUTH_BLOCKED) == 0
					&& (this.collisionFlags[1 + x][z] & CollisionFlag.EAST_BLOCKED) == 0
					&& (this.collisionFlags[x + 1][z - 1] & CollisionFlag.SOUTH_EAST_BLOCKED) == 0
					&& this.pathFindSource[1 + x][z - 1] == 0) {
					pathX[openListWrite] = 1 + x;
					pathZ[openListWrite] = z - 1;
					this.pathFindSource[x + 1][z - 1] = CollisionFlag.SOURCE_SOUTH_EAST;
					openListWrite = (1 + openListWrite) % openListSize;
				}

				if (x > 0 && z < LOCAL_FACE_TILE_COUNT && (this.collisionFlags[x][1 + z] & CollisionFlag.NORTH_BLOCKED) == 0
					&& (this.collisionFlags[x - 1][z] & CollisionFlag.WEST_BLOCKED) == 0
					&& (this.collisionFlags[x - 1][1 + z] & CollisionFlag.NORTH_WEST_BLOCKED) == 0
					&& this.pathFindSource[x - 1][1 + z] == 0) {
					pathX[openListWrite] = x - 1;
					pathZ[openListWrite] = 1 + z;
					openListWrite = (1 + openListWrite) % openListSize;
					this.pathFindSource[x - 1][z + 1] = CollisionFlag.SOURCE_NORTH_WEST;
				}

				if (x < LOCAL_FACE_TILE_COUNT && z < LOCAL_FACE_TILE_COUNT && (CollisionFlag.NORTH_BLOCKED & this.collisionFlags[x][1 + z]) == 0
					&& (this.collisionFlags[x + 1][z] & CollisionFlag.EAST_BLOCKED) == 0
					&& (CollisionFlag.NORTH_EAST_BLOCKED & this.collisionFlags[x + 1][1 + z]) == 0
					&& this.pathFindSource[x + 1][1 + z] == 0) {
					pathX[openListWrite] = 1 + x;
					pathZ[openListWrite] = 1 + z;
					this.pathFindSource[1 + x][1 + z] = CollisionFlag.SOURCE_NORTH_EAST;
					openListWrite = (openListWrite + 1) % openListSize;
				}
			}

			if (!complete)
				return -1;
			else {
				pathX[0] = x;
				pathZ[0] = z;
				openListRead = 1;

				int prevSource;
				int source = prevSource = this.pathFindSource[x][z];
				while (x != startX || z != startZ) {
					if (prevSource != source) {
						prevSource = source;
						pathX[openListRead] = x;
						pathZ[openListRead++] = z;
					}

					if ((source & CollisionFlag.SOURCE_SOUTH) != 0)
						++z;
					else if ((CollisionFlag.SOURCE_NORTH & source) != 0)
						--z;

					if ((CollisionFlag.SOURCE_WEST & source) != 0)
						++x;
					else if ((source & CollisionFlag.SOURCE_EAST) != 0)
						--x;
					source = this.pathFindSource[x][z];
				}
				return openListRead;
			}
		} catch (RuntimeException var19) {
			throw GenUtil.makeThrowable(var19,
				"k.Q(" + (pathX != null ? "{...}" : "null") + ',' + xLow + ',' + "dummy" + ',' + zHigh + ','
					+ (pathZ != null ? "{...}" : "null") + ',' + startX + ',' + startZ + ',' + xHigh + ','
					+ zLow + ',' + reachBorder + ')');
		}
	}

	private void generateLandscapeModel(int var1, int var2, boolean showWallOnMinimap, int plane, int var5) {
		try {

			int chunkX = worldTileToSection(var1);
			int chunkZ = worldTileToSection(var5);
			boolean bridgeDecorationsApplied = this.loadSectionWindow(sectors, plane, chunkX, chunkZ);
			if (var2 >= 66) {
				if (!bridgeDecorationsApplied) {
					this.setTileDecorationOnBridge();
				}
				if (this.modelAccumulate == null)
					this.modelAccumulate = new RSModel(MODEL_BUFFER_CAPACITY, MODEL_BUFFER_CAPACITY, true, true, false, false, true);

				boolean includeRoofGeometry = !Config.C_HIDE_ROOFS;
				WorldModelProduct worldProduct = this.loadWorldModelProduct(
					plane,
					chunkX,
					chunkZ,
					showWallOnMinimap,
					includeRoofGeometry);
				if (showWallOnMinimap) {
					this.minimapGraphics.blackScreen(true);

					for (int x = 0; x < LOCAL_TILE_COUNT; ++x)
						for (int z = 0; z < LOCAL_TILE_COUNT; ++z)
							this.collisionFlags[x][z] = 0;

					RSModel worldMod = this.modelAccumulate;
					worldMod.resetFaceVertHead((int) 1);

					this.emitTerrainProduct(worldProduct.terrainInput, worldMod);

					this.publishTerrainProduct(worldMod);
				}
				this.modelAccumulate.resetFaceVertHead((int) 1);
				this.emitWallProduct(worldProduct.wallInput, showWallOnMinimap);
				this.publishWallProduct(plane);

				if (includeRoofGeometry) {
					this.emitRoofFaceProduct(worldProduct.roofInput);
					this.publishRoofProduct(plane, worldProduct.roofInput);
				} else {
					this.publishHiddenRoofProduct(plane, worldProduct.roofInput);
				}
			}
		} catch (RuntimeException var37) {
			throw GenUtil.makeThrowable(var37,
				"k.I(" + var1 + ',' + var2 + ',' + showWallOnMinimap + ',' + plane + ',' + var5 + ')');
		}
	}

	private WorldModelProduct loadWorldModelProduct(
		int plane,
		int sectionX,
		int sectionY,
		boolean includeTerrain,
		boolean includeRoofGeometry) {
		String key = worldModelProductKey(plane, sectionX, sectionY, includeRoofGeometry);
		WorldModelProduct cached;
		synchronized (worldModelProductCacheLock) {
			cached = worldModelProductCache.get(key);
		}
		if (cached != null && cached.hasTerrainIfNeeded(includeTerrain)) {
			this.worldStreamManager.markWorldProductCacheHit(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return cached;
		}

		TerrainModelInput terrainInput = cached == null ? null : cached.terrainInput;
		WallModelInput wallInput = cached == null ? null : cached.wallInput;
		RoofModelInput roofInput = cached == null ? null : cached.roofInput;
		if (includeTerrain && terrainInput == null) {
			terrainInput = this.loadTerrainModelInput(plane, sectionX, sectionY);
		}
		if (wallInput == null) {
			wallInput = this.loadWallModelInput(plane, sectionX, sectionY);
		}
		if (terrainInput != null) {
			terrainInput = applyWallEndpointShadows(terrainInput, wallInput);
		}
		if (roofInput == null) {
			roofInput = this.loadRoofModelInput(plane, sectionX, sectionY);
		}

		WorldGpuChunkMesh gpuChunkMesh = buildWorldGpuChunkMesh(
			plane,
			sectionX,
			sectionY,
			terrainInput,
			wallInput,
			roofInput,
			includeRoofGeometry);
		WorldModelProduct built = new WorldModelProduct(terrainInput, wallInput, roofInput, gpuChunkMesh);
		if (!key.equals(worldModelProductKey(
				plane, sectionX, sectionY, includeRoofGeometry))) {
			return built;
		}
		boolean storedBuilt = false;
		synchronized (worldModelProductCacheLock) {
			cached = worldModelProductCache.get(key);
			if (cached == null || !cached.hasTerrainIfNeeded(includeTerrain)) {
				worldModelProductCache.put(key, built);
				storedBuilt = true;
			}
		}
		if (storedBuilt) {
			this.worldStreamManager.markWorldProductBuilt(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			this.worldStreamManager.markGpuMeshProductBuilt(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET,
				built.gpuChunkMesh.getTriangleCount());
			return built;
		}
		this.worldStreamManager.markWorldProductCacheHit(
			plane,
			sectionX,
			sectionY,
			ACTIVE_SECTION_GRID,
			ACTIVE_SECTION_ORIGIN_OFFSET);
		return cached;
	}

	public Renderer3DWorldChunkFrame getRenderer3DWorldChunkFrame() {
		return renderer3DWorldChunkFrame;
	}

	private Renderer3DWorldChunkFrame buildRenderer3DWorldChunkFrame(int plane, int sectionX, int sectionY) {
		boolean includeUpperPlanes =
			plane == 0 && !syntheticDeepFixtureTerrain;
		List<Renderer3DWorldChunkFrame.ChunkMesh> chunks =
			new ArrayList<Renderer3DWorldChunkFrame.ChunkMesh>(
				includeUpperPlanes ? 3 : 1);
		addRenderer3DWorldChunkMesh(chunks, plane, sectionX, sectionY, true);
		if (includeUpperPlanes) {
			addRenderer3DWorldChunkMesh(chunks, 1, sectionX, sectionY, false);
			addRenderer3DWorldChunkMesh(chunks, 2, sectionX, sectionY, false);
		}
		return Renderer3DWorldChunkFrame.fromChunks(chunks);
	}

	private void addRenderer3DWorldChunkMesh(
		List<Renderer3DWorldChunkFrame.ChunkMesh> chunks,
		int plane,
		int sectionX,
		int sectionY,
		boolean requireTerrain) {
		WorldModelProduct product;
		synchronized (worldModelProductCacheLock) {
			product = worldModelProductCache.get(worldModelProductKey(plane, sectionX, sectionY, !Config.C_HIDE_ROOFS));
		}
		if (product == null || !product.hasTerrainIfNeeded(requireTerrain) || product.gpuChunkMesh == null) {
			return;
		}
		Renderer3DWorldChunkFrame.ChunkMesh chunk = product.gpuChunkMesh.toRenderer3DWorldChunkMesh();
		if (requireTerrain && product.terrainInput != null) {
			chunk.setWorldEditorTerrainGrid(LOCAL_TILE_COUNT, worldEditorTerrainGridHeights(product.terrainInput));
		}
		chunks.add(chunk);
	}

	private static int[] worldEditorTerrainGridHeights(TerrainModelInput input) {
		int[] heights = new int[input.vertices.length];
		for (int index = 0; index < heights.length; index++) {
			TerrainVertexInput vertex = input.vertices[index];
			heights[index] = vertex == null ? 0 : vertex.y;
		}
		return heights;
	}

	private static WorldGpuChunkMesh buildWorldGpuChunkMesh(
		int plane,
		int sectionX,
		int sectionY,
		TerrainModelInput terrainInput,
		WallModelInput wallInput,
		RoofModelInput roofInput,
		boolean includeRoofGeometry) {
		int originWorldX = (sectionX - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE * 128;
		int originWorldZ = (sectionY - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE * 128;
		WorldGpuChunkMeshBuilder builder = new WorldGpuChunkMeshBuilder(
			plane,
			sectionX,
			sectionY,
			originWorldX,
			originWorldZ);
		if (roofInput != null) {
			builder.setRoofCoverage(
				roofInput.roofCoverageBits,
				LOCAL_FACE_TILE_COUNT,
				roofInput.roofCoveredTileCount);
		}
		// The current chunk frame is the active 3x3 landscape product. Keep draw
		// vertices in the same local coordinate space as Scene camera offsets.
		int drawOriginX = 0;
		int drawOriginZ = 0;
		if (terrainInput != null) {
			addTerrainGpuChunkMesh(builder, terrainInput, drawOriginX, drawOriginZ);
		}
		addWallGpuChunkMesh(builder, wallInput, drawOriginX, drawOriginZ);
		if (includeRoofGeometry) {
			addRoofGpuChunkMesh(builder, roofInput, drawOriginX, drawOriginZ);
		}
		return builder.build();
	}

	private static void addTerrainGpuChunkMesh(
		WorldGpuChunkMeshBuilder builder,
		TerrainModelInput input,
		int drawOriginX,
		int drawOriginZ) {
		for (TerrainTileFaceInput face : input.tileFaces) {
			if (face.lavaGlowEmitter) {
				addTerrainTileGlowEmitter(builder, input, face, drawOriginX, drawOriginZ);
			}
			addTerrainTileGpuFaces(builder, input, face, drawOriginX, drawOriginZ);
		}

		for (TerrainOverlayFaceInput overlay : input.overlayFaces) {
			builder.addFace(
				Renderer3DModelKind.TERRAIN,
				overlay.materialFamily,
				overlay.texture,
				Scene.TRANSPARENT,
				offsetCoords(drawOriginX, drawOriginZ, overlay.vertexCoords),
				null);
		}
	}

	private static void addTerrainTileGlowEmitter(
		WorldGpuChunkMeshBuilder builder,
		TerrainModelInput input,
		TerrainTileFaceInput face,
		int drawOriginX,
		int drawOriginZ) {
		int baseIndex = face.z + face.x * LOCAL_TILE_COUNT;
		TerrainVertexInput southwest = input.vertices[baseIndex];
		TerrainVertexInput southeast = input.vertices[baseIndex + LOCAL_TILE_COUNT];
		TerrainVertexInput northwest = input.vertices[baseIndex + 1];
		TerrainVertexInput northeast = input.vertices[baseIndex + LOCAL_TILE_COUNT + 1];
		int centerY = (southwest.y + southeast.y + northwest.y + northeast.y) / 4;
		builder.addGlowEmitter(
			Renderer3DModelKind.TERRAIN,
			drawOriginX + face.x * 128 + 64,
			centerY,
			drawOriginZ + face.z * 128 + 64,
			LAVA_GLOW_RADIUS,
			LAVA_GLOW_COLOR,
			builder.plane == 0 ? LAVA_GLOW_INTENSITY : LAVA_GLOW_NON_OVERWORLD_INTENSITY);
	}

	private static void addTerrainTileGpuFaces(
		WorldGpuChunkMeshBuilder builder,
		TerrainModelInput input,
		TerrainTileFaceInput face,
		int drawOriginX,
		int drawOriginZ) {
			if (face.colorResource == face.res01 && face.slope == 0) {
				if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
					addTerrainQuadByVertexIndex(builder, input, Scene.TRANSPARENT, face.colorResource, drawOriginX, drawOriginZ,
						face.materialFamily,
						face.terrainVariationEligible ? 1 : 0,
						face.z - (-(face.x * LOCAL_TILE_COUNT) - LOCAL_TILE_COUNT),
						face.z + face.x * LOCAL_TILE_COUNT,
						1 + face.x * LOCAL_TILE_COUNT + face.z,
					face.z - (-(face.x * LOCAL_TILE_COUNT) - LOCAL_TILE_COUNT) + 1);
			}
			return;
		}

		if (face.bridge00_11 == 0) {
			if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
				addTerrainTriangleByVertexIndex(builder, input, Scene.TRANSPARENT, face.colorResource, drawOriginX, drawOriginZ,
					face.materialFamily,
					face.terrainVariationEligible ? 1 : 0,
					LOCAL_TILE_COUNT + face.z + face.x * LOCAL_TILE_COUNT,
					face.x * LOCAL_TILE_COUNT + face.z,
					1 + face.z + face.x * LOCAL_TILE_COUNT);
			}

			if (face.res01 != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
				addTerrainTriangleByVertexIndex(builder, input, Scene.TRANSPARENT, face.res01, drawOriginX, drawOriginZ,
					face.materialFamily,
					face.terrainVariationEligible ? 1 : 0,
					1 + face.x * LOCAL_TILE_COUNT + face.z,
					LOCAL_TILE_COUNT + 1 + face.x * LOCAL_TILE_COUNT + face.z,
					face.z + face.x * LOCAL_TILE_COUNT + LOCAL_TILE_COUNT);
			}
			return;
		}

		if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
			addTerrainTriangleByVertexIndex(builder, input, Scene.TRANSPARENT, face.colorResource, drawOriginX, drawOriginZ,
				face.materialFamily,
				face.terrainVariationEligible ? 1 : 0,
				1 + face.x * LOCAL_TILE_COUNT + face.z,
				LOCAL_TILE_COUNT + face.x * LOCAL_TILE_COUNT + face.z + 1,
				face.z + face.x * LOCAL_TILE_COUNT);
		}

		if (face.res01 != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
			addTerrainTriangleByVertexIndex(builder, input, Scene.TRANSPARENT, face.res01, drawOriginX, drawOriginZ,
				face.materialFamily,
				face.terrainVariationEligible ? 1 : 0,
				face.x * LOCAL_TILE_COUNT + face.z + LOCAL_TILE_COUNT,
				face.z + face.x * LOCAL_TILE_COUNT,
				face.z - (-(face.x * LOCAL_TILE_COUNT) - (LOCAL_TILE_COUNT + 1)));
		}
	}

	private static void addTerrainQuadByVertexIndex(
		WorldGpuChunkMeshBuilder builder,
		TerrainModelInput input,
		int texture,
		int fallbackColor,
		int drawOriginX,
		int drawOriginZ,
		Renderer3DMaterialFamily materialFamily,
		int terrainVariationMask,
		int a,
		int b,
		int c,
		int d) {
		TerrainVertexInput va = input.vertices[a];
		TerrainVertexInput vb = input.vertices[b];
		TerrainVertexInput vc = input.vertices[c];
		TerrainVertexInput vd = input.vertices[d];
		builder.addFace(
			Renderer3DModelKind.TERRAIN,
			materialFamily,
			texture,
			fallbackColor,
			new int[] {
				drawOriginX + va.x, va.y, drawOriginZ + va.z,
				drawOriginX + vb.x, vb.y, drawOriginZ + vb.z,
				drawOriginX + vc.x, vc.y, drawOriginZ + vc.z,
				drawOriginX + vd.x, vd.y, drawOriginZ + vd.z
			},
			new int[] {va.light, vb.light, vc.light, vd.light},
			new int[] {va.terrainBlendColor, vb.terrainBlendColor, vc.terrainBlendColor, vd.terrainBlendColor},
			new int[] {va.terrainBlendStrength, vb.terrainBlendStrength, vc.terrainBlendStrength, vd.terrainBlendStrength},
			terrainVariationMask);
	}

	private static void addTerrainTriangleByVertexIndex(
		WorldGpuChunkMeshBuilder builder,
		TerrainModelInput input,
		int texture,
		int fallbackColor,
		int drawOriginX,
		int drawOriginZ,
		Renderer3DMaterialFamily materialFamily,
		int terrainVariationMask,
		int a,
		int b,
		int c) {
		TerrainVertexInput va = input.vertices[a];
		TerrainVertexInput vb = input.vertices[b];
		TerrainVertexInput vc = input.vertices[c];
		builder.addFace(
			Renderer3DModelKind.TERRAIN,
			materialFamily,
			texture,
			fallbackColor,
			new int[] {
				drawOriginX + va.x, va.y, drawOriginZ + va.z,
				drawOriginX + vb.x, vb.y, drawOriginZ + vb.z,
				drawOriginX + vc.x, vc.y, drawOriginZ + vc.z
			},
			new int[] {va.light, vb.light, vc.light},
			new int[] {va.terrainBlendColor, vb.terrainBlendColor, vc.terrainBlendColor},
			new int[] {va.terrainBlendStrength, vb.terrainBlendStrength, vc.terrainBlendStrength},
			terrainVariationMask);
	}

	private static void addWallGpuChunkMesh(
		WorldGpuChunkMeshBuilder builder,
		WallModelInput input,
		int drawOriginX,
		int drawOriginZ) {
		for (WallSegmentInput segment : input.segments) {
			builder.addFace(
				Renderer3DModelKind.WALL,
				segment.frontTexture,
				segment.backTexture,
				offsetCoords(drawOriginX, drawOriginZ, segment.vertexCoords),
				null);
		}
	}

	private static void addRoofGpuChunkMesh(
		WorldGpuChunkMeshBuilder builder,
		RoofModelInput input,
		int drawOriginX,
		int drawOriginZ) {
		for (RoofFaceInput face : input.faces) {
			builder.addFace(
				Renderer3DModelKind.ROOF,
				face.texture,
				Scene.TRANSPARENT,
				offsetCoords(drawOriginX, drawOriginZ, face.vertexCoords),
				null);
		}
	}

	private static int[] offsetCoords(int offsetX, int offsetZ, int[] localCoords) {
		int[] offsetCoords = localCoords == null ? new int[0] : localCoords.clone();
		for (int coord = 0; coord + 2 < offsetCoords.length; coord += 3) {
			offsetCoords[coord] += offsetX;
			offsetCoords[coord + 2] += offsetZ;
		}
		return offsetCoords;
	}

	private static TerrainModelInput applyWallEndpointShadows(
		TerrainModelInput input,
		WallModelInput wallInput) {
		if (input == null || wallInput == null || wallInput.segments.length == 0) {
			return input;
		}
		TerrainVertexInput[] vertices = input.vertices.clone();
		boolean changed = false;
		for (WallSegmentInput segment : wallInput.segments) {
			changed |= applyTerrainVertexLight(vertices, segment.vertexCoords[0] / 128, segment.vertexCoords[2] / 128, 40);
			changed |= applyTerrainVertexLight(vertices, segment.vertexCoords[9] / 128, segment.vertexCoords[11] / 128, 40);
		}
		return changed ? new TerrainModelInput(vertices, input.tileFaces, input.overlayFaces) : input;
	}

	private static boolean applyTerrainVertexLight(TerrainVertexInput[] vertices, int x, int z, int light) {
		if (!isLocalTile(x, z)) {
			return false;
		}
		int index = z + x * LOCAL_TILE_COUNT;
		if (index < 0 || index >= vertices.length) {
			return false;
		}
		TerrainVertexInput vertex = vertices[index];
		if (vertex == null || vertex.light == light) {
			return false;
		}
		vertices[index] = new TerrainVertexInput(
			vertex.x,
			vertex.y,
			vertex.z,
			light,
			vertex.terrainBlendColor,
			vertex.terrainBlendStrength);
		return true;
	}

	private TerrainModelInput buildTerrainModelInput(int plane, Sector[] sourceSectors) {
		TerrainModelInputSource source = new TerrainModelInputSource(sourceSectors);
		return new TerrainModelInput(
			collectTerrainVertexInputs(plane, source),
			collectTerrainTileFaceInputs(plane, source),
			collectTerrainOverlayFaceInputs(plane, source));
	}

	private TerrainModelInput loadTerrainModelInput(int plane, int sectionX, int sectionY) {
		String key = terrainModelInputKey(plane, sectionX, sectionY);
		TerrainModelInput cached;
		synchronized (terrainModelInputCacheLock) {
			cached = terrainModelInputCache.get(key);
		}
		if (cached != null) {
			this.worldStreamManager.markTerrainInputCacheHit(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return cached;
		}

		CpuSectionWindow window = loadCpuSectionWindow(plane, sectionX, sectionY);
		TerrainModelInput built = buildTerrainModelInput(plane, window.sectors);
		if (!key.equals(terrainModelInputKey(plane, sectionX, sectionY))) {
			return built;
		}
		boolean storedBuilt = false;
		synchronized (terrainModelInputCacheLock) {
			cached = terrainModelInputCache.get(key);
			if (cached == null) {
				terrainModelInputCache.put(key, built);
				storedBuilt = true;
			}
		}
		if (storedBuilt) {
			this.worldStreamManager.markTerrainInputBuilt(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return built;
		}
		this.worldStreamManager.markTerrainInputCacheHit(
			plane,
			sectionX,
			sectionY,
			ACTIVE_SECTION_GRID,
			ACTIVE_SECTION_ORIGIN_OFFSET);
		return cached;
	}

	private TerrainVertexInput[] collectTerrainVertexInputs(int plane, TerrainModelInputSource source) {
		TerrainVertexInput[] vertices = new TerrainVertexInput[LOCAL_TILE_COUNT * LOCAL_TILE_COUNT];
		for (int x = 0; x < LOCAL_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_TILE_COUNT; ++z) {
				int y = -source.tileElevation(x, z);
				if (source.tileDecorationID(x, z) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x, z) - 1)).getTileValue() == 4) {
					y = 0;
				}
				if (source.tileDecorationID(x - 1, z) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x - 1, z) - 1)).getTileValue() == 4) {
					y = 0;
				}

				if (source.tileDecorationID(x, z - 1) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x, z - 1) - 1)).getTileValue() == 4) {
					y = 0;
				}

				if (source.tileDecorationID(x - 1, z - 1) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x - 1, z - 1) - 1)).getTileValue() == 4) {
					y = 0;
				}

				int light = (int) (Math.random() * 10.0D) - 5;
				TerrainVertexBlendInput blend = terrainVertexBlendInput(plane, source, x, z);
				vertices[z + x * LOCAL_TILE_COUNT] =
					new TerrainVertexInput(x * 128, y, z * 128, light, blend.color, blend.strength);
			}
		}
		return vertices;
	}

	private TerrainVertexBlendInput terrainVertexBlendInput(
		int plane,
		TerrainModelInputSource source,
		int vertexX,
		int vertexZ) {
		if (plane != 0) {
			return TerrainVertexBlendInput.NONE;
		}

		int red = 0;
		int green = 0;
		int blue = 0;
		int count = 0;
		int firstColor = -1;
		boolean varied = false;
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = -1; dz <= 0; dz++) {
				int tileX = vertexX + dx;
				int tileZ = vertexZ + dz;
				if (tileX < 0 || tileZ < 0 || tileX >= LOCAL_FACE_TILE_COUNT || tileZ >= LOCAL_FACE_TILE_COUNT) {
					continue;
				}
				if (source.tileDecorationID(tileX, tileZ) != 0) {
					continue;
				}
				int color = terrainRgbForTile(source, tileX, tileZ);
				if (firstColor < 0) {
					firstColor = color;
				} else if (firstColor != color) {
					varied = true;
				}
				red += (color >> 16) & 0xff;
				green += (color >> 8) & 0xff;
				blue += color & 0xff;
				count++;
			}
		}
		if (count <= 0 || firstColor < 0) {
			return TerrainVertexBlendInput.NONE;
		}
		if (!varied) {
			return new TerrainVertexBlendInput(firstColor, 0);
		}
		int blendedColor = ((red / count) << 16) | ((green / count) << 8) | (blue / count);
		return new TerrainVertexBlendInput(blendedColor, 255);
	}

	private int terrainRgbForTile(TerrainModelInputSource source, int tileX, int tileZ) {
		return resourceToRgb(this.colorToResource[source.terrainColour(tileX, tileZ)]) & 0xffffff;
	}

	private static int resourceToRgb(int resource) {
		if (resource == Scene.TRANSPARENT) {
			return 0;
		}
		if (resource >= 0) {
			return resource;
		}

		int encoded = -(resource + 1);
		int red = (encoded & 0x7C00) >> 10;
		int green = (encoded & 0x3E0) >> 5;
		int blue = encoded & 0x1F;
		return (red << 19) + (green << 11) + (blue << 3);
	}

	private TerrainTileFaceInput[] collectTerrainTileFaceInputs(int plane, TerrainModelInputSource source) {
		TerrainTileFaceInput[] faces = new TerrainTileFaceInput[LOCAL_FACE_TILE_COUNT * LOCAL_FACE_TILE_COUNT];
		int count = 0;
		for (int x = 0; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_FACE_TILE_COUNT; ++z) {
				int colorResource = this.colorToResource[source.terrainColour(x, z)];
				int res01 = colorResource;
				int defaultVal = colorResource;
				if (plane == 1 || plane == 2) {
					colorResource = Scene.TRANSPARENT;
					res01 = Scene.TRANSPARENT;
					defaultVal = Scene.TRANSPARENT;
				}

				byte bridge00_11 = 0;
				boolean collisionFullBlock = false;
				boolean collisionObject = false;
				boolean waterLike = false;
				int decorID = source.tileDecorationID(x, z);
				boolean terrainVariationEligible = plane == 0 && decorID == 0;
				boolean lavaGlowEmitter = decorID == LAVA_GLOW_OVERLAY_ID;
				if (decorID > 0) {
					int decorType = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getTileValue();
					waterLike = decorType == 4;
					int decorType2 = source.tileType2(x, z);
					colorResource = res01 = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getColour();
					if (decorType == 4) {
						colorResource = 1;
						res01 = 1;
						if (decorID == 12) {
							colorResource = 31;
							res01 = 31;
						}
					}

					if (decorType == 5) {
						if (source.wallDiagonal(x, z) > 0 && source.wallDiagonal(x, z) < 24000) {
							if (source.tileDecorationCacheVal(x - 1, z, defaultVal) != Scene.TRANSPARENT
								&& source.tileDecorationCacheVal(x, z - 1, defaultVal) != Scene.TRANSPARENT) {
								bridge00_11 = 0;
								colorResource = source.tileDecorationCacheVal(x - 1, z, defaultVal);
							} else if (source.tileDecorationCacheVal(x + 1, z, defaultVal) != Scene.TRANSPARENT
								&& source.tileDecorationCacheVal(x, z + 1, defaultVal) != Scene.TRANSPARENT) {
								res01 = source.tileDecorationCacheVal(x + 1, z, defaultVal);
								bridge00_11 = 0;
							} else if (source.tileDecorationCacheVal(x + 1, z, defaultVal) != Scene.TRANSPARENT
								&& source.tileDecorationCacheVal(x, z - 1, defaultVal) != Scene.TRANSPARENT) {
								res01 = source.tileDecorationCacheVal(x + 1, z, defaultVal);
								bridge00_11 = 1;
							} else if (source.tileDecorationCacheVal(x - 1, z, defaultVal) != Scene.TRANSPARENT
								&& source.tileDecorationCacheVal(x, z + 1, defaultVal) != Scene.TRANSPARENT) {
								bridge00_11 = 1;
								colorResource = source.tileDecorationCacheVal(x - 1, z, defaultVal);
							}
						}
					} else if (decorType != 2
						|| source.wallDiagonal(x, z) > 0 && source.wallDiagonal(x, z) < 24000) {
						if (decorType2 != source.tileType2(x - 1, z)
							&& source.tileType2(x, z - 1) != decorType2) {
							colorResource = defaultVal;
							bridge00_11 = 0;
						} else if (decorType2 != source.tileType2(x + 1, z)
							&& source.tileType2(x, z + 1) != decorType2) {
							bridge00_11 = 0;
							res01 = defaultVal;
						} else if (decorType2 != source.tileType2(x + 1, z)
							&& source.tileType2(x, z - 1) != decorType2) {
							res01 = defaultVal;
							bridge00_11 = 1;
						} else if (decorType2 != source.tileType2(x - 1, z)
							&& decorType2 != source.tileType2(x, z + 1)) {
							colorResource = defaultVal;
							bridge00_11 = 1;
						}
					}

					collisionFullBlock = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getObjectType() != 0;
					collisionObject = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getTileValue() == 2;
				}

				int slope = source.tileElevation(x + 1, z + 1) - source.tileElevation(x, z)
					+ source.tileElevation(x, z + 1) - source.tileElevation(x + 1, z);
				faces[count++] = new TerrainTileFaceInput(
					x,
					z,
					bridge00_11,
					res01,
					colorResource,
					slope,
					source.pickableInvisibleOverlay(x, z),
					terrainVariationEligible,
					lavaGlowEmitter,
					Renderer3DMaterialClassifier.classifyTerrain(waterLike, lavaGlowEmitter),
					collisionFullBlock,
					collisionObject);
			}
		}
		return faces;
	}

	private TerrainOverlayFaceInput[] collectTerrainOverlayFaceInputs(int plane, TerrainModelInputSource source) {
		List<TerrainOverlayFaceInput> overlays = new ArrayList<TerrainOverlayFaceInput>();
		for (int x = 1; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 1; z < LOCAL_FACE_TILE_COUNT; ++z) {
				if (source.tileDecorationID(x, z) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x, z) - 1)).getTileValue() == 4) {

					int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(source.tileDecorationID(x, z) - 1))
						.getColour();
					addTerrainOverlayFace(overlays, x, z, tileDecor, source.tileDecorationID(x, z),
						x * 128, -source.tileElevation(x, z), z * 128,
						(x + 1) * 128, -source.tileElevation(x + 1, z), z * 128,
						(x + 1) * 128, -source.tileElevation(x + 1, z + 1), (z + 1) * 128,
						x * 128, -source.tileElevation(x, z + 1), 128 + z * 128);
				} else if (source.tileDecorationID(x, z) == 0 || Objects.requireNonNull(EntityHandler
					.getTileDef(source.tileDecorationID(x, z) - 1)).getTileValue() != 3) {
					if (source.tileDecorationID(x, z + 1) > 0 && !source.editorPaintedOverlay(x,z+1)
						&& Objects.requireNonNull(EntityHandler.getTileDef(source.tileDecorationID(x, z + 1) - 1))
						.getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler
							.getTileDef(source.tileDecorationID(x, z + 1) - 1))
							.getColour();
						addTerrainOverlayFace(overlays, x, z, tileDecor, source.tileDecorationID(x, z + 1),
							x * 128, -source.tileElevation(x, z), z * 128,
							(x + 1) * 128, -source.tileElevation(x + 1, z), z * 128,
							128 + x * 128, -source.tileElevation(x + 1, z + 1), (z + 1) * 128,
							x * 128, -source.tileElevation(x, z + 1), z * 128 + 128);
					}

					if (source.tileDecorationID(x, z - 1) > 0 && !source.editorPaintedOverlay(x,z-1)
						&& Objects.requireNonNull(EntityHandler.getTileDef(source.tileDecorationID(x, z - 1) - 1))
						.getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler
							.getTileDef(source.tileDecorationID(x, z - 1) - 1))
							.getColour();
						addTerrainOverlayFace(overlays, x, z, tileDecor, source.tileDecorationID(x, z - 1),
							x * 128, -source.tileElevation(x, z), z * 128,
							(x + 1) * 128, -source.tileElevation(x + 1, z), z * 128,
							(x + 1) * 128, -source.tileElevation(x + 1, z + 1), (z + 1) * 128,
							x * 128, -source.tileElevation(x, z + 1), 128 + z * 128);
					}

					if (source.tileDecorationID(x + 1, z) > 0 && !source.editorPaintedOverlay(x+1,z) && Objects.requireNonNull(EntityHandler
						.getTileDef(source.tileDecorationID(x + 1, z) - 1))
						.getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler
							.getTileDef(source.tileDecorationID(x + 1, z) - 1))
							.getColour();
						addTerrainOverlayFace(overlays, x, z, tileDecor, source.tileDecorationID(x + 1, z),
							x * 128, -source.tileElevation(x, z), z * 128,
							128 + x * 128, -source.tileElevation(x + 1, z), z * 128,
							(x + 1) * 128, -source.tileElevation(x + 1, z + 1), z * 128 + 128,
							x * 128, -source.tileElevation(x, z + 1), (z + 1) * 128);
					}

					if (source.tileDecorationID(x - 1, z) > 0 && !source.editorPaintedOverlay(x-1,z) && Objects.requireNonNull(EntityHandler
						.getTileDef(source.tileDecorationID(x - 1, z) - 1))
						.getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler
							.getTileDef(source.tileDecorationID(x - 1, z) - 1))
							.getColour();
						addTerrainOverlayFace(overlays, x, z, tileDecor, source.tileDecorationID(x - 1, z),
							x * 128, -source.tileElevation(x, z), z * 128,
							(x + 1) * 128, -source.tileElevation(x + 1, z), z * 128,
							128 + x * 128, -source.tileElevation(x + 1, z + 1), z * 128 + 128,
							x * 128, -source.tileElevation(x, z + 1), (z + 1) * 128);
					}
				}
			}
		}
		return overlays.toArray(new TerrainOverlayFaceInput[overlays.size()]);
	}

	private void addTerrainOverlayFace(
		List<TerrainOverlayFaceInput> overlays,
		int x,
		int z,
		int texture,
		int decorationId,
		int... vertexCoords) {
		overlays.add(new TerrainOverlayFaceInput(
			x,
			z,
			texture,
			Renderer3DMaterialClassifier.classifyTerrain(true, decorationId == LAVA_GLOW_OVERLAY_ID),
			vertexCoords));
	}

	private void emitTerrainProduct(TerrainModelInput input, RSModel worldMod) {
		emitTerrainVertices(input, worldMod);
		emitTerrainFaceProduct(input, worldMod);
	}

	private void emitTerrainVertices(TerrainModelInput input, RSModel worldMod) {
		for (TerrainVertexInput vertex : input.vertices) {
			int vID = worldMod.insertVertex(vertex.x, vertex.y, vertex.z);
			worldMod.setVertexLightOther(vID, vertex.light);
		}
	}

	private void emitTerrainFaceProduct(TerrainModelInput input, RSModel worldMod) {
		for (TerrainTileFaceInput face : input.tileFaces) {
			if (face.collisionFullBlock) {
				this.collisionFlags[face.x][face.z] = FastMath.bitwiseOr(this.collisionFlags[face.x][face.z],
					CollisionFlag.FULL_BLOCK_C);
			}
			if (face.collisionObject) {
				this.collisionFlags[face.x][face.z] = FastMath.bitwiseOr(this.collisionFlags[face.x][face.z],
					CollisionFlag.OBJECT);
			}
			this.drawMinimapTile(face.x, face.z, face.bridge00_11, face.res01, face.colorResource);
			emitTerrainTileFace(worldMod, face);
		}

		for (TerrainOverlayFaceInput overlay : input.overlayFaces) {
			int[] indices = new int[4];
			for (int i = 0; i < 4; i++) {
				int offset = i * 3;
				indices[i] = worldMod.insertVertex(
					overlay.vertexCoords[offset],
					overlay.vertexCoords[offset + 1],
					overlay.vertexCoords[offset + 2]);
			}
			int faceID = worldMod.insertFace(4, indices, overlay.texture, Scene.TRANSPARENT, false);
			this.faceTileX[faceID] = overlay.x;
			this.faceTileZ[faceID] = overlay.z;
			worldMod.facePickIndex[faceID] = faceID + 200000;
			this.drawMinimapTile(overlay.x, overlay.z, 0, overlay.texture, overlay.texture);
		}
	}

	private void emitTerrainTileFace(RSModel worldMod, TerrainTileFaceInput face) {
		if (face.colorResource == face.res01 && face.slope == 0) {
			if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
				int[] faceIndicies = new int[]{face.z - (-(face.x * LOCAL_TILE_COUNT) - LOCAL_TILE_COUNT),
					face.z + face.x * LOCAL_TILE_COUNT, 1 + face.x * LOCAL_TILE_COUNT + face.z,
					face.z - (-(face.x * LOCAL_TILE_COUNT) - LOCAL_TILE_COUNT) + 1};
				int faceID = worldMod.insertFace(4, faceIndicies, Scene.TRANSPARENT, face.colorResource, false);
				this.faceTileX[faceID] = face.x;
				this.faceTileZ[faceID] = face.z;
				worldMod.facePickIndex[faceID] = faceID + 200000;
			}
			return;
		}

		int[] faceIndicies = new int[3];
		int[] faceIndices2 = new int[3];
		if (face.bridge00_11 == 0) {
			if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
				faceIndicies[1] = face.x * LOCAL_TILE_COUNT + face.z;
				faceIndicies[0] = LOCAL_TILE_COUNT + face.z + face.x * LOCAL_TILE_COUNT;
				faceIndicies[2] = 1 + face.z + face.x * LOCAL_TILE_COUNT;
				int faceID = worldMod.insertFace(3, faceIndicies, Scene.TRANSPARENT, face.colorResource, false);
				this.faceTileX[faceID] = face.x;
				this.faceTileZ[faceID] = face.z;
				worldMod.facePickIndex[faceID] = faceID + 200000;
			}

			if (face.res01 != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
				faceIndices2[2] = face.z + face.x * LOCAL_TILE_COUNT + LOCAL_TILE_COUNT;
				faceIndices2[1] = LOCAL_TILE_COUNT + 1 + face.x * LOCAL_TILE_COUNT + face.z;
				faceIndices2[0] = 1 + face.x * LOCAL_TILE_COUNT + face.z;
				int faceID = worldMod.insertFace(3, faceIndices2, Scene.TRANSPARENT, face.res01, false);
				this.faceTileX[faceID] = face.x;
				this.faceTileZ[faceID] = face.z;
				worldMod.facePickIndex[faceID] = faceID + 200000;
			}
			return;
		}

		if (face.colorResource != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
			faceIndicies[2] = face.z + face.x * LOCAL_TILE_COUNT;
			faceIndicies[1] = LOCAL_TILE_COUNT + face.x * LOCAL_TILE_COUNT + face.z + 1;
			faceIndicies[0] = 1 + face.x * LOCAL_TILE_COUNT + face.z;
			int faceID = worldMod.insertFace(3, faceIndicies, Scene.TRANSPARENT, face.colorResource, false);
			this.faceTileX[faceID] = face.x;
			this.faceTileZ[faceID] = face.z;
			worldMod.facePickIndex[faceID] = 200000 + faceID;
		}

		if (face.res01 != Scene.TRANSPARENT || face.pickableInvisibleOverlay) {
			faceIndices2[1] = face.z + face.x * LOCAL_TILE_COUNT;
			faceIndices2[2] = face.z - (-(face.x * LOCAL_TILE_COUNT) - (LOCAL_TILE_COUNT + 1));
			faceIndices2[0] = face.x * LOCAL_TILE_COUNT + face.z + LOCAL_TILE_COUNT;
			int faceID = worldMod.insertFace(3, faceIndices2, Scene.TRANSPARENT, face.res01, false);
			this.faceTileX[faceID] = face.x;
			this.faceTileZ[faceID] = face.z;
			worldMod.facePickIndex[faceID] = faceID + 200000;
		}
	}

	private void publishTerrainProduct(RSModel worldMod) {
		worldMod.setDiffuseLightAndColor(-50, -10, -50, 40, 48, true, 105);
		this.modelLandscapeGrid = this.modelAccumulate.divideModelByGrid(0, MODEL_GRID_AXIS,
			MODEL_GRID_WORLD_SIZE, 112, MODEL_GRID_COUNT, MODEL_SPLIT_VERTEX_LIMIT,
			MODEL_GRID_WORLD_SIZE, false, 0);
		tagRenderer3DModels(this.modelLandscapeGrid, Renderer3DModelKind.TERRAIN);

		for (int x = 0; x < MODEL_GRID_COUNT; ++x) {
			this.scene.addModel(this.modelLandscapeGrid[x]);
		}

		for (int x = 0; x < LOCAL_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_TILE_COUNT; ++z) {
				this.tileElevationCache[x][z] = this.getTileElevation(x, z);
			}
		}
	}

	private WallModelInput loadWallModelInput(int plane, int sectionX, int sectionY) {
		String key = wallModelInputKey(plane, sectionX, sectionY);
		WallModelInput cached;
		synchronized (wallModelInputCacheLock) {
			cached = wallModelInputCache.get(key);
		}
		if (cached != null) {
			this.worldStreamManager.markWallInputCacheHit(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return cached;
		}

		CpuSectionWindow window = loadCpuSectionWindow(plane, sectionX, sectionY);
		WallModelInput built = buildWallModelInput(window.sectors);
		if (!key.equals(wallModelInputKey(plane, sectionX, sectionY))) {
			return built;
		}
		boolean storedBuilt = false;
		synchronized (wallModelInputCacheLock) {
			cached = wallModelInputCache.get(key);
			if (cached == null) {
				wallModelInputCache.put(key, built);
				storedBuilt = true;
			}
		}
		if (storedBuilt) {
			this.worldStreamManager.markWallInputBuilt(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return built;
		}
		this.worldStreamManager.markWallInputCacheHit(
			plane,
			sectionX,
			sectionY,
			ACTIVE_SECTION_GRID,
			ACTIVE_SECTION_ORIGIN_OFFSET);
		return cached;
	}

	private WallModelInput buildWallModelInput(Sector[] sourceSectors) {
		TerrainModelInputSource source = new TerrainModelInputSource(sourceSectors);
		List<WallSegmentInput> segments = new ArrayList<WallSegmentInput>();
		for (int x = 0; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_FACE_TILE_COUNT; ++z) {
				addWallSegmentInput(segments, source, source.verticalWall(x, z), WallSegmentInput.VERTICAL, x, z,
					1 + x, z, x, z);
				addWallSegmentInput(segments, source, source.horizontalWall(x, z), WallSegmentInput.HORIZONTAL, x, z,
					x, z, x, 1 + z);

				int wall = source.wallDiagonal(x, z);
				if (wall > 0 && wall < 12000) {
					addWallSegmentInput(segments, source, wall, WallSegmentInput.DIAGONAL_A, x, z,
						x + 1, z, x, 1 + z);
				} else if (wall > 12000 && wall < 24000) {
					addWallSegmentInput(segments, source, wall - 12000, WallSegmentInput.DIAGONAL_B, x, z,
						x, z, x + 1, 1 + z);
				}
			}
		}
		return new WallModelInput(segments.toArray(new WallSegmentInput[segments.size()]));
	}

	private void addWallSegmentInput(
		List<WallSegmentInput> segments,
		TerrainModelInputSource source,
		int wall,
		int kind,
		int x,
		int z,
		int t2X,
		int t1Z,
		int t1X,
		int t2Z) {
		if (wall <= 0) {
			return;
		}
		int wallID = wall - 1;
		if (Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getUnknown() == 0 || this.showInvisibleWalls) {
			int height = Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getWallObjectHeight();
			int frontTexture = Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getModelVar2();
			int backTexture = Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getModelVar3();
			int x1 = t1X * 128;
			int z1 = t1Z * 128;
			int x2 = t2X * 128;
			int z2 = t2Z * 128;
			int y1 = -source.tileElevation(t1X, t1Z);
			int y2 = -source.tileElevation(t2X, t2Z);
			int facePickIndex = Objects.requireNonNull(EntityHandler.getDoorDef(wallID)).getUnknown() == 5
				? 30000 + wallID
				: 0;
			segments.add(new WallSegmentInput(wallID, kind, x, z, frontTexture, backTexture, facePickIndex,
				x1, y1, z1,
				x1, y1 - height, z1,
				x2, y2 - height, z2,
				x2, y2, z2));
		}
	}

	private void emitWallProduct(WallModelInput input, boolean showWallOnMinimap) {
		final int wallColor = 6316128;
		for (WallSegmentInput segment : input.segments) {
			applyWallSegmentTerrainLight(segment);
			int[] indices = new int[4];
			for (int i = 0; i < indices.length; i++) {
				int offset = i * 3;
				indices[i] = this.modelAccumulate.insertVertex(
					segment.vertexCoords[offset],
					segment.vertexCoords[offset + 1],
					segment.vertexCoords[offset + 2]);
			}
			int face = this.modelAccumulate.insertFace(4, indices, segment.frontTexture, segment.backTexture, false);
			this.modelAccumulate.facePickIndex[face] = segment.facePickIndex;
			if (showWallOnMinimap && Objects.requireNonNull(EntityHandler.getDoorDef(segment.wallID)).getDoorType() != 0) {
				applyWallSegmentCollision(segment);
			}
			if (showWallOnMinimap && isLegacyMinimapFaceTile(segment.x, segment.z)) {
				drawWallSegmentMinimap(segment, wallColor);
			}
		}

		if (showWallOnMinimap) {
			this.minimapGraphics.copyPixelDataToSurface(GraphicsController.SPRITE_LAYER.MINIMAP, 0, 0,
				MINIMAP_PIXEL_SIZE, MINIMAP_PIXEL_SIZE);
		}
	}

	private void applyWallSegmentTerrainLight(WallSegmentInput segment) {
		this.setVertexLightOther(segment.vertexCoords[0] / 128, segment.vertexCoords[2] / 128, 40);
		this.setVertexLightOther(segment.vertexCoords[9] / 128, segment.vertexCoords[11] / 128, 40);
	}

	private void applyWallSegmentCollision(WallSegmentInput segment) {
		if (segment.kind == WallSegmentInput.VERTICAL) {
			this.collisionFlags[segment.x][segment.z] = FastMath.bitwiseOr(this.collisionFlags[segment.x][segment.z],
				CollisionFlag.WALL_NORTH);
			if (segment.z > 0) {
				this.collisionFlagBitwiseOr(segment.x, segment.z - 1, CollisionFlag.WALL_SOUTH);
			}
		} else if (segment.kind == WallSegmentInput.HORIZONTAL) {
			this.collisionFlags[segment.x][segment.z] = FastMath.bitwiseOr(this.collisionFlags[segment.x][segment.z],
				CollisionFlag.WALL_EAST);
			if (segment.x > 0) {
				this.collisionFlagBitwiseOr(segment.x - 1, segment.z, CollisionFlag.WALL_WEST);
			}
		} else if (segment.kind == WallSegmentInput.DIAGONAL_A) {
			this.collisionFlags[segment.x][segment.z] = FastMath.bitwiseOr(this.collisionFlags[segment.x][segment.z],
				CollisionFlag.FULL_BLOCK_B);
		} else if (segment.kind == WallSegmentInput.DIAGONAL_B) {
			this.collisionFlags[segment.x][segment.z] = FastMath.bitwiseOr(this.collisionFlags[segment.x][segment.z],
				CollisionFlag.FULL_BLOCK_A);
		}
	}

	private void drawWallSegmentMinimap(WallSegmentInput segment, int wallColor) {
		if (segment.kind == WallSegmentInput.VERTICAL) {
			this.minimapGraphics.drawLineHoriz(segment.x * 3, segment.z * 3, 3, wallColor);
		} else if (segment.kind == WallSegmentInput.HORIZONTAL) {
			this.minimapGraphics.drawLineVert(segment.x * 3, segment.z * 3, wallColor, 3);
		} else if (segment.kind == WallSegmentInput.DIAGONAL_A) {
			this.minimapGraphics.setPixel(segment.x * 3, segment.z * 3, wallColor);
			this.minimapGraphics.setPixel(1 + segment.x * 3, 1 + segment.z * 3, wallColor);
			this.minimapGraphics.setPixel(segment.x * 3 + 2, 2 + segment.z * 3, wallColor);
		} else if (segment.kind == WallSegmentInput.DIAGONAL_B) {
			this.minimapGraphics.setPixel(2 + segment.x * 3, segment.z * 3, wallColor);
			this.minimapGraphics.setPixel(segment.x * 3 + 1, segment.z * 3 + 1, wallColor);
			this.minimapGraphics.setPixel(segment.x * 3, 2 + segment.z * 3, wallColor);
		}
	}

	private void publishWallProduct(int plane) {
		this.modelAccumulate.setDiffuseLightAndColor(-50, -10, -50, 60, 24, false, 122);
		this.modelWallGrid[plane] = this.modelAccumulate.divideModelByGrid(0, MODEL_GRID_AXIS,
			MODEL_GRID_WORLD_SIZE, -120, MODEL_GRID_COUNT, MODEL_SPLIT_VERTEX_LIMIT, MODEL_GRID_WORLD_SIZE,
			true, 0);
		tagRenderer3DModels(this.modelWallGrid[plane], Renderer3DModelKind.WALL);

		for (int x = 0; x < MODEL_GRID_COUNT; ++x) {
			this.scene.addModel(this.modelWallGrid[plane][x]);
		}
		this.modelAccumulate.resetFaceVertHead((int) 1);
	}

	private RoofModelInput loadRoofModelInput(int plane, int sectionX, int sectionY) {
		String key = roofModelInputKey(plane, sectionX, sectionY);
		RoofModelInput cached;
		synchronized (roofModelInputCacheLock) {
			cached = roofModelInputCache.get(key);
		}
		if (cached != null) {
			this.worldStreamManager.markRoofInputCacheHit(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return cached;
		}

		CpuSectionWindow window = loadCpuSectionWindow(plane, sectionX, sectionY);
		RoofModelInput built = buildRoofModelInput(window.sectors);
		if (!key.equals(roofModelInputKey(plane, sectionX, sectionY))) {
			return built;
		}
		boolean storedBuilt = false;
		synchronized (roofModelInputCacheLock) {
			cached = roofModelInputCache.get(key);
			if (cached == null) {
				roofModelInputCache.put(key, built);
				storedBuilt = true;
			}
		}
		if (storedBuilt) {
			this.worldStreamManager.markRoofInputBuilt(
				plane,
				sectionX,
				sectionY,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			return built;
		}
		this.worldStreamManager.markRoofInputCacheHit(
			plane,
			sectionX,
			sectionY,
			ACTIVE_SECTION_GRID,
			ACTIVE_SECTION_ORIGIN_OFFSET);
		return cached;
	}

	private RoofModelInput buildRoofModelInput(Sector[] sourceSectors) {
		TerrainModelInputSource source = new TerrainModelInputSource(sourceSectors);
		RoofElevationWorkspace elevations = this.prepareRoofElevationProduct(source);
		List<RoofFaceInput> faces = this.collectRoofFaceInputs(source, elevations);
		RoofCoverageTiles roofCoverage = collectRoofCoverageTiles(source);
		elevations.clearRoofMarkers();
		return new RoofModelInput(
			faces.toArray(new RoofFaceInput[faces.size()]),
			elevations.copyElevations(),
			roofCoverage.bits,
			roofCoverage.count);
	}

	private RoofCoverageTiles collectRoofCoverageTiles(TerrainModelInputSource source) {
		long[] bits = new long[((LOCAL_FACE_TILE_COUNT * LOCAL_FACE_TILE_COUNT) + 63) / 64];
		int count = 0;
		for (int x = 0; x < LOCAL_FACE_TILE_COUNT; x++) {
			for (int z = 0; z < LOCAL_FACE_TILE_COUNT; z++) {
				if (source.wallRoof(x, z) <= 0) {
					continue;
				}
				int bitIndex = z + x * LOCAL_FACE_TILE_COUNT;
				bits[bitIndex >>> 6] |= 1L << (bitIndex & 63);
				count++;
			}
		}
		return new RoofCoverageTiles(bits, count);
	}

	private RoofElevationWorkspace prepareRoofElevationProduct(TerrainModelInputSource source) {
		RoofElevationWorkspace elevations = RoofElevationWorkspace.fromSource(source);
		for (int x = 0; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_FACE_TILE_COUNT; ++z) {
				int wall = source.verticalWall(x, z);
				if (wall > 0) {
					this.applyWallToElevationCache(elevations, wall - 1, x, z, x + 1, z);
				}

				wall = source.horizontalWall(x, z);
				if (wall > 0) {
					this.applyWallToElevationCache(elevations, wall - 1, x, z, x, z + 1);
				}

				wall = source.wallDiagonal(x, z);
				if (wall > 0 && wall < 12000) {
					this.applyWallToElevationCache(elevations, wall - 1, x, z, x + 1, z + 1);
				}

				if (wall > 12000 && wall < 24000) {
					this.applyWallToElevationCache(elevations, wall - 12001, x + 1, z, x, z + 1);
				}
			}
		}

		for (int x = 1; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 1; z < LOCAL_FACE_TILE_COUNT; ++z) {
				int roof = source.wallRoof(x, z);
				if (roof > 0) {
					int xp1 = x + 1;
					int xp12 = x + 1;
					int zp1 = z + 1;
					int zp12 = z + 1;
					int max = 0;
					int ec00 = elevations.get(x, z);
					int ec10 = elevations.get(xp1, z);
					int ec11 = elevations.get(xp12, zp1);
					int ec01 = elevations.get(x, zp12);

					if (ec00 > 80000) {
						ec00 -= 80000;
					}
					if (ec10 > 80000) {
						ec10 -= 80000;
					}
					if (ec11 > 80000) {
						ec11 -= 80000;
					}
					if (ec01 > 80000) {
						ec01 -= 80000;
					}

					if (ec00 > max) {
						max = ec00;
					}
					if (max < ec10) {
						max = ec10;
					}
					if (max < ec11) {
						max = ec11;
					}
					if (max < ec01) {
						max = ec01;
					}
					if (max >= 80000) {
						max -= 80000;
					}

					if (ec00 < 80000) {
						elevations.set(x, z, max);
					} else {
						elevations.set(x, z, elevations.get(x, z) - 80000);
					}

					if (ec10 < 80000) {
						elevations.set(xp1, z, max);
					} else {
						elevations.set(xp1, z, elevations.get(xp1, z) - 80000);
					}

					if (ec11 < 80000) {
						elevations.set(xp12, zp1, max);
					} else {
						elevations.set(xp12, zp1, elevations.get(xp12, zp1) - 80000);
					}

					if (ec01 < 80000) {
						elevations.set(x, zp12, max);
					} else {
						elevations.set(x, zp12, elevations.get(x, zp12) - 80000);
					}
				}
			}
		}
		return elevations;
	}

	private List<RoofFaceInput> collectRoofFaceInputs(TerrainModelInputSource source, RoofElevationWorkspace elevations) {
		List<RoofFaceInput> faces = new ArrayList<RoofFaceInput>();
		for (int x = 1; x < LOCAL_FACE_TILE_COUNT; ++x) {
			for (int z = 1; z < LOCAL_FACE_TILE_COUNT; ++z) {
				int roof = source.wallRoof(x, z);
				if (roof > 0) {
					int x10 = x + 1;
					int x11 = x + 1;
					int z11 = z + 1;
					int z01 = z + 1;
					int p00x = x * 128;
					int p00z = z * 128;

					int p10x = 128 + p00x;
					int p11z = 128 + p00z;
					int p01x = p00x;
					int p10z = p00z;
					int p11x = p10x;
					int p01z = p11z;

					int ec00 = elevations.get(x, z);
					int ec10 = elevations.get(x10, z);
					int ec11 = elevations.get(x11, z11);
					int ec01 = elevations.get(x, z01);
					int var32 = Objects.requireNonNull(EntityHandler.getElevationDef(roof - 1)).getUnknown1();
					if (source.hasRoofTile(x, z) && ec00 < 80000) {
						ec00 += var32 + 80000;
						elevations.set(x, z, ec00);
					}

					if (source.hasRoofTile(x10, z) && ec10 < 80000) {
						ec10 += var32 + 80000;
						elevations.set(x10, z, ec10);
					}

					if (source.hasRoofTile(x11, z11) && ec11 < 80000) {
						ec11 += 80000 + var32;
						elevations.set(x11, z11, ec11);
					}

					if (ec10 >= 80000) {
						ec10 -= 80000;
					}

					if (ec11 >= 80000) {
						ec11 -= 80000;
					}

					if (source.hasRoofTile(x, z01) && ec01 < 80000) {
						ec01 += var32 + 80000;
						elevations.set(x, z01, ec01);
					}

					if (ec00 >= 80000) {
						ec00 -= 80000;
					}

					if (ec01 >= 80000) {
						ec01 -= 80000;
					}

					final byte eaveSize = 16;

					if (source.hasRoofStrut(x - 1, z)) {
						p00x -= eaveSize;
					}
					if (source.hasRoofStrut(x + 1, z)) {
						p00x += eaveSize;
					}
					if (source.hasRoofStrut(x, z - 1)) {
						p00z -= eaveSize;
					}
					if (source.hasRoofStrut(x, z + 1)) {
						p00z += eaveSize;
					}

					if (source.hasRoofStrut(x10 - 1, z)) {
						p10x -= eaveSize;
					}
					if (source.hasRoofStrut(x10 + 1, z)) {
						p10x += eaveSize;
					}
					if (source.hasRoofStrut(x10, z - 1)) {
						p10z -= eaveSize;
					}
					if (source.hasRoofStrut(x10, z + 1)) {
						p10z += eaveSize;
					}

					if (source.hasRoofStrut(x11 - 1, z11)) {
						p11x -= eaveSize;
					}
					if (source.hasRoofStrut(x11 + 1, z11)) {
						p11x += eaveSize;
					}
					if (source.hasRoofStrut(x11, z11 - 1)) {
						p11z -= eaveSize;
					}
					if (source.hasRoofStrut(x11, z11 + 1)) {
						p11z += eaveSize;
					}

					if (source.hasRoofStrut(x - 1, z01)) {
						p01x -= eaveSize;
					}
					if (source.hasRoofStrut(x + 1, z01)) {
						p01x += eaveSize;
					}
					if (source.hasRoofStrut(x, z01 - 1)) {
						p01z -= eaveSize;
					}
					if (source.hasRoofStrut(x, z01 + 1)) {
						p01z += eaveSize;
					}

					roof = Objects.requireNonNull(EntityHandler.getElevationDef(roof - 1)).getUnknown2();
					ec10 = -ec10;
					ec01 = -ec01;
					ec11 = -ec11;
					ec00 = -ec00;
					if (source.wallDiagonal(x, z) > 12000 && source.wallDiagonal(x, z) < 24000
						&& source.wallRoof(x - 1, z - 1) == 0) {
						addRoofFaceInput(faces, roof,
							p11x, ec11, p11z,
							p01x, ec01, p01z,
							p10x, ec10, p10z);
					} else if (source.wallDiagonal(x, z) > 12000 && source.wallDiagonal(x, z) < 24000
						&& source.wallRoof(x + 1, z + 1) == 0) {
						addRoofFaceInput(faces, roof,
							p00x, ec00, p00z,
							p10x, ec10, p10z,
							p01x, ec01, p01z);
					} else if (source.wallDiagonal(x, z) > 0 && source.wallDiagonal(x, z) < 12000
						&& source.wallRoof(x + 1, z - 1) == 0) {
						addRoofFaceInput(faces, roof,
							p01x, ec01, p01z,
							p00x, ec00, p00z,
							p11x, ec11, p11z);
					} else if (source.wallDiagonal(x, z) > 0 && source.wallDiagonal(x, z) < 12000
						&& source.wallRoof(x - 1, z + 1) == 0) {
						addRoofFaceInput(faces, roof,
							p10x, ec10, p10z,
							p11x, ec11, p11z,
							p00x, ec00, p00z);
					} else if (ec10 == ec00 && ec11 == ec01) {
						addRoofFaceInput(faces, roof,
							p00x, ec00, p00z,
							p10x, ec10, p10z,
							p11x, ec11, p11z,
							p01x, ec01, p01z);
					} else if (ec00 == ec01 && ec11 == ec10) {
						addRoofFaceInput(faces, roof,
							p01x, ec01, p01z,
							p00x, ec00, p00z,
							p10x, ec10, p10z,
							p11x, ec11, p11z);
					} else {
						boolean var34 = true;
						if (source.wallRoof(x - 1, z - 1) > 0) {
							var34 = false;
						}

						if (source.wallRoof(x + 1, z + 1) > 0) {
							var34 = false;
						}

						if (!var34) {
							addRoofFaceInput(faces, roof,
								p10x, ec10, p10z,
								p11x, ec11, p11z,
								p00x, ec00, p00z);

							addRoofFaceInput(faces, roof,
								p01x, ec01, p01z,
								p00x, ec00, p00z,
								p11x, ec11, p11z);
						} else {
							addRoofFaceInput(faces, roof,
								p00x, ec00, p00z,
								p10x, ec10, p10z,
								p01x, ec01, p01z);

							addRoofFaceInput(faces, roof,
								p11x, ec11, p11z,
								p01x, ec01, p01z,
								p10x, ec10, p10z);
						}
					}
				}
			}
		}
		return faces;
	}

	private void addRoofFaceInput(List<RoofFaceInput> faces, int texture, int... vertexCoords) {
		faces.add(new RoofFaceInput(texture, vertexCoords));
	}

	private void emitRoofFaceProduct(RoofModelInput input) {
		for (RoofFaceInput face : input.faces) {
			int[] indices = new int[face.vertexCoords.length / 3];
			for (int i = 0; i < indices.length; i++) {
				int offset = i * 3;
				indices[i] = this.modelAccumulate.insertVertex(
					face.vertexCoords[offset],
					face.vertexCoords[offset + 1],
					face.vertexCoords[offset + 2]);
			}
			this.modelAccumulate.insertFace(indices.length, indices, face.texture, Scene.TRANSPARENT, false);
		}
	}

	private void publishRoofProduct(int plane, RoofModelInput input) {
		this.modelAccumulate.setDiffuseLightAndColor(-50, -10, -50, 50, 50, true, -98);
		this.modelRoofGrid[plane] = this.modelAccumulate.divideModelByGrid(0, MODEL_GRID_AXIS,
			MODEL_GRID_WORLD_SIZE, -112, MODEL_GRID_COUNT, MODEL_SPLIT_VERTEX_LIMIT, MODEL_GRID_WORLD_SIZE,
			true, 0);
		tagRenderer3DModels(this.modelRoofGrid[plane], Renderer3DModelKind.ROOF);

		for (int x = 0; x < MODEL_GRID_COUNT; ++x) {
			this.scene.addModel(this.modelRoofGrid[plane][x]);
		}

		if (this.modelRoofGrid[plane][0] == null) {
			throw new RuntimeException("null roof!");
		}
		input.copyElevationsInto(this.tileElevationCache);
	}

	private void publishHiddenRoofProduct(int plane, RoofModelInput input) {
		this.modelRoofGrid[plane] = new RSModel[MODEL_GRID_COUNT];
		input.copyElevationsInto(this.tileElevationCache);
	}

	public void registerObjectDir(int x, int y, int dir) {
		if (!isLocalTile(x, y)) {
			return;
		}
		tileDirection[x][y] = (byte) dir;
	}

	public final int getElevation(int x, int z) {
		try {

			int xTile = x >> 7;
			int zTile = z >> 7;
			int xLerp = 127 & x;
			int zLerp = 127 & z;
			if (isLocalFaceTile(xTile, zTile)) {
				int tileCorner;
				int dEX;
				int dEZ;
				if (xLerp <= 128 - zLerp) {
					tileCorner = this.getTileElevation(xTile, zTile);
					dEX = this.getTileElevation(1 + xTile, zTile) - tileCorner;
					dEZ = this.getTileElevation(xTile, 1 + zTile) - tileCorner;
				} else {
					tileCorner = this.getTileElevation(1 + xTile, zTile + 1);
					dEX = this.getTileElevation(xTile, zTile + 1) - tileCorner;
					dEZ = this.getTileElevation(1 + xTile, zTile) - tileCorner;
					xLerp = 128 - xLerp;
					zLerp = 128 - zLerp;
				}

				return dEZ * zLerp / 128 + tileCorner + dEX * xLerp / 128;
			} else
				return 0;
		} catch (RuntimeException var13) {
			throw GenUtil.makeThrowable(var13, "k.GA(" + x + ',' + z + ',' + "dummy" + ')');
		}
	}

	private int getTerrainColour(int tileX, int tileZ) {
		try {

			Sector sector = sectorForLocalTile(tileX, tileZ);
			if (sector != null) {
				return sector.getTile(tileInSector(tileX), tileInSector(tileZ)).groundTexture & 0xff;
			} else
				return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.V(" + "dummy" + ',' + tileX + ',' + tileZ + ')');
		}
	}

	private int getTileDecorationCacheVal(int xTile, int zTile, int plane, int defaultVal) {
		try {

			int id = this.getTileDecorationID(xTile, zTile, plane);
			if (id == 0) {
				return defaultVal;
			}
			return Objects.requireNonNull(EntityHandler.getTileDef(id - 1)).getColour();
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7,
				"k.M(" + "dummy" + ',' + xTile + ',' + defaultVal + ',' + plane + ',' + zTile + ')');
		}
	}

	private boolean isPickableInvisibleOverlay(int xTile, int zTile, int plane) {
		return this.getTileDecorationID(xTile, zTile, plane) == 26;
	}

	private int getTileDecorationID(int xTile, int zTile, int plane) {
		try {

			Sector sector = sectorForLocalTile(xTile, zTile);
			if (sector != null) {
				return sector.getTile(tileInSector(xTile), tileInSector(zTile)).groundOverlay & 0xFF;
				// like this while adding stuff, objects etc.
				// return 255 & this.tileDecoration[chunk][xTile * 48 + zTile];
			} else
				return 0;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.J(" + plane + ',' + xTile + ',' + 4 + ',' + zTile + ')');
		}
	}

	private int getTileDirection(int xTile, int zTile) {
		try {

			if (isLocalTile(xTile, zTile)) {
				return this.tileDirection[xTile][zTile];
			} else
				return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.BA(" + xTile + ',' + zTile + ',' + "dummy" + ')');
		}
	}

	private int getTileElevation(int xTile, int zTile) {
		try {

			Sector sector = sectorForLocalTile(xTile, zTile);
			if (sector != null) {
				return (sector.getTile(tileInSector(xTile), tileInSector(zTile)).groundElevation & 0xff) * 3;
				// return (255 & this.elevation[region][xTile * 48 + zTile]) *
				// 3;
			} else
				return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.S(" + 2 + ',' + zTile + ',' + xTile + ')');
		}
	}

	private int getWallDiagonal(int tileX, int tileZ) {
		try {

			Sector sector = sectorForLocalTile(tileX, tileZ);
			if (sector != null) {
				return sector.getTile(tileInSector(tileX), tileInSector(tileZ)).diagonalWalls;
				// here.
			} else
				return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.HA(" + tileX + ',' + tileZ + ',' + "dummy" + ')');
		}
	}

	private int getVerticalWall(int tileX, int tileZ) {
		try {

			Sector sector = sectorForLocalTile(tileX, tileZ);
			if (sector != null) {
				return sector.getTile(tileInSector(tileX), tileInSector(tileZ)).verticalWall & 0xff;
			} else
				return 0;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.R(" + tileX + ',' + "dummy" + ',' + tileZ + ')');
		}
	}

	private int getHorizontalWall(int xTile, int zTile) {
		try {

			Sector sector = sectorForLocalTile(xTile, zTile);
			if (sector != null) {
				return sector.getTile(tileInSector(xTile), tileInSector(zTile)).horizontalWall & 0xFF;
			} else
				return 0;
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.LA(" + "dummy" + ',' + xTile + ',' + zTile + ')');
		}
	}

	private int getWallRoof(int tileX, int tileZ) {
		try {

			Sector sector = sectorForLocalTile(tileX, tileZ);
			if (sector != null) {
				return sector.getTile(tileInSector(tileX), tileInSector(tileZ)).roofTexture;
				// return this.wallsRoof[chunk][tileZ + tileX * 48];
			} else
				return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.P(" + tileZ + ',' + tileX + ',' + "dummy" + ')');
		}
	}

	private boolean hasRoofStrut(int tileX, int tileZ) {
		try {

			return this.getWallRoof(tileX, tileZ) <= 0 && this.getWallRoof(tileX - 1, tileZ) <= 0
				&& this.getWallRoof(tileX - 1, tileZ - 1) <= 0 && this.getWallRoof(tileX, tileZ - 1) <= 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.DA(" + tileX + ',' + "dummy" + ',' + tileZ + ')');
		}
	}

	private boolean hasRoofTile(boolean var1, int tileX, int tileZ) {
		try {

			return this.getWallRoof(tileX, tileZ) > 0 && this.getWallRoof(tileX - 1, tileZ) > 0
				&& this.getWallRoof(tileX - 1, tileZ - 1) > 0 && this.getWallRoof(tileX, tileZ - 1) > 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "k.EA(" + false + ',' + tileX + ',' + tileZ + ')');
		}
	}

	private void insertWallIntoModel(int var1, RSModel model, int t2X, int t1Z, int t1X, int var6, int t2Z) {
		try {

			this.setVertexLightOther(t1X, t1Z, 40);
			this.setVertexLightOther(t2X, t2Z, 40);
			int height = Objects.requireNonNull(EntityHandler.getDoorDef(var1)).getWallObjectHeight();// CacheValues.wallObjectHeight[var1];
			int frontTex = Objects.requireNonNull(EntityHandler.getDoorDef(var1)).getModelVar2();
			if (var6 != -14584)
				this.getTerrainColour((int) 104, -113);

			int backTex = Objects.requireNonNull(EntityHandler.getDoorDef(var1)).getModelVar3();
			int x1 = t1X * 128;
			int z1 = t1Z * 128;
			int x2 = t2X * 128;
			int z2 = t2Z * 128;
			int v1 = model.insertVertex(x1, -this.tileElevationCache[t1X][t1Z], z1);
			int v2 = model.insertVertex(x1, -this.tileElevationCache[t1X][t1Z] - height, z1);
			int v3 = model.insertVertex(x2, -height - this.tileElevationCache[t2X][t2Z], z2);
			int v4 = model.insertVertex(x2, -this.tileElevationCache[t2X][t2Z], z2);
			int[] var19 = new int[]{v1, v2, v3, v4};
			int face = model.insertFace(4, var19, frontTex, backTex, false);
			if (Objects.requireNonNull(EntityHandler.getDoorDef(var1)).getUnknown() == 5)
				model.facePickIndex[face] = 30000 + var1;
			else
				model.facePickIndex[face] = 0;

		} catch (RuntimeException var21) {
			throw GenUtil.makeThrowable(var21, "k.F(" + var1 + ',' + (model != null ? "{...}" : "null") + ',' + t2X
				+ ',' + t1Z + ',' + t1X + ',' + var6 + ',' + t2Z + ')');
		}
	}

	private int isTileType2(int xTile, int zTile, int plane, int var3) {
		try {

			byte[] membersMapPack;
			if (var3 != 15282)
				membersMapPack = (byte[]) null;

			int id = this.getTileDecorationID(xTile, zTile, plane);
			if (id == 0)
				return -1;
			else {
				int type = Objects.requireNonNull(EntityHandler.getTileDef(id - 1)).getTileValue();
				return type != 2 ? 0 : 1;
			}
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "k.T(" + plane + ',' + zTile + ',' + var3 + ',' + xTile + ')');
		}
	}

	public final void loadSections(int worldX, int worldZ, int plane) {
		try {
			long loadStart = WorldStreamManager.now();
			long telemetryLoadStart = RenderTelemetry.now();
			long phaseStart = RenderTelemetry.now();
			this.resetModels();
			long resetNanos = RenderTelemetry.elapsedSince(phaseStart);

			int x = worldTileToSection(worldX);
			int z = worldTileToSection(worldZ);

			phaseStart = RenderTelemetry.now();
			this.generateLandscapeModel(worldX, 122, true, plane, worldZ);
			long activePlaneNanos = RenderTelemetry.elapsedSince(phaseStart);
			long upperPlanesNanos = 0L;
			long bridgeNanos = 0L;
			if (plane == 0 && !syntheticDeepFixtureTerrain) {
				phaseStart = RenderTelemetry.now();
				this.generateLandscapeModel(worldX, 112, false, 1, worldZ);
				this.generateLandscapeModel(worldX, 69, false, 2, worldZ);
				upperPlanesNanos = RenderTelemetry.elapsedSince(phaseStart);
				phaseStart = RenderTelemetry.now();
				boolean bridgeDecorationsApplied = this.loadSectionWindow(sectors, plane, x, z);
				if (!bridgeDecorationsApplied) {
					this.setTileDecorationOnBridge();
				}
				bridgeNanos = RenderTelemetry.elapsedSince(phaseStart);
			}
			phaseStart = RenderTelemetry.now();
			this.renderer3DWorldChunkFrame = this.buildRenderer3DWorldChunkFrame(plane, x, z);
			long chunkFrameNanos = RenderTelemetry.elapsedSince(phaseStart);
			phaseStart = RenderTelemetry.now();
			this.preloadSections(worldX, worldZ, plane);
			long preloadNanos = RenderTelemetry.elapsedSince(phaseStart);
			this.worldStreamManager.markActiveWindow(
				plane,
				x,
				z,
				ACTIVE_SECTION_GRID,
				ACTIVE_SECTION_ORIGIN_OFFSET);
			this.worldStreamManager.recordActiveWindowLoad(
				plane,
				x,
				z,
				WorldStreamManager.elapsedSince(loadStart));
			RenderTelemetry.recordWorldSectionLoadPhases(
				resetNanos,
				activePlaneNanos,
				upperPlanesNanos,
				bridgeNanos,
				chunkFrameNanos,
				preloadNanos);
			RenderTelemetry.recordWorldSectionLoad(RenderTelemetry.elapsedSince(telemetryLoadStart));

		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "k.L(" + worldX + ',' + "dummy" + ',' + worldZ + ',' + plane + ')');
		}
	}

	public void preloadSections(int worldX, int worldZ, int plane) {
		/*
		 * Native package terrain arrives as one complete, server-authoritative
		 * presentation window. A speculative build cannot fetch the next
		 * package window, and letting it outlive a rapid window shift can pair
		 * terrain decoded from the new snapshot with the old snapshot's cache
		 * key. Build native windows only on the foreground receipt path.
		 */
		if (nativeLayeredTerrainSnapshot != null) {
			return;
		}
		int sectionX = worldTileToSection(worldX);
		int sectionY = worldTileToSection(worldZ);
		preloadSectionWindow(plane, sectionX, sectionY);
		queueCpuSectionWindowPreload(plane, sectionX, sectionY);
		queueWorldModelProductPreload(plane, sectionX, sectionY, true, !Config.C_HIDE_ROOFS);
		if (plane == 0 && !syntheticDeepFixtureTerrain) {
			preloadSectionWindow(1, sectionX, sectionY);
			preloadSectionWindow(2, sectionX, sectionY);
			queueCpuSectionWindowPreload(1, sectionX, sectionY);
			queueCpuSectionWindowPreload(2, sectionX, sectionY);
			queueWorldModelProductPreload(1, sectionX, sectionY, true, !Config.C_HIDE_ROOFS);
			queueWorldModelProductPreload(2, sectionX, sectionY, true, !Config.C_HIDE_ROOFS);
		}
	}

	private void preloadSectionWindow(int height, int sectionX, int sectionY) {
		this.worldStreamManager.markWindowRequested(
			height,
			sectionX,
			sectionY,
			SECTOR_PRELOAD_LOW_OFFSET,
			SECTOR_PRELOAD_HIGH_OFFSET);
		for (int x = sectionX + SECTOR_PRELOAD_LOW_OFFSET; x <= sectionX + SECTOR_PRELOAD_HIGH_OFFSET; x++) {
			for (int y = sectionY + SECTOR_PRELOAD_LOW_OFFSET; y <= sectionY + SECTOR_PRELOAD_HIGH_OFFSET; y++) {
				queueSectorPreload(height, x, y);
			}
		}
	}

	private void queueSectorPreload(final int height, final int sectionX, final int sectionY) {
		final String filename = sectorFilename(height, sectionX, sectionY);
		synchronized (sectorCacheLock) {
			if (sectorTemplateCache.containsKey(filename) || sectorPreloadsInFlight.contains(filename)) {
				return;
			}
			sectorPreloadsInFlight.add(filename);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadSectorTemplate(height, sectionX, sectionY);
				} finally {
					synchronized (sectorCacheLock) {
						sectorPreloadsInFlight.remove(filename);
					}
				}
			}
		});
	}

	private void queueCpuSectionWindowPreload(final int height, final int sectionX, final int sectionY) {
		final String key = sectionWindowKey(height, sectionX, sectionY);
		synchronized (cpuSectionWindowCacheLock) {
			if (cpuSectionWindowCache.containsKey(key) || cpuSectionWindowBuildsInFlight.contains(key)) {
				return;
			}
			cpuSectionWindowBuildsInFlight.add(key);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadCpuSectionWindow(height, sectionX, sectionY);
				} finally {
					synchronized (cpuSectionWindowCacheLock) {
						cpuSectionWindowBuildsInFlight.remove(key);
					}
				}
			}
		});
	}

	private void queueWorldModelProductPreload(
		final int height,
		final int sectionX,
		final int sectionY,
		final boolean includeTerrain,
		final boolean includeRoofGeometry) {
		final String key = worldModelProductKey(height, sectionX, sectionY, includeRoofGeometry);
		final String preloadKey = key + (includeTerrain ? "-terrain" : "-surface");
		synchronized (worldModelProductCacheLock) {
			WorldModelProduct cached = worldModelProductCache.get(key);
			if (cached != null && cached.hasTerrainIfNeeded(includeTerrain)
				|| worldModelProductBuildsInFlight.contains(preloadKey)) {
				return;
			}
			worldModelProductBuildsInFlight.add(preloadKey);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadWorldModelProduct(height, sectionX, sectionY, includeTerrain, includeRoofGeometry);
				} finally {
					synchronized (worldModelProductCacheLock) {
						worldModelProductBuildsInFlight.remove(preloadKey);
					}
				}
			}
		});
	}

	private void queueTerrainModelInputPreload(final int height, final int sectionX, final int sectionY) {
		final String key = terrainModelInputKey(height, sectionX, sectionY);
		synchronized (terrainModelInputCacheLock) {
			if (terrainModelInputCache.containsKey(key) || terrainModelInputBuildsInFlight.contains(key)) {
				return;
			}
			terrainModelInputBuildsInFlight.add(key);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadTerrainModelInput(height, sectionX, sectionY);
				} finally {
					synchronized (terrainModelInputCacheLock) {
						terrainModelInputBuildsInFlight.remove(key);
					}
				}
			}
		});
	}

	private void queueWallModelInputPreload(final int height, final int sectionX, final int sectionY) {
		final String key = wallModelInputKey(height, sectionX, sectionY);
		synchronized (wallModelInputCacheLock) {
			if (wallModelInputCache.containsKey(key) || wallModelInputBuildsInFlight.contains(key)) {
				return;
			}
			wallModelInputBuildsInFlight.add(key);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadWallModelInput(height, sectionX, sectionY);
				} finally {
					synchronized (wallModelInputCacheLock) {
						wallModelInputBuildsInFlight.remove(key);
					}
				}
			}
		});
	}

	private void queueRoofModelInputPreload(final int height, final int sectionX, final int sectionY) {
		final String key = roofModelInputKey(height, sectionX, sectionY);
		synchronized (roofModelInputCacheLock) {
			if (roofModelInputCache.containsKey(key) || roofModelInputBuildsInFlight.contains(key)) {
				return;
			}
			roofModelInputBuildsInFlight.add(key);
		}

		sectorPreloadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					loadRoofModelInput(height, sectionX, sectionY);
				} finally {
					synchronized (roofModelInputCacheLock) {
						roofModelInputBuildsInFlight.remove(key);
					}
				}
			}
		});
	}

	private CpuSectionWindow loadCpuSectionWindow(int height, int sectionX, int sectionY) {
		String key = sectionWindowKey(height, sectionX, sectionY);
		CpuSectionWindow cached;
		synchronized (cpuSectionWindowCacheLock) {
			cached = cpuSectionWindowCache.get(key);
			if (cached != null) {
				this.worldStreamManager.markCpuCacheHit(
					height,
					sectionX,
					sectionY,
					ACTIVE_SECTION_GRID,
					ACTIVE_SECTION_ORIGIN_OFFSET);
				return cached;
			}
		}

		long buildStart = WorldStreamManager.now();
		CpuSectionWindow built = buildCpuSectionWindow(height, sectionX, sectionY);
		this.worldStreamManager.markCpuBuilt(
			height,
			sectionX,
			sectionY,
			ACTIVE_SECTION_GRID,
			ACTIVE_SECTION_ORIGIN_OFFSET,
			WorldStreamManager.elapsedSince(buildStart));
		if (!key.equals(sectionWindowKey(height, sectionX, sectionY))) {
			/*
			 * A package receipt or editor revision changed while an async
			 * build was running. The caller may discard its detached result,
			 * but it must never poison the cache entry for the prior scope.
			 */
			return built;
		}
		synchronized (cpuSectionWindowCacheLock) {
			cached = cpuSectionWindowCache.get(key);
			if (cached != null) {
				this.worldStreamManager.markCpuCacheHit(
					height,
					sectionX,
					sectionY,
					ACTIVE_SECTION_GRID,
					ACTIVE_SECTION_ORIGIN_OFFSET);
				return cached;
			}
			cpuSectionWindowCache.put(key, built);
			return built;
		}
	}

	private CpuSectionWindow buildCpuSectionWindow(int height, int sectionX, int sectionY) {
		Sector[] window = new Sector[ACTIVE_SECTION_COUNT];
		int originX = sectionX - ACTIVE_SECTION_ORIGIN_OFFSET;
		int originY = sectionY - ACTIVE_SECTION_ORIGIN_OFFSET;
		int nativePresentationPlane = nativeLayeredPresentationPlane();
		for (int y = 0; y < ACTIVE_SECTION_GRID; y++) {
			for (int x = 0; x < ACTIVE_SECTION_GRID; x++) {
				int sectorX=originX+x,sectorY=originY+y;
				if (height == nativePresentationPlane
					&& nativeLayeredTerrainSnapshot != null) {
					window[y * ACTIVE_SECTION_GRID + x] =
						nativeLayeredVoidSector();
				} else {
					window[y * ACTIVE_SECTION_GRID + x] =
						loadSectorTemplate(height,sectorX,sectorY).copy();
					applyWorldEditorTerrainPatches(
						window[y * ACTIVE_SECTION_GRID + x],
						height,
						sectorX,
						sectorY);
				}
			}
		}
		if (height == nativePresentationPlane
			&& nativeLayeredTerrainSnapshot != null) {
			applyNativeLayeredTerrain(window, sectionX, sectionY);
			for (int y = 0; y < ACTIVE_SECTION_GRID; y++) {
				for (int x = 0; x < ACTIVE_SECTION_GRID; x++) {
					applyWorldEditorTerrainPatches(
						window[y * ACTIVE_SECTION_GRID + x],
						nativeLayeredTerrainSnapshot.getLevel(),
						originX + x,
						originY + y);
				}
			}
		} else if (height == 0 && syntheticDeepFixtureTerrain) {
			applySyntheticDeepFixtureTerrain(window, sectionX, sectionY);
		}
		applyBridgeDecorations(window);
		return new CpuSectionWindow(window, true);
	}

	private int nativeLayeredPresentationPlane() {
		if (nativeLayeredTerrainSnapshot == null) {
			return -1;
		}
		switch (nativeLayeredTerrainSnapshot.getLevel()) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case -1:
				return 3;
			default:
				return 0;
		}
	}

	private static Sector nativeLayeredVoidSector() {
		Sector sector = Sector.blankLoaded();
		for (int localX = 0; localX < SECTION_SIZE; localX++) {
			for (int localZ = 0; localZ < SECTION_SIZE; localZ++) {
				Tile tile = sector.getTile(localX, localZ);
				tile.groundOverlay = (byte) 250;
				tile.editorPaintedOverlay = true;
			}
		}
		return sector;
	}

	private void applyNativeLayeredTerrain(
		Sector[] window,
		int sectionX,
		int sectionY) {
		NativeLayeredTerrainSnapshot snapshot =
			nativeLayeredTerrainSnapshot;
		if (snapshot == null) {
			throw new IllegalStateException(
				"Native layered terrain scope lost its packet snapshot");
		}
		int originX =
			(sectionX - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE;
		int originZ =
			(sectionY - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE;
		for (int localX = 0; localX < LOCAL_TILE_COUNT; localX++) {
			for (int localZ = 0; localZ < LOCAL_TILE_COUNT; localZ++) {
				int worldX = Math.addExact(originX, localX);
				int worldZ = Math.addExact(originZ, localZ);
				int logicalX = Math.subtractExact(
					worldX, syntheticDeepFixtureOffsetX);
				int logicalZ = Math.subtractExact(
					worldZ, syntheticDeepFixtureOffsetZ);
				if (!snapshot.covers(
						snapshot.getWorldSpace(),
						snapshot.getLevel(),
						logicalX,
						logicalZ)) {
					continue;
				}
				Sector sector = sectorForLocalTile(
					window, localX, localZ);
				if (sector == null) {
					throw new IllegalStateException(
						"Native layered tile escaped its active section window");
				}
				sector.setTile(
					tileInSector(localX),
					tileInSector(localZ),
					snapshot.createTile(logicalX, logicalZ));
			}
		}
	}

	private void applySyntheticDeepFixtureTerrain(
		Sector[] window,
		int sectionX,
		int sectionY) {
		int originX =
			(sectionX - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE;
		int originZ =
			(sectionY - ACTIVE_SECTION_ORIGIN_OFFSET) * SECTION_SIZE;
		int roomMinX =
			Math.addExact(
				SYNTHETIC_DEEP_MIN_X,
				syntheticDeepFixtureOffsetX);
		int roomMaxX =
			Math.addExact(
				SYNTHETIC_DEEP_MAX_X,
				syntheticDeepFixtureOffsetX);
		int roomMinZ =
			Math.addExact(
				SYNTHETIC_DEEP_MIN_Z,
				syntheticDeepFixtureOffsetZ);
		int roomMaxZ =
			Math.addExact(
				SYNTHETIC_DEEP_MAX_Z,
				syntheticDeepFixtureOffsetZ);
		for (int worldX = roomMinX;
			worldX <= roomMaxX;
			worldX++) {
			for (int worldZ = roomMinZ;
				worldZ <= roomMaxZ;
				worldZ++) {
				int localX = worldX - originX;
				int localZ = worldZ - originZ;
				Sector sector = sectorForLocalTile(
					window, localX, localZ);
				if (sector == null) {
					continue;
				}
				Tile tile = new Tile();
				tile.groundOverlay = 0;
				tile.editorPaintedOverlay = true;
				sector.setTile(
					tileInSector(localX),
					tileInSector(localZ),
					tile);
			}
		}
		for (int worldX = roomMinX - 1;
			worldX <= roomMaxX + 1;
			worldX++) {
			for (int worldZ = roomMinZ - 1;
				worldZ <= roomMaxZ + 1;
				worldZ++) {
				if (worldX >= roomMinX
					&& worldX <= roomMaxX
					&& worldZ >= roomMinZ
					&& worldZ <= roomMaxZ) {
					continue;
				}
				int localX = worldX - originX;
				int localZ = worldZ - originZ;
				Sector sector = sectorForLocalTile(
					window, localX, localZ);
				if (sector != null) {
					sector.getTile(
						tileInSector(localX),
						tileInSector(localZ))
						.editorPaintedOverlay = true;
				}
			}
		}
	}

	private static void applyBridgeDecorations(Sector[] window) {
		for (int x = 0; x < LOCAL_TILE_COUNT; ++x) {
			for (int z = 0; z < LOCAL_TILE_COUNT; ++z) {
				if (getTileDecorationID(window, x, z) == 250) {
					if (x % SECTION_SIZE == SECTION_SIZE - 1
						&& getTileDecorationID(window, x + 1, z) != 250
						&& getTileDecorationID(window, x + 1, z) != 2) {
						setTileDecoration(window, x, z, 9);
					} else if (z % SECTION_SIZE == SECTION_SIZE - 1
						&& getTileDecorationID(window, x, z + 1) != 250
						&& getTileDecorationID(window, x, z + 1) != 2) {
						setTileDecoration(window, x, z, 9);
					} else {
						setTileDecoration(window, x, z, 2);
					}
				}
			}
		}
	}

	private static int getTileDecorationID(Sector[] window, int xTile, int zTile) {
		Sector sector = sectorForLocalTile(window, xTile, zTile);
		if (sector == null) {
			return 0;
		}
		return sector.getTile(tileInSector(xTile), tileInSector(zTile)).groundOverlay & 0xFF;
	}

	private static void setTileDecoration(Sector[] window, int xTile, int zTile, int val) {
		Sector sector = sectorForLocalTile(window, xTile, zTile);
		if (sector != null) {
			sector.getTile(tileInSector(xTile), tileInSector(zTile)).groundOverlay = (byte) val;
		}
	}

	private static Sector sectorForLocalTile(Sector[] window, int tileX, int tileZ) {
		int sectorIndex = sectorIndexForLocalTile(tileX, tileZ);
		return sectorIndex < 0 || sectorIndex >= window.length ? null : window[sectorIndex];
	}

	private String sectionWindowKey(int height, int sectionX, int sectionY) {
		return sectorFilename(height, sectionX, sectionY)
			+ "-editor-" + worldEditorTerrainRevision
			+ (nativeLayeredTerrainSnapshot != null
				? "-native-" + nativeLayeredTerrainSnapshot.scopeIdentity()
				: syntheticDeepFixtureTerrain
					? "-synthetic-deep-room-v1"
					: "-legacy-terrain")
			+ "-window";
	}

	private String terrainModelInputKey(int height, int sectionX, int sectionY) {
		return sectionWindowKey(height, sectionX, sectionY) + "-terrain-input";
	}

	private String wallModelInputKey(int height, int sectionX, int sectionY) {
		return sectionWindowKey(height, sectionX, sectionY) + "-wall-input";
	}

	private String roofModelInputKey(int height, int sectionX, int sectionY) {
		return sectionWindowKey(height, sectionX, sectionY) + "-roof-input";
	}

	private String worldModelProductKey(int height, int sectionX, int sectionY, boolean includeRoofGeometry) {
		return sectionWindowKey(height, sectionX, sectionY)
			+ (includeRoofGeometry ? "-world-product-roofs" : "-world-product-no-roofs");
	}

	public final void removeGameObject_CollisonFlags(int id, int x, int z) {
		try {

			if (isLocalFaceTile(x, z))
				if (Objects.requireNonNull(EntityHandler.getObjectDef(id)).getType() == 1 || Objects.requireNonNull(EntityHandler.getObjectDef(id)).getType() == 2) {
					int var5 = this.getTileDirection((int) x, z);
					int var6;
					int var7;
					if (var5 != 0 && var5 != 4) {
						var7 = Objects.requireNonNull(EntityHandler.getObjectDef(id)).getWidth();
						var6 = Objects.requireNonNull(EntityHandler.getObjectDef(id)).getHeight();
					} else {
						var7 = Objects.requireNonNull(EntityHandler.getObjectDef(id)).getWidth();
						var6 = Objects.requireNonNull(EntityHandler.getObjectDef(id)).getHeight();
					}

					for (int var8 = x; x + var6 > var8; ++var8)
						for (int var9 = z; var7 + z > var9; ++var9)
							if (Objects.requireNonNull(EntityHandler.getObjectDef(id)).getType() != 1) {
								if (var5 == 0) {
									this.collisionFlags[var8][var9] = FastMath
										.bitwiseAnd(this.collisionFlags[var8][var9], ~CollisionFlag.WALL_EAST);
									if (var8 > 0)
										this.collisionFlagModify(var8 - 1, var9, 0xFFFF, CollisionFlag.WALL_WEST);
								} else if (var5 != 2) {
									if (var5 != 4) {
										if (var5 == 6) {
											this.collisionFlags[var8][var9] = FastMath.bitwiseAnd(
												this.collisionFlags[var8][var9], ~CollisionFlag.WALL_NORTH);
											if (var9 > 0)
												this.collisionFlagModify(var8, var9 - 1, 0xFFFF,
													CollisionFlag.WALL_SOUTH);
										}
									} else {
										this.collisionFlags[var8][var9] = FastMath
											.bitwiseAnd(this.collisionFlags[var8][var9], ~CollisionFlag.WALL_WEST);
										if (var8 < LOCAL_FACE_TILE_COUNT)
											this.collisionFlagModify(1 + var8, var9, 0xFFFF, CollisionFlag.WALL_EAST);
									}
								} else {
									this.collisionFlags[var8][var9] = FastMath
										.bitwiseAnd(this.collisionFlags[var8][var9], ~CollisionFlag.WALL_SOUTH);
									if (var9 < LOCAL_FACE_TILE_COUNT)
										this.collisionFlagModify(var8, var9 + 1, 0xFFFF, CollisionFlag.WALL_NORTH);
								}
							} else
								this.collisionFlags[var8][var9] = FastMath.bitwiseAnd(this.collisionFlags[var8][var9],
									~CollisionFlag.FULL_BLOCK_C);

					this.setVertexLightArea(x, z, var6, var7);
				}
		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10, "k.D(" + id + ',' + x + ',' + z + ',' + 4081 + ')');
		}
	}

	public final void removeWallObject_CollisionFlags(boolean var1, int dir, int z, int x, int id) {
		try {

			if (isLocalFaceTile(x, z))
				if (Objects.requireNonNull(EntityHandler.getDoorDef(id)).getDoorType() == 1) {
					if (dir == 0) {
						this.collisionFlags[x][z] = FastMath.bitwiseAnd(this.collisionFlags[x][z],
							~CollisionFlag.WALL_NORTH);
						if (z > 0)
							this.collisionFlagModify(x, z - 1, 0xFFFF, CollisionFlag.WALL_SOUTH);
					} else if (dir == 1) {
						this.collisionFlags[x][z] = FastMath.bitwiseAnd(this.collisionFlags[x][z],
							~CollisionFlag.WALL_EAST);
						if (x > 0)
							this.collisionFlagModify(x - 1, z, 0xFFFF, CollisionFlag.WALL_WEST);
					} else if (dir == 2)
						this.collisionFlags[x][z] = FastMath.bitwiseAnd(this.collisionFlags[x][z],
							~CollisionFlag.FULL_BLOCK_A);
					else if (dir == 3)
						this.collisionFlags[x][z] = FastMath.bitwiseAnd(this.collisionFlags[x][z],
							~CollisionFlag.FULL_BLOCK_B);

					this.setVertexLightArea(x, z, 1, 1);
				}
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "k.IA(" + true + ',' + dir + ',' + z + ',' + x + ',' + id + ')');
		}
	}

	private void resetModels() {
		try {
			boolean removeAllObjectsOnReset = true;
			if (removeAllObjectsOnReset)
				this.scene.removeAllGameObjects(false);


			for (int j = 0; j < MODEL_GRID_COUNT; ++j) {
				this.modelLandscapeGrid[j] = null;

				int i;
				for (i = 0; i < 4; ++i)
					this.modelWallGrid[i][j] = null;

				for (i = 0; i < 4; ++i)
					this.modelRoofGrid[i][j] = null;
			}
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "k.G(" + -10185 + ')');
		}
	}

	private void setTileDecoration(int xTile, int zTile, int val) {
		try {

			Sector sector = sectorForLocalTile(xTile, zTile);
			if (sector != null) {
				sector.getTile(tileInSector(xTile), tileInSector(zTile)).groundOverlay = (byte) val;
				// this.tileDecoration[chunk][zTile + xTile * 48] = (byte) val;
			}
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "k.KA(" + val + ',' + zTile + ',' + "dummy" + ',' + xTile + ')');
		}
	}

	private void setTileDecorationOnBridge() {
		try {


			for (int x = 0; x < LOCAL_TILE_COUNT; ++x)
				for (int z = 0; z < LOCAL_TILE_COUNT; ++z)
					if (this.getTileDecorationID((int) x, z, 0) == 250)
						if (x % SECTION_SIZE == SECTION_SIZE - 1 && this.getTileDecorationID((int) (x + 1), z, 0) != 250
							&& this.getTileDecorationID((int) (1 + x), z, 0) != 2)
							this.setTileDecoration(x, z, 9);
						else if (z % SECTION_SIZE == SECTION_SIZE - 1 && this.getTileDecorationID((int) x, z + 1, 0) != 250
							&& this.getTileDecorationID((int) x, 1 + z, 0) != 2)
							this.setTileDecoration(x, z, 9);
						else
							this.setTileDecoration(x, z, 2);

		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "k.N(" + 0 + ')');
		}
	}

	private void setVertexLightArea(int tileX, int tileZ, int width, int height) {
		try {

			if (tileX >= 1 && tileZ >= 1 && width + tileX < LOCAL_TILE_COUNT && height + tileZ < LOCAL_TILE_COUNT)
				for (int x = tileX; x <= width + tileX; ++x)
					for (int z = tileZ; tileZ + height >= z; ++z) {
						final int flag00 = CollisionFlag.FULL_BLOCK_C | CollisionFlag.FULL_BLOCK_B
							| CollisionFlag.WALL_NORTH | CollisionFlag.WALL_EAST;
						final int flag10 = CollisionFlag.FULL_BLOCK_C | CollisionFlag.FULL_BLOCK_A
							| CollisionFlag.WALL_WEST | CollisionFlag.WALL_NORTH;
						final int flag01 = CollisionFlag.FULL_BLOCK_C | CollisionFlag.FULL_BLOCK_A
							| CollisionFlag.WALL_SOUTH | CollisionFlag.WALL_EAST;
						final int flag11 = CollisionFlag.FULL_BLOCK_C | CollisionFlag.FULL_BLOCK_B
							| CollisionFlag.WALL_WEST | CollisionFlag.WALL_SOUTH;

						if ((flag00 & this.collisionFlagSafe(x, z)) == 0
							&& (flag10 & this.collisionFlagSafe(x - 1, z)) == 0
							&& (this.collisionFlagSafe(x, z - 1) & flag01) == 0
							&& (this.collisionFlagSafe(x - 1, z - 1) & flag11) == 0)
							this.setVertexLightOther(x, z, 0);
						else
							this.setVertexLightOther(x, z, 35);
					}
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"k.B(" + width + ',' + height + ',' + "dummy" + ',' + tileX + ',' + tileZ + ')');
		}
	}

	private void setVertexLightOther(int x, int z, int light) {
		try {

			int chunkX = x / MODEL_GRID_TILE_SIZE;
			int chunkZ = z / MODEL_GRID_TILE_SIZE;
			int chunkXM1 = (x - 1) / MODEL_GRID_TILE_SIZE;
			int chunkZM1 = (z - 1) / MODEL_GRID_TILE_SIZE;

			this.setVertexLightOther((int) chunkX, chunkZ, x, z, (int) light);
			if (chunkX != chunkXM1)
				this.setVertexLightOther((int) chunkXM1, chunkZ, x, z, (int) light);
			if (chunkZM1 != chunkZ)
				this.setVertexLightOther((int) chunkX, chunkZM1, x, z, (int) light);
			if (chunkXM1 != chunkX && chunkZ != chunkZM1)
				this.setVertexLightOther((int) chunkXM1, chunkZM1, x, z, (int) light);
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9, "k.U(" + light + ',' + "dummy" + ',' + x + ',' + z + ')');
		}
	}

	private void setVertexLightOther(int chunkX, int chunkZ, int tileX, int tileZ, int light) {
		try {

			if (chunkX < 0 || chunkZ < 0 || chunkX >= MODEL_GRID_AXIS || chunkZ >= MODEL_GRID_AXIS) {
				return;
			}
			RSModel m = this.modelLandscapeGrid[chunkX + chunkZ * MODEL_GRID_AXIS];
			if (m == null) {
				return;
			}

			for (int id = 0; m.vertHead > id; ++id)
				if (m.vertX[id] == tileX * 128 && tileZ * 128 == m.vertZ[id]) {
					m.setVertexLightOther(id, light);
					return;
				}

		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"k.A(" + tileZ + ',' + light + ',' + chunkZ + ',' + 2 + ',' + chunkX + ',' + tileX + ')');
		}
	}

	public void setWorldMapPoint(int offsetX, int offsetY) {
		mapPointX = offsetX;
		mapPointZ = offsetY;
		System.out.println(mapPointX + ", " + mapPointZ);
	}

	public void generateWorldMap() {
		int plane = 0;

		int chunkX = worldTileToSection(mapPointX);
		int chunkZ = worldTileToSection(mapPointZ);

		this.loadWorldmapSection(0, plane, chunkX - 1, chunkZ - 1);
		this.loadWorldmapSection(1, plane, chunkX, chunkZ - 1);
		this.loadWorldmapSection(2, plane, chunkX - 1, chunkZ);
		this.loadWorldmapSection(3, plane, chunkX, chunkZ);

		//this.minimapGraphics.blackScreen(true);
		for (int x = 0; x < LEGACY_MINIMAP_FACE_TILE_COUNT; ++x) {
			for (int z = 0; z < LEGACY_MINIMAP_FACE_TILE_COUNT; ++z) {
				int colorResource = this.colorToResource[this.getTerrainColour(x, z)];
				int res01 = colorResource;
				int defaultVal = colorResource;
				if (plane == 1 || plane == 2) {
					colorResource = Scene.TRANSPARENT;
					res01 = Scene.TRANSPARENT;
					defaultVal = Scene.TRANSPARENT;
				}
				byte bridge00_11 = 0;
				if (this.getTileDecorationID((int) x, z, plane) > 0) {
					int decorID = this.getTileDecorationID((int) x, z, plane);
					int decorType = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getTileValue();
					int decorType2 = this.isTileType2(x, z, plane, 15282);
					colorResource = res01 = Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getColour();
					if (decorType == 4) {
						colorResource = 1;
						res01 = 1;
						if (decorID == 12) {
							colorResource = 31;
							res01 = 31;
						}
					}

					if (decorType == 5) {
						if (this.getWallDiagonal(x, z) > 0 && this.getWallDiagonal(x, z) < 24000)
							if (this.getTileDecorationCacheVal(x - 1, z, plane, defaultVal) != Scene.TRANSPARENT && this
								.getTileDecorationCacheVal(x, z - 1, plane, defaultVal) != Scene.TRANSPARENT) {
								bridge00_11 = 0;
								colorResource = this.getTileDecorationCacheVal(x - 1, z, plane, defaultVal);
							} else if (this.getTileDecorationCacheVal(1 + x, z, plane, defaultVal) != Scene.TRANSPARENT
								&& this.getTileDecorationCacheVal(x, 1 + z, plane,
								defaultVal) != Scene.TRANSPARENT) {
								res01 = this.getTileDecorationCacheVal(x + 1, z, plane, defaultVal);
								bridge00_11 = 0;
							} else if (this.getTileDecorationCacheVal(1 + x, z, plane, defaultVal) != Scene.TRANSPARENT
								&& this.getTileDecorationCacheVal(x, z - 1, plane,
								defaultVal) != Scene.TRANSPARENT) {
								res01 = this.getTileDecorationCacheVal(x + 1, z, plane, defaultVal);
								bridge00_11 = 1;
							} else if (this.getTileDecorationCacheVal(x - 1, z, plane, defaultVal) != Scene.TRANSPARENT
								&& this.getTileDecorationCacheVal(x, z + 1, plane,
								defaultVal) != Scene.TRANSPARENT) {
								bridge00_11 = 1;
								colorResource = this.getTileDecorationCacheVal(x - 1, z, plane, defaultVal);
							}
					} else if (decorType != 2 || this.getWallDiagonal(x, z) > 0 && this.getWallDiagonal(x, z) < 24000)
						if (decorType2 != this.isTileType2(x - 1, z, plane, 15282)
							&& this.isTileType2(x, z - 1, plane, 15282) != decorType2) {
							colorResource = defaultVal;
							bridge00_11 = 0;
						} else if (decorType2 != this.isTileType2(x + 1, z, plane, 15282)
							&& this.isTileType2(x, z + 1, plane, 15282) != decorType2) {
							bridge00_11 = 0;
							res01 = defaultVal;
						} else if (decorType2 != this.isTileType2(1 + x, z, plane, 15282)
							&& this.isTileType2(x, z - 1, plane, 15282) != decorType2) {
							res01 = defaultVal;
							bridge00_11 = 1;
						} else if (decorType2 != this.isTileType2(x - 1, z, plane, 15282)
							&& decorType2 != this.isTileType2(x, 1 + z, plane, 15282)) {
							colorResource = defaultVal;
							bridge00_11 = 1;
						}

					if (Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getObjectType() != 0)
						this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z],
							CollisionFlag.FULL_BLOCK_C);

					if (Objects.requireNonNull(EntityHandler.getTileDef(decorID - 1)).getTileValue() == 2)
						this.collisionFlags[x][z] = FastMath.bitwiseOr(this.collisionFlags[x][z], CollisionFlag.OBJECT);
				}
				this.drawMinimapTile(x, (int) z, bridge00_11, res01, colorResource);
			}
		}
		for (int x = 1; x < LEGACY_MINIMAP_FACE_TILE_COUNT; ++x) {
			for (int z = 1; z < LEGACY_MINIMAP_FACE_TILE_COUNT; ++z) {
				if (this.getTileDecorationID((int) x, z, plane) > 0 && Objects.requireNonNull(EntityHandler
					.getTileDef(this.getTileDecorationID((int) x, z, plane) - 1)).getTileValue() == 4) {
					int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID(x, z, plane) - 1)).getColour();
					this.drawMinimapTile(x, z, 0, tileDecor, tileDecor);
				} else if (this.getTileDecorationID((int) x, z, plane) == 0
					|| Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID(x, z, plane) - 1)).getTileValue() != 3) {
					if (this.getTileDecorationID(x, z + 1, plane) > 0 && Objects.requireNonNull(EntityHandler
						.getTileDef(this.getTileDecorationID(x, 1 + z, plane) - 1)).getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID((int) x, z + 1, plane) - 1))
							.getColour();
						this.drawMinimapTile(x, (int) z, 0, tileDecor, tileDecor);
					}

					if (this.getTileDecorationID((int) x, z - 1, plane) > 0 && Objects.requireNonNull(EntityHandler
						.getTileDef(this.getTileDecorationID((int) x, z - 1, plane) - 1)).getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID((int) x, z - 1, plane) - 1))
							.getColour();
						this.drawMinimapTile(x, (int) z, 0, tileDecor, tileDecor);
					}

					if (this.getTileDecorationID((int) (x + 1), z, plane) > 0 && Objects.requireNonNull(EntityHandler
						.getTileDef(this.getTileDecorationID((int) (x + 1), z, plane) - 1)).getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID((int) (1 + x), z, plane) - 1))
							.getColour();
						this.drawMinimapTile(x, (int) z, 0, tileDecor, tileDecor);
					}

					if (this.getTileDecorationID((int) (x - 1), z, plane) > 0 && Objects.requireNonNull(EntityHandler
						.getTileDef(this.getTileDecorationID((int) (x - 1), z, plane) - 1)).getTileValue() == 4) {
						int tileDecor = Objects.requireNonNull(EntityHandler.getTileDef(this.getTileDecorationID((int) (x - 1), z, plane) - 1))
							.getColour();
						this.drawMinimapTile(x, (int) z, 0, tileDecor, tileDecor);
					}
				}
			}
		}

		final int wallColor = 6316128;
		for (int x = 0; x < LEGACY_MINIMAP_FACE_TILE_COUNT; ++x)
			for (int z = 0; z < LEGACY_MINIMAP_FACE_TILE_COUNT; ++z) {

				int wall = this.getVerticalWall(x, z);
				if (wall > 0 && (Objects.requireNonNull(EntityHandler.getDoorDef(wall - 1)).getUnknown() == 0 || this.showInvisibleWalls)) {
					this.minimapGraphics.drawLineHoriz(x * 3, z * 3, 3, wallColor);
				}
				wall = this.getHorizontalWall(x, z);
				if (wall > 0 && (Objects.requireNonNull(EntityHandler.getDoorDef(wall - 1)).getUnknown() == 0 || this.showInvisibleWalls)) {
					this.minimapGraphics.drawLineVert(x * 3, z * 3, wallColor, 3);
				}
				wall = this.getWallDiagonal(x, z);
				if (wall > 0 && wall < 12000
					&& (Objects.requireNonNull(EntityHandler.getDoorDef(wall - 1)).getUnknown() == 0 || this.showInvisibleWalls)) {
					this.minimapGraphics.setPixel(x * 3, z * 3, wallColor);
					this.minimapGraphics.setPixel(1 + x * 3, 1 + z * 3, wallColor);
					this.minimapGraphics.setPixel(x * 3 + 2, 2 + z * 3, wallColor);
				}
				if (wall > 12000 && wall < 24000
					&& (Objects.requireNonNull(EntityHandler.getDoorDef(wall - 12001)).getUnknown() == 0 || this.showInvisibleWalls)) {

					this.minimapGraphics.setPixel(2 + x * 3, z * 3, wallColor);
					this.minimapGraphics.setPixel(x * 3 + 1, z * 3 + 1, wallColor);
					this.minimapGraphics.setPixel(x * 3, 2 + z * 3, wallColor);
				}
			}
		this.minimapGraphics.copyPixelDataToSurface(GraphicsController.SPRITE_LAYER.WORLDMAP, 0, 0,
			MINIMAP_PIXEL_SIZE, MINIMAP_PIXEL_SIZE);
	}

	private void loadWorldmapSection(int sector, int height, int sectionX, int sectionY) {
		worldMapSector[sector] = loadSectorTemplate(height, sectionX, sectionY).copy();
		applyWorldEditorTerrainPatches(worldMapSector[sector],height,sectionX,sectionY);
	}

	private void loadSection(int sector, int height, int sectionX, int sectionY) {
		sectors[sector] = loadSectorTemplate(height, sectionX, sectionY).copy();
		applyWorldEditorTerrainPatches(sectors[sector],height,sectionX,sectionY);
	}

	public void applyWorldEditorTerrainPatch(int plane,int archiveX,int archiveZ,int elevation,int groundTexture,int groundOverlay,
		int roofTexture,int horizontalWall,int verticalWall,int diagonal,boolean overlayPainted){
		int sectionX=Math.floorDiv(archiveX,SECTION_SIZE),sectionY=Math.floorDiv(archiveZ,SECTION_SIZE);
		int localX=Math.floorMod(archiveX,SECTION_SIZE),localZ=Math.floorMod(archiveZ,SECTION_SIZE);
		String sector=sectorFilename(plane,sectionX,sectionY);
		synchronized(worldEditorTerrainPatchLock){
			Map<Integer,TerrainPatch> patches=worldEditorTerrainPatches.get(sector);
			if(patches==null){patches=new HashMap<Integer,TerrainPatch>();worldEditorTerrainPatches.put(sector,patches);}
			int key=localX*SECTION_SIZE+localZ;TerrainPatch previous=patches.get(key);
			patches.put(key,new TerrainPatch(localX,localZ,elevation,groundTexture,groundOverlay,roofTexture,horizontalWall,verticalWall,diagonal,overlayPainted||(previous!=null&&previous.editorPaintedOverlay)));
			worldEditorTerrainRevision++;
		}
	}
	private void applyWorldEditorTerrainPatches(Sector sector,int plane,int sectionX,int sectionY){
		if(sector==null)return;Map<Integer,TerrainPatch> snapshot;
		synchronized(worldEditorTerrainPatchLock){Map<Integer,TerrainPatch> stored=worldEditorTerrainPatches.get(sectorFilename(plane,sectionX,sectionY));if(stored==null||stored.isEmpty())return;snapshot=new HashMap<Integer,TerrainPatch>(stored);}
		for(TerrainPatch patch:snapshot.values()){
			com.openrsc.client.model.Tile tile=sector.getTile(patch.localX,patch.localZ);
			tile.groundElevation=(byte)patch.elevation;tile.groundTexture=(byte)patch.groundTexture;tile.groundOverlay=(byte)patch.groundOverlay;
			tile.roofTexture=(byte)patch.roofTexture;tile.horizontalWall=(byte)patch.horizontalWall;tile.verticalWall=(byte)patch.verticalWall;tile.diagonalWalls=patch.diagonal;tile.editorPaintedOverlay=patch.editorPaintedOverlay;
		}
	}
	private static final class TerrainPatch{
		final int localX,localZ,elevation,groundTexture,groundOverlay,roofTexture,horizontalWall,verticalWall,diagonal;final boolean editorPaintedOverlay;
		TerrainPatch(int x,int z,int e,int t,int o,int r,int h,int v,int d,boolean painted){localX=x;localZ=z;elevation=e;groundTexture=t;groundOverlay=o;roofTexture=r;horizontalWall=h;verticalWall=v;diagonal=d;editorPaintedOverlay=painted;}
	}

	private Sector loadSectorTemplate(int height, int sectionX, int sectionY) {
		String filename = sectorFilename(height, sectionX, sectionY);
		this.worldStreamManager.markRequested(height, sectionX, sectionY);
		Sector cached;
		synchronized (sectorCacheLock) {
			cached = sectorTemplateCache.get(filename);
			if (cached != null) {
				this.worldStreamManager.markCacheHit(height, sectionX, sectionY);
				return cached;
			}
		}

		long decodeStart = WorldStreamManager.now();
		this.worldStreamManager.markDecoding(height, sectionX, sectionY);
		Sector sector = readSectorTemplate(filename, height);
		this.worldStreamManager.markDecoded(height, sectionX, sectionY, WorldStreamManager.elapsedSince(decodeStart));
		synchronized (sectorCacheLock) {
			cached = sectorTemplateCache.get(filename);
			if (cached != null) {
				this.worldStreamManager.markCacheHit(height, sectionX, sectionY);
				return cached;
			}
			sectorTemplateCache.put(filename, sector);
			return sector;
		}
	}

	private Sector readSectorTemplate(String filename, int height) {
		try {
			ZipEntry e;
			ByteBuffer data = null;
			synchronized (tileArchiveLock) {
				e = tileArchive.getEntry(filename);
				if (e != null) {
					data = DataConversions
						.streamToBuffer(new BufferedInputStream(tileArchive.getInputStream(e)));
				}
			}
			if (e == null) {
				Sector sector = new Sector();
				if (height == 0 || height == 3) {
					for (int i = 0; i < 2304; i++) {
						sector.getTile(i).groundOverlay = (byte) (height == 0 ? -6 : 8);
					}
				}
				return sector;
			}
			return Sector.unpack(data);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return new Sector();
	}

	private String sectorFilename(int height, int sectionX, int sectionY) {
		return "h" + height + "x" + sectionX + "y" + sectionY;
	}

	private static final class WorldModelProduct {
		private final TerrainModelInput terrainInput;
		private final WallModelInput wallInput;
		private final RoofModelInput roofInput;
		private final WorldGpuChunkMesh gpuChunkMesh;

		private WorldModelProduct(
			TerrainModelInput terrainInput,
			WallModelInput wallInput,
			RoofModelInput roofInput,
			WorldGpuChunkMesh gpuChunkMesh) {
			this.terrainInput = terrainInput;
			this.wallInput = wallInput;
			this.roofInput = roofInput;
			this.gpuChunkMesh = gpuChunkMesh;
		}

		private boolean hasTerrainIfNeeded(boolean includeTerrain) {
			return !includeTerrain || terrainInput != null;
		}
	}

	private static final class WorldGpuChunkMesh {
		private final int plane;
		private final int centerSectionX;
		private final int centerSectionY;
		private final int originWorldX;
		private final int originWorldZ;
		private final int[] vertexCoords;
		private final float[] vertexTextureU;
		private final float[] vertexTextureV;
		private final int[] vertexLights;
		private final int[] vertexTerrainBlendColors;
		private final int[] vertexTerrainBlendStrengths;
		private final int[] indices;
		private final int[] triangleTextures;
		private final int[] triangleFallbackColors;
		private final Renderer3DModelKind[] triangleModelKinds;
		private final Renderer3DMaterialFamily[] triangleMaterialFamilies;
		private final int[] triangleTerrainVariationMasks;
		private final Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasters;
		private final Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitters;
		private final long[] roofCoverageBits;
		private final int roofCoverageAxis;
		private final int roofCoveredTileCount;
		private final int terrainTriangles;
		private final int wallTriangles;
		private final int roofTriangles;
		private final long signature;

		private WorldGpuChunkMesh(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ,
			int[] vertexCoords,
			float[] vertexTextureU,
			float[] vertexTextureV,
			int[] vertexLights,
			int[] vertexTerrainBlendColors,
			int[] vertexTerrainBlendStrengths,
			int[] indices,
			int[] triangleTextures,
			int[] triangleFallbackColors,
			Renderer3DModelKind[] triangleModelKinds,
			Renderer3DMaterialFamily[] triangleMaterialFamilies,
			int[] triangleTerrainVariationMasks,
			Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasters,
			Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitters,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount,
			int terrainTriangles,
			int wallTriangles,
			int roofTriangles,
			long signature) {
			this.plane = plane;
			this.centerSectionX = centerSectionX;
			this.centerSectionY = centerSectionY;
			this.originWorldX = originWorldX;
			this.originWorldZ = originWorldZ;
			this.vertexCoords = vertexCoords;
			this.vertexTextureU = vertexTextureU;
			this.vertexTextureV = vertexTextureV;
			this.vertexLights = vertexLights;
			this.vertexTerrainBlendColors = vertexTerrainBlendColors;
			this.vertexTerrainBlendStrengths = vertexTerrainBlendStrengths;
			this.indices = indices;
			this.triangleTextures = triangleTextures;
			this.triangleFallbackColors = triangleFallbackColors;
			this.triangleModelKinds = triangleModelKinds;
			this.triangleMaterialFamilies = triangleMaterialFamilies;
			this.triangleTerrainVariationMasks = triangleTerrainVariationMasks;
			this.shadowCasters = shadowCasters;
			this.glowEmitters = glowEmitters;
			this.roofCoverageBits = roofCoverageBits;
			this.roofCoverageAxis = roofCoverageAxis;
			this.roofCoveredTileCount = roofCoveredTileCount;
			this.terrainTriangles = terrainTriangles;
			this.wallTriangles = wallTriangles;
			this.roofTriangles = roofTriangles;
			this.signature = signature;
		}

		private int getTriangleCount() {
			return triangleTextures.length;
		}

		private Renderer3DWorldChunkFrame.ChunkMesh toRenderer3DWorldChunkMesh() {
			return new Renderer3DWorldChunkFrame.ChunkMesh(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexCoords,
				vertexTextureU,
				vertexTextureV,
				vertexLights,
				vertexTerrainBlendColors,
				vertexTerrainBlendStrengths,
				indices,
				triangleTextures,
				triangleFallbackColors,
				triangleModelKinds,
				triangleMaterialFamilies,
				shadowCasters,
				glowEmitters,
				triangleTerrainVariationMasks,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				false,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,
				signature);
		}
	}

	private static final class WorldGpuChunkMeshBuilder {
		private static final long FNV_OFFSET_BASIS = -3750763034362895579L;
		private static final long FNV_PRIME = 1099511628211L;

		private final int plane;
		private final int centerSectionX;
		private final int centerSectionY;
		private final int originWorldX;
		private final int originWorldZ;
		private final List<Integer> vertexCoords = new ArrayList<Integer>();
		private final List<Float> vertexTextureU = new ArrayList<Float>();
		private final List<Float> vertexTextureV = new ArrayList<Float>();
		private final List<Integer> vertexLights = new ArrayList<Integer>();
		private final List<Integer> vertexTerrainBlendColors = new ArrayList<Integer>();
		private final List<Integer> vertexTerrainBlendStrengths = new ArrayList<Integer>();
		private final List<Integer> indices = new ArrayList<Integer>();
		private final List<Integer> triangleTextures = new ArrayList<Integer>();
		private final List<Integer> triangleFallbackColors = new ArrayList<Integer>();
		private final List<Renderer3DModelKind> triangleModelKinds = new ArrayList<Renderer3DModelKind>();
		private final List<Renderer3DMaterialFamily> triangleMaterialFamilies =
			new ArrayList<Renderer3DMaterialFamily>();
		private final List<Integer> triangleTerrainVariationMasks = new ArrayList<Integer>();
		private final List<Renderer3DWorldChunkFrame.ShadowCaster> shadowCasters =
			new ArrayList<Renderer3DWorldChunkFrame.ShadowCaster>();
		private final List<Renderer3DWorldChunkFrame.GlowEmitter> glowEmitters =
			new ArrayList<Renderer3DWorldChunkFrame.GlowEmitter>();
		private long[] roofCoverageBits = new long[0];
		private int roofCoverageAxis;
		private int roofCoveredTileCount;
		private int terrainTriangles;
		private int wallTriangles;
		private int roofTriangles;

		private WorldGpuChunkMeshBuilder(
			int plane,
			int centerSectionX,
			int centerSectionY,
			int originWorldX,
			int originWorldZ) {
			this.plane = plane;
			this.centerSectionX = centerSectionX;
			this.centerSectionY = centerSectionY;
			this.originWorldX = originWorldX;
			this.originWorldZ = originWorldZ;
		}

		private void setRoofCoverage(long[] bits, int axis, int coveredTileCount) {
			this.roofCoverageBits = bits == null ? new long[0] : bits.clone();
			this.roofCoverageAxis = axis <= 0 ? 0 : axis;
			this.roofCoveredTileCount = Math.max(0, coveredTileCount);
		}

		private void addFace(
			Renderer3DModelKind kind,
			int texture,
			int fallbackColor,
			int[] faceVertexCoords,
			int[] faceVertexLights) {
			addFace(
				kind,
				Renderer3DMaterialClassifier.fallbackFor(kind),
				texture,
				fallbackColor,
				faceVertexCoords,
				faceVertexLights,
				null,
				null,
				0);
		}

		private void addFace(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int texture,
			int fallbackColor,
			int[] faceVertexCoords,
			int[] faceVertexLights) {
			addFace(
				kind,
				family,
				texture,
				fallbackColor,
				faceVertexCoords,
				faceVertexLights,
				null,
				null,
				0);
		}

		private void addFace(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int texture,
			int fallbackColor,
			int[] faceVertexCoords,
			int[] faceVertexLights,
			int[] faceVertexTerrainBlendColors,
			int[] faceVertexTerrainBlendStrengths,
			int terrainVariationMask) {
			int vertexCount = faceVertexCoords == null ? 0 : faceVertexCoords.length / 3;
			if (vertexCount < 3) {
				return;
			}

			addWallShadowCaster(kind, faceVertexCoords);
			float[] textureU = new float[vertexCount];
			float[] textureV = new float[vertexCount];
			populateTextureCoordinates(texture, faceVertexCoords, textureU, textureV);
			for (int vertex = 1; vertex < vertexCount - 1; vertex++) {
				addTriangle(
					kind,
					family,
					texture,
					fallbackColor,
					faceVertexCoords,
					faceVertexLights,
					faceVertexTerrainBlendColors,
					faceVertexTerrainBlendStrengths,
					textureU,
					textureV,
					terrainVariationMask,
					0,
					vertex,
					vertex + 1);
			}
		}

		private void addTriangle(
			Renderer3DModelKind kind,
			Renderer3DMaterialFamily family,
			int texture,
			int fallbackColor,
			int[] faceVertexCoords,
			int[] faceVertexLights,
			int[] faceVertexTerrainBlendColors,
			int[] faceVertexTerrainBlendStrengths,
			float[] textureU,
			float[] textureV,
			int terrainVariationMask,
			int a,
			int b,
			int c) {
			int baseVertex = vertexCoords.size() / 3;
			addVertex(faceVertexCoords, faceVertexLights, faceVertexTerrainBlendColors, faceVertexTerrainBlendStrengths, textureU, textureV, a);
			addVertex(faceVertexCoords, faceVertexLights, faceVertexTerrainBlendColors, faceVertexTerrainBlendStrengths, textureU, textureV, b);
			addVertex(faceVertexCoords, faceVertexLights, faceVertexTerrainBlendColors, faceVertexTerrainBlendStrengths, textureU, textureV, c);
			indices.add(Integer.valueOf(baseVertex));
			indices.add(Integer.valueOf(baseVertex + 1));
			indices.add(Integer.valueOf(baseVertex + 2));
			triangleTextures.add(Integer.valueOf(texture));
			triangleFallbackColors.add(Integer.valueOf(resolveFallbackColor(texture, fallbackColor)));
			triangleModelKinds.add(kind);
			triangleMaterialFamilies.add(family == null
				? Renderer3DMaterialClassifier.fallbackFor(kind)
				: family);
			triangleTerrainVariationMasks.add(Integer.valueOf(terrainVariationMask));
			if (kind == Renderer3DModelKind.TERRAIN) {
				terrainTriangles++;
			} else if (kind == Renderer3DModelKind.WALL) {
				wallTriangles++;
			} else if (kind == Renderer3DModelKind.ROOF) {
				roofTriangles++;
			}
		}

		private int resolveFallbackColor(int texture, int fallbackColor) {
			if (texture == Scene.TRANSPARENT && fallbackColor != Scene.TRANSPARENT) {
				return resourceToRgb(fallbackColor);
			}
			return fallbackColor;
		}

		private void addWallShadowCaster(Renderer3DModelKind kind, int[] faceVertexCoords) {
			if (kind != Renderer3DModelKind.WALL || faceVertexCoords == null || faceVertexCoords.length < 9) {
				return;
			}
			int vertexCount = faceVertexCoords.length / 3;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int bestA = 0;
			int bestB = 0;
			long bestDistance = 0L;
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int coord = vertex * 3;
				int y = faceVertexCoords[coord + 1];
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				for (int other = vertex + 1; other < vertexCount; other++) {
					int otherCoord = other * 3;
					long dx = faceVertexCoords[otherCoord] - faceVertexCoords[coord];
					long dz = faceVertexCoords[otherCoord + 2] - faceVertexCoords[coord + 2];
					long distance = dx * dx + dz * dz;
					if (distance > bestDistance) {
						bestDistance = distance;
						bestA = vertex;
						bestB = other;
					}
				}
			}
			if (bestDistance <= 0L || minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE) {
				return;
			}
			int firstCoord = bestA * 3;
			int secondCoord = bestB * 3;
			int width = Math.max(1, (int) Math.sqrt(bestDistance));
			if (width > RENDERER_3D_WALL_SHADOW_MAX_FOOTPRINT) {
				return;
			}
			shadowCasters.add(new Renderer3DWorldChunkFrame.ShadowCaster(
				kind,
				faceVertexCoords[firstCoord],
				minY,
				faceVertexCoords[firstCoord + 2],
				faceVertexCoords[secondCoord],
				faceVertexCoords[secondCoord + 2],
				maxY - minY,
				width,
				192,
				true));
		}

		private void addGlowEmitter(
			Renderer3DModelKind kind,
			int centerX,
			int centerY,
			int centerZ,
			int radius,
			int color,
			int intensity) {
			glowEmitters.add(new Renderer3DWorldChunkFrame.GlowEmitter(
				kind,
				centerX,
				centerY,
				centerZ,
				radius,
				color,
				intensity));
		}

		private int resourceToRgb(int resource) {
			if (resource == Scene.TRANSPARENT) {
				return 0;
			}
			if (resource >= 0) {
				return resource;
			}

			int encoded = -(resource + 1);
			int red = (encoded & 0x7C00) >> 10;
			int green = (encoded & 0x3E0) >> 5;
			int blue = encoded & 0x1F;
			return (red << 19) + (green << 11) + (blue << 3);
		}

		private void addVertex(
			int[] faceVertexCoords,
			int[] faceVertexLights,
			int[] faceVertexTerrainBlendColors,
			int[] faceVertexTerrainBlendStrengths,
			float[] textureU,
			float[] textureV,
			int vertex) {
			int coord = vertex * 3;
			int x = faceVertexCoords[coord];
			int y = faceVertexCoords[coord + 1];
			int z = faceVertexCoords[coord + 2];
			vertexCoords.add(Integer.valueOf(x));
			vertexCoords.add(Integer.valueOf(y));
			vertexCoords.add(Integer.valueOf(z));
			vertexTextureU.add(Float.valueOf(textureU[vertex]));
			vertexTextureV.add(Float.valueOf(textureV[vertex]));
			vertexLights.add(Integer.valueOf(vertexLight(faceVertexLights, vertex)));
			vertexTerrainBlendColors.add(Integer.valueOf(vertexTerrainBlendColor(faceVertexTerrainBlendColors, vertex)));
			vertexTerrainBlendStrengths.add(Integer.valueOf(vertexTerrainBlendStrength(faceVertexTerrainBlendStrengths, vertex)));
		}

		private int vertexLight(int[] faceVertexLights, int vertex) {
			return faceVertexLights == null || vertex < 0 || vertex >= faceVertexLights.length
				? 0
				: faceVertexLights[vertex];
		}

		private int vertexTerrainBlendColor(int[] faceVertexTerrainBlendColors, int vertex) {
			return faceVertexTerrainBlendColors == null || vertex < 0 || vertex >= faceVertexTerrainBlendColors.length
				? 0
				: faceVertexTerrainBlendColors[vertex];
		}

		private int vertexTerrainBlendStrength(int[] faceVertexTerrainBlendStrengths, int vertex) {
			return faceVertexTerrainBlendStrengths == null || vertex < 0 || vertex >= faceVertexTerrainBlendStrengths.length
				? 0
				: faceVertexTerrainBlendStrengths[vertex];
		}

		private void populateTextureCoordinates(
			int texture,
			int[] faceVertexCoords,
			float[] textureU,
			float[] textureV) {
			if (texture < 0 || faceVertexCoords.length < 9) {
				return;
			}

			int last = faceVertexCoords.length / 3 - 1;
			double ux = faceVertexCoords[3] - faceVertexCoords[0];
			double uy = faceVertexCoords[4] - faceVertexCoords[1];
			double uz = faceVertexCoords[5] - faceVertexCoords[2];
			double vx = faceVertexCoords[last * 3] - faceVertexCoords[0];
			double vy = faceVertexCoords[last * 3 + 1] - faceVertexCoords[1];
			double vz = faceVertexCoords[last * 3 + 2] - faceVertexCoords[2];
			double uu = dot(ux, uy, uz, ux, uy, uz);
			double uv = dot(ux, uy, uz, vx, vy, vz);
			double vv = dot(vx, vy, vz, vx, vy, vz);
			double determinant = uu * vv - uv * uv;
			if (Math.abs(determinant) < 0.000001) {
				return;
			}

			for (int vertex = 0; vertex < textureU.length; vertex++) {
				int coord = vertex * 3;
				double px = faceVertexCoords[coord] - faceVertexCoords[0];
				double py = faceVertexCoords[coord + 1] - faceVertexCoords[1];
				double pz = faceVertexCoords[coord + 2] - faceVertexCoords[2];
				double pu = dot(px, py, pz, ux, uy, uz);
				double pv = dot(px, py, pz, vx, vy, vz);
				textureU[vertex] = (float) ((pu * vv - pv * uv) / determinant);
				textureV[vertex] = (float) ((pv * uu - pu * uv) / determinant);
			}
		}

		private static double dot(
			double leftX,
			double leftY,
			double leftZ,
			double rightX,
			double rightY,
			double rightZ) {
			return leftX * rightX + leftY * rightY + leftZ * rightZ;
		}

		private WorldGpuChunkMesh build() {
			int[] vertexArray = toIntArray(vertexCoords);
			float[] textureUArray = toFloatArray(vertexTextureU);
			float[] textureVArray = toFloatArray(vertexTextureV);
			int[] lightArray = toIntArray(vertexLights);
			int[] terrainBlendColorArray = toIntArray(vertexTerrainBlendColors);
			int[] terrainBlendStrengthArray = toIntArray(vertexTerrainBlendStrengths);
			int[] indexArray = toIntArray(indices);
			int[] textureArray = toIntArray(triangleTextures);
			int[] fallbackArray = toIntArray(triangleFallbackColors);
			int[] terrainVariationMaskArray = toIntArray(triangleTerrainVariationMasks);
			Renderer3DModelKind[] kindArray =
				triangleModelKinds.toArray(new Renderer3DModelKind[triangleModelKinds.size()]);
			Renderer3DMaterialFamily[] familyArray = triangleMaterialFamilies.toArray(
				new Renderer3DMaterialFamily[triangleMaterialFamilies.size()]);
			Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasterArray =
				shadowCasters.toArray(new Renderer3DWorldChunkFrame.ShadowCaster[shadowCasters.size()]);
			Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitterArray =
				glowEmitters.toArray(new Renderer3DWorldChunkFrame.GlowEmitter[glowEmitters.size()]);
			long signature = signature(
				vertexArray,
				textureUArray,
				textureVArray,
				lightArray,
				terrainBlendColorArray,
				terrainBlendStrengthArray,
				indexArray,
				textureArray,
				fallbackArray,
				terrainVariationMaskArray,
				kindArray,
				familyArray,
				shadowCasterArray,
				glowEmitterArray,
				roofCoverageBits,
				roofCoverageAxis,
				roofCoveredTileCount);
			return new WorldGpuChunkMesh(
				plane,
				centerSectionX,
				centerSectionY,
				originWorldX,
				originWorldZ,
				vertexArray,
				textureUArray,
				textureVArray,
				lightArray,
				terrainBlendColorArray,
				terrainBlendStrengthArray,
				indexArray,
				textureArray,
				fallbackArray,
				kindArray,
				familyArray,
				terrainVariationMaskArray,
				shadowCasterArray,
				glowEmitterArray,
				roofCoverageBits.clone(),
				roofCoverageAxis,
				roofCoveredTileCount,
				terrainTriangles,
				wallTriangles,
				roofTriangles,
				signature);
		}

		private static int[] toIntArray(List<Integer> values) {
			int[] array = new int[values.size()];
			for (int i = 0; i < values.size(); i++) {
				array[i] = values.get(i).intValue();
			}
			return array;
		}

		private static float[] toFloatArray(List<Float> values) {
			float[] array = new float[values.size()];
			for (int i = 0; i < values.size(); i++) {
				array[i] = values.get(i).floatValue();
			}
			return array;
		}

		private long signature(
			int[] vertexArray,
			float[] textureUArray,
			float[] textureVArray,
			int[] lightArray,
			int[] terrainBlendColorArray,
			int[] terrainBlendStrengthArray,
			int[] indexArray,
			int[] textureArray,
			int[] fallbackArray,
			int[] terrainVariationMaskArray,
			Renderer3DModelKind[] kindArray,
			Renderer3DMaterialFamily[] familyArray,
			Renderer3DWorldChunkFrame.ShadowCaster[] shadowCasterArray,
			Renderer3DWorldChunkFrame.GlowEmitter[] glowEmitterArray,
			long[] roofCoverageBits,
			int roofCoverageAxis,
			int roofCoveredTileCount) {
			long hash = FNV_OFFSET_BASIS;
			hash = mix(hash, plane);
			hash = mix(hash, centerSectionX);
			hash = mix(hash, centerSectionY);
			hash = mix(hash, originWorldX);
			hash = mix(hash, originWorldZ);
			for (int value : vertexArray) {
				hash = mix(hash, value);
			}
			for (float value : textureUArray) {
				hash = mix(hash, Float.floatToIntBits(value));
			}
			for (float value : textureVArray) {
				hash = mix(hash, Float.floatToIntBits(value));
			}
			for (int value : lightArray) {
				hash = mix(hash, value);
			}
			for (int value : terrainBlendColorArray) {
				hash = mix(hash, value);
			}
			for (int value : terrainBlendStrengthArray) {
				hash = mix(hash, value);
			}
			for (int value : indexArray) {
				hash = mix(hash, value);
			}
			for (int value : textureArray) {
				hash = mix(hash, value);
			}
			for (int value : fallbackArray) {
				hash = mix(hash, value);
			}
			for (int value : terrainVariationMaskArray) {
				hash = mix(hash, value);
			}
			for (Renderer3DModelKind kind : kindArray) {
				hash = mix(hash, kind.ordinal());
			}
			for (Renderer3DMaterialFamily family : familyArray) {
				hash = mix(hash, family.getShaderId());
			}
			for (Renderer3DWorldChunkFrame.ShadowCaster caster : shadowCasterArray) {
				hash = mix(hash, caster.getModelKind().ordinal());
				hash = mix(hash, caster.getBaseX0());
				hash = mix(hash, caster.getBaseY());
				hash = mix(hash, caster.getBaseZ0());
				hash = mix(hash, caster.getBaseX1());
				hash = mix(hash, caster.getBaseZ1());
				hash = mix(hash, caster.getHeight());
				hash = mix(hash, caster.getWidth());
				hash = mix(hash, caster.getOpacity());
				hash = mix(hash, caster.isOutdoorOnly() ? 1 : 0);
			}
			for (Renderer3DWorldChunkFrame.GlowEmitter emitter : glowEmitterArray) {
				hash = mix(hash, emitter.getModelKind().ordinal());
				hash = mix(hash, emitter.getCenterX());
				hash = mix(hash, emitter.getCenterY());
				hash = mix(hash, emitter.getCenterZ());
				hash = mix(hash, emitter.getRadius());
				hash = mix(hash, emitter.getColor());
				hash = mix(hash, emitter.getIntensity());
			}
			hash = mix(hash, roofCoverageAxis);
			hash = mix(hash, roofCoveredTileCount);
			for (long value : roofCoverageBits) {
				hash = mix(hash, (int) value);
				hash = mix(hash, (int) (value >>> 32));
			}
			return hash;
		}

		private static long mix(long hash, int value) {
			hash ^= value & 0xffffffffL;
			return hash * FNV_PRIME;
		}
	}

	private static final class TerrainModelInput {
		private final TerrainVertexInput[] vertices;
		private final TerrainTileFaceInput[] tileFaces;
		private final TerrainOverlayFaceInput[] overlayFaces;

		private TerrainModelInput(
			TerrainVertexInput[] vertices,
			TerrainTileFaceInput[] tileFaces,
			TerrainOverlayFaceInput[] overlayFaces) {
			this.vertices = vertices;
			this.tileFaces = tileFaces;
			this.overlayFaces = overlayFaces;
		}
	}

	private static final class TerrainModelInputSource {
		private final Sector[] sectors;

		private TerrainModelInputSource(Sector[] sectors) {
			this.sectors = sectors;
		}

		private int terrainColour(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).groundTexture & 0xff;
		}

		private int tileDecorationID(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).groundOverlay & 0xff;
		}
		private boolean editorPaintedOverlay(int tileX,int tileZ){
			Sector sector=sectorForLocalTile(sectors,tileX,tileZ);
			return sector!=null&&sector.getTile(tileInSector(tileX),tileInSector(tileZ)).editorPaintedOverlay;
		}

		private int tileDecorationCacheVal(int tileX, int tileZ, int defaultVal) {
			int id = tileDecorationID(tileX, tileZ);
			if (id == 0) {
				return defaultVal;
			}
			return Objects.requireNonNull(EntityHandler.getTileDef(id - 1)).getColour();
		}

		private int tileElevation(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null
				? 0
				: (sector.getTile(tileInSector(tileX), tileInSector(tileZ)).groundElevation & 0xff) * 3;
		}

		private int tileType2(int tileX, int tileZ) {
			int id = tileDecorationID(tileX, tileZ);
			if (id == 0) {
				return -1;
			}
			return Objects.requireNonNull(EntityHandler.getTileDef(id - 1)).getTileValue() == 2 ? 1 : 0;
		}

		private boolean pickableInvisibleOverlay(int tileX, int tileZ) {
			return tileDecorationID(tileX, tileZ) == 26;
		}

		private int wallDiagonal(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).diagonalWalls;
		}

		private int verticalWall(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).verticalWall & 0xff;
		}

		private int horizontalWall(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).horizontalWall & 0xff;
		}

		private int wallRoof(int tileX, int tileZ) {
			Sector sector = sectorForLocalTile(sectors, tileX, tileZ);
			return sector == null ? 0 : sector.getTile(tileInSector(tileX), tileInSector(tileZ)).roofTexture;
		}

		private boolean hasRoofStrut(int tileX, int tileZ) {
			return wallRoof(tileX, tileZ) <= 0
				&& wallRoof(tileX - 1, tileZ) <= 0
				&& wallRoof(tileX - 1, tileZ - 1) <= 0
				&& wallRoof(tileX, tileZ - 1) <= 0;
		}

		private boolean hasRoofTile(int tileX, int tileZ) {
			return wallRoof(tileX, tileZ) > 0
				&& wallRoof(tileX - 1, tileZ) > 0
				&& wallRoof(tileX - 1, tileZ - 1) > 0
				&& wallRoof(tileX, tileZ - 1) > 0;
		}
	}

	private static final class WallModelInput {
		private final WallSegmentInput[] segments;

		private WallModelInput(WallSegmentInput[] segments) {
			this.segments = segments;
		}
	}

	private static final class WallSegmentInput {
		private static final int VERTICAL = 0;
		private static final int HORIZONTAL = 1;
		private static final int DIAGONAL_A = 2;
		private static final int DIAGONAL_B = 3;

		private final int wallID;
		private final int kind;
		private final int x;
		private final int z;
		private final int frontTexture;
		private final int backTexture;
		private final int facePickIndex;
		private final int[] vertexCoords;

		private WallSegmentInput(
			int wallID,
			int kind,
			int x,
			int z,
			int frontTexture,
			int backTexture,
			int facePickIndex,
			int... vertexCoords) {
			this.wallID = wallID;
			this.kind = kind;
			this.x = x;
			this.z = z;
			this.frontTexture = frontTexture;
			this.backTexture = backTexture;
			this.facePickIndex = facePickIndex;
			this.vertexCoords = vertexCoords.clone();
		}
	}

	private static final class RoofElevationWorkspace {
		private final int[][] elevations;

		private RoofElevationWorkspace(int[][] elevations) {
			this.elevations = elevations;
		}

		private static RoofElevationWorkspace fromSource(TerrainModelInputSource source) {
			int[][] elevations = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
			for (int x = 0; x < LOCAL_TILE_COUNT; x++) {
				for (int z = 0; z < LOCAL_TILE_COUNT; z++) {
					elevations[x][z] = source.tileElevation(x, z);
				}
			}
			return new RoofElevationWorkspace(elevations);
		}

		private int get(int x, int z) {
			return elevations[x][z];
		}

		private void set(int x, int z, int value) {
			elevations[x][z] = value;
		}

		private void clearRoofMarkers() {
			for (int x = 0; x < LOCAL_TILE_COUNT; x++) {
				for (int z = 0; z < LOCAL_TILE_COUNT; z++) {
					if (elevations[x][z] >= 80000) {
						elevations[x][z] -= 80000;
					}
				}
			}
		}

		private int[][] copyElevations() {
			int[][] copy = new int[LOCAL_TILE_COUNT][LOCAL_TILE_COUNT];
			for (int x = 0; x < LOCAL_TILE_COUNT; x++) {
				System.arraycopy(elevations[x], 0, copy[x], 0, LOCAL_TILE_COUNT);
			}
			return copy;
		}
	}

	private static final class RoofModelInput {
		private final RoofFaceInput[] faces;
		private final int[][] finalElevations;
		private final long[] roofCoverageBits;
		private final int roofCoveredTileCount;

		private RoofModelInput(
			RoofFaceInput[] faces,
			int[][] finalElevations,
			long[] roofCoverageBits,
			int roofCoveredTileCount) {
			this.faces = faces;
			this.finalElevations = finalElevations;
			this.roofCoverageBits = roofCoverageBits == null ? new long[0] : roofCoverageBits.clone();
			this.roofCoveredTileCount = Math.max(0, roofCoveredTileCount);
		}

		private void copyElevationsInto(int[][] target) {
			for (int x = 0; x < LOCAL_TILE_COUNT; x++) {
				System.arraycopy(finalElevations[x], 0, target[x], 0, LOCAL_TILE_COUNT);
			}
		}
	}

	private static final class RoofCoverageTiles {
		private final long[] bits;
		private final int count;

		private RoofCoverageTiles(long[] bits, int count) {
			this.bits = bits == null ? new long[0] : bits;
			this.count = Math.max(0, count);
		}
	}

	private static final class RoofFaceInput {
		private final int texture;
		private final int[] vertexCoords;

		private RoofFaceInput(int texture, int[] vertexCoords) {
			this.texture = texture;
			this.vertexCoords = vertexCoords.clone();
		}
	}

	private static final class TerrainVertexInput {
		private final int x;
		private final int y;
		private final int z;
		private final int light;
		private final int terrainBlendColor;
		private final int terrainBlendStrength;

		private TerrainVertexInput(
			int x,
			int y,
			int z,
			int light,
			int terrainBlendColor,
			int terrainBlendStrength) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.light = light;
			this.terrainBlendColor = terrainBlendColor;
			this.terrainBlendStrength = terrainBlendStrength;
		}
	}

	private static final class TerrainVertexBlendInput {
		private static final TerrainVertexBlendInput NONE = new TerrainVertexBlendInput(0, 0);

		private final int color;
		private final int strength;

		private TerrainVertexBlendInput(int color, int strength) {
			this.color = color;
			this.strength = strength;
		}
	}

	private static final class TerrainTileFaceInput {
		private final int x;
		private final int z;
		private final byte bridge00_11;
		private final int res01;
		private final int colorResource;
		private final int slope;
		private final boolean pickableInvisibleOverlay;
		private final boolean terrainVariationEligible;
		private final boolean lavaGlowEmitter;
		private final Renderer3DMaterialFamily materialFamily;
		private final boolean collisionFullBlock;
		private final boolean collisionObject;

		private TerrainTileFaceInput(
			int x,
			int z,
			byte bridge00_11,
			int res01,
			int colorResource,
			int slope,
			boolean pickableInvisibleOverlay,
			boolean terrainVariationEligible,
			boolean lavaGlowEmitter,
			Renderer3DMaterialFamily materialFamily,
			boolean collisionFullBlock,
			boolean collisionObject) {
			this.x = x;
			this.z = z;
			this.bridge00_11 = bridge00_11;
			this.res01 = res01;
			this.colorResource = colorResource;
			this.slope = slope;
			this.pickableInvisibleOverlay = pickableInvisibleOverlay;
			this.terrainVariationEligible = terrainVariationEligible;
			this.lavaGlowEmitter = lavaGlowEmitter;
			this.materialFamily = materialFamily == null
				? Renderer3DMaterialFamily.TERRAIN
				: materialFamily;
			this.collisionFullBlock = collisionFullBlock;
			this.collisionObject = collisionObject;
		}
	}

	private static final class TerrainOverlayFaceInput {
		private final int x;
		private final int z;
		private final int texture;
		private final Renderer3DMaterialFamily materialFamily;
		private final int[] vertexCoords;

		private TerrainOverlayFaceInput(
			int x,
			int z,
			int texture,
			Renderer3DMaterialFamily materialFamily,
			int[] vertexCoords) {
			this.x = x;
			this.z = z;
			this.texture = texture;
			this.materialFamily = materialFamily == null
				? Renderer3DMaterialFamily.WATER
				: materialFamily;
			this.vertexCoords = vertexCoords.clone();
		}
	}

	private static final class CpuSectionWindow {
		private final Sector[] sectors;
		private final boolean bridgeDecorationsApplied;

		private CpuSectionWindow(Sector[] sectors, boolean bridgeDecorationsApplied) {
			this.sectors = sectors;
			this.bridgeDecorationsApplied = bridgeDecorationsApplied;
		}

		private void copyInto(Sector[] target) {
			for (int i = 0; i < sectors.length; i++) {
				target[i] = sectors[i].copy();
			}
		}
	}

	public int getWorldMapX() {
		return mapPointX;
	}

	public int getWorldMapZ() {
		return mapPointZ;
	}

}
