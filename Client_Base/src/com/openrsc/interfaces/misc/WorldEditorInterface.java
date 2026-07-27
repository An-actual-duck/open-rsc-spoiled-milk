package com.openrsc.interfaces.misc;

import com.openrsc.interfaces.InputListener;
import com.openrsc.interfaces.NCustomComponent;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sprite;
import orsc.Config;
import orsc.WorldBuilderClientProfile;
import orsc.mudclient;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Desktop-only world editor shell and the first command-backed entity tools. */
public final class WorldEditorInterface extends NCustomComponent {
	private static final int TERRAIN_BATCH_LIMIT=64,TERRAIN_DRAG_LIMIT=4096;
	private static final int DOCK_WIDTH=70,DOCK_HEIGHT=276,FLYOUT_WIDTH=180,FLYOUT_GAP=4;
	private static final int DOCK_LEFT=6,DOCK_RIGHT=36,DOCK_TOP=4,DOCK_STEP=30;
	public enum Mode { NAVIGATE, INSPECT, TERRAIN, SCENERY, NPC }
	public enum SceneryTool { PLACE, ROTATE, REMOVE }
	public enum NpcTool { PLACE, REMOVE }
	private static final String[] TABS={"Navigate","Inspect","Terrain","Scenery","NPC"};
	private final mudclient mc;
	private final WorldEditorIconRegistry icons=new WorldEditorIconRegistry();
	private final WorldEditorToolbarState toolbar=new WorldEditorToolbarState();
	private Mode mode=Mode.NAVIGATE;
	private long sessionId;
	private int nextSequence;
	private String inspectionStatus="Nothing inspected yet";
	private String[] inspectionDetails=new String[0];
	private String inspectionKind="";
	private String copiedInspectionKind="";
	private String[] copiedInspectionDetails=new String[0];
	private boolean copyNextInspection=false;
	private int[] copiedTerrainFields;
	private int[] inspectedTerrainFields;
	private int lastClickedX=-1,lastClickedY=-1,lastClickedLevel=0,brushX=-1,brushY=-1,brushLevel=0;
	private String teleportX="",teleportY="",teleportLevel="0";
	private int coordinateFocus=0;
	private boolean replaceFocusedText=false;
	private boolean clickTeleportPreferred=false;
	private SceneryTool sceneryTool=SceneryTool.PLACE;
	private NpcTool npcTool=NpcTool.PLACE;
	private int sceneryId=0,npcId=0,npcRadius=0;
	private String sceneryIdText="0",npcIdText="0",npcRadiusText="0";
	private boolean paintElevation=false,paintFloorColor=true,paintFloorTexture=false;
	private int terrainElevation=0,terrainFloorColor=0,terrainFloorTexture=0;
	private String terrainElevationText="0",terrainFloorColorText="0",terrainFloorTextureText="0";
	private boolean terrainStructureTab=false,paintRoof=false,paintEastWall=false,paintNorthWall=false,paintDiagonalWall=false;
	private int terrainRoof=0,terrainEastWall=0,terrainNorthWall=0,terrainDiagonalWall=0,terrainDiagonalOrientation=0;
	private String terrainRoofText="0",terrainEastWallText="0",terrainNorthWallText="0",terrainDiagonalWallText="0";
	private int terrainBrushSize=1,terrainStrokeMask=0;
	private long terrainStrokeStartedNanos=0L;
	private int terrainStrokeElevation=0,terrainStrokeColor=0,terrainStrokeTexture=0;
	private int terrainStrokeRoof=0,terrainStrokeEastWall=0,terrainStrokeNorthWall=0,terrainStrokeDiagonal=0;
	private int[][] terrainStrokeTiles=null;
	private boolean terrainBuildMode=false,terrainDragActive=false,terrainDragReleasePending=false;
	private int terrainDragHoverX=-1,terrainDragHoverY=-1,terrainDragAccepted=0;
	private long terrainDragAckMillis=0L,terrainDragRebuildMillis=0L;
	private final LinkedHashMap<Long,int[]> terrainDragPending=new LinkedHashMap<Long,int[]>();
	private final HashSet<Long> terrainDragSeen=new HashSet<Long>();
	private int dragX=-1,dragY=-1;
	private int compactMouseX=-1,compactMouseY=-1,terrainActiveField=7;
	private String toolbarTooltip="";
	private boolean keyboardShortcutsEnabled=true;
	private boolean unsavedChanges=false,saveRequested=false,closeArmed=false;
	private int pendingEntityActions=0;
	private long lastAckMillis=0L,lastRebuildMillis=0L;

	public WorldEditorInterface(mudclient client) {
		super(client);mc=client;setLocation(8,8);setSize(DOCK_WIDTH+FLYOUT_GAP+FLYOUT_WIDTH,DOCK_HEIGHT);setVisible(false);setIsOverlay(true);
		setInputListener(new InputListener(){
			@Override public boolean onMouseDown(int mx,int my,int down,int click){return handleMouse(mx,my,down,click);}
			@Override public boolean onCharTyped(char c,int key){return handleKey(c,key);}
			@Override public boolean onMouseMove(int mx,int my){compactMouseX=mx;compactMouseY=my;return false;}
		});
	}

