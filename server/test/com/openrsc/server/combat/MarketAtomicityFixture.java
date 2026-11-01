package com.openrsc.server.combat;

import com.openrsc.server.Server;
import com.openrsc.server.content.market.*;
import com.openrsc.server.database.*;
import com.openrsc.server.database.struct.*;
import com.openrsc.server.login.*;
import com.openrsc.server.util.rsc.LoginResponse;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.database.impl.sqlite.SqliteGameDatabase;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.service.PlayerService;
import java.lang.reflect.*;
import java.security.Permission;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Real disposable JDBC engines; no accounts, listener, or production map inputs. */
public final class MarketAtomicityFixture {
    static String url, user, password;
    static final String P = "qa_";
    static int checks, playerNumber = 100;
    static boolean mysql;
    static int endpointPort;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static Connection open() throws SQLException { return DriverManager.getConnection(url + (mysql ? "&connectTimeout=2000&socketTimeout=3000" : ""), user, password); }
    static void sql(String sql) throws SQLException { try (Connection c = open(); Statement s = c.createStatement()) { s.executeUpdate(sql); } }
    static long n(String sql) throws SQLException { try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    static void set(Object owner, Class<?> type, String field, Object value) throws Exception { Field f=type.getDeclaredField(field); f.setAccessible(true); f.set(owner,value); }
    static void schema() throws Exception {
        String auto = mysql ? "INTEGER PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        for (String table : Arrays.asList("market_operations","auctions","expired_auctions","invitems","bank","equipped","itemstatuses","players")) sql("DROP TABLE IF EXISTS "+P+table);
        sql("CREATE TABLE "+P+"players(id INTEGER PRIMARY KEY, username VARCHAR(12))");
        for(String table:Arrays.asList("invitems","bank")) sql("CREATE TABLE "+P+table+"(playerID INTEGER,itemID BIGINT,slot INTEGER,PRIMARY KEY(playerID,itemID,slot))");
        sql("CREATE TABLE "+P+"equipped(playerID INTEGER,itemID BIGINT)");
        sql("CREATE TABLE "+P+"itemstatuses(itemID BIGINT PRIMARY KEY,catalogID INTEGER,amount INTEGER,noted INTEGER,wielded INTEGER,durability INTEGER)");
        sql("CREATE TABLE "+P+"auctions(auctionID "+auto+",itemID INTEGER,amount INTEGER,amount_left INTEGER,price INTEGER,seller INTEGER,seller_username VARCHAR(12),buyer_info TEXT,`sold-out` INTEGER DEFAULT 0,time BIGINT,was_cancel INTEGER DEFAULT 0)");
        sql("CREATE TABLE "+P+"expired_auctions(claim_id "+auto+",playerID INTEGER,item_id INTEGER,item_amount INTEGER,time BIGINT,claim_time BIGINT DEFAULT 0,claimed INTEGER DEFAULT 0,explanation VARCHAR(255))");
    }
    static final class Provider extends JDBCDatabaseConnection implements AutoCloseable {
        final Connection c; Provider() throws SQLException { c=MarketAtomicityFixture.open(); }
        public Connection getConnection(){return c;}
        protected Statement getStatement(){try{return c.createStatement();}catch(SQLException e){throw new RuntimeException(e);}}
        protected boolean checkConnection(){return true;} public boolean isConnected(){return true;}
        public boolean open(){return true;} public void close(){try{c.close();}catch(SQLException ignored){}}
        public DatabaseType getDatabaseType(){return mysql?DatabaseType.MYSQL:DatabaseType.SQLITE;}
        public Connection openIsolatedConnection() throws SQLException{return MarketAtomicityFixture.open();}
    }
    static class Store extends MarketJdbcStore {
        String failure; boolean unavailable, halt;
        Store(){super(() -> { if (failResolution) throw new SQLException("injected resolver unavailable"); return open(); }, P);}
        static boolean failResolution;
        @Override protected void checkpoint(String stage)throws SQLException{
            if (stage.equals(failure)) {
                failure=null;
                if(halt) Runtime.getRuntime().halt(77);
                if(unavailable) failResolution=true;
                throw new SQLException("injected "+stage);
            }
        }
    }
    static Player player(CurrentCombatHarness h) throws Exception {
        int id=++playerNumber;
        Player p=h.player("auction"+id,449,450); p.setDatabaseID(id); p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2)); p.setBank(new Bank(p)); p.getClientLimitations().maxItemId=Integer.MAX_VALUE; h.recordOutgoingPackets(p);
        sql("INSERT INTO "+P+"players VALUES("+id+",'"+p.getUsername()+"')");
        check(p.getCarriedItems().getInventory().add(new Item(10,1000),false),"seed coins");
        persist(p); return p;
    }
    static java.util.List<Item> equipment(Player p) {
        java.util.List<Item> result=new ArrayList<>();for(Item item:p.getCarriedItems().getEquipment().getList())if(item!=null)result.add(item.copyForTransaction());return result;
    }
    static void persist(Player p)throws Exception {try(Connection c=open()){c.setAutoCommit(false);new Store().saveContainers(c,p.getDatabaseID(),p.getCarriedItems().getInventory().getItems(),p.getBank().getItems(),equipment(p));c.commit();}}
    static int listing(int seller,int amount,int left,int price)throws Exception {
        sql("INSERT INTO "+P+"auctions(itemID,amount,amount_left,price,seller,seller_username,buyer_info,time) VALUES(384,"+amount+","+left+","+price+","+seller+",'seller','',"+(System.currentTimeMillis()/1000)+")");
        return (int)n("SELECT MAX(auctionID) FROM "+P+"auctions");
    }
    static long persisted(int actor,String table,int catalog)throws Exception {return n("SELECT COALESCE(SUM(s.amount),0) FROM "+P+table+" c JOIN "+P+"itemstatuses s ON c.itemID=s.itemID WHERE c.playerID="+actor+" AND s.catalogID="+catalog);}
    static MarketOperation op(MarketOperation.Kind kind,Player p,int auction,int catalog,int amount,int price){return new MarketOperation(kind,p,auction,catalog,amount,price);}
    static void run(Store store,CurrentCombatHarness h,MarketOperation op){new MarketTransactions(h.world(),store).execute(op);}
    static void installDatabase(CurrentCombatHarness h,Provider provider)throws Exception{
        h.server().getConfig().DB_TABLE_PREFIX=P;
        JDBCDatabase db=mysql?new MySqlGameDatabase(h.server()) {public JDBCDatabaseConnection getConnection(){return provider;}}
            :new SqliteGameDatabase(h.server()){public JDBCDatabaseConnection getConnection(){return provider;}};
        set(h.server(),Server.class,"database",db);
        set(h.server(),Server.class,"playerService",new PlayerService(h.world(),h.server().getConfig(),db));
    }
    static void reloadContainers(CurrentCombatHarness h,Player p)throws Exception {
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2));
        for(String name:Arrays.asList("loadPlayerEquipment","loadPlayerInventory","loadPlayerBank")) {
            Method m=PlayerService.class.getDeclaredMethod(name,Player.class);m.setAccessible(true);m.invoke(h.server().getPlayerService(),p);
        }
    }
    static void assertBuyRollback(CurrentCombatHarness h,String failure)throws Exception{
        Player p=player(h);int id=listing(999,2,2,200); Store s=new Store();s.failure=failure;
        long sellerBefore=n("SELECT COALESCE(SUM(item_amount),0) FROM "+P+"expired_auctions WHERE playerID=999");
        MarketOperation request=op(MarketOperation.Kind.BUY,p,id,0,1,0);Item coins=p.getCarriedItems().getInventory().getItems().get(0);
        coins.setAttribute("opaque-fixture","preserved");
        run(s,h,request);
        check(p.isLoggedIn()&&p.getCarriedItems().getInventory().countId(10)==1000&&p.getCarriedItems().getInventory().countId(384,Optional.empty())==0,"rollback live "+failure);
        check(coins==p.getCarriedItems().getInventory().getItems().get(0)&&"preserved".equals(coins.getAttribute("opaque-fixture", "")),"rollback identity/attribute "+failure);
        check(persisted(p.getDatabaseID(),"invitems",10)==1000&&n("SELECT amount_left FROM "+P+"auctions WHERE auctionID="+id)==2,"rollback durable "+failure);
        check(n("SELECT COUNT(*) FROM "+P+"market_operations WHERE operation_id='"+request.id+"'")==0,"rollback receipt "+failure);
        check(n("SELECT COALESCE(SUM(item_amount),0) FROM "+P+"expired_auctions WHERE playerID=999")==sellerBefore,"rollback seller conservation "+failure);
        run(s,h,request);check(p.getCarriedItems().getInventory().countId(384,Optional.empty())==1,"same UUID succeeds retry "+failure);
        check(sellerBefore+100==n("SELECT COALESCE(SUM(item_amount),0) FROM "+P+"expired_auctions WHERE playerID=999")&&persisted(p.getDatabaseID(),"invitems",10)==900,"buyer debit equals seller credit "+failure);
        run(s,h,request);check(p.getCarriedItems().getInventory().countId(384,Optional.empty())==1,"same UUID replay no second grant "+failure);
    }
    static void scenarios(CurrentCombatHarness h)throws Exception{
        for(String stage:Arrays.asList("buyer-stage","seller-credit","listing-update","inventory-save","bank-save","equipment-save","before-receipt","before-commit")) assertBuyRollback(h,stage);
        Player p=player(h);int id=listing(999,3,3,300);Store s=new Store();s.failure="after-commit";
        MarketOperation buy=op(MarketOperation.Kind.BUY,p,id,0,2,0);run(s,h,buy);
        check(p.getCarriedItems().getInventory().countId(384,Optional.empty())==2&&p.getCarriedItems().getInventory().countId(10)==800,"lost commit acknowledgement publishes once");
        check(persisted(p.getDatabaseID(),"invitems",384)==2,"ack recovery durable");run(s,h,buy);
        check(n("SELECT amount_left FROM "+P+"auctions WHERE auctionID="+id)==1,"partial listing quantity");
        final int partialId=id;check(new Store().activeListings().stream().anyMatch(x->x.getAuctionID()==partialId&&x.getAmountLeft()==1&&x.getPrice()==100),"fresh advisory cache includes partial changes");
        // Definitive live publication failure revokes the entire stale session; normal container load recovers.
        Player stale=player(h);com.openrsc.server.login.PlayerSaveRequest queuedSave=new com.openrsc.server.login.PlayerSaveRequest(h.server(),stale,false);id=listing(998,1,1,100);Store publish=new Store();publish.failure="before-publish";
        run(publish,h,op(MarketOperation.Kind.BUY,stale,id,0,1,0));
        check(stale.isPersistenceSessionClosed()&&!stale.isLoggedIn()&&h.world().getPlayer(stale.getUsernameHash())==null,"failed publication detaches stale session");
        check(!h.server().getPlayerService().savePlayer(stale),"queued stale save rejected before any SQL");
        Player successor=h.player(stale.getUsername(),449,450);successor.setDatabaseID(stale.getDatabaseID());h.recordOutgoingPackets(successor);reloadContainers(h,successor);
        check(successor.getCarriedItems().getInventory().countId(10)==900&&successor.getCarriedItems().getInventory().countId(384,Optional.empty())==1,"ordinary provider reload gets committed inventory");
        queuedSave.process();
        check(!h.server().getPlayerService().savePlayer(stale)&&persisted(stale.getDatabaseID(),"invitems",10)==900&&h.world().getPlayer(successor.getUsernameHash())==successor,"queued old save cannot overwrite/remove successor");
        Player unknown=player(h);id=listing(997,1,1,100);Store unresolved=new Store();unresolved.failure="after-commit";unresolved.unavailable=true;
        run(unresolved,h,op(MarketOperation.Kind.BUY,unknown,id,0,1,0));Store.failResolution=false;
        check(unknown.isPersistenceSessionClosed()&&!unknown.isLoggedIn(),"unresolved commit fails closed");
        check(persisted(unknown.getDatabaseID(),"invitems",10)==900,"unresolved operation actually committed");
        Player owner=player(h);check(owner.getCarriedItems().getInventory().add(new Item(384,3,true),false),"seed noted sale");persist(owner);
        Store create=new Store();create.failure="listing-create";MarketOperation createOp=op(MarketOperation.Kind.CREATE,owner,0,384,3,300);
        run(create,h,createOp);check(owner.getCarriedItems().getInventory().countId(384,Optional.empty())==3,"failed creation preserves exact noted items");
        run(create,h,createOp);check(owner.getCarriedItems().getInventory().countId(384,Optional.empty())==0,"created listing escrows items");
        id=(int)n("SELECT MAX(auctionID) FROM "+P+"auctions");Store cancel=new Store();cancel.failure="listing-update";
        MarketOperation cancelOp=op(MarketOperation.Kind.CANCEL,owner,id,0,0,0);run(cancel,h,cancelOp);check(owner.getCarriedItems().getInventory().countId(384,Optional.empty())==0,"cancel rollback no grant");
        run(cancel,h,cancelOp);run(cancel,h,cancelOp);check(owner.getCarriedItems().getInventory().countId(384,Optional.empty())==3,"cancel idempotent escrow return");
        Player collector=player(h);sql("INSERT INTO "+P+"expired_auctions(playerID,item_id,item_amount,time,explanation) VALUES("+collector.getDatabaseID()+",384,2,0,'fixture')");
        Store collect=new Store();collect.failure="claim-update";MarketOperation collectOp=op(MarketOperation.Kind.COLLECT,collector,0,0,0,0);run(collect,h,collectOp);
        check(collector.getBank().countId(384)==0&&n("SELECT COUNT(*) FROM "+P+"expired_auctions WHERE playerID="+collector.getDatabaseID()+" AND claimed=0")==1,"claim failure no live or durable grant");
        run(collect,h,collectOp);run(collect,h,collectOp);run(collect,h,op(MarketOperation.Kind.COLLECT,collector,0,0,0,0));
        check(collector.getBank().countId(384)==2&&persisted(collector.getDatabaseID(),"bank",384)==2,"collection retries exactly once");
        // Stale queued actor and account mismatch never enter SQL.
        MarketOperation staleRequest=op(MarketOperation.Kind.BUY,successor,listing(996,1,1,100),0,1,0);h.logout(successor);run(new Store(),h,staleRequest);
        check(n("SELECT COUNT(*) FROM "+P+"market_operations WHERE operation_id='"+staleRequest.id+"'")==0,"disconnected queued actor ignored");
        Player unauthorized=player(h);id=listing(995,1,1,100);run(new Store(),h,op(MarketOperation.Kind.CANCEL,unauthorized,id,0,0,0));
        check(n("SELECT amount_left FROM "+P+"auctions WHERE auctionID="+id)==1,"non-owner cancellation denied");
        // Expiry returns remaining amount only; repeats cannot create another return claim.
        id=listing(994,10,3,300);sql("UPDATE "+P+"auctions SET time=-4000000000 WHERE auctionID="+id);
        MarketTransactions tx=new MarketTransactions(h.world(),new Store());MarketItem expired=new MarketItem(id,384,10,3,300,994,"seller","",-4000000000L);
        check(tx.expire(expired),"expiry commit");tx.expire(expired);
        check(n("SELECT SUM(item_amount) FROM "+P+"expired_auctions WHERE playerID=994")==3,"expiry only unsold remainder once");
        Player moderator=player(h);moderator.setGroupID(com.openrsc.server.model.entity.player.Group.MOD);
        id=listing(992,8,4,400);Store moderation=new Store();moderation.failure="listing-update";
        MarketOperation moderate=op(MarketOperation.Kind.MODERATE,moderator,id,0,0,0);run(moderation,h,moderate);
        check(n("SELECT COUNT(*) FROM "+P+"expired_auctions WHERE playerID=992")==0,"moderation failure returns nothing prematurely");
        run(moderation,h,moderate);run(moderation,h,moderate);
        check(n("SELECT SUM(item_amount) FROM "+P+"expired_auctions WHERE playerID=992")==4,"moderation returns remainder once to seller");
        bankPins(h);equipmentTransitions(h);saveLockOrder(h);admission(h);concurrentBuy(h);gameQueue(h);engineSafety(h);loginBarrier(h);legacyStorage(h);providerConnections(h);
    }
    static void bankPins(CurrentCombatHarness h)throws Exception {
        Player p=player(h);Bank bank=p.getBank();check(bank.add(new Item(384,1),false),"pin seed");
        check(bank.pinDisplaySlot(0,384),"pin item");check(bank.remove(384,1,false),"leave empty pin");
        for(int i=0;i<h.world().getMaxBankSize()-1;i++)bank.getItems().add(new Item(18,1,false,3000000L+i));
        String pins=p.getCache().getString("bank_item_pins");persist(p);
        sql("INSERT INTO "+P+"expired_auctions(playerID,item_id,item_amount,time,explanation) VALUES("+p.getDatabaseID()+",384,2,0,'pin')");
        Store s=new Store();s.failure="bank-save";MarketOperation claim=op(MarketOperation.Kind.COLLECT,p,0,0,0,0);run(s,h,claim);
        check(pins.equals(p.getCache().getString("bank_item_pins"))&&bank.getDisplaySlot(0).isPinned()&&bank.getDisplaySlot(0).getItem()==null,"rollback leaves empty pin and cache exact");
        run(s,h,claim);check(bank.getDisplaySlot(0).isPinned()&&bank.getDisplaySlot(0).getItem().getCatalogId()==384&&bank.countId(384)==2,"claim fills reserved pin at full logical capacity");
        check(pins.equals(p.getCache().getString("bank_item_pins")),"successful bank claim preserves pin cache");
        Player status=player(h);Inventory inv=status.getCarriedItems().getInventory();
        Item old=inv.getItems().get(0);old.getItemStatus().setDurability(37);old.setAttribute("tag","kept");
        Item worn=new Item(81,1,false,4000000L);worn.setWielded(true);worn.getItemStatus().setDurability(41);inv.getItems().add(worn);persist(status);
        MarketOperation forbidden=op(MarketOperation.Kind.CREATE,status,0,81,1,100);run(new Store(),h,forbidden);
        check(inv.getItems().contains(worn)&&worn.isWielded()&&worn.getItemStatus().getDurability()==41,"wielded input refused intact");
        int id=listing(991,1,1,100);run(new Store(),h,op(MarketOperation.Kind.BUY,status,id,0,1,0));
        check(inv.getItems().get(0)==old&&old.getItemStatus().getDurability()==37&&"kept".equals(old.getAttribute("tag","")),"successful publication preserves surviving identity metadata");
        check(n("SELECT durability FROM "+P+"itemstatuses WHERE itemID="+worn.getItemId())==41&&n("SELECT wielded FROM "+P+"itemstatuses WHERE itemID="+worn.getItemId())==1,"durable untouched wield/durability exact");
    }
    static Item equipFromInventory(Player p)throws Exception {
        Inventory inv=p.getCarriedItems().getInventory();check(inv.add(new Item(66,1),false),"seed weapon before ordinary container save");persist(p);
        Item sword=inv.get(inv.getLastIndexById(66));
        check(p.getCarriedItems().getEquipment().equipItem(new com.openrsc.server.model.struct.EquipRequest(p,sword,com.openrsc.server.model.struct.EquipRequest.RequestType.FROM_INVENTORY,false),false),"ordinary equip since last save");
        Item current=p.getCarriedItems().getEquipment().get(sword.getDef(p.getWorld()).getWieldPosition());current.getItemStatus().setDurability(37);return current;
    }
    static void equipmentTransitions(CurrentCombatHarness h)throws Exception {
        Player p=player(h);Item sword=equipFromInventory(p);long id=sword.getItemId();int slot=sword.getDef(h.world()).getWieldPosition();
        int listing=listing(987,2,2,200);Store failing=new Store();failing.failure="equipment-save";
        MarketOperation buy=op(MarketOperation.Kind.BUY,p,listing,0,1,0);run(failing,h,buy);
        check(p.getCarriedItems().getEquipment().get(slot)==sword&&sword.getItemStatus().getDurability()==37,"rollback keeps live equipped object unchanged");
        check(persisted(p.getDatabaseID(),"invitems",66)==1&&persisted(p.getDatabaseID(),"equipped",66)==0,"rollback retains old coherent durable equipment/container state");
        run(failing,h,buy);check(persisted(p.getDatabaseID(),"equipped",66)==1&&persisted(p.getDatabaseID(),"invitems",66)==0,"market includes current equipped ownership");
        h.logout(p);Player next=h.player(p.getUsername(),449,450);next.setDatabaseID(p.getDatabaseID());h.recordOutgoingPackets(next);reloadContainers(h,next);
        Item loaded=next.getCarriedItems().getEquipment().get(slot);
        check(loaded!=null&&loaded.getItemId()==id&&loaded.getItemStatus().getDurability()==37,"ordinary reload preserves exact equipped identity, slot and durability");
        check(next.getCarriedItems().getEquipment().unequipItem(new com.openrsc.server.model.struct.UnequipRequest(next,loaded,com.openrsc.server.model.struct.UnequipRequest.RequestType.FROM_BANK,false),false),"ordinary unequip into bank since last save");
        Item bankItem=next.getBank().get(next.getBank().getFirstIndexById(66));long bankId=bankItem.getItemId();
        run(new Store(),h,op(MarketOperation.Kind.BUY,next,listing,0,1,0));
        check(persisted(next.getDatabaseID(),"equipped",66)==0&&persisted(next.getDatabaseID(),"bank",66)==1,"stale equipped rows do not reject current bank item");
        reloadContainers(h,next);check(next.getBank().get(next.getBank().getFirstIndexById(66)).getItemId()==bankId&&next.getCarriedItems().getEquipment().get(slot)==null,"unequip then market then reload preserves current bank ownership");
    }
    static void saveLockOrder(CurrentCombatHarness h)throws Exception {
        for(String action:Arrays.asList("unequip","equip","deposit-all")) {
            boolean equip=action.equals("equip");
            Player p=player(h);Item sword=equipFromInventory(p);int slot=sword.getDef(h.world()).getWieldPosition();
            if(equip)check(p.getCarriedItems().getEquipment().unequipItem(new com.openrsc.server.model.struct.UnequipRequest(p,sword,com.openrsc.server.model.struct.UnequipRequest.RequestType.FROM_BANK,false),false),"prepare bank equip lock test");
            CountDownLatch holdingEquipment=new CountDownLatch(1),saveEntered=new CountDownLatch(1);
            MySqlGameDatabase db=new MySqlGameDatabase(h.server()) { @Override public boolean queryPlayerExists(int id) {
                saveEntered.countDown();p.getCarriedItems().getEquipment().get(slot);return false;
            }};
            PlayerService service=new PlayerService(h.world(),h.server().getConfig(),db);
            ExecutorService pool=Executors.newFixedThreadPool(2, r->{Thread t=new Thread(r,"market-lock-order-fixture");t.setDaemon(true);return t;});
            Future<Boolean> writer=pool.submit(()->{synchronized(action.equals("deposit-all")?p.getBank().getItems():p.getCarriedItems().getEquipment().getList()){
                holdingEquipment.countDown();check(saveEntered.await(2,TimeUnit.SECONDS),"ordinary save entered while equipment held");
                if(action.equals("deposit-all")){p.getBank().depositAllFromEquipment();return p.getBank().countId(66)==1;}
                if(equip){Item item=p.getBank().get(p.getBank().getFirstIndexById(66));return p.getCarriedItems().getEquipment().equipItem(new com.openrsc.server.model.struct.EquipRequest(p,item,com.openrsc.server.model.struct.EquipRequest.RequestType.FROM_BANK,false),false);}
                return p.getCarriedItems().getEquipment().unequipItem(new com.openrsc.server.model.struct.UnequipRequest(p,sword,com.openrsc.server.model.struct.UnequipRequest.RequestType.FROM_BANK,false),false);
            }});
            check(holdingEquipment.await(2,TimeUnit.SECONDS),"equipment monitor latched");Future<Boolean> saver=pool.submit(()->service.savePlayer(p));
            check(writer.get(4,TimeUnit.SECONDS)&&!saver.get(4,TimeUnit.SECONDS),"bank "+action+" and ordinary save complete without monitor inversion");pool.shutdownNow();
        }
    }
    static void concurrentBuy(CurrentCombatHarness h)throws Exception {
        Player a=player(h),b=player(h);int id=listing(989,1,1,100);
        CountDownLatch staged=new CountDownLatch(2);
        Store s=new Store(){@Override protected void checkpoint(String stage)throws SQLException {
            if(stage.equals("buyer-stage")) { staged.countDown(); try { if(!staged.await(3,TimeUnit.SECONDS))throw new SQLException("fixture staging timeout"); } catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException(e);} }
        }};
        ExecutorService pool=Executors.newFixedThreadPool(2);
        Future<?> x=pool.submit(()->run(s,h,op(MarketOperation.Kind.BUY,a,id,0,1,0)));
        Future<?> y=pool.submit(()->run(s,h,op(MarketOperation.Kind.BUY,b,id,0,1,0)));
        x.get(8,TimeUnit.SECONDS);y.get(8,TimeUnit.SECONDS);pool.shutdownNow();
        check(a.getCarriedItems().getInventory().countId(384)+b.getCarriedItems().getInventory().countId(384)==1,"competing buyers receive one listing once");
        check(persisted(a.getDatabaseID(),"invitems",10)+persisted(b.getDatabaseID(),"invitems",10)==1900&&n("SELECT SUM(item_amount) FROM "+P+"expired_auctions WHERE playerID=989")==100,"competing buyer/seller coin conservation");
    }
    static void gameQueue(CurrentCombatHarness h)throws Exception {
        Player p=player(h);int id=listing(988,1,1,100);
        Field field=com.openrsc.server.model.world.World.class.getDeclaredField("market");field.setAccessible(true);
        field.set(h.world(),new Market(h.world()));
        h.world().getMarket().addBuyAuctionItemTask(p,id,1);
        check(p.getCarriedItems().getInventory().countId(384)==0,"queued request changes nothing before game event");
        h.advanceOneCombatTick();h.advanceOneCombatTick();
        check(p.getCarriedItems().getInventory().countId(384)==1&&persisted(p.getDatabaseID(),"invitems",10)==900,"ordinary game-event task settles durable transaction");
    }
    static final class LoginDatabase extends MySqlGameDatabase {
        final Provider provider;int snapshots;boolean failSnapshot;
        LoginDatabase(Server s,Provider p){super(s);provider=p;}
        @Override public JDBCDatabaseConnection getConnection(){return provider;}
        @Override protected void startTransaction(){try{provider.c.setAutoCommit(false);}catch(SQLException e){throw new RuntimeException(e);}}
        @Override protected void commitTransaction(){try{provider.c.commit();provider.c.setAutoCommit(true);}catch(SQLException e){throw new RuntimeException(e);}}
        @Override protected void rollbackTransaction(){try{provider.c.rollback();provider.c.setAutoCommit(true);}catch(SQLException e){throw new RuntimeException(e);}}
        @Override public PlayerData queryLoadPlayerData(Player p){
            snapshots++;
            if(failSnapshot)throw new IllegalStateException("injected incomplete ordinary snapshot");
            try(PreparedStatement s=provider.c.prepareStatement("SELECT id,username FROM "+P+"players WHERE username LIKE ?")){
                s.setString(1,p.getUsername());try(ResultSet r=s.executeQuery()){
                    if(!r.next())throw new IllegalStateException("missing synthetic profile");
                    PlayerData d=new PlayerData();d.playerId=r.getInt(1);d.username=r.getString(2);d.groupId=10;d.xLocation=449;d.yLocation=450;d.headSprite=1;d.bodySprite=2;d.male=true;d.loginIp="127.0.0.1";return d;
                }
            }catch(SQLException e){throw new RuntimeException(e);}
        }
        @Override public PlayerSkills[] queryLoadPlayerSkills(Player p,boolean max){PlayerSkills[] result=new PlayerSkills[server.getConstants().getSkills().getSkillsCount()];for(int i=0;i<result.length;i++){result[i]=new PlayerSkills();result[i].skillId=i;result[i].skillLevel=40;}return result;}
        @Override public PlayerExperience[] queryLoadPlayerExperience(int id){PlayerExperience[] result=new PlayerExperience[server.getConstants().getSkills().getSkillsCount()];for(int i=0;i<result.length;i++){result[i]=new PlayerExperience();result[i].skillId=i;result[i].experience=40000;}return result;}
        @Override public PlayerExperienceCapped[] queryLoadPlayerExperienceCapped(int id){return new PlayerExperienceCapped[0];}
        @Override public PlayerRecoveryQuestions[] queryPlayerRecoveryChanges(Player p){return new PlayerRecoveryQuestions[0];}
        @Override public PlayerBankPreset[] queryLoadPlayerBankPresets(Player p){return new PlayerBankPreset[0];}
        @Override public PlayerFriend[] queryLoadPlayerFriends(Player p){return new PlayerFriend[0];}
        @Override public PlayerIgnore[] queryLoadPlayerIgnored(Player p){return new PlayerIgnore[0];}
        @Override public PlayerQuest[] queryLoadPlayerQuests(Player p){return new PlayerQuest[0];}
        @Override public PlayerCache[] queryLoadPlayerCache(Player p){return new PlayerCache[0];}
        @Override public PlayerNpcKills[] queryLoadPlayerNpcKills(Player p){return new PlayerNpcKills[0];}
    }
    static final class RecordingLogin extends LoginRequest {
        final CurrentCombatHarness h;int response=-999;Player completed;String redirect;int authCode=LoginResponse.LOGIN_SUCCESSFUL[10];
        RecordingLogin(CurrentCombatHarness h,String name){this(h,name,h.server().getConfig().CLIENT_VERSION);}
        RecordingLogin(CurrentCombatHarness h,String name,int version){super(h.server(),loginChannel(),name,"synthetic-unused",false,version,false,null);this.h=h;}
        @Override public ValidatedLogin validateLogin(){
            // Authentication alone is a synthetic fixture boundary. The actual
            // processInternal, PlayerService and economic provider reads run.
            if(redirect!=null)setUsername(redirect); // model accepted former-name redirect; initial hash remains the alias
            if(authCode==LoginResponse.LOGIN_SUCCESSFUL[10]||authCode==LoginResponse.RECONNECT_SUCCESFUL)getServer().getPacketFilter().addLoggedInPlayer(getIpAddress(),getUsernameHash());
            return new ValidatedLogin(authCode);
        }
        @Override public void loginValidated(int code){check(response==-999,"one login response only");response=code;}
        @Override public void loadingComplete(Player p){completed=p;h.world().getPlayers().add(p);p.updateRegion();}
    }
    static io.netty.channel.Channel loginChannel(){
        return (io.netty.channel.Channel)Proxy.newProxyInstance(io.netty.channel.Channel.class.getClassLoader(),new Class<?>[]{io.netty.channel.Channel.class},(proxy,method,args)->{
            if(method.getName().equals("remoteAddress"))return new java.net.InetSocketAddress("127.0.0.1",12345);
            if(method.getName().equals("isOpen")||method.getName().equals("isActive")||method.getName().equals("isWritable"))return true;
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return args[0]==proxy;
            return null;
        });
    }
    static void loginBarrier(CurrentCombatHarness h)throws Exception {
        GameDatabase originalDatabase=h.server().getDatabase();Object originalService=h.server().getPlayerService();
        boolean auctionEnabled=h.server().getConfig().SPAWN_AUCTION_NPCS;
        try(Provider provider=new Provider()) {
            LoginDatabase db=new LoginDatabase(h.server(),provider);PlayerService service=new PlayerService(h.world(),h.server().getConfig(),db);
            set(h.server(),Server.class,"database",db);set(h.server(),Server.class,"playerService",service);
            for(boolean commit:Arrays.asList(true,false)) {
                Player p=player(h);h.logout(p);p.closePersistenceSession();int snapshotsBefore=db.snapshots;
                sql("UPDATE "+P+"players SET username='aUcTiOn"+p.getDatabaseID()+"' WHERE id="+p.getDatabaseID());
                Connection pending=open();pending.setAutoCommit(false);
                try(PreparedStatement s=pending.prepareStatement("UPDATE "+P+"players SET username=username WHERE id=?")){s.setInt(1,p.getDatabaseID());s.executeUpdate();}
                try(PreparedStatement s=pending.prepareStatement("UPDATE "+P+"itemstatuses SET amount=900 WHERE itemID IN (SELECT itemID FROM "+P+"invitems WHERE playerID=?) AND catalogID=10")){s.setInt(1,p.getDatabaseID());s.executeUpdate();}
                try(PreparedStatement s=pending.prepareStatement("INSERT INTO "+P+"market_operations VALUES(?,?,?,?,?)")){s.setString(1,UUID.randomUUID().toString());s.setInt(2,p.getDatabaseID());s.setString(3,"BUY");s.setString(4,"held-login-fixture");s.setLong(5,0);s.executeUpdate();}
                // A plain read really can see old coins while remote settlement owns its locks.
                check(persisted(p.getDatabaseID(),"invitems",10)==1000,"unsettled transaction exposes old state to plain reads");
                h.server().getConfig().SPAWN_AUCTION_NPCS=false;
                RecordingLogin blocked=new RecordingLogin(h,p.getUsername().toUpperCase(Locale.ROOT));
                long start=System.nanoTime();blocked.process();
                check(blocked.response==LoginResponse.LOGINSERVER_OFFLINE&&blocked.completed==null,"pending settlement denies login before success");
                check(db.snapshots==snapshotsBefore&&h.world().getPlayer(p.getUsernameHash())==null,"barrier timeout creates no snapshot or spendable actor");
                check(h.server().getPacketFilter().getPlayersCount("127.0.0.1")==0,"denied login releases validation reservation");
                check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<7,"login settlement barrier bounded");
                if(commit)pending.commit();else pending.rollback();pending.close();
                RecordingLogin settled=new RecordingLogin(h,p.getUsername().toUpperCase(Locale.ROOT));settled.process();h.advanceOneCombatTick();h.advanceOneCombatTick();
                check(settled.isLoginSuccessful(settled.response)&&settled.completed!=null,"settled current actor row permits ordinary LoginRequest load");
                check(settled.completed.getCarriedItems().getInventory().countId(10)==(commit?900:1000),"fresh login snapshot reflects exact commit or rollback outcome");
                check(settled.completed.getUsername().equals("aUcTiOn"+p.getDatabaseID()),"stored mixed-case account survives ordinary case-insensitive lookup");
                h.server().getPacketFilter().removeLoggedInPlayer("127.0.0.1",p.getUsernameHash());h.logout(settled.completed);
            }
            Player redirected=player(h);h.logout(redirected);
            RecordingLogin alias=new RecordingLogin(h,"formername");alias.redirect=redirected.getUsername();long oldHash=alias.getUsernameHash();alias.process();h.advanceOneCombatTick();h.advanceOneCombatTick();
            check(alias.completed!=null&&alias.completed.getDatabaseID()==redirected.getDatabaseID()&&oldHash!=alias.completed.getUsernameHash(),"accepted renamed-account redirect settles final username instead of initial alias hash");
            h.server().getPacketFilter().removeLoggedInPlayer("127.0.0.1",alias.getUsernameHash());h.logout(alias.completed);
            Player denied=player(h);h.logout(denied);int authSnapshots=db.snapshots;
            for(int version:new int[]{204,h.server().getConfig().CLIENT_VERSION})for(int code:new int[]{0,3,8,16,100}){
                RecordingLogin denial=new RecordingLogin(h,denied.getUsername(),version);denial.authCode=code;denial.process();
                check(db.snapshots==authSnapshots&&denial.completed==null&&h.world().getPlayer(denied.getUsernameHash())==null,"modern validation denial never loads/activates classic or modern client");
            }
            RecordingLogin legacy=new RecordingLogin(h,denied.getUsername(),204);legacy.process();h.advanceOneCombatTick();h.advanceOneCombatTick();
            check(legacy.completed!=null&&legacy.completed.getDatabaseID()==denied.getDatabaseID(),"authenticated classic client still loads before legacy wire success");
            h.server().getPacketFilter().removeLoggedInPlayer("127.0.0.1",denied.getUsernameHash());h.logout(legacy.completed);
            Player p=player(h);h.logout(p);sql("DROP TABLE "+P+"market_operations");
            db.failSnapshot=true;RecordingLogin incomplete=new RecordingLogin(h,p.getUsername());incomplete.process();
            check(incomplete.response==LoginResponse.LOGINSERVER_OFFLINE&&incomplete.completed==null,"atomically false rejects partial profile without success response");
            db.failSnapshot=false;RecordingLogin clean=new RecordingLogin(h,p.getUsername().toUpperCase(Locale.ROOT));clean.process();h.advanceOneCombatTick();h.advanceOneCombatTick();
            check(clean.completed!=null&&clean.completed.getCarriedItems().getInventory().countId(10)==1000,"disabled auction profile can load after incomplete-snapshot rollback");
            String tables=mysql?"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='qa_market_operations'":"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='qa_market_operations'";
            check(n(tables)==0,"login barrier does not create receipt schema");
            h.server().getPacketFilter().removeLoggedInPlayer("127.0.0.1",p.getUsernameHash());h.logout(clean.completed);
        }finally{h.server().getConfig().SPAWN_AUCTION_NPCS=auctionEnabled;set(h.server(),Server.class,"database",originalDatabase);set(h.server(),Server.class,"playerService",originalService);}
    }
    static void providerConnections(CurrentCombatHarness h)throws Exception {
        com.openrsc.server.ServerConfiguration cfg=h.server().getConfig();
        String oldName=cfg.DB_NAME,oldHost=cfg.DB_HOST,oldUser=cfg.DB_USER,oldPass=cfg.DB_PASS;
        try {
            JDBCDatabaseConnection factory;
            if(mysql){
                cfg.DB_HOST="127.0.0.1:"+endpointPort;cfg.DB_NAME="auction_test";cfg.DB_USER=user;cfg.DB_PASS=password;
                factory=new com.openrsc.server.database.impl.mysql.MySQLDatabaseConnection(h.server());
            }else{
                java.nio.file.Path directory=java.nio.file.Paths.get("inc/sqlite");java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.copy(java.nio.file.Paths.get(url.substring("jdbc:sqlite:".length())),directory.resolve("market-provider-check.db"));
                cfg.DB_NAME="market-provider-check";
                factory=new com.openrsc.server.database.impl.sqlite.SqliteGameDatabaseConnection(h.server());
            }
            try(Connection a=factory.openIsolatedConnection();Connection b=factory.openIsolatedConnection()){
                check(a!=b,"configured production provider gives distinct physical connections");
                a.setAutoCommit(false);try(Statement s=a.createStatement()){s.executeUpdate("INSERT INTO "+P+"players VALUES(777777,'isolation')");}
                try(Statement s=b.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+P+"players WHERE id=777777")){r.next();check(r.getInt(1)==0,"provider connections do not share uncommitted state");}
                a.rollback();
            }
            if(!mysql){cfg.DB_NAME="missing-market-provider";boolean denied=false;try{factory.openIsolatedConnection();}catch(SQLException expected){denied=true;}
                check(denied&&!java.nio.file.Files.exists(java.nio.file.Paths.get("inc/sqlite/missing-market-provider.db")),"missing configured SQLite file fails without creating replacement database");}
        }finally{cfg.DB_NAME=oldName;cfg.DB_HOST=oldHost;cfg.DB_USER=oldUser;cfg.DB_PASS=oldPass;}
    }
    static void legacyStorage(CurrentCombatHarness h)throws Exception {
        Player p=player(h);int id=listing(986,1,1,100);sql("DROP TABLE "+P+"equipped");
        MarketJdbcStore store=new MarketJdbcStore(MarketAtomicityFixture::open,P,false);
        new MarketTransactions(h.world(),store).execute(op(MarketOperation.Kind.BUY,p,id,0,1,0));
        check(p.getCarriedItems().getInventory().countId(384)==1&&persisted(p.getDatabaseID(),"invitems",10)==900,"legacy inventory world works without optional equipped table");
        sql("CREATE TABLE "+P+"equipped(playerID INTEGER,itemID BIGINT)");
    }
    static void engineSafety(CurrentCombatHarness h)throws Exception {
        Player p=player(h);int id=listing(990,1,1,100);
        h.server().getConfig().WANT_THREADING__BREAK_PID_PRIORITY=true;
        run(new Store(),h,op(MarketOperation.Kind.BUY,p,id,0,1,0));
        h.server().getConfig().WANT_THREADING__BREAK_PID_PRIORITY=false;
        check(p.getCarriedItems().getInventory().countId(10)==1000,"experimental concurrent events refuse auction");
        if(mysql){
            sql("ALTER TABLE "+P+"expired_auctions ENGINE=MyISAM");
            run(new Store(),h,op(MarketOperation.Kind.BUY,p,id,0,1,0));
            check(p.getCarriedItems().getInventory().countId(10)==1000&&n("SELECT amount_left FROM "+P+"auctions WHERE auctionID="+id)==1,"nontransactional table refused before mutations");
            sql("ALTER TABLE "+P+"expired_auctions ENGINE=InnoDB");
        }
    }
    static int groundCount(CurrentCombatHarness h,Player p)throws Exception {
        Object region=h.world().getRegionManager().getRegion(p.getLocation());
        Method m=region.getClass().getDeclaredMethod("getGroundItems");m.setAccessible(true);
        return ((java.util.Collection<?>)m.invoke(region)).size();
    }
    static void admission(CurrentCombatHarness h)throws Exception{
        Player p=player(h);Inventory inv=p.getCarriedItems().getInventory();
        check(inv.add(new Item(384,2,true),false),"seed matching noted stack");
        while(inv.getItems().size()<inv.getCapacity())check(inv.add(new Item(18,1),false),"fill inventory");
        // Bank is also full, including a max-sized matching stack. A one-item purchase is unnoted.
        for(int i=0;i<h.world().getMaxBankSize();i++)p.getBank().getItems().add(new Item(18,1,false,1000000L+i));
        persist(p);int grounds=groundCount(h,p),id=listing(993,1,1,100);run(new Store(),h,op(MarketOperation.Kind.BUY,p,id,0,1,0));
        check(inv.countId(384,Optional.empty())==2&&inv.countId(10)==1000,"full noted stack cannot admit one unnoted delivery");
        check(groundCount(h,p)==grounds,"staging creates zero ground spills");
        check(n("SELECT amount_left FROM "+P+"auctions WHERE auctionID="+id)==1,"full containers preserve listing");
        Player over=player(h);over.getBank().getItems().add(new Item(384,Integer.MAX_VALUE,false,2000000L));persist(over);
        sql("INSERT INTO "+P+"expired_auctions(playerID,item_id,item_amount,time,explanation) VALUES("+over.getDatabaseID()+",384,1,0,'overflow')");
        run(new Store(),h,op(MarketOperation.Kind.COLLECT,over,0,0,0,0));
        check(over.getBank().getItems().stream().filter(x->x.getCatalogId()==384).mapToLong(Item::getAmount).sum()==2147483648L,"bank overflow uses separate valid stack without wrap");
    }
    static void lockResolution()throws Exception{
        Store store=new Store();MarketOperation op=MarketOperation.expiry(700000);
        store.transact(MarketOperation.expiry(700001),c->{}); // schema ready before lock
        Connection original=open();original.setAutoCommit(false);
        try(PreparedStatement s=original.prepareStatement("INSERT INTO "+P+"market_operations VALUES(?,?,?,?,?)")){
            s.setString(1,op.id.toString());s.setInt(2,0);s.setString(3,"EXPIRE");s.setString(4,op.requestKey());s.setLong(5,0);s.executeUpdate();
        }
        // Force the transaction body's connection to fail; the resolver must wait
        // for the original unacknowledged COMMIT rather than trust a missing row.
        Store resolver=store;CountDownLatch started=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
        Future<Boolean> f=pool.submit(()->{started.countDown();return resolver.transact(op,c->{throw new SQLException("simulate lost original response");});});
        started.await();Thread.sleep(250);check(!f.isDone(),"resolver waits behind original receipt transaction");
        original.commit();original.close();check(f.get(8,TimeUnit.SECONDS),"resolver finds durable receipt after original commit");pool.shutdownNow();
        MarketOperation blocked=MarketOperation.expiry(700002);Connection holder=open();holder.setAutoCommit(false);
        try(PreparedStatement s=holder.prepareStatement("INSERT INTO "+P+"market_operations VALUES(?,?,?,?,?)")){
            s.setString(1,blocked.id.toString());s.setInt(2,0);s.setString(3,"EXPIRE");s.setString(4,blocked.requestKey());s.setLong(5,0);s.executeUpdate();
        }
        long start=System.nanoTime();boolean unresolved=false;
        try{store.transact(blocked,c->{throw new SQLException("original transaction outstanding");});}catch(MarketJdbcStore.UnresolvedCommit expected){unresolved=true;}
        check(unresolved&&TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<8,"blocked resolver times out and fails closed");
        holder.rollback();holder.close();
    }
    public static void main(String[] args)throws Exception{
        url=System.getenv("MARKET_TEST_JDBC");user=System.getenv("MARKET_TEST_USER");password=System.getenv("MARKET_TEST_PASSWORD");
        if(url==null)throw new IllegalArgumentException("Explicit disposable MARKET_TEST_JDBC required");mysql=url.startsWith("jdbc:mysql:");endpointPort=mysql?Integer.parseInt(System.getenv("MARKET_TEST_PORT")):0;
        if(mysql&&!url.startsWith("jdbc:mysql://127.0.0.1:"+endpointPort+"/auction_test?"))throw new IllegalArgumentException("Only assigned disposable MySQL endpoint permitted");
        System.setSecurityManager(new SecurityManager(){public void checkPermission(Permission p){} public void checkListen(int port){throw new AssertionError("Listeners forbidden");}public void checkConnect(String host,int port){if(mysql&&host.equals("127.0.0.1")&&(port==endpointPort||port==-1))return;if(port<0)throw new SecurityException("No hostname lookup");throw new AssertionError("Unassigned network access "+host+":"+port);}});
        String mode=args.length==0?"test":args[0];
        if(!mode.startsWith("recover"))schema();
        try(CurrentCombatHarness h=new CurrentCombatHarness();Provider provider=new Provider()){
            installDatabase(h,provider);
            if(mode.startsWith("crash")){
                Player p=player(h);equipFromInventory(p);int id=listing(999,1,1,100);Store s=new Store();s.failure=mode.endsWith("before")?"before-commit":"after-commit";s.halt=true;run(s,h,op(MarketOperation.Kind.BUY,p,id,0,1,0));throw new AssertionError("halt missing");
            }else if(mode.startsWith("recover")){
                h.server().getConfig().SPAWN_AUCTION_NPCS=false;
                check(com.openrsc.server.service.AccountLoadBarrier.await(h.server().getDatabase(),P,"Auction101")==101,"fresh JVM login barrier settles actor without process-local flags");
                Player p=h.player("auction101",449,450);p.setDatabaseID(101);reloadContainers(h,p);
                boolean before=mode.endsWith("before");
                check(p.getCarriedItems().getInventory().countId(10)==(before?1000:900)&&p.getCarriedItems().getInventory().countId(384,Optional.empty())==(before?0:1),"fresh process provider reload after crash");
                check(n("SELECT COUNT(*) FROM "+P+"market_operations")== (before?0:1)&&n("SELECT COALESCE(SUM(item_amount),0) FROM "+P+"expired_auctions WHERE playerID=999")==(before?0:100),"crash receipt and seller payout atomic");
                check(persisted(101,before?"invitems":"equipped",66)==1&&equipment(p).size()==(before?0:1),"crash keeps coherent equipment ownership since last save");
            }else{scenarios(h);lockResolution();}
        }
        System.out.println("PASS market "+(mysql?"MySQL/MariaDB":"SQLite")+" "+mode+": "+checks+" assertions; no listeners or account databases");
    }
}
