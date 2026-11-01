package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.client.model.Sprite;
import com.openrsc.interfaces.misc.ClericSpellGuideCatalog;
import com.openrsc.interfaces.misc.SkillGuideInterface;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.MudClientGraphics;

/** Actual spellbook/guide draw, viewport, navigation and input checks; no login. */
public final class ClericSpellbookUiFixture {
  static int checks;
  static int maximumBottom;
  static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
  static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);
  }
  static void set(mudclient mc,String name,Object value)throws Exception {ClericThreeTierClientFixture.set(mc,name,value);}
  static mudclient client(Path root,List<ClericSpellDef> defs,int width,int height)throws Exception {
    mudclient mc=(mudclient)ClericThreeTierClientFixture.unsafe().allocateInstance(mudclient.class);
    MudClientGraphics surface=new MudClientGraphics(width,height+12,5000);surface.fillSpriteTree();mc.setSurface(surface);
    ClericSpellbookCatalog book=new ClericSpellbookCatalog();book.replace(ClericBlessingContract.SCHEMA_VERSION,defs);
    set(mc,"clericSpellbook",book);set(mc,"clericIconSprites",new HashMap<Integer,Sprite>());
    set(mc,"externalAssetLoader",new ClientExternalAssetLoader(root,ClericSpellbookUiFixture.class));
    set(mc,"spellIconSprites",new Sprite[300]);
    set(mc,"gameWidth",width);set(mc,"gameHeight",height);
    int[] stats=new int[30];Arrays.fill(stats,60);set(mc,"playerStatBase",stats);set(mc,"playerStatCurrent",stats.clone());
    set(mc,"playerStatEquipment",new int[8]);mc.equippedItems=new ItemDef[16];
    mc.hiscoreData=new com.openrsc.interfaces.misc.HiscoreData();
    set(mc,"selectedSpell",-1);set(mc,"autoCastSpell",-1);set(mc,"lastSelectedSpell",-1);
    Panel panel=new Panel(surface,5);set(mc,"panelMagic",panel);
    set(mc,"controlMagicPanel",panel.addScrollingList(width-199,78,196,90,500,1,true));
    mc.showUiTab=Config.MAGIC_AND_PRAYER_TAB;
    call(mc,"loadExternalClericIconSprites",new Class<?>[]{});
    return mc;
  }
  static void bounded(Renderer2DFrame frame,int left,int top,int bottom) {
    check(frame.getTextCommands().length>0,"real text commands captured");
    for(Renderer2DFrame.TextCommand command:frame.getTextCommands())
      for(Renderer2DFrame.TextCommand.GlyphCommand glyph:command.getGlyphs()) {
        maximumBottom=Math.max(maximumBottom,glyph.getY()+glyph.getHeight());
        check(glyph.getX()>=left&&glyph.getX()+glyph.getWidth()<=left+196,"text remains inside abilities width");
        check(glyph.getY()>=top&&glyph.getY()+glyph.getHeight()<bottom,"text and cost counts fit expanded panel");
      }
    for(Renderer2DFrame.SpriteCommand command:frame.getSpriteCommands()) {
      maximumBottom=Math.max(maximumBottom,command.getY()+command.getHeight());
      check(command.getX()>=left&&command.getX()+command.getWidth()<=left+196,"cost icons remain inside abilities width");
      check(command.getY()>=top&&command.getY()+command.getHeight()<bottom,"cost icons fit expanded panel");
    }
  }
  public static void main(String[] args)throws Exception {
    Path root=Paths.get(args[0]),out=Paths.get(args[2]);Files.createDirectories(out);
    Config.F_CACHE_DIR=root.resolve("Client_Base/Cache").toString();Config.S_WANT_CUSTOM_SPRITES=true;
    Config.C_SIDE_MENU_OVERLAY=true;Config.S_SIDE_MENU_TOGGLE=true;
    EntityHandler.load(true);ClericThreeTierClientRenderFixture.fonts(root);
    List<ClericSpellDef> defs=ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[1])));
    check(defs.size()==17,"active roster unchanged");
    for(boolean custom:new boolean[]{false,true})for(int[] size:new int[][]{{512,334},{960,528}}) {
      Config.C_CUSTOM_UI=custom;mudclient mc=client(root,defs,size[0],size[1]);
      MudClientGraphics surface=(MudClientGraphics)mc.getSurface();int left=size[0]-199;
      int top=custom?size[1]-42-280:36,detail=top+132,bottom=top+280;
      check(top>=0&&bottom<=size[1],"abilities fits minimum viewport in both UI modes");
      for(ClericSpellDef d:defs) {
        Arrays.fill(surface.pixelData,0x22262b);surface.beginRenderer2DFrame();
        surface.drawBoxAlpha(left,detail,196,148,0x989898,128);
        call(mc,"drawClericTooltip",new Class<?>[]{int.class,int.class,int.class,int.class},d.getStableCode(),left,detail,196);
        bounded(surface.consumeRenderer2DFrame(),left,detail,bottom);
        check(ClericSpellSummary.describe(d).indexOf("Holy Power")<0,"book does not explain power formulas");
        if(d.getStableCode()==12||d.getStableCode()==17||d.getStableCode()==27)
          ClericThreeTierClientRenderFixture.png(surface,out.resolve("compact-"+custom+"-"+size[0]+"-"+d.getStableCode()+".png"));
      }
      maximumBottom=0;
      for(int index=0;index<EntityHandler.spellCount();index++) {
        surface.beginRenderer2DFrame();
        call(mc,"drawMagicTooltip",new Class<?>[]{int.class,int.class,int.class,int.class},index,left,detail,196);
        bounded(surface.consumeRenderer2DFrame(),left,detail,bottom);
      }
      System.out.println("Magic details maximum bottom "+(maximumBottom-top)+"px from panel top");
      SpellbookLayoutSettings.setMode(SpellbookLayoutSettings.Mode.ICONS);
      set(mc,"spellsSubtab",1);mc.mouseX=left+35;mc.mouseY=top+53;
      Arrays.fill(surface.pixelData,0x22262b);
      call(mc,"drawUiTabMagic",new Class<?>[]{boolean.class,byte.class},false,(byte)-74);
      ClericThreeTierClientRenderFixture.png(surface,out.resolve("abilities-cleric-"+custom+"-"+size[0]+".png"));
      set(mc,"spellsSubtab",0);
      Arrays.fill(surface.pixelData,0x22262b);
      call(mc,"drawUiTabMagic",new Class<?>[]{boolean.class,byte.class},false,(byte)-74);
      ClericThreeTierClientRenderFixture.png(surface,out.resolve("abilities-mage-"+custom+"-"+size[0]+".png"));
      check(mc.isMouseOverOpenUiTabPanel(left+20,bottom-2),"expanded details keep menu open");
      check(!mc.isMouseOverOpenUiTabPanel(left+20,bottom+1),"space below menu belongs to world");
      mc.mouseX=left+20;mc.mouseY=bottom-4;set(mc,"mouseButtonClick",1);
      call(mc,"drawUiTabMagic",new Class<?>[]{boolean.class,byte.class},true,(byte)-74);
      check((Integer)ClericThreeTierClientFixture.get(mc,"mouseButtonClick")==0,"expanded details consume click instead of walking");
    }
    for(boolean custom:new boolean[]{false,true})for(int height:new int[]{334,528}) {
      Config.C_CUSTOM_UI=custom;osConfig.F_ANDROID_BUILD=true;
      mudclient mobile=client(root,defs,512,height);set(mobile,"lastSelectedSpell",0);
      int top=(Integer)call(mobile,"getAbilitiesPanelTop",new Class<?>[]{});
      int panelHeight=(Integer)call(mobile,"getAbilitiesPanelHeight",new Class<?>[]{});
      int extra=(Integer)call(mobile,"getAbilitiesExtraHeight",new Class<?>[]{});
      check(extra==69&&top>=0&&top+panelHeight+extra<=height,"Android last-spell box fits viewport");
      MudClientGraphics surface=(MudClientGraphics)mobile.getSurface();
      for(int index=0;index<EntityHandler.spellCount();index++) {
        surface.beginRenderer2DFrame();
        call(mobile,"drawMagicTooltip",new Class<?>[]{int.class,int.class,int.class,int.class},index,313,top+132,196);
        bounded(surface.consumeRenderer2DFrame(),313,top+132,top+panelHeight);
      }
      mobile.mouseX=333;mobile.mouseY=top+52;
      Arrays.fill(surface.pixelData,0x22262b);surface.beginRenderer2DFrame();
      call(mobile,"drawUiTabMagic",new Class<?>[]{boolean.class,byte.class},false,(byte)-74);
      for(Renderer2DFrame.TextCommand command:surface.consumeRenderer2DFrame().getTextCommands())
        for(Renderer2DFrame.TextCommand.GlyphCommand glyph:command.getGlyphs())
          check(glyph.getY()>=top&&glyph.getY()+glyph.getHeight()<=top+panelHeight+extra,"Android footer text stays on screen");
      check(mobile.isMouseOverOpenUiTabPanel(333,top+panelHeight+extra-2),"Android footer remains interactive");
      ClericThreeTierClientRenderFixture.png(surface,out.resolve("abilities-android-"+custom+"-"+height+".png"));
    }
    osConfig.F_ANDROID_BUILD=false;Config.C_CUSTOM_UI=false;mudclient mc=client(root,defs,512,334);
    SkillGuideInterface guide=new SkillGuideInterface(mc);set(mc,"skillGuideInterface",guide);
    call(mc,"setSkillGuideChosen",new Class<?>[]{String.class},"Worship");
    guide.curTab=mc.skillGuideChosenTabs.indexOf("Spells");guide.reposition();guide.drawSkillItems();
    List<ClericSpellGuideCatalog.Entry> pages=ClericSpellGuideCatalog.build(defs,id->EntityHandler.getItemDef(id).getName());
    check(pages.size()<90,"all guide pages fit native list capacity");
    guide.skillGuide.scrollMethodList(guide.skillGuideScroll,1000);guide.drawSkillItems();
    check(guide.skillGuide.getScrollPosition(guide.skillGuideScroll)==pages.size()-1,"native scrollbar reaches final spell page");
    ClericThreeTierClientRenderFixture.png((MudClientGraphics)mc.getSurface(),out.resolve("guide-last-page-512.png"));
    StringBuilder overview=new StringBuilder();Set<Integer> covered=new HashSet<Integer>();
    for(ClericSpellGuideCatalog.Entry page:pages) {
      check(page.getLines().size()<=12,"guide page height is bounded");
      check(mc.getSurface().stringWidth(2,page.getHeader())<=410,"guide header fits native font width");
      for(String line:page.getLines())check(mc.getSurface().stringWidth(1,line)<=410,"guide body fits native font width");
      if(page.getStableCode()<0)overview.append(page.getLines());else covered.add(page.getStableCode());
    }
    check(covered.size()==17,"guide retains every active spell");
    check(overview.toString().contains("maximum strength")&&overview.toString().contains("Worship unlocks"),"guide explains terminology");
    for(int value:new int[]{0,20,45,75,115,160})check(overview.toString().contains(value+" Holy Power"),"explicit power thresholds");
    StringBuilder curse=new StringBuilder();for(ClericSpellGuideCatalog.Entry page:pages)if(page.getStableCode()==27||page.getStableCode()==24)curse.append(page.getLines());
    check(curse.toString().contains("Magical damage still works")&&curse.toString().contains("at least 1 cleansing"),"curse meanings are explained in guide");
    StringBuilder longName=new StringBuilder();for(int i=0;i<100;i++)longName.append("Ancient sigil ");
    for(ClericSpellGuideCatalog.Entry page:ClericSpellGuideCatalog.build(defs,id->longName.toString()))
      check(page.getLines().size()<=12,"oversized descriptive block is split into bounded guide pages");
    Files.write(out.resolve("guide-content.txt"),pagesToText(pages).getBytes("UTF-8"));
    System.out.println("PASS "+checks+" real spellbook/guide text, cost, viewport, input and final-page navigation assertions; "+pages.size()+" guide pages");
  }
  static String pagesToText(List<ClericSpellGuideCatalog.Entry> pages) {
    StringBuilder text=new StringBuilder();for(ClericSpellGuideCatalog.Entry page:pages){text.append(page.getHeader()).append('\n');for(String line:page.getLines())text.append(line).append('\n');text.append('\n');}return text.toString();
  }
}