	public void open(long id,int sequence){
		if(Config.isAndroid())return;
		sessionId=id;nextSequence=sequence;mode=Mode.NAVIGATE;toolbar.reset();icons.initialize();
		int x=mc.getEditorPlayerWorldX(),y=mc.getEditorPlayerWorldY(),level=mc.getEditorPlayerWorldLevel();
		brushX=x;brushY=y;brushLevel=level;teleportX=String.valueOf(x);teleportY=String.valueOf(y);teleportLevel=String.valueOf(level);
		clickTeleportPreferred=false;keyboardShortcutsEnabled=true;unsavedChanges=false;saveRequested=false;closeArmed=false;pendingEntityActions=0;
		setTerrainBuildMode(false);mc.setWorldEditorNavigateClickTeleport(false);clearTerrainDrag();updatePresentationBounds();setVisible(true);
	}
	public void closeFromServer(){setTerrainBuildMode(false);mc.setWorldEditorNavigateClickTeleport(false);setVisible(false);sessionId=0;coordinateFocus=0;clearTerrainDrag();toolbar.reset();}
	public boolean isEditorOpen(){return isVisible()&&sessionId!=0;}
	public boolean isKeyboardCaptureActive(){return isEditorOpen()&&(keyboardShortcutsEnabled||coordinateFocus!=0);}
	public boolean isKeyboardShortcutMode(){return isEditorOpen()&&keyboardShortcutsEnabled;}
	public boolean isInspecting(){return isEditorOpen()&&mode==Mode.INSPECT;}
	public boolean isNavigating(){return isEditorOpen()&&mode==Mode.NAVIGATE;}
	public boolean isTerrainPainting(){return isEditorOpen()&&(!isLayeredReview()||isLayeredTerrainDraft())&&mode==Mode.TERRAIN;}
	public boolean isSceneryPlacing(){return isEditorOpen()&&(!isLayeredReview()||isLayeredSceneryDraftLevel())&&mode==Mode.SCENERY&&sceneryTool==SceneryTool.PLACE;}
	public boolean isSceneryRotating(){return isEditorOpen()&&(!isLayeredReview()||isLayeredSceneryDraftLevel())&&mode==Mode.SCENERY&&sceneryTool==SceneryTool.ROTATE;}
	public boolean isSceneryRemoving(){return isEditorOpen()&&(!isLayeredReview()||isLayeredSceneryDraftLevel())&&mode==Mode.SCENERY&&sceneryTool==SceneryTool.REMOVE;}
	public boolean isNpcPlacing(){return isEditorOpen()&&!isLayeredReview()&&mode==Mode.NPC&&npcTool==NpcTool.PLACE;}
	public boolean isNpcRemoving(){return isEditorOpen()&&!isLayeredReview()&&mode==Mode.NPC&&npcTool==NpcTool.REMOVE;}
	public int getSceneryId(){return sceneryId;}
	public int getNpcId(){return npcId;}
	public int getNpcRadius(){return npcRadius;}
	public void selectScenery(int id){setSceneryId(id);}
	public void selectNpc(int id,int radius){setNpcId(id);setNpcRadius(radius);}
	public void setSequence(int sequence){nextSequence=sequence;}
	public void recordWorldClick(int x,int y){
		int level=mc.getEditorPlayerWorldLevel();lastClickedX=x;lastClickedY=y;lastClickedLevel=level;
		if(mode!=Mode.NAVIGATE){brushX=x;brushY=y;brushLevel=level;}
		coordinateFocus=0;toolbar.closeUnpinnedAfterWorldAction();updatePresentationBounds();
	}
	public void markPotentialEntityEdit(){if(!isEditorOpen())return;pendingEntityActions++;unsavedChanges=true;saveRequested=false;closeArmed=false;}
	public void observeGameMessage(String message){
		if(!isEditorOpen()||message==null)return;
		if((message.contains("Saved ")&&message.contains(" world edits."))||message.contains("No pending world edits to save.")){
			unsavedChanges=false;saveRequested=false;pendingEntityActions=0;closeArmed=false;inspectionStatus="World edits saved; no pending changes.";
		}else if(message.contains("Failed to save world edits:")){saveRequested=false;inspectionStatus=message;}
	}
	public void showInfo(int responseType,String text){
		inspectionKind=responseType==5?"NPC":(text!=null&&text.contains("type=boundary")?"Boundary":"Scenery");
		inspectionStatus="Authoritative "+inspectionKind.toLowerCase()+" inspection";inspectionDetails=wrap(text,58);
		if(responseType==4){int id=valueAfter(text,"id=");if(id>=0&&copyNextInspection)setSceneryId(id);}
		if(responseType==5){int id=valueAfter(text,"id="),radius=valueAfter(text,"radius=");if(copyNextInspection){if(id>=0)setNpcId(id);if(radius>=0)setNpcRadius(radius);}}
		if(copyNextInspection)copyInspected();copyNextInspection=false;
	}
	public void showError(String text){
		copyNextInspection=false;terrainStrokeTiles=null;terrainStrokeStartedNanos=0L;clearTerrainDrag();
		inspectionStatus="Server rejected request";inspectionDetails=wrap(text,58);
	}
	public void showTerrain(int sequence,int x,int y,int plane,int sx,int sy,int lx,int ly,int elev,int texture,int overlay,int roof,int hwall,int vwall,int diag,int collision,boolean projectile,boolean copied,String definitions){
		nextSequence=sequence;inspectionKind="Terrain";inspectionStatus="Authoritative terrain inspection";
		inspectedTerrainFields=new int[]{elev,texture,overlay,roof,hwall,vwall,diag};
		String[] names=definitions==null?new String[0]:definitions.split("\\t",-1);
		String northName=names.length>0?names[0]:"unknown",eastName=names.length>1?names[1]:"unknown",diagonalName=names.length>2?names[2]:"unknown";
		int northId=vwall>0?vwall-1:-1,eastId=hwall>0?hwall-1:-1,diagonalId=diagonalDefinitionId(diag);
		int displayY=isLayeredReview()?y:Math.floorMod(y,944);
		int displayLevel=isLayeredReview()?plane:logicalLevelForLegacyPlane(plane);
		java.util.List<String> lines=new java.util.ArrayList<String>();java.util.Collections.addAll(lines,
			"Coordinates: "+x+", "+displayY+", L"+displayLevel,"Level: "+displayLevel+" ("+planeName(displayLevel)+")","Elevation: "+elev,
			"Floor Color: "+texture,"Floor Texture: "+overlay,
			"Walls: North "+wall(northId,northName)+" | East "+wall(eastId,eastName),
			"Diagonal "+wall(diagonalId,diagonalName)+" ("+diagonalRotation(diag)+")",
			"Collision: 0x"+Integer.toHexString(collision)+" | Projectiles: "+(projectile?"allowed":"blocked"),
			"Archive: sector "+sx+","+sy+" | local "+lx+","+ly);
		inspectionDetails=lines.toArray(new String[lines.size()]);
		if(copied||copyNextInspection)copyInspected();copyNextInspection=false;
	}
	public void acceptTerrainPaint(int sequence,int x,int y,int plane,int sx,int sy,int lx,int ly,int elev,int texture,int overlay,int roof,int hwall,int vwall,int diag,int collision,boolean projectile,int fieldMask,String definitions){
		showTerrain(sequence,x,y,plane,sx,sy,lx,ly,elev,texture,overlay,roof,hwall,vwall,diag,collision,projectile,false,definitions);
		mc.applyWorldEditorTerrainPatch(x,y,plane,elev,texture,overlay,roof,hwall,vwall,diag,(fieldMask&4)!=0,true);
		terrainStrokeTiles=null;unsavedChanges=true;saveRequested=false;closeArmed=false;inspectionStatus="Paint accepted: 1 tile (unsaved draft)";
	}
	public void acceptTerrainStroke(int sequence,int fieldMask,int[][] tiles,boolean[] projectiles,String definitions){
		if(tiles==null||tiles.length<1||tiles.length>64||projectiles==null||projectiles.length!=tiles.length){showError("Server returned an invalid terrain stroke.");return;}
		long responseNanos=System.nanoTime();int[] center=tiles[0];
		showTerrain(sequence,center[0],center[1],center[2],center[3],center[4],center[5],center[6],center[7],center[8],center[9],center[10],center[11],center[12],center[13],center[14],projectiles[0],false,definitions);
		for(int i=0;i<tiles.length;i++){int[] tile=tiles[i];
			mc.applyWorldEditorTerrainPatch(tile[0],tile[1],tile[2],tile[7],tile[8],tile[9],tile[10],tile[11],tile[12],tile[13],(fieldMask&4)!=0,i==tiles.length-1);
		}
		long completedNanos=System.nanoTime();long ackMs=terrainStrokeStartedNanos==0L?0L:(responseNanos-terrainStrokeStartedNanos)/1000000L;
		long rebuildMs=(completedNanos-responseNanos)/1000000L;terrainStrokeTiles=null;terrainStrokeStartedNanos=0L;
		lastAckMillis=ackMs;lastRebuildMillis=rebuildMs;
		unsavedChanges=true;saveRequested=false;closeArmed=false;
		boolean dragStroke=terrainDragActive||terrainDragReleasePending||!terrainDragSeen.isEmpty();
		if(!dragStroke){inspectionStatus="Paint accepted: "+tiles.length+" tile"+(tiles.length==1?"":"s")+" | ack "+ackMs+"ms, rebuild "+rebuildMs+"ms";return;}
		terrainDragAccepted+=tiles.length;terrainDragAckMillis+=ackMs;terrainDragRebuildMillis+=rebuildMs;
		if(terrainDragPending.size()>=TERRAIN_BATCH_LIMIT||terrainDragReleasePending)sendNextTerrainDragBatch();
		if(terrainDragReleasePending&&terrainStrokeTiles==null&&terrainDragPending.isEmpty())completeTerrainDrag();
		else inspectionStatus=terrainDragStatus();
	}
	public int[] getCopiedTerrainFields(){return copiedTerrainFields==null?null:copiedTerrainFields.clone();}
	public void inspectTerrain(int worldX,int worldY,boolean copy){recordWorldClick(worldX,worldY);send(2,worldX,worldY,editorLevel(worldY),0,0,copy?1:0);}
	public void paintTerrain(int worldX,int worldY){
		recordWorldClick(worldX,worldY);int mask=terrainPaintMask();
		if(mask==0){showError("Select at least one terrain field to paint.");return;}if(!isTerrainPainting()||terrainStrokeTiles!=null||terrainDragActive||terrainDragReleasePending)return;
		int strokeSize=terrainBrushSize;terrainStrokeTiles=strokeSize==1?new int[][]{{worldX,worldY}}:centeredThreeByThree(worldX,worldY);
		snapshotTerrainPaint(mask);
		terrainStrokeStartedNanos=System.nanoTime();sendTerrainStroke();
	}
	public boolean updateTerrainDrag(boolean controlDown,boolean primaryDown,int worldX,int worldY){
		boolean gesture=controlDown&&primaryDown&&isTerrainPainting();
		if(!terrainDragActive){
			if(!gesture||worldX<0||worldY<0||terrainStrokeTiles!=null||terrainDragReleasePending)return false;
			int mask=terrainPaintMask();if(mask==0){showError("Select at least one terrain field to paint.");return true;}
			clearTerrainDrag();terrainDragActive=true;snapshotTerrainPaint(mask);addTerrainDragCenter(worldX,worldY);inspectionStatus=terrainDragStatus();return true;
		}
		if(!gesture){releaseTerrainDrag();return true;}
		if(worldX>=0&&worldY>=0)addTerrainDragCenter(worldX,worldY);inspectionStatus=terrainDragStatus();return true;
	}
	private int terrainPaintMask(){return (paintElevation?1:0)|(paintFloorColor?2:0)|(paintFloorTexture?4:0)|(paintRoof?8:0)|(paintEastWall?16:0)|(paintNorthWall?32:0)|(paintDiagonalWall?64:0);}
	private void snapshotTerrainPaint(int mask){terrainStrokeMask=mask;terrainStrokeElevation=terrainElevation;terrainStrokeColor=terrainFloorColor;terrainStrokeTexture=terrainFloorTexture;terrainStrokeRoof=terrainRoof;terrainStrokeEastWall=terrainEastWall;terrainStrokeNorthWall=terrainNorthWall;terrainStrokeDiagonal=encodedDiagonalWall();}
	private void addTerrainDragCenter(int worldX,int worldY){
		terrainDragHoverX=worldX;terrainDragHoverY=worldY;recordWorldClick(worldX,worldY);int strokeSize=terrainBrushSize;
		int[][] footprint=strokeSize==1?new int[][]{{worldX,worldY}}:centeredThreeByThree(worldX,worldY);int plane=editorLevel(worldY);
		for(int[] tile:footprint){if(!isLayeredReview()&&Math.floorDiv(tile[1],944)!=plane)continue;long key=terrainTileKey(tile[0],tile[1]);
			if(terrainDragSeen.size()>=TERRAIN_DRAG_LIMIT&&!terrainDragSeen.contains(key))continue;
			if(terrainDragSeen.add(key))terrainDragPending.put(key,new int[]{tile[0],tile[1]});}
		if(terrainDragPending.size()>=TERRAIN_BATCH_LIMIT&&terrainStrokeTiles==null)sendNextTerrainDragBatch();
	}
	private void releaseTerrainDrag(){terrainDragActive=false;terrainDragReleasePending=true;terrainDragHoverX=terrainDragHoverY=-1;if(terrainStrokeTiles==null)sendNextTerrainDragBatch();if(terrainStrokeTiles==null&&terrainDragPending.isEmpty())completeTerrainDrag();}
	private void sendNextTerrainDragBatch(){
		if(terrainStrokeTiles!=null||terrainDragPending.isEmpty())return;int count=Math.min(TERRAIN_BATCH_LIMIT,terrainDragPending.size());terrainStrokeTiles=new int[count][2];
		Iterator<Map.Entry<Long,int[]>> iterator=terrainDragPending.entrySet().iterator();for(int i=0;i<count;i++){terrainStrokeTiles[i]=iterator.next().getValue();iterator.remove();}
		terrainStrokeStartedNanos=System.nanoTime();inspectionStatus=terrainDragStatus();sendTerrainStroke();
	}
	private void completeTerrainDrag(){int accepted=terrainDragAccepted;long ack=terrainDragAckMillis,rebuild=terrainDragRebuildMillis;clearTerrainDrag();inspectionStatus="Brush accepted: "+accepted+" unique tile"+(accepted==1?"":"s")+" | ack "+ack+"ms, rebuild "+rebuild+"ms";}
	private void clearTerrainDrag(){terrainDragActive=false;terrainDragReleasePending=false;terrainDragHoverX=terrainDragHoverY=-1;terrainDragAccepted=0;terrainDragAckMillis=terrainDragRebuildMillis=0L;terrainStrokeTiles=null;terrainStrokeStartedNanos=0L;terrainDragPending.clear();terrainDragSeen.clear();}
	private String terrainDragStatus(){return "Brush "+(terrainDragActive?"dragging":"committing")+": "+terrainDragSeen.size()+" unique | pending "+terrainDragPending.size()+" | accepted "+terrainDragAccepted+(terrainDragHoverX>=0?" | hover "+terrainDragHoverX+","+terrainDragHoverY:"");}
	private static long terrainTileKey(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
	private void sendTerrainStroke(){
		mc.packetHandler.getClientStream().newPacket(152);mc.packetHandler.getClientStream().bufferBits.putByte(6);
		mc.packetHandler.getClientStream().bufferBits.putLong(sessionId);mc.packetHandler.getClientStream().bufferBits.putInt(nextSequence);
		mc.packetHandler.getClientStream().bufferBits.putByte(editorLevel(terrainStrokeTiles[0][1]));mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeMask);
		mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeElevation);mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeColor);
		mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeTexture);mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeRoof);
		mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeEastWall);mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeNorthWall);
		mc.packetHandler.getClientStream().bufferBits.putInt(terrainStrokeDiagonal);mc.packetHandler.getClientStream().bufferBits.putByte(terrainStrokeTiles.length);
		for(int[] tile:terrainStrokeTiles){mc.packetHandler.getClientStream().bufferBits.putShort(tile[0]);mc.packetHandler.getClientStream().bufferBits.putShort(tile[1]);}
		mc.packetHandler.getClientStream().finishPacket();
	}
	private static int[][] centeredThreeByThree(int x,int y){int[][] tiles=new int[9][2];tiles[0][0]=x;tiles[0][1]=y;int at=1;for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0){tiles[at][0]=x+dx;tiles[at++][1]=y+dy;}return tiles;}
	public void inspectObject(int worldX,int worldY,int id,int direction,int type){inspectObject(worldX,worldY,id,direction,type,false);}
	public void inspectObject(int worldX,int worldY,int id,int direction,int type,boolean copy){recordWorldClick(worldX,worldY);copyNextInspection=copy;send(3,worldX,worldY,editorLevel(worldY),id,direction,type);}
	public void inspectNpc(int serverIndex){inspectNpc(serverIndex,false);}
	public void inspectNpc(int serverIndex,boolean copy){copyNextInspection=copy;send(4,0,0,0,serverIndex,0,0);}
	public void copyInspected(){
		if(inspectionKind.isEmpty())return;copiedInspectionKind=inspectionKind;copiedInspectionDetails=inspectionDetails.clone();
		if("Terrain".equals(inspectionKind)&&inspectedTerrainFields!=null){copiedTerrainFields=inspectedTerrainFields.clone();seedTerrain(inspectedTerrainFields);}
		if("Scenery".equals(inspectionKind)){int id=valueAfter(join(inspectionDetails),"id=");if(id>=0)setSceneryId(id);}
		if("NPC".equals(inspectionKind)){String text=join(inspectionDetails);int id=valueAfter(text,"id="),radius=valueAfter(text,"radius=");if(id>=0)setNpcId(id);if(radius>=0)setNpcRadius(radius);}
		inspectionStatus="Copied "+inspectionKind.toLowerCase()+" inspection into its editor selection";
	}

	private void selectMode(Mode selected){
		if(isLayeredReview()&&((selected==Mode.TERRAIN&&!isLayeredTerrainDraft())||(selected==Mode.SCENERY&&!isLayeredSceneryDraftLevel())||selected==Mode.NPC)){
			mode=Mode.NAVIGATE;rejectLayeredReviewMutation(isLayeredTerrainDraft()
				?"Scenery editing is limited to Builder-created levels; NPC and boundary editing remain locked."
				:"Layered package review is read-only; create a draft level before editing terrain.");
			coordinateFocus=0;toolbar.open(WorldEditorToolbarState.Flyout.NAVIGATE);updatePresentationBounds();return;
		}
		if(mode==Mode.TERRAIN&&selected!=Mode.TERRAIN&&terrainDragActive)releaseTerrainDrag();
		boolean same=mode==selected;mode=selected;coordinateFocus=0;replaceFocusedText=false;closeArmed=false;
		WorldEditorToolbarState.Flyout flyout=flyoutFor(selected);if(same)toolbar.selectMode(flyout);else toolbar.open(flyout);
		mc.setWorldEditorNavigateClickTeleport(mode==Mode.NAVIGATE&&clickTeleportPreferred);updatePresentationBounds();
	}
	private void setTerrainBuildMode(boolean enabled){terrainBuildMode=enabled;mc.setWorldEditorBuildMode(enabled);}
	private void setSceneryId(int id){sceneryId=Math.max(0,Math.min(id,EntityHandler.objectCount()-1));sceneryIdText=String.valueOf(sceneryId);}
	private void setNpcId(int id){npcId=Math.max(0,Math.min(id,EntityHandler.npcs.size()-1));npcIdText=String.valueOf(npcId);}
	private void setNpcRadius(int radius){npcRadius=Math.max(0,Math.min(radius,64));npcRadiusText=String.valueOf(npcRadius);}
	private void setTerrainElevation(int value){terrainElevation=rawByte(value);terrainElevationText=String.valueOf(terrainElevation);}
	private void setTerrainFloorColor(int value){terrainFloorColor=rawByte(value);terrainFloorColorText=String.valueOf(terrainFloorColor);}
	private void setTerrainFloorTexture(int value){terrainFloorTexture=rawByte(value);terrainFloorTextureText=String.valueOf(terrainFloorTexture);}
	private void setTerrainRoof(int value){terrainRoof=Math.max(0,Math.min(value,EntityHandler.elevationCount()));terrainRoofText=String.valueOf(terrainRoof);}
	private void setTerrainEastWall(int value){terrainEastWall=Math.max(0,Math.min(value,EntityHandler.doorCount()));terrainEastWallText=String.valueOf(terrainEastWall);}
	private void setTerrainNorthWall(int value){terrainNorthWall=Math.max(0,Math.min(value,EntityHandler.doorCount()));terrainNorthWallText=String.valueOf(terrainNorthWall);}
	private void setTerrainDiagonalWall(int value){terrainDiagonalWall=Math.max(0,Math.min(value,EntityHandler.doorCount()));terrainDiagonalWallText=String.valueOf(terrainDiagonalWall);}
	private void seedTerrain(int[] fields){setTerrainElevation(fields[0]);setTerrainFloorColor(fields[1]);setTerrainFloorTexture(fields[2]);setTerrainRoof(fields[3]);setTerrainEastWall(fields[4]);setTerrainNorthWall(fields[5]);int diagonal=fields[6];terrainDiagonalOrientation=diagonal>12000?1:0;setTerrainDiagonalWall(diagonal>12000?diagonal-12000:diagonal);}
	private int encodedDiagonalWall(){return terrainDiagonalWall==0?0:(terrainDiagonalOrientation==0?terrainDiagonalWall:12000+terrainDiagonalWall);}
	private static int rawByte(int value){return Math.max(0,Math.min(value,255));}
	private void teleportToFields(){
		try{int x=Integer.parseInt(teleportX),y=Integer.parseInt(teleportY),level=Integer.parseInt(teleportLevel);
			if(x<0||x>32767||y<0||y>32767||!validTeleportLevel(level))throw new NumberFormatException();
			lastClickedX=x;lastClickedY=y;lastClickedLevel=level;coordinateFocus=0;mc.worldEditorTeleport(x,y,level);
		}catch(NumberFormatException e){inspectionStatus=isLayeredReview()
			?"X/Y must be 0..32767 and Level must be one of "+WorldBuilderClientProfile.current().layeredLevelsLabel()
			:"Coordinates must be whole numbers from 0 to 32767";inspectionDetails=new String[0];}
	}
	private void send(int type,int x,int y,int plane,int id,int direction,int subtype){
		if(!isEditorOpen())return;mc.packetHandler.getClientStream().newPacket(152);mc.packetHandler.getClientStream().bufferBits.putByte(type);
		mc.packetHandler.getClientStream().bufferBits.putLong(sessionId);mc.packetHandler.getClientStream().bufferBits.putInt(nextSequence);
		if(type==2){mc.packetHandler.getClientStream().bufferBits.putShort(x);mc.packetHandler.getClientStream().bufferBits.putShort(y);mc.packetHandler.getClientStream().bufferBits.putByte(plane);mc.packetHandler.getClientStream().bufferBits.putByte(subtype);}
		else if(type==3){mc.packetHandler.getClientStream().bufferBits.putShort(x);mc.packetHandler.getClientStream().bufferBits.putShort(y);mc.packetHandler.getClientStream().bufferBits.putByte(plane);mc.packetHandler.getClientStream().bufferBits.putShort(id);mc.packetHandler.getClientStream().bufferBits.putByte(direction);mc.packetHandler.getClientStream().bufferBits.putByte(subtype);}
		else if(type==4)mc.packetHandler.getClientStream().bufferBits.putShort(id);mc.packetHandler.getClientStream().finishPacket();
	}

	private boolean handleKey(char c,int key){
		WorldEditorKeyboardShortcuts.Action shortcut=WorldEditorKeyboardShortcuts.resolve(c,key,mc.getDesktopKeyCode(),mc.controlPressed,mc.shiftPressed,mode==Mode.TERRAIN,keyboardShortcutsEnabled);
		if(shortcut==WorldEditorKeyboardShortcuts.Action.TOGGLE_CHAT){
			keyboardShortcutsEnabled=!keyboardShortcutsEnabled;coordinateFocus=0;replaceFocusedText=false;
			inspectionStatus=keyboardShortcutsEnabled?"Editor shortcuts enabled; Ctrl+Enter opens chat input.":"Chat input enabled; Ctrl+Enter restores editor shortcuts.";
			return true;
		}
		if(key==27){
			coordinateFocus=0;replaceFocusedText=false;
			if(toolbar.isExpandedFallback()){toolbar.setExpandedFallback(false);updatePresentationBounds();return true;}
			if(toolbar.closeFlyout()){updatePresentationBounds();return true;}
			requestEditorClose();return true;
		}
		if(coordinateFocus==0){
			if(!keyboardShortcutsEnabled)return false;
			if(shortcut!=WorldEditorKeyboardShortcuts.Action.NONE)applyKeyboardShortcut(shortcut);
			return true;
		}
		String value=focusedText();
		if(key==8){if(replaceFocusedText)value="";else if(value.length()>0)value=value.substring(0,value.length()-1);replaceFocusedText=false;}
		else if(key==9){
			if(coordinateFocus==1)coordinateFocus=2;
			else if(coordinateFocus==2)coordinateFocus=isLayeredReview()?13:1;
			else if(coordinateFocus==13)coordinateFocus=1;
			replaceFocusedText=true;return true;
		}
		else if((key==10||key==13)&&!value.isEmpty()){applyFocusedValue(value);return true;}
		else if(c=='-'&&coordinateFocus==13&&(replaceFocusedText||value.isEmpty())){value="-";replaceFocusedText=false;}
		else if(c>='0'&&c<='9'&&(replaceFocusedText||value.length()<5)){value=replaceFocusedText?String.valueOf(c):value+c;replaceFocusedText=false;}
		else return true;
		setFocusedText(value);return true;
	}
	private void applyKeyboardShortcut(WorldEditorKeyboardShortcuts.Action shortcut){
		switch(shortcut){
			case SAVE:requestWorldEditSave();return;
			case BRUSH:if(mode==Mode.TERRAIN&&terrainActiveField==0)toggleBrushSize();else openTerrainTool(0);return;
			case NAVIGATE:
				if(mode!=Mode.NAVIGATE){selectMode(Mode.NAVIGATE);return;}
				clickTeleportPreferred=!clickTeleportPreferred;mc.setWorldEditorNavigateClickTeleport(clickTeleportPreferred);
				inspectionStatus="Navigate click teleport "+(clickTeleportPreferred?"enabled":"disabled")+".";return;
			case INSPECT:if(mode==Mode.INSPECT)copyInspected();else selectMode(Mode.INSPECT);return;
			case DOCK:
				coordinateFocus=0;replaceFocusedText=false;if(toolbar.isExpandedFallback())toolbar.setExpandedFallback(false);
				toolbar.toggleCollapsed();updatePresentationBounds();return;
			case TOGGLE_ELEVATION:toggleTerrainField(6);return;
			case TOGGLE_FLOOR_COLOR:toggleTerrainField(7);return;
			case TOGGLE_FLOOR_TEXTURE:toggleTerrainField(8);return;
			case TOGGLE_ROOF:toggleTerrainField(9);return;
			case TOGGLE_NORTH_WALL:toggleTerrainField(10);return;
			case TOGGLE_EAST_WALL:toggleTerrainField(11);return;
			case TOGGLE_DIAGONAL_WALL:toggleTerrainField(12);return;
			case EDIT_ELEVATION:openTerrainValueEditor(6);return;
			case EDIT_FLOOR_COLOR:openTerrainValueEditor(7);return;
			case EDIT_FLOOR_TEXTURE:openTerrainValueEditor(8);return;
			case EDIT_ROOF:openTerrainValueEditor(9);return;
			case EDIT_NORTH_WALL:openTerrainValueEditor(10);return;
			case EDIT_EAST_WALL:openTerrainValueEditor(11);return;
			case EDIT_DIAGONAL_WALL:openTerrainValueEditor(12);return;
			default:return;
		}
	}
	private void openTerrainValueEditor(int field){
		if(toolbar.isCollapsed())toolbar.toggleCollapsed();openTerrainTool(field);focusNumber(field);updatePresentationBounds();
	}
	private void applyFocusedValue(String value){
		try{int parsed=Integer.parseInt(value);if(coordinateFocus==1||coordinateFocus==2||coordinateFocus==13){teleportToFields();return;}if(coordinateFocus==3)setSceneryId(parsed);else if(coordinateFocus==4)setNpcId(parsed);else if(coordinateFocus==5)setNpcRadius(parsed);else if(coordinateFocus==6)setTerrainElevation(parsed);else if(coordinateFocus==7)setTerrainFloorColor(parsed);else if(coordinateFocus==8)setTerrainFloorTexture(parsed);else if(coordinateFocus==9)setTerrainRoof(parsed);else if(coordinateFocus==10)setTerrainNorthWall(parsed);else if(coordinateFocus==11)setTerrainEastWall(parsed);else setTerrainDiagonalWall(parsed);}
		catch(NumberFormatException ignored){}coordinateFocus=0;
	}
	private String focusedText(){switch(coordinateFocus){case 1:return teleportX;case 2:return teleportY;case 13:return teleportLevel;case 3:return sceneryIdText;case 4:return npcIdText;case 5:return npcRadiusText;case 6:return terrainElevationText;case 7:return terrainFloorColorText;case 8:return terrainFloorTextureText;case 9:return terrainRoofText;case 10:return terrainNorthWallText;case 11:return terrainEastWallText;default:return terrainDiagonalWallText;}}
	private void setFocusedText(String value){switch(coordinateFocus){case 1:teleportX=value;break;case 2:teleportY=value;break;case 13:teleportLevel=value;break;case 3:sceneryIdText=value;break;case 4:npcIdText=value;break;case 5:npcRadiusText=value;break;case 6:terrainElevationText=value;break;case 7:terrainFloorColorText=value;break;case 8:terrainFloorTextureText=value;break;case 9:terrainRoofText=value;break;case 10:terrainNorthWallText=value;break;case 11:terrainEastWallText=value;break;default:terrainDiagonalWallText=value;}}
	private void focusNumber(int focus){coordinateFocus=focus;replaceFocusedText=true;}
	private void rejectLayeredReviewMutation(String message){inspectionStatus=message;mc.showWorldEditorStatus(message);}
	private void requestWorldEditSave(){if(isLayeredReview()&&!isLayeredTerrainDraft()){rejectLayeredReviewMutation("Layered package review is read-only; no files were changed.");saveRequested=false;return;}if(terrainStrokeTiles!=null||terrainDragActive||terrainDragReleasePending){inspectionStatus="Wait for the active terrain stroke to finish before saving.";return;}mc.sendCommandString("saveworldedits");saveRequested=true;closeArmed=false;inspectionStatus=isLayeredTerrainDraft()?"Layered draft save requested; it will commit to working/ when this Builder closes.":"World edit save requested; see game messages for verification.";}
	private void requestEditorClose(){
		if(unsavedChanges&&!closeArmed){closeArmed=true;inspectionStatus="Unsaved edits remain. Select Close again to exit without saving.";return;}
		setTerrainBuildMode(false);mc.setWorldEditorNavigateClickTeleport(false);send(1,0,0,0,0,0,0);setVisible(false);
	}
	private static WorldEditorToolbarState.Flyout flyoutFor(Mode selected){
		switch(selected){case INSPECT:return WorldEditorToolbarState.Flyout.INSPECT;case TERRAIN:return WorldEditorToolbarState.Flyout.TERRAIN;
			case SCENERY:return WorldEditorToolbarState.Flyout.SCENERY;case NPC:return WorldEditorToolbarState.Flyout.NPC;default:return WorldEditorToolbarState.Flyout.NAVIGATE;}
	}
	private void updatePresentationBounds(){
		if(toolbar.isExpandedFallback()){setSize(390,330);return;}
		setLocation(8,8);
		setSize(toolbar.isCollapsed()?40:(toolbar.isFlyoutOpen()?DOCK_WIDTH+FLYOUT_GAP+FLYOUT_WIDTH:DOCK_WIDTH),toolbar.isCollapsed()?38:DOCK_HEIGHT);
	}
	private boolean handleMouse(int mx,int my,int down,int click){
		if(!isVisible())return false;
		if(toolbar.isExpandedFallback())return handleExpandedMouse(mx,my,down,click);
		int rx=mx-getX(),ry=my-getY();
		if(rx<0||ry<0||ry>=getHeight())return false;
		if(click==0)return true;
		if(click!=1&&click!=2)return false;
		if(rx<DOCK_WIDTH){
			if(dockHit(rx,ry,0,0)){if(click==1){coordinateFocus=0;toolbar.toggleCollapsed();updatePresentationBounds();}return true;}
			if(toolbar.isCollapsed())return true;
			Mode selected=dockModeAt(rx,ry);if(selected!=null){if(click==1)selectMode(selected);return true;}
			int field=terrainFieldAtDock(rx,ry);if(field>=0){if(click==2)toggleTerrainField(field);else openTerrainTool(field);return true;}
			if(dockHit(rx,ry,0,3)){if(click==2)toggleBrushSize();else toggleBrushFlyout();return true;}
			if(dockHit(rx,ry,0,4)){if(click==1)setTerrainBuildMode(!terrainBuildMode);return true;}
			if(dockHit(rx,ry,0,5)){if(click==1)requestWorldEditSave();return true;}
			if(dockHit(rx,ry,0,6)){if(click==1)requestEditorClose();return true;}
			return true;
		}
		if(!toolbar.isFlyoutOpen()||rx<DOCK_WIDTH+FLYOUT_GAP)return false;
		int fx=rx-(DOCK_WIDTH+FLYOUT_GAP);
		if(ry<28){
			if(click==1&&fx>=100&&fx<136)toolbar.togglePinned();
			else if(click==1&&fx>=138&&fx<178){toolbar.setExpandedFallback(true);updatePresentationBounds();}
			return true;
		}
		if(click==2)return true;
		if(mode==Mode.NAVIGATE)handleCompactNavigateMouse(fx,ry);
		else if(mode==Mode.INSPECT)handleCompactInspectMouse(fx,ry);
		else if(mode==Mode.TERRAIN)handleCompactTerrainMouse(fx,ry);
		else if(mode==Mode.SCENERY)handleCompactSceneryMouse(fx,ry);
		else handleCompactNpcMouse(fx,ry);
		return true;
	}
	private static boolean hitRow(int y,int start){return y>=start&&y<start+28;}
	private static boolean dockHit(int x,int y,int column,int row){int startX=column==0?DOCK_LEFT:DOCK_RIGHT;return x>=startX&&x<startX+28&&hitRow(y,DOCK_TOP+row*DOCK_STEP);}
	private Mode dockModeAt(int x,int y){if(dockHit(x,y,0,1))return Mode.NAVIGATE;if(dockHit(x,y,0,2))return Mode.INSPECT;if(dockHit(x,y,1,0))return Mode.SCENERY;if(dockHit(x,y,1,1))return Mode.NPC;return null;}
	private int terrainFieldAtDock(int x,int y){if(dockHit(x,y,1,2))return 6;if(dockHit(x,y,1,3))return 7;if(dockHit(x,y,1,4))return 8;if(dockHit(x,y,1,5))return 9;if(dockHit(x,y,1,6))return 10;if(dockHit(x,y,1,7))return 11;if(dockHit(x,y,1,8))return 12;return -1;}
	private void openTerrainTool(int field){
		if(isLayeredReview()&&!isLayeredTerrainDraft()){selectMode(Mode.TERRAIN);return;}
		mode=Mode.TERRAIN;terrainActiveField=field;if(field>0)terrainStructureTab=field>=9;coordinateFocus=0;replaceFocusedText=false;closeArmed=false;
		mc.setWorldEditorNavigateClickTeleport(false);toolbar.open(WorldEditorToolbarState.Flyout.TERRAIN);updatePresentationBounds();
	}
	private void toggleBrushFlyout(){
		if(mode==Mode.TERRAIN&&terrainActiveField==0&&toolbar.getFlyout()==WorldEditorToolbarState.Flyout.TERRAIN){toolbar.closeFlyout();updatePresentationBounds();return;}
		openTerrainTool(0);
	}
	private void toggleBrushSize(){terrainBrushSize=terrainBrushSize==1?3:1;closeArmed=false;}
	private void toggleTerrainField(int field){
		switch(field){case 6:paintElevation=!paintElevation;break;case 7:paintFloorColor=!paintFloorColor;break;case 8:paintFloorTexture=!paintFloorTexture;break;
			case 9:paintRoof=!paintRoof;break;case 10:paintNorthWall=!paintNorthWall;break;case 11:paintEastWall=!paintEastWall;break;case 12:paintDiagonalWall=!paintDiagonalWall;break;default:return;}
		closeArmed=false;
	}
	private void handleCompactNavigateMouse(int x,int y){
		if(y>=74&&y<98){clickTeleportPreferred=!clickTeleportPreferred;mc.setWorldEditorNavigateClickTeleport(clickTeleportPreferred);return;}
		if(y>=118&&y<142){
			if(!isLayeredReview()){if(x<90)focusNumber(1);else focusNumber(2);}
			else if(x<62)focusNumber(1);else if(x<120)focusNumber(2);else focusNumber(13);
			return;
		}if(y>=148&&y<172){coordinateFocus=0;teleportToFields();}
	}
	private void handleCompactInspectMouse(int x,int y){if(y>=158&&y<182&&!inspectionKind.isEmpty())copyInspected();}
	private void handleCompactTerrainMouse(int x,int y){
		if(terrainActiveField==0){if(y>=58&&y<82){terrainBrushSize=1;return;}if(y>=88&&y<112)terrainBrushSize=3;return;}
		if(y>=58&&y<82){if(x>=8&&x<38)adjustActiveTerrain(-1);else if(x>=42&&x<130)focusNumber(terrainActiveField);else if(x>=134&&x<164)adjustActiveTerrain(1);return;}
		if(y>=112&&y<136){toggleTerrainField(terrainActiveField);return;}
		if(terrainActiveField==12&&y>=144&&y<168){if(x<88)terrainDiagonalOrientation=0;else terrainDiagonalOrientation=1;}
	}
	private void adjustActiveTerrain(int amount){switch(terrainActiveField){case 6:setTerrainElevation(terrainElevation+amount);break;case 7:setTerrainFloorColor(terrainFloorColor+amount);break;
		case 8:setTerrainFloorTexture(terrainFloorTexture+amount);break;case 9:setTerrainRoof(terrainRoof+amount);break;case 10:setTerrainNorthWall(terrainNorthWall+amount);break;
		case 11:setTerrainEastWall(terrainEastWall+amount);break;case 12:setTerrainDiagonalWall(terrainDiagonalWall+amount);break;default:break;}}
	private void handleCompactSceneryMouse(int x,int y){
		if(y>=68&&y<92){if(x>=8&&x<38)setSceneryId(sceneryId-1);else if(x>=42&&x<130)focusNumber(3);else if(x>=134&&x<164)setSceneryId(sceneryId+1);return;}
		if(y>=108&&y<132){if(x<60)sceneryTool=SceneryTool.PLACE;else if(x<116)sceneryTool=SceneryTool.ROTATE;else sceneryTool=SceneryTool.REMOVE;}
	}
	private void handleCompactNpcMouse(int x,int y){
		if(y>=56&&y<80){if(x>=8&&x<38)setNpcId(npcId-1);else if(x>=42&&x<130)focusNumber(4);else if(x>=134&&x<164)setNpcId(npcId+1);return;}
		if(y>=100&&y<124){if(x>=8&&x<38)setNpcRadius(npcRadius-1);else if(x>=42&&x<130)focusNumber(5);else if(x>=134&&x<164)setNpcRadius(npcRadius+1);return;}
		if(y>=138&&y<162)npcTool=x<88?NpcTool.PLACE:NpcTool.REMOVE;
	}
	private boolean handleExpandedMouse(int mx,int my,int down,int click){
		if(!isVisible())return false;int rx=mx-getX(),ry=my-getY();
		if(down==1&&ry>=0&&ry<24){if(dragX<0){dragX=rx;dragY=ry;}else setLocation(Math.max(0,mx-dragX),Math.max(0,my-dragY));}else{dragX=dragY=-1;}
		if(click==1){
			if(rx>=365&&ry<24){requestEditorClose();return true;}
			if(rx>=278&&rx<360&&ry<24){toolbar.setExpandedFallback(false);updatePresentationBounds();return true;}
			if(ry>=30&&ry<50){selectMode(Mode.values()[Math.min(4,Math.max(0,rx/78))]);return true;}
			if(mode==Mode.NAVIGATE){
				if(ry>=150&&ry<172){clickTeleportPreferred=!clickTeleportPreferred;mc.setWorldEditorNavigateClickTeleport(clickTeleportPreferred);return true;}
				if(ry>=197&&ry<221&&rx>=38&&rx<108){focusNumber(1);return true;}
				if(ry>=197&&ry<221&&rx>=128&&rx<198){focusNumber(2);return true;}
				if(isLayeredReview()&&ry>=197&&ry<221&&rx>=220&&rx<278){focusNumber(13);return true;}
				if(ry>=197&&ry<221&&rx>=295&&rx<375){coordinateFocus=0;teleportToFields();return true;}
			}
			if(mode==Mode.INSPECT&&ry>=276&&ry<300&&rx>=10&&rx<175&&!inspectionKind.isEmpty()){copyInspected();return true;}
			if(mode==Mode.TERRAIN){
				if(ry>=56&&ry<78){if(rx>=10&&rx<110)terrainStructureTab=false;else if(rx>=117&&rx<217)terrainStructureTab=true;else if(rx>=235&&rx<380)setTerrainBuildMode(!terrainBuildMode);coordinateFocus=0;return true;}
				if(!terrainStructureTab){
					if(ry>=82&&ry<106){if(rx>=10&&rx<30)paintElevation=!paintElevation;else if(rx>=150&&rx<178)setTerrainElevation(terrainElevation-1);else if(rx>=185&&rx<265)focusNumber(6);else if(rx>=272&&rx<300)setTerrainElevation(terrainElevation+1);return true;}
					if(ry>=122&&ry<146){if(rx>=10&&rx<30)paintFloorColor=!paintFloorColor;else if(rx>=150&&rx<178)setTerrainFloorColor(terrainFloorColor-1);else if(rx>=185&&rx<265)focusNumber(7);else if(rx>=272&&rx<300)setTerrainFloorColor(terrainFloorColor+1);return true;}
					if(ry>=162&&ry<186){if(rx>=10&&rx<30)paintFloorTexture=!paintFloorTexture;else if(rx>=150&&rx<178)setTerrainFloorTexture(terrainFloorTexture-1);else if(rx>=185&&rx<265)focusNumber(8);else if(rx>=272&&rx<300)setTerrainFloorTexture(terrainFloorTexture+1);return true;}
					if(ry>=194&&ry<218){if(rx>=80&&rx<140)terrainBrushSize=1;else if(rx>=147&&rx<207)terrainBrushSize=3;else if(rx>=220&&rx<375)requestWorldEditSave();return true;}
				}else{
					if(ry>=82&&ry<106){if(rx>=10&&rx<30)paintRoof=!paintRoof;else if(rx>=118&&rx<142)setTerrainRoof(terrainRoof-1);else if(rx>=148&&rx<202)focusNumber(9);else if(rx>=208&&rx<232)setTerrainRoof(terrainRoof+1);return true;}
					if(ry>=118&&ry<142){if(rx>=10&&rx<30)paintNorthWall=!paintNorthWall;else if(rx>=118&&rx<142)setTerrainNorthWall(terrainNorthWall-1);else if(rx>=148&&rx<202)focusNumber(10);else if(rx>=208&&rx<232)setTerrainNorthWall(terrainNorthWall+1);return true;}
					if(ry>=154&&ry<178){if(rx>=10&&rx<30)paintEastWall=!paintEastWall;else if(rx>=118&&rx<142)setTerrainEastWall(terrainEastWall-1);else if(rx>=148&&rx<202)focusNumber(11);else if(rx>=208&&rx<232)setTerrainEastWall(terrainEastWall+1);return true;}
					if(ry>=190&&ry<214){if(rx>=10&&rx<30)paintDiagonalWall=!paintDiagonalWall;else if(rx>=118&&rx<142)setTerrainDiagonalWall(terrainDiagonalWall-1);else if(rx>=148&&rx<202)focusNumber(12);else if(rx>=208&&rx<232)setTerrainDiagonalWall(terrainDiagonalWall+1);return true;}
					if(ry>=220&&ry<244){if(rx>=118&&rx<178)terrainDiagonalOrientation=0;else if(rx>=185&&rx<245)terrainDiagonalOrientation=1;return true;}
					if(ry>=248&&ry<272){if(rx>=80&&rx<140)terrainBrushSize=1;else if(rx>=147&&rx<207)terrainBrushSize=3;else if(rx>=220&&rx<375)requestWorldEditSave();return true;}
				}
			}
			if(mode==Mode.SCENERY){
				if(ry>=86&&ry<110&&rx>=10&&rx<38){setSceneryId(sceneryId-1);return true;}
				if(ry>=86&&ry<110&&rx>=45&&rx<125){focusNumber(3);return true;}
				if(ry>=86&&ry<110&&rx>=132&&rx<160){setSceneryId(sceneryId+1);return true;}
				if(ry>=145&&ry<169){if(rx>=10&&rx<105)sceneryTool=SceneryTool.PLACE;else if(rx>=112&&rx<207)sceneryTool=SceneryTool.ROTATE;else if(rx>=214&&rx<309)sceneryTool=SceneryTool.REMOVE;return true;}
				if(ry>=276&&ry<300&&rx>=10&&rx<175){requestWorldEditSave();return true;}
			}
			if(mode==Mode.NPC){
				if(ry>=86&&ry<110&&rx>=10&&rx<38){setNpcId(npcId-1);return true;}
				if(ry>=86&&ry<110&&rx>=45&&rx<125){focusNumber(4);return true;}
				if(ry>=86&&ry<110&&rx>=132&&rx<160){setNpcId(npcId+1);return true;}
				if(ry>=145&&ry<169&&rx>=10&&rx<38){setNpcRadius(npcRadius-1);return true;}
				if(ry>=145&&ry<169&&rx>=45&&rx<125){focusNumber(5);return true;}
				if(ry>=145&&ry<169&&rx>=132&&rx<160){setNpcRadius(npcRadius+1);return true;}
				if(ry>=190&&ry<214){if(rx>=10&&rx<105)npcTool=NpcTool.PLACE;else if(rx>=112&&rx<207)npcTool=NpcTool.REMOVE;return true;}
				if(ry>=276&&ry<300&&rx>=10&&rx<175){requestWorldEditSave();return true;}
			}
			coordinateFocus=0;
		}
		return rx>=0&&ry>=0&&rx<=390&&ry<=330;
	}

	@Override public void render(){
		if(!isVisible()||Config.isAndroid())return;
		if(toolbar.isExpandedFallback()){renderExpanded();return;}
		renderCompact();
	}
	private void renderCompact(){
		int x=getX(),y=getY();
		int dockWidth=toolbar.isCollapsed()?40:DOCK_WIDTH;graphics().drawBoxAlpha(x,y,dockWidth,toolbar.isCollapsed()?38:DOCK_HEIGHT,0x24190c,235);graphics().drawBoxBorder(x,dockWidth,y,toolbar.isCollapsed()?38:DOCK_HEIGHT,0);
		drawIconButton(toolbar.isCollapsed()?WorldEditorIconRegistry.Key.TOOLBAR_EXPAND:WorldEditorIconRegistry.Key.TOOLBAR_COLLAPSE,x+DOCK_LEFT,y+DOCK_TOP,toolbar.isCollapsed(),false,false,false);
		if(toolbar.isCollapsed()){renderCompactTooltip(x,y);return;}
		drawModeIcon(WorldEditorIconRegistry.Key.MODE_SCENERY,x+DOCK_RIGHT,y+dockRowY(0),Mode.SCENERY);
		drawModeIcon(WorldEditorIconRegistry.Key.MODE_NAVIGATE,x+DOCK_LEFT,y+dockRowY(1),Mode.NAVIGATE);
		drawModeIcon(WorldEditorIconRegistry.Key.MODE_NPC,x+DOCK_RIGHT,y+dockRowY(1),Mode.NPC);
		drawModeIcon(WorldEditorIconRegistry.Key.MODE_INSPECT,x+DOCK_LEFT,y+dockRowY(2),Mode.INSPECT);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_ELEVATION,x+DOCK_RIGHT,y+dockRowY(2),6,paintElevation);
		drawIconButton(terrainBrushSize==1?WorldEditorIconRegistry.Key.TOOL_BRUSH_1X1:WorldEditorIconRegistry.Key.TOOL_BRUSH_3X3,x+DOCK_LEFT,y+dockRowY(3),mode==Mode.TERRAIN,mode==Mode.TERRAIN&&terrainActiveField==0&&toolbar.isFlyoutOpen(),false,false);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_FLOOR_COLOR,x+DOCK_RIGHT,y+dockRowY(3),7,paintFloorColor);
		drawIconButton(WorldEditorIconRegistry.Key.PROFILE_BUILD,x+DOCK_LEFT,y+dockRowY(4),terrainBuildMode,false,false,false);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_FLOOR_TEXTURE,x+DOCK_RIGHT,y+dockRowY(4),8,paintFloorTexture);
		drawIconButton(WorldEditorIconRegistry.Key.ACTION_SAVE,x+DOCK_LEFT,y+dockRowY(5),false,false,false,unsavedChanges||saveRequested);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_ROOF,x+DOCK_RIGHT,y+dockRowY(5),9,paintRoof);
		drawIconButton(WorldEditorIconRegistry.Key.ACTION_CLOSE,x+DOCK_LEFT,y+dockRowY(6),false,false,closeArmed,false);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_WALL_NORTH,x+DOCK_RIGHT,y+dockRowY(6),10,paintNorthWall);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_WALL_EAST,x+DOCK_RIGHT,y+dockRowY(7),11,paintEastWall);
		drawTerrainIcon(WorldEditorIconRegistry.Key.FIELD_WALL_DIAGONAL,x+DOCK_RIGHT,y+dockRowY(8),12,paintDiagonalWall);
		if(toolbar.isFlyoutOpen())renderCompactFlyout(x+DOCK_WIDTH+FLYOUT_GAP,y);
		renderCompactTooltip(x,y);
	}
	private static int dockRowY(int row){return DOCK_TOP+row*DOCK_STEP;}
	private void drawModeIcon(WorldEditorIconRegistry.Key key,int x,int y,Mode iconMode){boolean selected=mode==iconMode;drawIconButton(key,x,y,selected,selected&&toolbar.isFlyoutOpen(),false,false);}
	private void drawTerrainIcon(WorldEditorIconRegistry.Key key,int x,int y,int field,boolean enabled){drawIconButton(key,x,y,enabled,mode==Mode.TERRAIN&&terrainActiveField==field&&toolbar.isFlyoutOpen(),terrainFieldInvalid(field),false);}
	private void drawIconButton(WorldEditorIconRegistry.Key key,int x,int y,boolean selected,boolean viewed,boolean warning,boolean badge){
		int background=warning?0x7a281f:viewed?0x526f24:selected?0x365b82:0x333333;
		graphics().drawBoxAlpha(x,y,28,28,background,235);graphics().drawBoxBorder(x,28,y,28,viewed?0xb6e36a:selected?0x66b3ff:warning?0xff981f:0x080808);
		Sprite sprite=icons.get(key);if(sprite!=null)graphics().drawSprite(sprite,x+2,y+2);else{
			String label=key.fallbackLabel();int width=graphics().stringWidth(1,label);graphics().drawString(label,x+14-width/2,y+17,0xffffff,1);
		}
		if(selected){graphics().drawBoxAlpha(x+21,y+3,4,4,viewed?0xc8ff75:0x66b3ff,255);}if(warning){graphics().drawString("!",x+21,y+12,0xffff00,1);}if(badge){graphics().drawString("*",x+20,y+13,0xff981f,2);}
	}
	private void renderCompactFlyout(int x,int y){
		graphics().drawBoxAlpha(x,y,FLYOUT_WIDTH,DOCK_HEIGHT,0x24190c,235);graphics().drawBoxBorder(x,FLYOUT_WIDTH,y,DOCK_HEIGHT,0);
		graphics().drawBoxAlpha(x,y,FLYOUT_WIDTH,28,0x4a3620,255);graphics().drawString(compactFlyoutTitle(),x+8,y+19,0xffff00,2);
		drawHeaderIcon(WorldEditorIconRegistry.Key.ACTION_PIN,x+100,y+2,toolbar.isPinned());button(x+138,y+2,40,"Full");
		if(mode==Mode.NAVIGATE)renderCompactNavigate(x,y);else if(mode==Mode.INSPECT)renderCompactInspect(x,y);else if(mode==Mode.TERRAIN)renderCompactTerrain(x,y);
		else if(mode==Mode.SCENERY)renderCompactScenery(x,y);else renderCompactNpc(x,y);
		renderCompactStatus(x,y);
	}
	private String compactFlyoutTitle(){return mode==Mode.TERRAIN?(terrainActiveField==0?"Brush":activeTerrainLabel()):TABS[mode.ordinal()];}
	private void drawHeaderIcon(WorldEditorIconRegistry.Key key,int x,int y,boolean active){
		graphics().drawBoxAlpha(x,y,36,24,active?0x365b82:0x333333,235);graphics().drawBoxBorder(x,36,y,24,active?0x66b3ff:0);Sprite sprite=icons.get(key);
		if(sprite!=null)graphics().drawSprite(sprite,x+6,y);else graphics().drawString(key.fallbackLabel(),x+5,y+16,0xffffff,1);
	}
	private void renderCompactNavigate(int x,int y){
		int px=mc.getEditorPlayerWorldX(),py=mc.getEditorPlayerWorldY(),level=mc.getEditorPlayerWorldLevel();graphics().drawString("Player "+px+","+py+",L"+level,x+8,y+47,0xffffff,1);
		graphics().drawString("Clicked "+point(lastClickedX,lastClickedY,lastClickedLevel),x+8,y+63,0xbdbdbd,1);
		checkbox(x+8,y+74,clickTeleportPreferred,"Click teleport");graphics().drawString(isLayeredReview()?"Teleport X / Y / Level":"Teleport X / Y",x+8,y+111,0xffff00,1);
		if(isLayeredReview()){textField(x+8,y+118,50,teleportX,coordinateFocus==1);textField(x+62,y+118,54,teleportY,coordinateFocus==2);textField(x+120,y+118,52,teleportLevel,coordinateFocus==13);}
		else{textField(x+8,y+118,78,teleportX,coordinateFocus==1);textField(x+94,y+118,78,teleportY,coordinateFocus==2);}
		button(x+8,y+148,164,"Teleport");
	}
	private void renderCompactInspect(int x,int y){
		graphics().drawString(compactLine(inspectionStatus,28),x+8,y+47,0xffff00,1);int line=y+64;for(String text:inspectionDetails){if(line>y+145)break;graphics().drawString(compactLine(text,28),x+8,line,0xffffff,1);line+=14;}
		button(x+8,y+158,164,inspectionKind.isEmpty()?"Copy (empty)":"Copy inspected");
	}
	private void renderCompactTerrain(int x,int y){
		if(terrainActiveField==0){graphics().drawString("Footprint: "+terrainBrushSize+"x"+terrainBrushSize,x+8,y+49,0xffff00,2);toolButton(x+8,y+58,164,"1x1 single tile",terrainBrushSize==1);toolButton(x+8,y+88,164,"3x3 centered",terrainBrushSize==3);
			graphics().drawString("Right-click Brush toggles size.",x+8,y+130,0xff981f,1);graphics().drawString("Ctrl + left-drag paints continuously.",x+8,y+146,0xff981f,1);return;}
		graphics().drawString(activeTerrainLabel(),x+8,y+49,0xffff00,2);button(x+8,y+58,30,"-");textField(x+42,y+58,88,activeTerrainText(),coordinateFocus==terrainActiveField);button(x+134,y+58,30,"+");
		String name=activeTerrainCompactName();if(!name.isEmpty())graphics().drawString(compactLine(name,28),x+8,y+101,terrainFieldInvalid(terrainActiveField)?0xff981f:0xbdbdbd,1);
		toolButton(x+8,y+112,164,activeTerrainEnabled()?"Paint: ON":"Paint: OFF",activeTerrainEnabled());
		if(terrainActiveField==12){toolButton(x+8,y+144,76,"\\",terrainDiagonalOrientation==0);toolButton(x+92,y+144,76,"/",terrainDiagonalOrientation==1);}
	}
	private void renderCompactScenery(int x,int y){
		graphics().drawString(compactLine(sceneryName(),28),x+8,y+49,0xffff00,1);button(x+8,y+68,30,"-");textField(x+42,y+68,88,sceneryIdText,coordinateFocus==3);button(x+134,y+68,30,"+");
		toolButton(x+8,y+108,50,"Place",sceneryTool==SceneryTool.PLACE);toolButton(x+62,y+108,50,"Rotate",sceneryTool==SceneryTool.ROTATE);toolButton(x+116,y+108,52,"Remove",sceneryTool==SceneryTool.REMOVE);
		graphics().drawString("Inspect/Copy selects an object ID.",x+8,y+150,0xff981f,1);
	}
	private void renderCompactNpc(int x,int y){
		graphics().drawString(compactLine(npcName(),28),x+8,y+47,0xffff00,1);button(x+8,y+56,30,"-");textField(x+42,y+56,88,npcIdText,coordinateFocus==4);button(x+134,y+56,30,"+");
		graphics().drawString("Roam radius",x+8,y+95,0xffff00,1);button(x+8,y+100,30,"-");textField(x+42,y+100,88,npcRadiusText,coordinateFocus==5);button(x+134,y+100,30,"+");
		toolButton(x+8,y+138,76,"Place",npcTool==NpcTool.PLACE);toolButton(x+92,y+138,76,"Remove",npcTool==NpcTool.REMOVE);
	}
	private void renderCompactStatus(int x,int y){
		int px=mc.getEditorPlayerWorldX(),py=mc.getEditorPlayerWorldY(),level=mc.getEditorPlayerWorldLevel(),queued=terrainDragPending.size()+(terrainStrokeTiles==null?0:terrainStrokeTiles.length)+pendingEntityActions;
		graphics().drawLineHoriz(x+8,y+194,FLYOUT_WIDTH-16,0x70512d);graphics().drawString("@yel@"+px+","+py+",L"+level+" @whi@| "+mode,x+8,y+211,0xffffff,1);
		graphics().drawString(compactLine("Queued "+queued+" | ack "+lastAckMillis+" | rebuild "+lastRebuildMillis,28),x+8,y+228,0xbdbdbd,1);
		graphics().drawString(unsavedChanges?"Unsaved"+(saveRequested?" (save requested)":""):"Saved/clean",x+8,y+245,unsavedChanges?0xff981f:0x80c080,1);
		String status=isLayeredReview()?(isLayeredTerrainDraft()?"Draft ":"Review ")+WorldBuilderClientProfile.current().layeredPackageVersion()+" "+WorldBuilderClientProfile.current().layeredManifestShort():(WorldBuilderClientProfile.isEnabled()?"Source "+WorldBuilderClientProfile.current().sourceRevisionShort():inspectionStatus);
		graphics().drawString(compactLine(status,28),x+8,y+264,0xbdbdbd,1);
	}
	private void renderCompactTooltip(int x,int y){
		toolbarTooltip=toolbarTooltipAt(compactMouseX-x,compactMouseY-y);if(toolbarTooltip.isEmpty())return;int width=Math.min(310,Math.max(150,graphics().stringWidth(1,toolbarTooltip)+12));
		graphics().drawBoxAlpha(x+DOCK_WIDTH+4,compactMouseY+6,width,24,0x111111,245);graphics().drawBoxBorder(x+DOCK_WIDTH+4,width,compactMouseY+6,24,0);graphics().drawString(toolbarTooltip,x+DOCK_WIDTH+10,compactMouseY+22,0xffffff,1);
	}
	private String toolbarTooltipAt(int x,int y){
		if(x<0||x>=DOCK_WIDTH||y<0)return "";if(dockHit(x,y,0,0))return "Collapse/expand dock";Mode selected=dockModeAt(x,y);if(selected!=null)return selected.name()+" mode | Left: select or toggle flyout";
		int field=terrainFieldAtDock(x,y);if(field>=0)return activeTerrainLabel(field)+": "+terrainText(field)+" | "+(terrainEnabled(field)?"paint ON":"paint OFF")+" | Left: edit | Right: toggle";
		if(dockHit(x,y,0,3))return "Brush "+terrainBrushSize+"x"+terrainBrushSize+" | Left: edit | Right: toggle size";if(dockHit(x,y,0,4))return "Build view: "+(terrainBuildMode?"ON":"OFF")+" | faceted terrain grid";
		if(dockHit(x,y,0,5))return "Save | "+(unsavedChanges?"unsaved changes":"clean")+(saveRequested?" | requested":"");if(dockHit(x,y,0,6))return closeArmed?"Close without saving: confirm":"Close editor";return "";
	}
	private boolean activeTerrainEnabled(){return terrainEnabled(terrainActiveField);}
	private boolean terrainEnabled(int field){switch(field){case 6:return paintElevation;case 7:return paintFloorColor;case 8:return paintFloorTexture;case 9:return paintRoof;case 10:return paintNorthWall;case 11:return paintEastWall;case 12:return paintDiagonalWall;default:return false;}}
	private String activeTerrainLabel(){return activeTerrainLabel(terrainActiveField);}
	private String activeTerrainLabel(int field){switch(field){case 6:return "Elevation";case 7:return "Floor Color";case 8:return "Floor Texture";case 9:return "Roof";case 10:return "North Wall";case 11:return "East Wall";case 12:return "Diagonal Wall";default:return "Brush";}}
	private String activeTerrainText(){return terrainText(terrainActiveField);}
	private String terrainText(int field){switch(field){case 6:return terrainElevationText;case 7:return terrainFloorColorText;case 8:return terrainFloorTextureText;case 9:return terrainRoofText;case 10:return terrainNorthWallText;case 11:return terrainEastWallText;case 12:return terrainDiagonalWallText;default:return terrainBrushSize+"x"+terrainBrushSize;}}
	private String activeTerrainCompactName(){switch(terrainActiveField){case 8:return floorTextureDescription();case 9:return roofDescription();case 10:return wallDescription(terrainNorthWall);case 11:return wallDescription(terrainEastWall);case 12:return wallDescription(terrainDiagonalWall);default:return "";}}
	private boolean terrainFieldInvalid(int field){try{if(field==8&&terrainFloorTexture!=0&&terrainFloorTexture!=250)return EntityHandler.getTileDef(terrainFloorTexture-1)==null;if(field==9)return terrainRoof<0||terrainRoof>EntityHandler.elevationCount();
		if(field==10)return terrainNorthWall<0||terrainNorthWall>EntityHandler.doorCount();if(field==11)return terrainEastWall<0||terrainEastWall>EntityHandler.doorCount();if(field==12)return terrainDiagonalWall<0||terrainDiagonalWall>EntityHandler.doorCount();return false;}catch(Exception e){return true;}}
	private void renderExpanded(){
		if(!isVisible()||Config.isAndroid())return;int x=getX(),y=getY();
		graphics().drawBoxAlpha(x,y,390,330,0x24190c,235);graphics().drawBoxBorder(x,390,y,330,0);graphics().drawBoxAlpha(x,y,390,24,0x4a3620,255);
		String title=isLayeredReview()?(isLayeredTerrainDraft()?"World Builder Draft: ":"World Builder Review: ")+WorldBuilderClientProfile.current().projectName():(WorldBuilderClientProfile.isEnabled()?"World Builder: "+WorldBuilderClientProfile.current().projectName():"World Editor");
		graphics().drawString(compactLine(title,38),x+8,y+17,0xffff00,2);button(x+278,y,82,"Compact");graphics().drawString("X",x+372,y+17,0xffffff,2);
		for(int i=0;i<TABS.length;i++){graphics().drawBoxAlpha(x+i*78,y+30,77,20,mode.ordinal()==i?0x6b8e23:0x333333,220);graphics().drawString(TABS[i],x+i*78+6,y+44,0xffffff,2);}
		if(mode==Mode.NAVIGATE)renderNavigate(x,y);else if(mode==Mode.INSPECT)renderInspect(x,y);else if(mode==Mode.TERRAIN)renderTerrain(x,y);else if(mode==Mode.SCENERY)renderScenery(x,y);else renderNpc(x,y);
		String revision=isLayeredReview()?" | package "+WorldBuilderClientProfile.current().layeredPackageVersion()+" "+WorldBuilderClientProfile.current().layeredManifestShort():(WorldBuilderClientProfile.isEnabled()?" | source "+WorldBuilderClientProfile.current().sourceRevisionShort():"");
		graphics().drawString("Mode: "+mode+" | session sequence "+nextSequence+revision,x+10,y+321,0xbdbdbd,1);
	}
	private void renderNavigate(int x,int y){
		int px=mc.getEditorPlayerWorldX(),py=mc.getEditorPlayerWorldY(),level=mc.getEditorPlayerWorldLevel();
		graphics().drawString("Navigation",x+10,y+70,0xffff00,2);
		graphics().drawString("Player: "+px+", "+py+", level "+level,x+10,y+91,0xffffff,2);
		graphics().drawString("Last clicked tile: "+point(lastClickedX,lastClickedY,lastClickedLevel),x+10,y+111,0xffffff,2);
		graphics().drawString(isLayeredReview()?"Package: "+WorldBuilderClientProfile.current().layeredPackageId()+" "+WorldBuilderClientProfile.current().layeredPackageVersion():"Brush: inactive at "+point(brushX,brushY,brushLevel),x+10,y+131,0xbdbdbd,2);
		checkbox(x+10,y+150,clickTeleportPreferred,"Click teleport (Navigate only)");
		graphics().drawString("Teleport to coordinates",x+10,y+188,0xffff00,2);
		graphics().drawString("X",x+20,y+214,0xffffff,2);textField(x+38,y+197,70,teleportX,coordinateFocus==1);
		graphics().drawString("Y",x+112,y+214,0xffffff,2);textField(x+128,y+197,70,teleportY,coordinateFocus==2);
		if(isLayeredReview()){graphics().drawString("L",x+204,y+214,0xffffff,2);textField(x+220,y+197,58,teleportLevel,coordinateFocus==13);}
		button(x+295,y+197,80,"Teleport");
		graphics().drawString(isLayeredTerrainDraft()?"Terrain plus new-level scenery editing; source placements/export are locked.":isLayeredReview()?"Read-only package review; navigate and inspect are enabled.":"Navigate uses movement options; brush/edit actions are off.",x+10,y+244,0xff981f,1);
	}
	private void renderInspect(int x,int y){
		graphics().drawString(inspectionStatus,x+10,y+70,0xffff00,2);int line=y+89;
		for(String s:inspectionDetails){if(line>y+242)break;graphics().drawString(s,x+10,line,0xffffff,2);line+=17;}
		graphics().drawString("Right-click targets to inspect or copy authoritative data.",x+10,y+263,0xff981f,1);
		button(x+10,y+276,165,inspectionKind.isEmpty()?"Copy inspected (empty)":"Copy inspected");
	}
	private void renderTerrain(int x,int y){
		toolButton(x+10,y+56,100,"Surface",!terrainStructureTab);toolButton(x+117,y+56,100,"Structure",terrainStructureTab);checkbox(x+240,y+59,terrainBuildMode,"Build mode");
		if(terrainStructureTab){renderTerrainStructure(x,y);return;}
		terrainField(x,y+82,"Elevation",paintElevation,terrainElevationText,coordinateFocus==6);
		terrainField(x,y+122,"Floor Color",paintFloorColor,terrainFloorColorText,coordinateFocus==7);
		terrainField(x,y+162,"Floor Texture",paintFloorTexture,terrainFloorTextureText,coordinateFocus==8);
		graphics().drawString("Brush",x+10,y+211,0xffffff,2);toolButton(x+80,y+194,60,"1x1",terrainBrushSize==1);toolButton(x+147,y+194,60,"3x3",terrainBrushSize==3);button(x+220,y+194,155,"Save edits");
		graphics().drawString(floorTextureDescription(),x+10,y+232,0xbdbdbd,1);
		graphics().drawString("Click once, or Ctrl + left-drag across distinct terrain tiles.",x+10,y+252,0xffffff,2);
		graphics().drawString(terrainDragActive||terrainDragReleasePending?terrainDragStatus():"Copy inspected fills values; checked fields are painted.",x+10,y+272,0xff981f,1);
		graphics().drawString(isLayeredTerrainDraft()?"Save journals the layered draft; close/reopen commits it.":"Save commits server/client archives; undo remains disabled.",x+10,y+290,0xff981f,1);
		graphics().drawString(inspectionStatus,x+10,y+307,0xbdbdbd,1);
	}
	private void renderTerrainStructure(int x,int y){
		structureField(x,y+82,"Roof",paintRoof,terrainRoofText,coordinateFocus==9,roofDescription());
		structureField(x,y+118,"North Wall",paintNorthWall,terrainNorthWallText,coordinateFocus==10,wallDescription(terrainNorthWall));
		structureField(x,y+154,"East Wall",paintEastWall,terrainEastWallText,coordinateFocus==11,wallDescription(terrainEastWall));
		structureField(x,y+190,"Diagonal",paintDiagonalWall,terrainDiagonalWallText,coordinateFocus==12,wallDescription(terrainDiagonalWall));
		graphics().drawString("Diagonal",x+10,y+237,0xffffff,2);toolButton(x+118,y+220,60,"\\",terrainDiagonalOrientation==0);toolButton(x+185,y+220,60,"/",terrainDiagonalOrientation==1);
		graphics().drawString("Brush",x+10,y+265,0xffffff,2);toolButton(x+80,y+248,60,"1x1",terrainBrushSize==1);toolButton(x+147,y+248,60,"3x3",terrainBrushSize==3);button(x+220,y+248,155,"Save edits");
		graphics().drawString(terrainDragActive||terrainDragReleasePending?terrainDragStatus():"Structures use 1x1/3x3; Ctrl-drag batches distinct tiles.",x+10,y+286,0xff981f,1);
		graphics().drawString(inspectionStatus,x+10,y+307,0xbdbdbd,1);
	}
	private void terrainField(int x,int y,String label,boolean enabled,String value,boolean focused){checkbox(x+10,y,enabled,label);button(x+150,y,28,"-");textField(x+185,y,80,value,focused);button(x+272,y,28,"+");}
	private void structureField(int x,int y,String label,boolean enabled,String value,boolean focused,String description){checkbox(x+10,y,enabled,label);button(x+118,y,24,"-");textField(x+148,y,54,value,focused);button(x+208,y,24,"+");graphics().drawString(description,x+240,y+17,0xbdbdbd,1);}
	private String roofDescription(){return terrainRoof==0?"none":"#"+(terrainRoof-1)+" profile";}
	private String wallDescription(int raw){try{return raw==0?"none":"#"+(raw-1)+" "+EntityHandler.getDoorDef(raw-1).getName();}catch(Exception e){return "undefined";}}
	private String floorTextureDescription(){
		if(terrainFloorTexture==0)return "Floor Texture 0: none (base Floor Color is visible).";
		if(terrainFloorTexture==250)return "Floor Texture 250: bridge transition sentinel.";
		try{return "Floor Texture "+terrainFloorTexture+": "+(EntityHandler.getTileDef(terrainFloorTexture-1).getObjectType()!=0?"blocking":"walkable")+" definition.";}catch(Exception e){return "Floor Texture "+terrainFloorTexture+": undefined and will be rejected.";}
	}
	private void renderScenery(int x,int y){
		graphics().drawString("Scenery editing",x+10,y+70,0xffff00,2);
		button(x+10,y+86,28,"-");textField(x+45,y+86,80,sceneryIdText,coordinateFocus==3);button(x+132,y+86,28,"+");
		graphics().drawString(sceneryName(),x+175,y+103,0xffffff,2);
		toolButton(x+10,y+145,95,"Place",sceneryTool==SceneryTool.PLACE);toolButton(x+112,y+145,95,"Rotate",sceneryTool==SceneryTool.ROTATE);toolButton(x+214,y+145,95,"Remove",sceneryTool==SceneryTool.REMOVE);
		graphics().drawString(sceneryTool==SceneryTool.PLACE?"Click terrain to place one object.":"Click an existing scenery object to "+sceneryTool.name().toLowerCase()+" it.",x+10,y+190,0xffffff,2);
		graphics().drawString("Copying scenery selects its ID. Boundaries remain inspection-only.",x+10,y+218,0xff981f,1);
		button(x+10,y+276,165,"Save queued edits");
	}
	private void renderNpc(int x,int y){
		graphics().drawString("NPC editing",x+10,y+70,0xffff00,2);
		button(x+10,y+86,28,"-");textField(x+45,y+86,80,npcIdText,coordinateFocus==4);button(x+132,y+86,28,"+");
		graphics().drawString(npcName(),x+175,y+103,0xffffff,2);
		graphics().drawString("Roam radius",x+10,y+137,0xffffff,2);button(x+10,y+145,28,"-");textField(x+45,y+145,80,npcRadiusText,coordinateFocus==5);button(x+132,y+145,28,"+");
		toolButton(x+10,y+190,95,"Place",npcTool==NpcTool.PLACE);toolButton(x+112,y+190,95,"Remove",npcTool==NpcTool.REMOVE);
		graphics().drawString(npcTool==NpcTool.PLACE?"Click terrain to place one NPC.":"Click an existing NPC to remove it.",x+10,y+235,0xffffff,2);
		button(x+10,y+276,165,"Save queued edits");
	}
	private void toolButton(int x,int y,int w,String text,boolean active){graphics().drawBoxAlpha(x,y,w,24,active?0x365b82:0x333333,220);graphics().drawBoxBorder(x,w,y,24,active?0x66b3ff:0);graphics().drawString(text,x+6,y+17,0xffffff,2);}
	private String sceneryName(){try{return EntityHandler.getObjectDef(sceneryId).getName();}catch(Exception e){return "Unknown scenery";}}
	private String npcName(){try{return EntityHandler.getNpcDef(npcId).getName();}catch(Exception e){return "Unknown NPC";}}
	private void textField(int x,int y,int w,String text,boolean focused){graphics().drawBoxAlpha(x,y,w,24,focused?0x6580b7:0x222222,240);graphics().drawBoxBorder(x,w,y,24,0);graphics().drawString(text+(focused?"*":""),x+6,y+17,0xffffff,2);}
	private void checkbox(int x,int y,boolean checked,String text){graphics().drawBoxAlpha(x,y,18,18,checked?0x365b82:0x333333,255);graphics().drawBoxBorder(x,18,y,18,checked?0x66b3ff:0);if(checked)graphics().drawString("X",x+5,y+14,0xffffff,2);graphics().drawString(text,x+26,y+14,0xffffff,2);}
	private void button(int x,int y,int w,String text){graphics().drawBoxAlpha(x,y,w,24,0x333333,220);graphics().drawBoxBorder(x,w,y,24,0);graphics().drawString(text,x+6,y+17,0xffffff,2);}
	private boolean isLayeredReview(){return WorldBuilderClientProfile.current().isLayeredReview();}
	private boolean isLayeredTerrainDraft(){return WorldBuilderClientProfile.current().isLayeredTerrainDraft();}
	private boolean isLayeredSceneryDraftLevel(){if(!isLayeredTerrainDraft())return false;int level=mc.getEditorPlayerWorldLevel();return level!=-1&&level!=0&&level!=1&&level!=2;}
	private int editorLevel(int worldY){return isLayeredReview()?mc.getEditorPlayerWorldLevel():Math.floorDiv(worldY,944);}
	private boolean validTeleportLevel(int level){return !isLayeredReview()||WorldBuilderClientProfile.current().declaresLayer(level);}
	private static int logicalLevelForLegacyPlane(int plane){return plane==3?-1:plane;}
	private static String point(int x,int y){return x<0||y<0?"not set":x+","+y;}
	private static String point(int x,int y,int level){return x<0||y<0?"not set":x+","+y+",L"+level;}
	private static int diagonalDefinitionId(int v){if(v>0&&v<12000)return v-1;if(v>12000&&v<24000)return v-12001;return -1;}
	private static String diagonalRotation(int v){return v>12000&&v<24000?"rotated":v>0&&v<12000?"not rotated":"none";}
	private static String wall(int id,String name){return id<0?"none":"#"+id+" ("+name+")";}
	private static String planeName(int plane){switch(plane){case 0:return "Surface";case 1:return "First floor";case 2:return "Second floor";case -1:case 3:return "Underground";default:return plane<0?"Depth "+plane:"Upper level "+plane;}}
	private static int valueAfter(String text,String marker){if(text==null)return -1;int at=text.indexOf(marker);if(at<0)return -1;at+=marker.length();int end=at;while(end<text.length()&&Character.isDigit(text.charAt(end)))end++;try{return Integer.parseInt(text.substring(at,end));}catch(Exception e){return -1;}}
	private static String join(String[] lines){StringBuilder b=new StringBuilder();for(String line:lines)b.append(line).append(' ');return b.toString();}
	private static String compactLine(String text,int max){if(text==null)return "";return text.length()<=max?text:text.substring(0,Math.max(0,max-3))+"...";}
	private static String[] wrap(String s,int width){if(s==null||s.isEmpty())return new String[0];java.util.List<String> lines=new java.util.ArrayList<String>();while(s.length()>width){int p=s.lastIndexOf(' ',width);if(p<1)p=width;lines.add(s.substring(0,p));s=s.substring(p).trim();}lines.add(s);return lines.toArray(new String[lines.size()]);}
}
