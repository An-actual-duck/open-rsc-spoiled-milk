package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.content.worldedit.WorldEditorSessionManager.Validation;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager.TerrainStrokeResult;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager.NativeTerrainSnapshot;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager.NativeTerrainStrokeResult;
import com.openrsc.server.content.worldedit.WorldEditorTerrainStroke;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.WorldEditorTerrainArchive;
import com.openrsc.server.io.WorldLoader;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.WorldEditorRequestStruct;
import com.openrsc.server.net.rsc.struct.outgoing.WorldEditorStruct;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class WorldEditorHandler implements PayloadProcessor<WorldEditorRequestStruct, OpcodeIn> {
	private static final int MAX_ROOF_TEXTURE=6;
	@Override public void process(WorldEditorRequestStruct request, Player player) {
		if (!player.getConfig().ALLOW_IN_GAME_WORLD_EDITOR || !player.isAdmin()) { error(player, 0, "Editor authorization was revoked."); return; }
		Validation validation = player.getWorld().getServer().getWorldEditorSessions().validate(player, request.sessionId, request.sequence);
		if (!validation.accepted) { error(player, 0, validation.message); return; }
		if (request.type == 1) {
			player.getWorld().getServer().getWorldEditorSessions().closeFor(player);
			WorldEditorStruct out = new WorldEditorStruct(); out.type=2; out.sequence=validation.nextSequence;
			ActionSender.sendWorldEditor(player, out); return;
		}
		try {
			if (player.getConfig().WORLD_BUILDER_LAYERED_REVIEW_MODE
				&& (request.type == 5 || request.type == 6)
				&& !"spoiled-milk-builder-draft".equals(
					player.getConfig().LAYERED_NATIVE_WORLD_RUNTIME_PROFILE)) {
				error(player, validation.nextSequence,
					"Layered package review is read-only; no terrain was changed.");
				return;
			}
			if (request.type == 2) inspectTerrain(request, player, validation.nextSequence);
			else if (request.type == 3) inspectObject(request, player, validation.nextSequence);
			else if (request.type == 4) inspectNpc(request, player, validation.nextSequence);
			else if (request.type == 5) paintTerrain(request, player, validation.nextSequence);
			else if (request.type == 6) paintTerrainStroke(request, player, validation.nextSequence);
			else error(player, validation.nextSequence, "Unsupported editor operation.");
		} catch (Exception e) { error(player, validation.nextSequence, "Editor request failed: " + e.getMessage()); }
	}

	private void inspectTerrain(WorldEditorRequestStruct r, Player p, int next) throws Exception {
		if (p.getConfig().WORLD_BUILDER_LAYERED_REVIEW_MODE) {
			inspectNativeTerrain(r, p, next);
			return;
		}
		WorldEditorTerrainArchive.Snapshot s=p.getWorld().getServer().getWorldEditorSessions().inspectTerrain(p,r.x,r.y,r.plane);
		sendTerrain(p,s,next,3,r.objectType==1,0);
	}
	private void inspectNativeTerrain(WorldEditorRequestStruct r,Player p,int next) {
		WorldLocation current=p.getLayeredLocation();
		if(r.plane!=current.getCoordinate().getLevel())
			throw new IllegalArgumentException("Inspect a tile on the active signed level.");
		WorldCoordinate coordinate=new WorldCoordinate(r.x,r.y,r.plane);
		WorldLocation location=new WorldLocation(current.getWorldSpace(),coordinate);
		NativeTerrainSnapshot inspected=p.getWorld().getServer()
			.getWorldEditorSessions().inspectNativeTerrain(p,location);
		NativeLayeredTerrainTile source=inspected.tile;
		TileValue runtime=p.getWorld().getTile(location);
		WorldEditorStruct out=new WorldEditorStruct();out.type=3;out.sequence=next;
		out.x=coordinate.getX();out.y=coordinate.getY();out.plane=coordinate.getLevel();
		out.sectorX=coordinate.getSectorX();out.sectorY=coordinate.getSectorY();
		out.localX=coordinate.getLocalX();out.localY=coordinate.getLocalY();
		out.elevation=source.getElevation();out.groundTexture=source.getTexture();
		out.groundOverlay=inspectionGroundOverlay(runtime,source.getOverlay());
		out.roofTexture=source.getRoof();out.horizontalWall=source.getHorizontalWall();
		out.verticalWall=source.getVerticalWall();out.diagonal=source.getDiagonalWall();
		out.traversalMask=runtime==null?0:runtime.traversalMask&0xff;
		out.projectileAllowed=runtime!=null&&runtime.projectileAllowed;
		out.copy=r.objectType==1;
		out.message=wallDefinitionName(p,out.verticalWall-1)+"\t"
			+wallDefinitionName(p,out.horizontalWall-1)+"\t"
			+wallDefinitionName(p,diagonalRawWall(out.diagonal)-1);
		ActionSender.sendWorldEditor(p,out);
	}
	private void paintTerrain(WorldEditorRequestStruct r,Player p,int next) throws Exception {
		if(p.getConfig().WORLD_BUILDER_LAYERED_REVIEW_MODE){
			r.terrainTiles=new int[][]{{r.x,r.y}};
			paintNativeTerrainStroke(r,p,next);
			return;
		}
		if(!p.getWorld().withinWorld(r.x,r.y))throw new IllegalArgumentException("Terrain tile is outside the runtime world.");
		validateTerrainDefinitions(p,r);
		WorldEditorTerrainArchive.Snapshot before=p.getWorld().getServer().getWorldEditorSessions().inspectTerrain(p,r.x,r.y,r.plane);
		WorldEditorTerrainArchive.Snapshot s=p.getWorld().getServer().getWorldEditorSessions().paintTerrain(p,r.x,r.y,r.plane,r.fieldMask,r.elevation,r.groundTexture,r.groundOverlay,r.roofTexture,r.horizontalWall,r.verticalWall,r.diagonal);
		applyRuntimeTerrain(p,before,s,r.fieldMask);
		sendTerrain(p,s,next,7,false,r.fieldMask);
	}
	private void paintTerrainStroke(WorldEditorRequestStruct r,Player p,int next) throws Exception {
		if(p.getConfig().WORLD_BUILDER_LAYERED_REVIEW_MODE){
			paintNativeTerrainStroke(r,p,next);
			return;
		}
		validateTerrainDefinitions(p,r);
		int[][] coordinates=WorldEditorTerrainStroke.validateTiles(r.terrainTiles);
		for(int[] coordinate:coordinates){
			if(!p.getWorld().withinWorld(coordinate[0],coordinate[1])||p.getWorld().getTile(coordinate[0],coordinate[1])==null)
				throw new IllegalArgumentException("Terrain stroke contains a tile outside the runtime world.");
			if(Math.floorDiv(coordinate[1],944)!=r.plane)throw new IllegalArgumentException("Terrain stroke crosses a plane boundary.");
		}
		TerrainStrokeResult result=p.getWorld().getServer().getWorldEditorSessions().paintTerrainStroke(p,coordinates,r.plane,
			r.fieldMask,r.elevation,r.groundTexture,r.groundOverlay,r.roofTexture,r.horizontalWall,r.verticalWall,r.diagonal);
		WorldEditorStruct out=new WorldEditorStruct();out.type=8;out.sequence=next;out.fieldMask=r.fieldMask;
		for(int i=0;i<result.after.size();i++){
			WorldEditorTerrainArchive.Snapshot before=result.before.get(i),after=result.after.get(i);
			applyRuntimeTerrain(p,before,after,r.fieldMask);out.terrainTiles.add(terrainTile(p,after));
		}
		WorldEditorTerrainArchive.Snapshot center=result.after.get(0);
		out.message=wallDefinitionName(p,center.verticalWall-1)+"\t"+wallDefinitionName(p,center.horizontalWall-1)
			+"\t"+wallDefinitionName(p,center.diagonalDefinitionId());
		ActionSender.sendWorldEditor(p,out);
	}
	private void paintNativeTerrainStroke(WorldEditorRequestStruct r,Player p,int next) {
		validateTerrainDefinitions(p,r);
		WorldLocation current=p.getLayeredLocation();
		if(r.plane!=current.getCoordinate().getLevel())
			throw new IllegalArgumentException("Paint terrain on the active signed level.");
		int[][] coordinates=WorldEditorTerrainStroke.validateTiles(r.terrainTiles);
		for(int[] coordinate:coordinates){
			if(coordinate[0]<0||coordinate[0]>32767||coordinate[1]<0||coordinate[1]>32767)
				throw new IllegalArgumentException("Terrain stroke leaves supported Builder coordinates.");
		}
		NativeTerrainStrokeResult result=p.getWorld().getServer().getWorldEditorSessions()
			.paintNativeTerrainStroke(p,coordinates,r.plane,r.fieldMask,r.elevation,
				r.groundTexture,r.groundOverlay,r.roofTexture,r.horizontalWall,
				r.verticalWall,r.diagonal);
		WorldEditorStruct out=new WorldEditorStruct();out.type=8;out.sequence=next;out.fieldMask=r.fieldMask;
		for(NativeTerrainSnapshot after:result.after)out.terrainTiles.add(nativeTerrainTile(p,after));
		NativeLayeredTerrainTile center=result.after.get(0).tile;
		out.message=wallDefinitionName(p,center.getVerticalWall()-1)+"\t"
			+wallDefinitionName(p,center.getHorizontalWall()-1)+"\t"
			+wallDefinitionName(p,diagonalRawWall(center.getDiagonalWall())-1);
		ActionSender.sendWorldEditor(p,out);
	}
	private WorldEditorStruct.TerrainTile nativeTerrainTile(Player p,NativeTerrainSnapshot snapshot){
		WorldCoordinate coordinate=snapshot.location.getCoordinate();
		NativeLayeredTerrainTile source=snapshot.tile;
		TileValue runtime=p.getWorld().getTile(snapshot.location);
		WorldEditorStruct.TerrainTile tile=new WorldEditorStruct.TerrainTile();
		tile.x=coordinate.getX();tile.y=coordinate.getY();tile.plane=coordinate.getLevel();
		tile.sectorX=coordinate.getSectorX();tile.sectorY=coordinate.getSectorY();
		tile.localX=coordinate.getLocalX();tile.localY=coordinate.getLocalY();
		tile.elevation=source.getElevation();tile.groundTexture=source.getTexture();
		tile.groundOverlay=inspectionGroundOverlay(runtime,source.getOverlay());
		tile.roofTexture=source.getRoof();tile.horizontalWall=source.getHorizontalWall();
		tile.verticalWall=source.getVerticalWall();tile.diagonal=source.getDiagonalWall();
		tile.traversalMask=runtime==null?0:runtime.traversalMask&0xff;
		tile.projectileAllowed=runtime!=null&&runtime.projectileAllowed;
		return tile;
	}
	private void applyRuntimeTerrain(Player p,WorldEditorTerrainArchive.Snapshot before,WorldEditorTerrainArchive.Snapshot s,int fieldMask){
		int x=s.coordinates.worldX,y=s.coordinates.worldY;TileValue runtime=p.getWorld().getMutableTile(x,y);
		if(runtime==null)throw new IllegalArgumentException("Terrain tile is outside the runtime world.");
		if((fieldMask&1)!=0)runtime.elevation=(byte)s.elevation;
		if((fieldMask&4)!=0){runtime.overlay=(byte)s.groundOverlay;runtime.setTerrainBlocked(overlayBlocks(p,s.groundOverlay));runtime.setTerrainOverlayProjectileBlocked(s.groundOverlay==2||s.groundOverlay==11);}
		if((fieldMask&16)!=0){applyEastWall(p,x,y,before.horizontalWall,false);runtime.horizontalWallVal=(byte)s.horizontalWall;applyEastWall(p,x,y,s.horizontalWall,true);}
		if((fieldMask&32)!=0){applyNorthWall(p,x,y,before.verticalWall,false);runtime.verticalWallVal=(byte)s.verticalWall;applyNorthWall(p,x,y,s.verticalWall,true);}
		if((fieldMask&64)!=0){applyDiagonalWall(p,x,y,before.diagonal,false);runtime.diagWallVal=(short)s.diagonal;applyDiagonalWall(p,x,y,s.diagonal,true);}
	}
	private WorldEditorStruct.TerrainTile terrainTile(Player p,WorldEditorTerrainArchive.Snapshot s){
		TileValue runtime=p.getWorld().getTile(s.coordinates.worldX,s.coordinates.worldY);WorldEditorStruct.TerrainTile tile=new WorldEditorStruct.TerrainTile();
		tile.x=s.coordinates.worldX;tile.y=s.coordinates.worldY;tile.plane=s.coordinates.plane;tile.sectorX=s.coordinates.sectorX;tile.sectorY=s.coordinates.sectorY;
		tile.localX=s.coordinates.localX;tile.localY=s.coordinates.localY;tile.elevation=s.elevation;tile.groundTexture=s.groundTexture;tile.groundOverlay=inspectionGroundOverlay(runtime,s.groundOverlay);
		tile.roofTexture=s.roofTexture;tile.horizontalWall=s.horizontalWall;tile.verticalWall=s.verticalWall;tile.diagonal=s.diagonal;
		tile.traversalMask=runtime==null?0:runtime.traversalMask&0xff;tile.projectileAllowed=runtime!=null&&runtime.projectileAllowed;return tile;
	}
	private void sendTerrain(Player p,WorldEditorTerrainArchive.Snapshot s,int next,int type,boolean copied,int fieldMask) {
		TileValue runtime=p.getWorld().getTile(s.coordinates.worldX,s.coordinates.worldY);
		WorldEditorStruct o=new WorldEditorStruct(); o.type=type; o.sequence=next; o.x=s.coordinates.worldX;o.y=s.coordinates.worldY;o.plane=s.coordinates.plane;
		o.sectorX=s.coordinates.sectorX;o.sectorY=s.coordinates.sectorY;o.localX=s.coordinates.localX;o.localY=s.coordinates.localY;
		o.elevation=s.elevation;o.groundTexture=s.groundTexture;o.groundOverlay=inspectionGroundOverlay(runtime,s.groundOverlay);o.roofTexture=s.roofTexture;
		o.horizontalWall=s.horizontalWall;o.verticalWall=s.verticalWall;o.diagonal=s.diagonal;
		o.traversalMask=runtime==null?0:runtime.traversalMask&0xff;o.projectileAllowed=runtime!=null&&runtime.projectileAllowed;o.copy=copied;o.fieldMask=fieldMask;
		// Names are tab-delimited protocol data; the client owns the concise semantic layout.
		o.message=wallDefinitionName(p,s.verticalWall-1)+"\t"+wallDefinitionName(p,s.horizontalWall-1)
			+"\t"+wallDefinitionName(p,s.diagonalDefinitionId());
		ActionSender.sendWorldEditor(p,o);
	}
	static int inspectionGroundOverlay(TileValue runtime,int archivedGroundOverlay){
		return runtime==null?archivedGroundOverlay:runtime.overlay&0xff;
	}
	private void validateTerrainDefinitions(Player p,WorldEditorRequestStruct r){
		if((r.fieldMask&4)!=0)overlayBlocks(p,r.groundOverlay);
		if((r.fieldMask&8)!=0&&r.roofTexture>MAX_ROOF_TEXTURE)throw new IllegalArgumentException("Roof texture "+r.roofTexture+" is not defined.");
		if((r.fieldMask&16)!=0)validateWall(p,r.horizontalWall,"East wall");
		if((r.fieldMask&32)!=0)validateWall(p,r.verticalWall,"North wall");
		if((r.fieldMask&64)!=0){if(r.diagonal!=0&&(r.diagonal<1||r.diagonal>=24000||r.diagonal==12000))throw new IllegalArgumentException("Diagonal wall encoding is invalid.");validateWall(p,diagonalRawWall(r.diagonal),"Diagonal wall");}
	}
	private void validateWall(Player p,int raw,String label){if(raw==0)return;try{if(p.getWorld().getServer().getEntityHandler().getDoorDef(raw-1)==null)throw new Exception();}catch(Exception e){throw new IllegalArgumentException(label+" "+raw+" is not defined.");}}
	private boolean overlayBlocks(Player p,int overlay){
		int effective=overlay==250?2:overlay;if(effective==0)return false;
		try{return p.getWorld().getServer().getEntityHandler().getTileDef(effective-1).getObjectType()!=0;}
		catch(Exception e){throw new IllegalArgumentException("Floor texture "+overlay+" is not defined.");}
	}
	private boolean wallBlocks(Player p,int raw){if(raw<=0)return false;try{return p.getWorld().getServer().getEntityHandler().getDoorDef(raw-1).getUnknown()==0&&p.getWorld().getServer().getEntityHandler().getDoorDef(raw-1).getDoorType()!=0;}catch(Exception e){return false;}}
	private static int diagonalRawWall(int diagonal){return diagonal>12000?diagonal-12000:diagonal;}
	private void applyNorthWall(Player p,int x,int y,int raw,boolean add){if(!wallBlocks(p,raw))return;terrainCollision(p,x,y,CollisionFlag.WALL_NORTH,add);terrainCollision(p,x,y-1,CollisionFlag.WALL_SOUTH,add);if(WorldLoader.projectileClipAllowed(raw)){terrainProjectile(p,x,y,add);terrainProjectile(p,x,y-1,add);}}
	private void applyEastWall(Player p,int x,int y,int raw,boolean add){if(!wallBlocks(p,raw))return;terrainCollision(p,x,y,CollisionFlag.WALL_EAST,add);terrainCollision(p,x-1,y,CollisionFlag.WALL_WEST,add);if(WorldLoader.projectileClipAllowed(raw)){terrainProjectile(p,x,y,add);terrainProjectile(p,x-1,y,add);}}
	private void applyDiagonalWall(Player p,int x,int y,int diagonal,boolean add){int raw=diagonalRawWall(diagonal);if(!wallBlocks(p,raw))return;terrainCollision(p,x,y,diagonal>12000?CollisionFlag.FULL_BLOCK_A:CollisionFlag.FULL_BLOCK_B,add);if(WorldLoader.projectileClipAllowed(raw))terrainProjectile(p,x,y,add);}
	private void terrainCollision(Player p,int x,int y,int flag,boolean add){TileValue tile=p.getWorld().getMutableTile(x,y);if(tile==null)return;if(add)tile.addTerrainCollision(flag);else tile.removeTerrainCollision(flag);}
	private void terrainProjectile(Player p,int x,int y,boolean add){TileValue tile=p.getWorld().getMutableTile(x,y);if(tile==null)return;if(add)tile.addTerrainWallProjectileBlock();else tile.removeTerrainWallProjectileBlock();}
	private String wallDefinitionName(Player p,int id){if(id<0)return "none";try{return p.getWorld().getServer().getEntityHandler().getDoorDef(id).getName();}catch(Exception e){return "unknown";}}
	private void inspectObject(WorldEditorRequestStruct r, Player p, int next) {
		GameObject object=p.getViewArea().getGameObject(Point.location(r.x,r.y));
		String text;
		if(object==null||object.getID()!=r.entityId) text="Object is no longer present at " + r.x + "," + r.y;
		else text=(object.isScenery()?object.getGameObjectDef().getName():object.getDoorDef().getName())
			+" | id="+object.getID()+" direction="+object.getDirection()+" type="+(object.isScenery()?"scenery":"boundary")
			+" source=runtime-authoritative @ "+logicalLocation(object.getWorldLocation());
		info(p,4,next,text);
	}
	private void inspectNpc(WorldEditorRequestStruct r, Player p, int next) {
		Npc npc=p.getWorld().getNpc(r.entityId);
		int radius=npc==null||npc.getLoc()==null?0:Math.max(Math.max(npc.getLoc().startX-npc.getLoc().minX,npc.getLoc().maxX-npc.getLoc().startX),Math.max(npc.getLoc().startY-npc.getLoc().minY,npc.getLoc().maxY-npc.getLoc().startY));
		String text=npc==null?"NPC is no longer present":npc.getDef().getName()+" | id="+npc.getID()+" radius="+radius+" serverIndex="+npc.getIndex()
			+" source=runtime-authoritative @ "+logicalLocation(npc.getWorldLocation());
		info(p,5,next,text);
	}
	private static void info(Player p,int type,int sequence,String text){WorldEditorStruct o=new WorldEditorStruct();o.type=type;o.sequence=sequence;o.message=text;ActionSender.sendWorldEditor(p,o);}
	private static void error(Player p,int sequence,String text){info(p,6,sequence,text==null?"Unknown editor error":text);}
	private static String logicalLocation(WorldLocation location){WorldCoordinate c=location.getCoordinate();return c.getX()+","+c.getY()+",L"+c.getLevel();}
}
