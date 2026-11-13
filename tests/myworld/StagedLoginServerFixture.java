package com.openrsc.server.login;

import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import com.openrsc.server.Server;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.ConnectionAttachment;
import com.openrsc.server.net.RSCConnectionHandler;
import com.openrsc.server.util.PlayerList;
import io.netty.channel.embedded.EmbeddedChannel;

/** Real reservation and packet grammar, no game server, network, account or database. */
public final class StagedLoginServerFixture {
    private static int checks;
    private static void check(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){checks++;}}
    private static LoginAdmissionReservations.Lease acquire(LoginAdmissionReservations registry,int account,String ip,Object owner,long now,boolean staged,int ipLimit,AtomicInteger tracked){
        return registry.acquire(account,ip,owner,now,staged,ipLimit,tracked::incrementAndGet,tracked::decrementAndGet);
    }
    private static void leases()throws Exception{
        LoginAdmissionReservations registry=new LoginAdmissionReservations(4,2);AtomicInteger tracked=new AtomicInteger();
        Object owner=new Object();long now=1000;
        LoginAdmissionReservations.Lease first=acquire(registry,1,"a",owner,now,true,1,tracked);
        check(first!=null&&tracked.get()==1&&registry.size()==1,"First pending lease");
        check(acquire(registry,1,"b",new Object(),now,false,1,tracked)==null,"Native cannot bypass cross-IP staged account reservation");
        check(acquire(registry,2,"a",new Object(),now,true,1,tracked)==null,"Staged per-IP bound");
        check(acquire(registry,2,"b",new Object(),now,true,1,tracked)!=null,"Other IP within staged cap");
        check(acquire(registry,3,"c",new Object(),now,true,1,tracked)==null,"Global staged cap");
        check(acquire(registry,3,"c",new Object(),now,false,1,tracked)!=null,"Native is not charged staged cap");
        check(acquire(registry,4,"d",new Object(),now,false,1,tracked)!=null,"Global final free lease");
        check(acquire(registry,5,"e",new Object(),now,false,1,tracked)==null,"Global overall cap");
        check(first.isLive(now+299999)&&!first.isLive(now+300000),"Exact hard expiry");
        long deadline=first.remainingMillis(now+5000);check(deadline==295000,"Heartbeat observation does not extend lease");
        LoginAdmissionReservations.Lease replacement=acquire(registry,1,"z",new Object(),now+300000,true,1,tracked);
        check(replacement!=null&&tracked.get()==4,"Expired same-account replaced with exact tracker cleanup");
        first.release();check(replacement.isLive(now+300000)&&tracked.get()==4,"Old owner cannot release replacement");
        check(!first.expire(now+600000),"Stale owner deadline cannot expire replacement");
        replacement.release();replacement.release();check(tracked.get()==3&&registry.size()==3,"Idempotent cancellation cleanup");

        LoginAdmissionReservations nativeRegistry=new LoginAdmissionReservations();AtomicInteger count=new AtomicInteger();
        LoginAdmissionReservations.Lease nativeLease=acquire(nativeRegistry,11,"a",new Object(),now,false,1,count);
        check(acquire(nativeRegistry,11,"b",new Object(),now,true,1,count)==null,"Staged cannot bypass native reservation");
        AtomicBoolean admitted=new AtomicBoolean();check(nativeLease.commit(now,()->admitted.set(true),admitted::get),"Commit admission succeeds");
        check(nativeRegistry.size()==0&&count.get()==1,"World owns tracker after commit");
        check(!nativeLease.expire(now+600000),"Old hard deadline never disconnects admitted player");
        nativeLease.release();check(count.get()==1,"Close after commit does not remove world-owned tracker");
        LoginAdmissionReservations.Lease failure=acquire(nativeRegistry,12,"a",new Object(),now,true,1,count);
        RuntimeException original=new RuntimeException("fixture");
        try{failure.commit(now,()->{throw original;},()->false);throw new AssertionError("Missing failure");}
        catch(RuntimeException actual){check(actual==original,"Commit preserves primary failure");}
        check(count.get()==1&&nativeRegistry.size()==0,"Failed admission releases reservation/tracker");
        AtomicInteger evaluated=new AtomicInteger();
        LoginAdmissionReservations.Lease verifyFailure=acquire(nativeRegistry,13,"a",new Object(),now,true,1,count);
        try{verifyFailure.commit(now,()->{},()->{evaluated.incrementAndGet();throw original;});throw new AssertionError("Missing supplier failure");}
        catch(RuntimeException actual){check(actual==original,"Verification failure preserved");}
        check(evaluated.get()==1&&nativeRegistry.size()==0&&count.get()==1,"Registration predicate once, cleanup even when predicate throws");
        LoginAdmissionReservations.Lease trackerFailure=nativeRegistry.acquire(14,"a",new Object(),now,true,1,()->{},()->{throw new IllegalStateException("tracker");});
        try{trackerFailure.commit(now,()->{throw original;},()->false);throw new AssertionError("Missing admission failure");}
        catch(RuntimeException actual){check(actual==original&&actual.getSuppressed().length==1,"Cleanup failure cannot mask admission failure");}
        check(nativeRegistry.size()==0,"Tracker callback failure cannot retain lease");

        // The lease lock spans actual admission; a second IP cannot slip into the world during it.
        LoginAdmissionReservations concurrent=new LoginAdmissionReservations();AtomicInteger concurrentTracked=new AtomicInteger();
        CountDownLatch start=new CountDownLatch(1);AtomicInteger winners=new AtomicInteger();List<Thread> threads=new ArrayList<Thread>();
        for(int n=0;n<24;n++){final int index=n;Thread thread=new Thread(()->{try{start.await();if(acquire(concurrent,99,"ip"+index,new Object(),1000,index%2==0,4,concurrentTracked)!=null)winners.incrementAndGet();}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}});threads.add(thread);thread.start();}
        start.countDown();for(Thread thread:threads)thread.join();
        check(winners.get()==1&&concurrentTracked.get()==1&&concurrent.size()==1,"One exact account owner under concurrent native/browser attempts");
    }
    private static final class Timer implements java.util.concurrent.ScheduledFuture<Object>{
        boolean cancelled;public boolean cancel(boolean interrupt){cancelled=true;return true;}public boolean isCancelled(){return cancelled;}
        public boolean isDone(){return cancelled;}public Object get(){return null;}public Object get(long t,java.util.concurrent.TimeUnit u){return null;}
        public long getDelay(java.util.concurrent.TimeUnit u){return 1;}public int compareTo(java.util.concurrent.Delayed d){return 0;}
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static final class Request extends LoginRequest{
        Request(){super(null,"fixture","127.0.0.1",250);}
        public void loginValidated(int response){throw new AssertionError("No login reply expected");}
        public void loadingComplete(Player player){throw new AssertionError("No world admission expected");}
    }
    private static final class FailedRequest extends LoginRequest{
        boolean replied;
        FailedRequest(){super(null,"fixture","127.0.0.1",250);}
        public ValidatedLogin validateLogin(){return new ValidatedLogin(com.openrsc.server.util.rsc.LoginResponse.INVALID_CREDENTIALS);}
        public void loginValidated(int response){replied=true;}
        public void loadingComplete(Player player){throw new AssertionError("Failed authentication must not load world");}
    }
    private static void cancellation()throws Exception{
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        Server server=(Server)unsafe.allocateInstance(Server.class);World world=(World)unsafe.allocateInstance(World.class);
        field(World.class,"players").set(world,new PlayerList(10));field(Server.class,"world").set(server,world);
        Player candidate=(Player)unsafe.allocateInstance(Player.class);field(Entity.class,"attributes").set(candidate,new java.util.concurrent.ConcurrentHashMap<String,Object>());candidate.setDatabaseID(71);
        Request request=(Request)unsafe.allocateInstance(Request.class);EmbeddedChannel channel=new EmbeddedChannel();ConnectionAttachment attachment=new ConnectionAttachment();
        channel.attr(RSCConnectionHandler.attachment).set(attachment);
        field(LoginRequest.class,"channel").set(request,channel);field(LoginRequest.class,"password").set(request,"test-only-secret");
        LoginAdmissionReservations registry=new LoginAdmissionReservations();AtomicInteger tracked=new AtomicInteger();
        LoginAdmissionReservations.Lease lease=acquire(registry,71,"a",channel,1000,true,2,tracked);field(LoginRequest.class,"admission").set(request,lease);
        StagedLoginSession session=(StagedLoginSession)unsafe.allocateInstance(StagedLoginSession.class);
        field(StagedLoginSession.class,"server").set(session,server);field(StagedLoginSession.class,"channel").set(session,channel);
        field(StagedLoginSession.class,"request").set(session,request);field(StagedLoginSession.class,"attachment").set(session,attachment);
        field(StagedLoginSession.class,"stateLock").set(session,new Object());field(StagedLoginSession.class,"candidate").set(session,candidate);
        Timer tick=new Timer(),deadline=new Timer(),leaseTimer=new Timer();lease.expirationTask(leaseTimer);
        field(StagedLoginSession.class,"tickTask").set(session,tick);field(StagedLoginSession.class,"deadlineTask").set(session,deadline);
        attachment.stagedLogin.set(session);candidate.setAttribute("staged_login_owner",session);
        session.close(false,0);session.close(false,0);
        check(candidate.isPersistenceSessionClosed()&&!candidate.isLoggedIn(),"Cancelled detached snapshot permanently cannot save");
        check(candidate.getAttribute("staged_login_owner",null)==null&&attachment.stagedLogin.get()==null,"Only own pending transport ownership removed");
        check(field(StagedLoginSession.class,"candidate").get(session)==null,"Terminal session does not retain heavy candidate");
        check(tick.cancelled&&deadline.cancelled&&leaseTimer.cancelled,"All candidate/lease timer references cancelled");
        check(field(StagedLoginSession.class,"tickTask").get(session)==null&&field(StagedLoginSession.class,"deadlineTask").get(session)==null,"Cancelled task handles cleared");
        check(request.getPassword()==null&&tracked.get()==0&&registry.size()==0,"Secret and exact reservation released once");
        check(world.getPlayers().size()==0,"Cancellation never registers any player");
        Timer late=new Timer();lease.expirationTask(late);check(late.cancelled,"Timer installed after cancellation cancelled immediately");
        FailedRequest failed=(FailedRequest)unsafe.allocateInstance(FailedRequest.class);
        field(LoginRequest.class,"password").set(failed,"test-only-secret");field(LoginRequest.class,"clientVersion").setInt(failed,250);
        failed.process();check(failed.replied&&failed.getPassword()==null,"Actual authentication failure path clears plaintext in common finally");
        channel.finishAndReleaseAll();
    }
    private static void grammar(){
        byte[] token=new byte[16],digest=new byte[32];for(int i=0;i<token.length;i++)token[i]=(byte)i;
        for(int kind=1;kind<=3;kind++){
            Packet p=StagedLoginProtocol.packet(kind).write(token).write(digest).toPacket();
            try{StagedLoginProtocol.Control command=StagedLoginProtocol.control(p);check(command.kind==kind&&StagedLoginProtocol.matches(token,command.token)&&StagedLoginProtocol.matches(digest,command.digest)&&p.getReadableBytes()==0,"Exact control roundtrip");}finally{p.getBuffer().release();}
        }
        for(int opcode:new int[]{154,155})for(int n:new int[]{opcode==154?24:28,222}){
            Packet p=StagedLoginProtocol.packet(4).write(token).write(digest).writeByte(opcode).write(new byte[n]).toPacket();
            try{StagedLoginProtocol.Control command=StagedLoginProtocol.control(p);check(command.receiptOpcode==opcode&&command.receipt.length==n,"Original terrain ACK envelope bound");}finally{p.getBuffer().release();}
        }
        List<Packet> bad=new ArrayList<Packet>();
        bad.add(new Packet(67,Unpooled.wrappedBuffer(new byte[54])));
        bad.add(new PacketBuilder(254).writeInt(0).writeByte(1).writeByte(1).write(token).write(digest).toPacket());
        bad.add(new PacketBuilder(254).writeInt(StagedLoginProtocol.MAGIC).writeByte(2).writeByte(1).write(token).write(digest).toPacket());
        bad.add(StagedLoginProtocol.packet(0).toPacket());
        bad.add(StagedLoginProtocol.packet(5).write(token).write(digest).toPacket());
        bad.add(StagedLoginProtocol.packet(1).write(new byte[47]).toPacket());
        bad.add(StagedLoginProtocol.packet(1).write(token).write(digest).writeByte(0).toPacket());
        bad.add(StagedLoginProtocol.packet(4).write(token).write(digest).writeByte(156).write(new byte[28]).toPacket());
        bad.add(StagedLoginProtocol.packet(4).write(token).write(digest).writeByte(155).write(new byte[24]).toPacket());
        bad.add(StagedLoginProtocol.packet(4).write(token).write(digest).writeByte(154).write(new byte[223]).toPacket());
        for(Packet p:bad)try{reject(()->StagedLoginProtocol.control(p));}finally{p.getBuffer().release();}
        check(!StagedLoginProtocol.matches(token,new byte[16]),"Wrong token rejected");
        check(!StagedLoginProtocol.matches(digest,new byte[31]),"Wrong digest length rejected");
    }
    public static void main(String[] args)throws Exception{
        java.io.PrintStream out=System.out,err=System.err;
        try{leases();grammar();cancellation();}finally{System.setOut(out);System.setErr(err);}
        System.out.println("PASS "+checks+" actual staged-login reservation/concurrency, strict pending packets and detached cancellation checks; no server launched");
    }
}
