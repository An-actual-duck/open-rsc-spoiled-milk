package com.openrsc.server.model.world.region;

import com.openrsc.server.util.rsc.CollisionFlag;
import java.util.Arrays;

public class TileValue {
	public byte traversalMask = CollisionFlag.FULL_BLOCK;
	public short diagWallVal = 0;
	public byte horizontalWallVal = 0;
	public byte overlay = 0;
	public byte verticalWallVal = 0;
	public byte elevation = 0;
	public boolean projectileAllowed = false;
	public boolean originalProjectileAllowed = false;
	private boolean terrainBlocked = false;
	private int blockingSceneryCount = 0;
	private int terrainCollisionMask = 0;
	private final int[] dynamicCollisionCounts = new int[6];
	private boolean terrainOverlayProjectileBlocked = false;
	private int terrainWallProjectileCount = 0;
	private int dynamicProjectileCount = 0;

	public TileValue() {
	}

	TileValue(
		final byte traversalMask,
		final short diagWallVal,
		final byte horizontalWallVal,
		final byte overlay,
		final byte verticalWallVal,
		final byte elevation,
		final boolean projectileAllowed,
		final boolean originalProjectileAllowed,
		final boolean terrainBlocked,
		final int blockingSceneryCount,
		final int terrainCollisionMask,
		final int[] dynamicCollisionCounts,
		final boolean terrainOverlayProjectileBlocked,
		final int terrainWallProjectileCount,
		final int dynamicProjectileCount) {
		if (dynamicCollisionCounts == null
			|| dynamicCollisionCounts.length != this.dynamicCollisionCounts.length) {
			throw new IllegalArgumentException(
				"Dynamic collision state must contain exactly "
					+ this.dynamicCollisionCounts.length + " counters");
		}
		this.traversalMask = traversalMask;
		this.diagWallVal = diagWallVal;
		this.horizontalWallVal = horizontalWallVal;
		this.overlay = overlay;
		this.verticalWallVal = verticalWallVal;
		this.elevation = elevation;
		this.projectileAllowed = projectileAllowed;
		this.originalProjectileAllowed = originalProjectileAllowed;
		this.terrainBlocked = terrainBlocked;
		this.blockingSceneryCount = blockingSceneryCount;
		this.terrainCollisionMask = terrainCollisionMask;
		System.arraycopy(
			dynamicCollisionCounts, 0, this.dynamicCollisionCounts, 0,
			dynamicCollisionCounts.length);
		this.terrainOverlayProjectileBlocked = terrainOverlayProjectileBlocked;
		this.terrainWallProjectileCount = terrainWallProjectileCount;
		this.dynamicProjectileCount = dynamicProjectileCount;
	}

	public TileValue copy() {
		TileValue copy = new TileValue();
		copy.traversalMask = traversalMask;
		copy.diagWallVal = diagWallVal;
		copy.horizontalWallVal = horizontalWallVal;
		copy.overlay = overlay;
		copy.verticalWallVal = verticalWallVal;
		copy.elevation = elevation;
		copy.projectileAllowed = projectileAllowed;
		copy.originalProjectileAllowed = originalProjectileAllowed;
		copy.terrainBlocked = terrainBlocked;
		copy.blockingSceneryCount = blockingSceneryCount;
		copy.terrainCollisionMask = terrainCollisionMask;
		System.arraycopy(dynamicCollisionCounts, 0, copy.dynamicCollisionCounts, 0, dynamicCollisionCounts.length);
		copy.terrainOverlayProjectileBlocked = terrainOverlayProjectileBlocked;
		copy.terrainWallProjectileCount = terrainWallProjectileCount;
		copy.dynamicProjectileCount = dynamicProjectileCount;
		return copy;
	}

	public void initializeTerrainCollision(){traversalMask=(byte)terrainCollisionMask;refreshFullBlock();refreshProjectile();}
	public void addTerrainCollision(int flags){terrainCollisionMask|=flags;refreshCollisionFlags(flags);}
	public void removeTerrainCollision(int flags){terrainCollisionMask&=~flags;refreshCollisionFlags(flags);}
	public void addDynamicCollision(int flags){for(int bit=0;bit<dynamicCollisionCounts.length;bit++)if((flags&(1<<bit))!=0)dynamicCollisionCounts[bit]++;refreshCollisionFlags(flags);}
	public void removeDynamicCollision(int flags){for(int bit=0;bit<dynamicCollisionCounts.length;bit++)if((flags&(1<<bit))!=0&&dynamicCollisionCounts[bit]>0)dynamicCollisionCounts[bit]--;refreshCollisionFlags(flags);}
	private void refreshCollisionFlags(int flags){for(int bit=0;bit<dynamicCollisionCounts.length;bit++){int flag=1<<bit;if((flags&flag)==0)continue;if((terrainCollisionMask&flag)!=0||dynamicCollisionCounts[bit]>0)traversalMask|=flag;else traversalMask&=~flag;}}

