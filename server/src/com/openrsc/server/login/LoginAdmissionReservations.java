package com.openrsc.server.login;

import java.util.HashMap;
import java.util.Map;

/** Bounded account-owner leases cover authentication through actual world admission. */
public final class LoginAdmissionReservations {
    public static final long TTL_MILLIS=300000L;
    private final Map<Integer,Lease> accounts=new HashMap<Integer,Lease>();
    private final int maximum,maximumStaged;
    public LoginAdmissionReservations(){this(4096,64);}
    LoginAdmissionReservations(int maximum,int maximumStaged){this.maximum=maximum;this.maximumStaged=maximumStaged;}
    public final class Lease {
        final int accountId;final String ip;final Object owner;final long expires;final boolean staged;
        private final Runnable releaseTracker;private boolean released;
        private java.util.concurrent.ScheduledFuture<?> expirationTask;
        private Lease(int accountId,String ip,Object owner,long now,boolean staged,Runnable releaseTracker){this.accountId=accountId;this.ip=ip;this.owner=owner;this.expires=now+TTL_MILLIS;this.staged=staged;this.releaseTracker=releaseTracker;}
        public boolean isLive(long now){synchronized(LoginAdmissionReservations.this){return !released&&accounts.get(accountId)==this&&now<expires;}}
        public long remainingMillis(long now){return Math.max(0L,expires-now);}
        public boolean expire(long now){synchronized(LoginAdmissionReservations.this){if(released||accounts.get(accountId)!=this||now<expires)return false;finish(false);return true;}}
        public void release(){synchronized(LoginAdmissionReservations.this){finish(false);}}
        public void expirationTask(java.util.concurrent.ScheduledFuture<?> task){synchronized(LoginAdmissionReservations.this){if(released)task.cancel(false);else expirationTask=task;}}
        private void finish(boolean admitted){if(released||accounts.get(accountId)!=this)return;released=true;try{if(!admitted)releaseTracker.run();}finally{accounts.remove(accountId);if(expirationTask!=null){expirationTask.cancel(false);expirationTask=null;}}}
        public boolean commit(long now,Runnable admission,java.util.function.BooleanSupplier registered){
            synchronized(LoginAdmissionReservations.this){
                if(!isLive(now))return false;
                Throwable failure=null;boolean admitted=false;
                try{admission.run();}catch(RuntimeException|Error primary){failure=primary;}
                try{admitted=registered.getAsBoolean();}catch(RuntimeException|Error verification){
                    if(failure==null)failure=verification;else failure.addSuppressed(verification);
                }
                try{finish(admitted);}catch(RuntimeException|Error cleanup){
                    if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);
                }
                if(failure instanceof Error)throw (Error)failure;
                if(failure!=null)throw (RuntimeException)failure;
                return admitted;
            }
        }
    }
    public synchronized Lease acquire(int accountId,String ip,Object owner,long now,boolean staged,int perIpLimit,Runnable addTracker,Runnable releaseTracker){
        if(accountId<1||ip==null||owner==null||perIpLimit<1)throw new IllegalArgumentException("Invalid login lease");
        Lease old=accounts.get(accountId);if(old!=null){if(old.isLive(now))return null;old.finish(false);}
        int stagedCount=0,ipCount=0;
        for(Lease lease:accounts.values())if(lease.staged&&!lease.released){stagedCount++;if(lease.ip.equals(ip))ipCount++;}
        if(accounts.size()>=maximum||staged&&(stagedCount>=maximumStaged||ipCount>=perIpLimit))return null;
        Lease lease=new Lease(accountId,ip,owner,now,staged,releaseTracker);accounts.put(accountId,lease);
        try{addTracker.run();}catch(RuntimeException|Error failure){accounts.remove(accountId);throw failure;}
        return lease;
    }
    public synchronized int size(){return accounts.size();}
}
