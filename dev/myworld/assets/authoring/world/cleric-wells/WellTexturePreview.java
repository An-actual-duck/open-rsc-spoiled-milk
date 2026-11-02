package orsc.graphics.three;
import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import java.nio.file.*;import java.util.*;import orsc.graphics.two.MudClientGraphics;
/** Read-only software renderer: no client/server session, maps or archive writes. */
public final class WellTexturePreview {
 static void texture(Scene scene,int index,Path file)throws Exception {
  BufferedImage im=ImageIO.read(file.toFile());int w=im.getWidth(),h=im.getHeight();int[] rgb=im.getRGB(0,0,w,h,null,0,w);LinkedHashMap<Integer,Integer> dict=new LinkedHashMap<>();int[] palette=new int[256];palette[0]=0xff00ff;byte[] indices=new byte[w*h];
  for(int i=0;i<rgb.length;i++){int c=rgb[i]&0xffffff;if(!dict.containsKey(c)){if(dict.size()>=255)throw new AssertionError("palette too large");dict.put(c,dict.size()+1);palette[dict.get(c)]=c;}indices[i]=(byte)(int)dict.get(c);}
  scene.loadTexture(index,palette,w/64-1,indices);
 }
 static RSModel wall(int height){RSModel m=new RSModel(4,1);int[] v={m.insertVertex(0,0,0),m.insertVertex(0,-height,0),m.insertVertex(256,-height,0),m.insertVertex(256,0,0)};m.insertFace(4,v,0,0,false);return m;}
 static RSModel floor(){RSModel m=new RSModel(4,1);int[] v={m.insertVertex(0,0,0),m.insertVertex(256,0,0),m.insertVertex(256,0,256),m.insertVertex(0,0,256)};m.insertFace(4,v,1,1,false);return m;}
 public static void main(String[]args)throws Exception{Path root=Paths.get(args[0]);int count=0;for(String liquid:new String[]{"ichor","divine-water"})for(String material:new String[]{"stone","dirt"})for(int height:new int[]{192,275}){
  MudClientGraphics g=new MudClientGraphics(640,512,1);Arrays.fill(g.pixelData,0x23262c);Scene s=new Scene(g,8,2048,4);s.setMidpoints(320,true,640,256,320,9,256);s.fogLandscapeDistance=4000;s.fogEntityDistance=4000;s.setFrustum(0,11,7,2);texture(s,0,root.resolve("candidates/"+liquid+"-"+material+"-seep.png"));texture(s,1,root.resolve("candidates/"+liquid+"-water.png"));s.addModel(wall(height));s.addModel(floor());s.setCamera(128,-height/2,96,944,512,0,700);s.forceLegacyWorldRasterOnce();s.endScene(0);long pixels=Arrays.stream(g.pixelData).filter(c->c!=0x23262c).count();if(pixels<1000)throw new AssertionError("empty native render");BufferedImage out=new BufferedImage(640,512,BufferedImage.TYPE_INT_RGB);out.setRGB(0,0,640,512,g.pixelData,0,640);ImageIO.write(out,"png",root.resolve("review/"+liquid+"-"+material+"-"+height+"-context.png").toFile());System.out.println(liquid+" "+material+" height="+height+" rasterPixels="+pixels);count++;
 }System.out.println("PASS "+count+" native software wall/floor contexts");}
}