	public void setTerrainBlocked(boolean blocked) {
		terrainBlocked=blocked;
		refreshFullBlock();
	}
	public boolean isTerrainBlocked(){return terrainBlocked;}
	public void addBlockingScenery(){blockingSceneryCount++;refreshFullBlock();}
	public void removeBlockingScenery(){if(blockingSceneryCount>0)blockingSceneryCount--;refreshFullBlock();}
	public int getBlockingSceneryCount(){return blockingSceneryCount;}
	public int getTerrainCollisionMask(){return terrainCollisionMask;}
	public int[] getDynamicCollisionCounts(){return Arrays.copyOf(dynamicCollisionCounts,dynamicCollisionCounts.length);}
	public boolean isTerrainOverlayProjectileBlocked(){return terrainOverlayProjectileBlocked;}
	public int getTerrainWallProjectileCount(){return terrainWallProjectileCount;}
	public int getDynamicProjectileCount(){return dynamicProjectileCount;}
	public void setTerrainOverlayProjectileBlocked(boolean blocked){terrainOverlayProjectileBlocked=blocked;refreshProjectile();}
	public void addTerrainWallProjectileBlock(){terrainWallProjectileCount++;refreshProjectile();}
	public void removeTerrainWallProjectileBlock(){if(terrainWallProjectileCount>0)terrainWallProjectileCount--;refreshProjectile();}
	public void addDynamicProjectileBlock(){dynamicProjectileCount++;refreshProjectile();}
	public void removeDynamicProjectileBlock(){if(dynamicProjectileCount>0)dynamicProjectileCount--;refreshProjectile();}
	private void refreshProjectile(){originalProjectileAllowed=terrainOverlayProjectileBlocked||terrainWallProjectileCount>0;projectileAllowed=originalProjectileAllowed||dynamicProjectileCount>0;}
	private void refreshFullBlock(){
		if(terrainBlocked||blockingSceneryCount>0)traversalMask|=CollisionFlag.FULL_BLOCK_C;
		else traversalMask&=~CollisionFlag.FULL_BLOCK_C;
	}

	@Override
	public String toString() {
		return "TileValue{" +
			"traversalMask=" + traversalMask +
			", diagWallVal=" + diagWallVal +
			", horizontalWallVal=" + horizontalWallVal +
			", overlay=" + overlay +
			", verticalWallVal=" + verticalWallVal +
			", elevation=" + elevation +
			", projectileAllowed=" + projectileAllowed +
			", originalProjectileAllowed=" + originalProjectileAllowed +
			", terrainBlocked=" + terrainBlocked +
			", blockingSceneryCount=" + blockingSceneryCount +
			", terrainCollisionMask=" + terrainCollisionMask +
			", dynamicCollisionCounts=" + Arrays.toString(dynamicCollisionCounts) +
			'}';
	}

	public boolean equals(final TileValue other) {
		return 	this.traversalMask == other.traversalMask &&
				this.diagWallVal == other.diagWallVal &&
				this.horizontalWallVal == other.horizontalWallVal &&
				this.overlay == other.overlay &&
				this.verticalWallVal == other.verticalWallVal &&
				this.elevation == other.elevation &&
				this.projectileAllowed == other.projectileAllowed &&
				this.originalProjectileAllowed == other.originalProjectileAllowed &&
				this.terrainBlocked == other.terrainBlocked &&
				this.blockingSceneryCount == other.blockingSceneryCount &&
				this.terrainCollisionMask == other.terrainCollisionMask &&
				Arrays.equals(this.dynamicCollisionCounts,other.dynamicCollisionCounts) &&
				this.terrainOverlayProjectileBlocked == other.terrainOverlayProjectileBlocked &&
				this.terrainWallProjectileCount == other.terrainWallProjectileCount &&
				this.dynamicProjectileCount == other.dynamicProjectileCount;
	}
}
