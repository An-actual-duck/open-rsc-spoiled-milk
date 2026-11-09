package orsc;
import com.openrsc.client.model.Sprite;
import java.util.Arrays;
import java.util.Collections;
import orsc.graphics.Renderer2DFrame.SpriteCommand;
public final class BrowserWorldSpriteBatchFixture {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("sprite batch check"+checks);}
    private static void reject(Runnable task){boolean failed=false;try{task.run();}catch(IllegalStateException e){failed=true;}check(failed);}
    public static void main(String[] args){
        BrowserWorldSpriteCache cache=new BrowserWorldSpriteCache(4096,1);
        OpenGLPixelWorkspace workspace=new OpenGLPixelWorkspace();
        BrowserWorldSpriteBatch frame=new BrowserWorldSpriteBatch();
        BrowserWorldSpriteCache.Result first=null,last=null;byte[] saved=null;
        for(int n=1;n<=12;n++){
            BrowserWorldSpriteCache.Result image=cache.compose(Collections.singletonList(new SpriteCommand(new Sprite(new int[]{n},1,1),0,0,2,2,255)),workspace);
            if(first==null){first=image;saved=image.rgba.clone();}last=image;
            float[] quad=new float[20];quad[0]=n;frame.add(image,quad);quad[0]=-10;
        }
        check(frame.size()==12);check(cache.getEntryCount()==1);check(Arrays.equals(first.rgba,saved));
        int[] descriptors=frame.descriptors();check(descriptors.length==36);check(descriptors[0]==first.id);check(descriptors[33]==last.id);
        check(frame.vertices()[0]==1);check(frame.vertices()[220]==12);check(frame.pixels(new int[0]).length==0);
        byte[] upload=frame.pixels(new int[]{last.id,first.id});check(upload.length==last.rgba.length+first.rgba.length);check(Arrays.equals(Arrays.copyOfRange(upload,last.rgba.length,upload.length),saved));
        reject(()->frame.pixels(null));reject(()->frame.pixels(new int[]{0}));final int id=first.id;reject(()->frame.pixels(new int[]{id,id}));
        BrowserWorldSpriteCache.Result duplicate=last;frame.add(duplicate,new float[20]);check(frame.size()==13);check(frame.pixels(new int[]{duplicate.id}).length==duplicate.rgba.length);
        reject(()->frame.add(new BrowserWorldSpriteCache.Result(0,0,1,1,new byte[4],duplicate.id),new float[20]));
        reject(()->frame.add(new BrowserWorldSpriteCache.Result(0,0,1,1,new byte[3],99),new float[20]));
        float[] invalid=new float[20];invalid[0]=Float.NaN;reject(()->frame.add(duplicate,invalid));
        BrowserWorldSpriteBatch large=new BrowserWorldSpriteBatch();large.add(new BrowserWorldSpriteCache.Result(0,0,2048,2048,new byte[16*1024*1024],100),new float[20]);large.add(new BrowserWorldSpriteCache.Result(0,0,1,1,new byte[4],101),new float[20]);reject(()->large.pixels(new int[]{100,101}));
        System.out.println("PASS "+checks+" actual sprite frame gather/eviction lifetime, ordered quad ownership, exact-ID dedup, missing-ID validation and16MiB upload bounds");
    }
}
