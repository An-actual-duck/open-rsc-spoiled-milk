package com.openrsc.server.login;

import com.openrsc.server.Server;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.*;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.parsers.impl.PayloadCustomParser;
import com.openrsc.server.net.rsc.struct.AbstractStruct;
import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainReadyStruct;
import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainStageReadyStruct;
import com.openrsc.server.net.rsc.struct.outgoing.WorldInfoStruct;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import io.netty.channel.Channel;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A bounded, authenticated account snapshot which has not entered the world. */
public final class StagedLoginSession {
    private static final Logger LOGGER=LogManager.getLogger();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String OWNER="staged_login_owner";
    private final LoginRequest request;private final Server server;private final Channel channel;
    private final ConnectionAttachment attachment;private final long deadline;
    private final Object stateLock=new Object();
    private volatile ScheduledFuture<?> tickTask,deadlineTask;
    private volatile Player candidate;private volatile byte[] token,digest;
    private volatile boolean closed,committing,complete;
    private int success;
    private StagedLoginSession(LoginRequest request,Player candidate,int success){
        this.request=request;this.server=request.getServer();this.channel=request.getChannel();this.candidate=candidate;this.success=success;
        attachment=channel.attr(RSCConnectionHandler.attachment).get();deadline=System.currentTimeMillis()+request.getAdmission().remainingMillis(System.currentTimeMillis());
    }
    public static StagedLoginSession forPlayer(Player player){return player.getAttribute(OWNER,null);}
    public static boolean supports(Server server,ConnectionAttachment attachment){
        return attachment!=null&&Boolean.TRUE.equals(attachment.isWebSocket.get())&&!server.getConfig().WORLD_BUILDER_MODE
            &&server.getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY&&server.getConfig().WANT_LAYERED_NATIVE_TERRAIN_PACKAGE
            &&server.getConfig().WANT_SYNC_SCENE_BASELINE&&Boolean.parseBoolean(System.getProperty("spoiledmilk.browserStagedLogin","true"));
    }
    public static boolean handlePreWorld(Server server,Channel channel,Packet packet){
        ConnectionAttachment attachment=channel.attr(RSCConnectionHandler.attachment).get();
        StagedLoginSession session=attachment.stagedLogin.get();
        if(session!=null){session.receive(packet);return true;}
        if(packet.getID()!=StagedLoginProtocol.OPCODE)return false;
        try{
            if(!supports(server,attachment)||!server.getPacketFilter().shouldAllowPacket(channel,false)
                ||StagedLoginProtocol.header(packet)!=StagedLoginProtocol.SUPPORT||packet.getReadableBytes()!=0)throw new IllegalArgumentException("Unsupported login negotiation");
            attachment.stagedLoginAdvertised.set(true);channel.writeAndFlush(StagedLoginProtocol.packet(StagedLoginProtocol.SUPPORT).toPacket());
        }catch(RuntimeException invalid){channel.close();}
        return true;
    }
    public static void begin(LoginRequest request,Player candidate,int success){
        StagedLoginSession session=new StagedLoginSession(request,candidate,success);
        if(!session.attachment.stagedLogin.compareAndSet(null,session)){request.releaseAdmission();request.getChannel().close();return;}
        request.getChannel().closeFuture().addListener(ignored->session.close(false,0));
        candidate.setAttribute(OWNER,session);
        try{
            session.channel.writeAndFlush(new PacketBuilder().writeByte(StagedLoginProtocol.PENDING_RESPONSE).toPacket());
            session.refresh(candidate);session.tick();
            session.deadlineTask=session.channel.eventLoop().schedule(()->session.close(true,1),Math.max(1L,session.deadline-System.currentTimeMillis()),TimeUnit.MILLISECONDS);
            if(session.closed)session.cancelTasks();
        }catch(RuntimeException|Error failure){LOGGER.error("Cannot prepare detached browser login",failure);session.close(true,3);}
    }
    private boolean live(){return !closed&&channel.isActive()&&System.currentTimeMillis()<deadline&&request.getAdmission().isLive(System.currentTimeMillis());}
    private static void string(PacketBuilder builder,String value){
        byte[] raw=value.getBytes(StandardCharsets.UTF_8);if(raw.length>128||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IllegalArgumentException("Preparation string bounds");builder.write(raw).writeByte(10);
    }
    private void refresh(Player next){
        if(!live())throw new IllegalStateException("Prepared login expired");
        synchronized(stateLock){
            if(closed){discardDetached(next);return;}
            Player old=candidate;if(old!=null)old.removeAttribute(OWNER);
            candidate=next;next.setAttribute(OWNER,this);complete=false;committing=false;
            if(old!=next)discardDetached(old);
        }
        server.getGameUpdater().resetStagedLoginScene(next);
        digest=server.getGameUpdater().stagedLoginSceneIdentity(next);token=new byte[16];RANDOM.nextBytes(token);
        WorldLocation location=next.getWorldLocation();WorldInfoStruct info=ActionSender.buildWorldInfo(next);
        String projection=server.getWorld().getRegionManager().runtimeProjectionId(location);
        String manifest=server.getWorld().getRegionManager().findNativeLayeredWorldPackage(location).orElseThrow(()->new IllegalStateException("Missing prepared source")).getManifestSha256();
        PacketBuilder builder=StagedLoginProtocol.packet(StagedLoginProtocol.PREPARE).write(token).write(digest)
            .writeInt((int)Math.min(Integer.MAX_VALUE,deadline-System.currentTimeMillis()))
            .writeInt(location.getCoordinate().getX()).writeInt(location.getCoordinate().getY()).writeInt(location.getCoordinate().getLevel())
            .writeInt(next.getX()).writeInt(next.getY()).writeInt(info.planeFloor).writeInt(info.planeWidth).writeInt(info.planeHeight).writeInt(info.distanceBetweenFloors);
        string(builder,location.getWorldSpace().getValue());string(builder,projection);string(builder,manifest);channel.writeAndFlush(builder.toPacket());
        ActionSender.sendGameSettings(next);
    }
    private void enqueue(Runnable action){
        if(closed)return;server.getGameEventHandler().add(new ImmediateEvent(server.getWorld(),"Browser Login Preparation"){
            public void action(){if(closed)return;try{action.run();}catch(RuntimeException|Error failure){LOGGER.error("Detached browser login failed",failure);close(true,3);}}
        });
    }
    private void tick(){
        if(!live()){close(true,1);return;}
        if(!committing&&channel.isWritable()){
            if(!StagedLoginProtocol.matches(digest,server.getGameUpdater().stagedLoginSceneIdentity(candidate)))refresh(candidate);
            if(server.getGameUpdater().sendStagedLoginScene(candidate)&&!complete){
                complete=true;channel.writeAndFlush(StagedLoginProtocol.packet(StagedLoginProtocol.COMPLETE).write(token).write(digest)
                    .writeInt(server.getGameUpdater().stagedLoginContextSequence(candidate)).toPacket());
            }
        }
        tickTask=channel.eventLoop().schedule(()->enqueue(this::tick),1000,TimeUnit.MILLISECONDS);
        if(closed)cancelTasks();
    }
    public boolean writePreparation(Player owner,Packet packet){
        if(owner!=candidate||owner.isLoggedIn()||!live())return false;
        if(packet.getID()!=157&&packet.getID()!=143&&packet.getID()!=154&&packet.getID()!=240)throw new IllegalStateException("Non-static packet in prepared login");
        if(!channel.isWritable())return false;channel.writeAndFlush(packet);return true;
    }
    private void receive(Packet packet){
        try{
            if(!live()||!server.getPacketFilter().shouldAllowPacket(channel,false))throw new IllegalArgumentException("Invalid pending connection");
            StagedLoginProtocol.Control control=StagedLoginProtocol.control(packet);
            int kind=control.kind;byte[] echoedToken=control.token,echoedDigest=control.digest;
            if(!StagedLoginProtocol.matches(token,echoedToken)||!StagedLoginProtocol.matches(digest,echoedDigest))return;
            if(kind==StagedLoginProtocol.ACK){
                int opcode=control.receiptOpcode;byte[] payload=control.receipt;Player owner=candidate;
                enqueue(()->{
                    if(owner!=candidate||!StagedLoginProtocol.matches(token,echoedToken)||!StagedLoginProtocol.matches(digest,echoedDigest))return;
                    Packet inner=new Packet(opcode,Unpooled.wrappedBuffer(payload));
                    AbstractStruct parsed;
                    try{parsed=new PayloadCustomParser().parse(inner,owner);}finally{inner.getBuffer().release();}
                    if(opcode==154&&parsed instanceof LayeredTerrainReadyStruct)server.getGameUpdater().acceptLayeredTerrainReady(owner,(LayeredTerrainReadyStruct)parsed);
                    else if(opcode==155&&parsed instanceof LayeredTerrainStageReadyStruct)server.getGameUpdater().acceptLayeredTerrainStageReady(owner,(LayeredTerrainStageReadyStruct)parsed);
                    else throw new IllegalArgumentException("Malformed preparation receipt");
                    tickOnce();
                });return;
            }
            if(kind==2){close(true,0);return;}
            if(kind==3)return; // Heartbeats never extend the hard account lease.
            enqueue(()->{
                if(!live()||committing||!complete||!StagedLoginProtocol.matches(token,echoedToken)||!StagedLoginProtocol.matches(digest,echoedDigest))return;
                committing=true;server.getLoginExecutor().add(new LoginExecutorProcess(){
                    protected void processInternal(){
                        Player fresh=request.reloadStagedPlayer();
                        if(closed){discardDetached(fresh);return;}
                        server.getGameEventHandler().add(new ImmediateEvent(server.getWorld(),"Complete Browser Login Preparation"){
                            public void action(){
                                if(closed){discardDetached(fresh);return;}
                                try{finish(fresh,echoedToken,echoedDigest);}catch(RuntimeException|Error failure){discardDetached(fresh);LOGGER.error("Cannot complete detached browser login",failure);close(true,3);}
                            }
                        });
                    }
                });
            });
        }catch(RuntimeException|Error invalid){close(true,2);}
    }
    private void tickOnce(){
        if(!live()||committing)return;
        if(!StagedLoginProtocol.matches(digest,server.getGameUpdater().stagedLoginSceneIdentity(candidate))){refresh(candidate);}
        if(server.getGameUpdater().sendStagedLoginScene(candidate)&&!complete){complete=true;channel.writeAndFlush(StagedLoginProtocol.packet(StagedLoginProtocol.COMPLETE).write(token).write(digest).writeInt(server.getGameUpdater().stagedLoginContextSequence(candidate)).toPacket());}
    }
    private void finish(Player fresh,byte[] echoedToken,byte[] echoedDigest){
        if(!live()||fresh==null){discardDetached(fresh);close(true,3);return;}
        if(!StagedLoginProtocol.matches(token,echoedToken)||!StagedLoginProtocol.matches(digest,echoedDigest)){discardDetached(fresh);committing=false;return;}
        byte[] current=server.getGameUpdater().stagedLoginSceneIdentity(fresh);
        if(!StagedLoginProtocol.matches(digest,current)){refresh(fresh);tickOnce();return;}
        synchronized(stateLock){
            if(!live()){discardDetached(fresh);return;}
            Player old=candidate;server.getGameUpdater().adoptStagedLoginScene(old,fresh);
            old.removeAttribute(OWNER);candidate=fresh;fresh.setAttribute(OWNER,this);discardDetached(old);
        }
        int group=fresh.getGroupID();if(group<0||group>=com.openrsc.server.util.rsc.LoginResponse.LOGIN_SUCCESSFUL.length){close(true,3);return;}
        success=com.openrsc.server.util.rsc.LoginResponse.LOGIN_SUCCESSFUL[group];
        boolean admitted=request.finishAdmission(fresh,()->{
            synchronized(stateLock){
                if(!live()||attachment.stagedLogin.get()!=this||candidate!=fresh
                    ||!StagedLoginProtocol.matches(digest,server.getGameUpdater().stagedLoginSceneIdentity(fresh)))throw new IllegalStateException("Prepared admission changed");
                channel.writeAndFlush(StagedLoginProtocol.packet(StagedLoginProtocol.COMMIT).write(token).write(digest).writeByte(success).toPacket());
                closed=true;attachment.stagedLogin.compareAndSet(this,null);fresh.removeAttribute(OWNER);candidate=null;cancelTasks();
            }
        });
        if(!admitted)close(true,3);
    }
    public void close(boolean closeChannel,int reason){
        synchronized(stateLock){
            if(closed)return;closed=true;attachment.stagedLogin.compareAndSet(this,null);
            Player owner=candidate;candidate=null;discardDetached(owner);cancelTasks();
        }
        request.clearLoginSecret();request.releaseAdmission();
        if(closeChannel){if(channel.isActive()&&reason!=0)channel.writeAndFlush(StagedLoginProtocol.packet(StagedLoginProtocol.ERROR).writeByte(reason).toPacket());channel.close();}
    }
    private void cancelTasks(){
        ScheduledFuture<?> task=tickTask;tickTask=null;if(task!=null)task.cancel(false);
        task=deadlineTask;deadlineTask=null;if(task!=null)task.cancel(false);
    }
    private void discardDetached(Player owner){
        if(owner==null||owner.isLoggedIn()||server.getWorld().getPlayerID(owner.getDatabaseID())==owner)return;
        if(forPlayer(owner)==this)owner.removeAttribute(OWNER);
        owner.closePersistenceSession();
    }
}
