package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.model.entity.player.Player;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes production shop transactions with a deterministic item-grant boundary. */
final class CurrentMonsterSlayerShopRuntimeCharacterization {
	static void runtimeTransactionsAreAtomic(CurrentCombatHarness h) throws Exception {
		MonsterSlayerData data = data(); AtomicInteger grants = new AtomicInteger();
		MonsterSlayerShopService shops = new MonsterSlayerShopService(data, new MonsterSlayerShopService.ItemGrant(){ public boolean grant(Player p,int id,int n){ grants.incrementAndGet(); return true; }});
		Player p = h.player("msshoptx", 780, 790); state(p,data,40L,0);
		assertTrue(shops.redeem(p,"falador","falador.brawn",2).isSuccessful(),"real redeem");
		assertEquals(1,grants.get(),"one output grant"); assertEquals(8,shops.getStock("falador.brawn"),"stock decremented");
		assertEquals(36L,MonsterSlayerState.read(p.getCache(),data).getBalances().get(MonsterSlayerChallenge.FLEDGLING),"exact point deduction");
		shops.restock(); assertEquals(9,shops.getStock("falador.brawn"),"bounded one-step restock");
		assertTrue(shops.purchaseCapacity(p,"falador").isSuccessful(),"capacity purchase");
		assertFalse(shops.purchaseCapacity(p,"falador").isSuccessful(),"duplicate capacity");
		assertEquals(31,MonsterSlayerState.read(p.getCache(),data).getDerivedInventoryCapacity(),"persisted capacity only");
		Player failed = h.player("msshopfail", 790, 790); state(failed,data,40L,0); failed.getCache().store("unrelated","keep");
		MonsterSlayerShopService rejecting = new MonsterSlayerShopService(data,new MonsterSlayerShopService.ItemGrant(){public boolean grant(Player q,int id,int n){return false;}});
		Map<String,Object> before=new LinkedHashMap<String,Object>(failed.getCache().getCacheMap());
		assertFalse(rejecting.redeem(failed,"falador","falador.brawn",1).isSuccessful(),"grant failure");
		assertEquals(before,failed.getCache().getCacheMap(),"grant rollback preserves cache"); assertEquals(10,rejecting.getStock("falador.brawn"),"grant rollback preserves stock");
		assertFalse(shops.redeem(p,"falador","falador.brawn",Long.MAX_VALUE).isSuccessful(),"quantity overflow");
		for (int tier = 0; tier < 6; tier++) {
			MonsterSlayerDefinitions.Shop shop = data.getShops().get(tier);
			Player tierPlayer = h.player("mss" + tier, 800 + tier, 790);
			state(tierPlayer, data, 1000L, prefixMask(tier), tier);
			String reward = shop.getCategories().get(0).getRewards().get(0).getKey();
			assertTrue(shops.redeem(tierPlayer, shop.getKey(), reward, 1).isSuccessful(),
				"runtime redemption tier " + tier);
		}
	}
	private static MonsterSlayerData data(){return MonsterSlayerData.load(Paths.get("conf","server","defs","extras","MonsterSlayer.json"),new MonsterSlayerData.ReferenceCatalog(){public boolean npcExists(int i){return true;}public boolean npcAttackable(int i){return true;}public boolean npcSpawned(int i){return true;}public boolean itemExists(int i){return true;}});}
	private static int prefixMask(int tier){return tier == 0 ? 0 : (1 << tier) - 1;}
	private static void state(Player p,MonsterSlayerData d,long points,int mask){state(p,d,points,mask,0);}
	private static void state(Player p,MonsterSlayerData d,long points,int mask,int tier){Map<MonsterSlayerChallenge,Long>a=new LinkedHashMap<MonsterSlayerChallenge,Long>();for(MonsterSlayerChallenge x:MonsterSlayerChallenge.values())a.put(x,points);Map<String,Integer>c=new LinkedHashMap<String,Integer>();int i=0;for(MonsterSlayerDefinitions.Contact x:d.getContactsInChallengeOrder()){c.put(x.getKey(),i++<tier?x.getMandatoryTasks().size():0);}MonsterSlayerState.write(p.getCache(),d,MonsterSlayerState.create(2,MonsterSlayerRank.fromCode(tier+1),MonsterSlayerBalances.of(a),c,null,0,0L,mask,1,MonsterSlayerState.LegacyStatus.NONE,0,d));}
	private static void assertTrue(boolean v,String m){if(!v)throw new AssertionError(m);}private static void assertFalse(boolean v,String m){assertTrue(!v,m);}private static void assertEquals(long a,long b,String m){if(a!=b)throw new AssertionError(m);}private static void assertEquals(int a,int b,String m){if(a!=b)throw new AssertionError(m);}private static void assertEquals(Object a,Object b,String m){if(a==null?b!=null:!a.equals(b))throw new AssertionError(m);}
}
