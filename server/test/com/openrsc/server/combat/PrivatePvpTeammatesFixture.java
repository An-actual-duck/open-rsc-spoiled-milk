package com.openrsc.server.combat;

import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.json.JSONObject;

/** Exercises the exact private controller without starting sockets or loading accounts. */
public final class PrivatePvpTeammatesFixture {
    private static Class<?> controller;
    private static int checks;

    private static Field field(String name) throws Exception {
        Field field=controller.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static Object call(String name,Class<?>[] parameters,Object... arguments) throws Exception {
        Method method=controller.getDeclaredMethod(name,parameters);method.setAccessible(true);return method.invoke(null,arguments);
    }
    private static void check(boolean value,String message) {
        checks++;if(!value)throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path output=Paths.get(args[0]).toAbsolutePath();Files.createDirectories(output.resolve("test-support"));
        controller=Class.forName("PrivatePvpServer");
        try(CurrentCombatHarness harness=new CurrentCombatHarness()) {
            harness.server().getConfig().WANT_PVP=true;
            harness.openCombatProjectileRectangle(176,184,196,204);
            field("server").set(null,harness.server());field("root").set(null,output);
            call("validateKits",new Class<?>[0]);
            check(field("teammates").getBoolean(null),"premade teammates are the default");
            Player human=(Player)call("bot",new Class<?>[]{String.class,int.class,int.class,int.class},"Team Tester",180,200,0);
            List<Player> bots=(List<Player>)field("bots").get(null);bots.remove(human);
            human.setAttribute("private_pvp_opponent",false);field("human").set(null,human);field("validated").setBoolean(null,true);
            for(String kit:new String[]{"melee","ranged","magic","cleric"}) {
                call("command",new Class<?>[]{String.class},"preset-"+kit);
                call("validateTeams",new Class<?>[0]);call("writeStatus",new Class<?>[0]);
                JSONObject status=new JSONObject(new String(Files.readAllBytes(output.resolve("test-support/status.json")),"UTF-8"));
                check(status.getBoolean("teammates"),kit+" keeps teammate mode");
                check(status.getJSONArray("opponents").getJSONObject(0).getString("role").equals("Enemy"),"Red is the enemy");
                check(status.getJSONArray("opponents").getJSONObject(1).getString("role").equals("Party teammate"),"Blue party role is shown");
                check(status.getJSONArray("opponents").getJSONObject(2).getString("role").equals("Clan teammate"),"Gold clan role is shown");
                check(human.getParty().getPlayer(bots.get(1).getUsername()).getPlayerReference()==bots.get(1),"party roster points at the registered teammate");
                check(human.getClan().getPlayer(bots.get(2).getUsername()).getPlayerReference()==bots.get(2),"clan roster points at the registered teammate");
            }
            call("command",new Class<?>[]{String.class},"attack-magic");call("tick",new Class<?>[0]);
            check(bots.get(0).getMagicCombatEvent()!=null && bots.get(0).getMagicCombatEvent().getTarget()==human,"enemy control starts normal magic combat against the user");
            check(!bots.get(1).hasOutgoingAttack() && !bots.get(2).hasOutgoingAttack(),"teammates never attack the user");
            call("command",new Class<?>[]{String.class},"no-allies");
            check(!field("teammates").getBoolean(null) && human.getParty()==null && human.getClan()==null,"all-enemy control removes teammate membership");
            call("command",new Class<?>[]{String.class},"preset-melee");
            check(!field("teammates").getBoolean(null) && human.getParty()==null,"explicit all-enemy mode survives a kit change");
            call("command",new Class<?>[]{String.class},"friend-blue");
            Player blue=bots.get(1),red=bots.get(0);
            check(human.getSocial().isFriendsWith(blue.getUsernameHash()) && !blue.getSocial().isFriendsWith(human.getUsernameHash()),"one-way friend control changes only your list");
            check(!com.openrsc.server.util.rsc.CombatEffectUtil.isPlayerOwnedPvpSplashTarget(human,red,blue)
                && com.openrsc.server.util.rsc.CombatEffectUtil.isPlayerOwnedPvpSplashTarget(blue,red,human),"own friendship excludes outward splash without reverse immunity");
            call("command",new Class<?>[]{String.class},"blue-friends-you");
            check(!human.getSocial().isFriendsWith(blue.getUsernameHash()) && blue.getSocial().isFriendsWith(human.getUsernameHash()),"reverse friend control changes only Blue's list");
            check(com.openrsc.server.util.rsc.CombatEffectUtil.isPlayerOwnedPvpSplashTarget(human,red,blue)
                && !com.openrsc.server.util.rsc.CombatEffectUtil.isPlayerOwnedPvpSplashTarget(blue,red,human),"reverse-only friendship cannot shield Blue from your splash");
            call("command",new Class<?>[]{String.class},"no-friends");
            check(!human.getSocial().isFriendsWith(blue.getUsernameHash()) && !blue.getSocial().isFriendsWith(human.getUsernameHash()),"friend controls cleanly restore both lists");
            call("command",new Class<?>[]{String.class},"allies");call("validateTeams",new Class<?>[0]);
            call("command",new Class<?>[]{String.class},"reset");call("validateTeams",new Class<?>[0]);
            check(field("teammates").getBoolean(null),"teammate mode survives reset");
            call("pause",new Class<?>[0]);call("removeBots",new Class<?>[0]);
            check(human.getParty()==null && human.getClan()==null,"scene cleanup detaches test memberships");
            harness.logout(human);
        }
        System.out.println("PASS "+checks+" private PvP teammate checks, four roster/splash validations and a real party blessing");
    }
}
