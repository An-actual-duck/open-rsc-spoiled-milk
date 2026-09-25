package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

/** One session-bound antidote tier; stronger replaces, weaker cannot extend it. */
public final class AntidoteCleansing {
 public static final int GIANT_SPIDER_EGGS=3400, JUNGLE_SPIDER_EGGS=3401;
 public static final int WEAK_FULL=3402, WEAK_TWO=3403, WEAK_ONE=3404;
 public static final int STRONG_FULL=3405, STRONG_TWO=3406, STRONG_ONE=3407;
 public static final long DURATION_MILLIS=600000L;
 private static final String POWER="antidote_cleanse_power", EXPIRY="antidote_cleanse_expiry", ICON="antidote_cleanse_item";
 private AntidoteCleansing() {}
 public static int powerForItem(int id) {
  if(id>=WEAK_FULL&&id<=WEAK_ONE)return 5;
  if(id>=1474&&id<=1476)return 10;
  return id>=STRONG_FULL&&id<=STRONG_ONE?20:0;
 }
 public static int doses(int id) {
  if(id>=WEAK_FULL&&id<=WEAK_ONE)return WEAK_ONE-id+1;
  if(id>=1474&&id<=1476)return 1477-id;
  return id>=STRONG_FULL&&id<=STRONG_ONE?STRONG_ONE-id+1:0;
 }
 public static long remainingMillis(Player p) {
  return Math.max(0L,p.getAttribute(EXPIRY,0L)-System.currentTimeMillis());
 }
 public static int bonus(Player p) {return remainingMillis(p)>0?p.getAttribute(POWER,0):0;}
 public static int itemId(Player p) {return p.getAttribute(ICON,-1);}
 public static boolean canActivate(Player p,int id) {return powerForItem(id)>0&&powerForItem(id)>=bonus(p);}
 public static boolean activate(Player p,int id) {
  if(!canActivate(p,id))return false;
  p.setAttribute(POWER,powerForItem(id));p.setAttribute(EXPIRY,System.currentTimeMillis()+DURATION_MILLIS);p.setAttribute(ICON,id);
  return true;
 }
}
