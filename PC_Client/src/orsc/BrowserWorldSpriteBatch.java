package orsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One synchronous frame's owned references; CPU LRU eviction cannot invalidate it.
 * No pooled Scene/frame objects or sprite source arrays are retained here. */
final class BrowserWorldSpriteBatch {
    private final List<BrowserWorldSpriteCache.Result> images = new ArrayList<>();
    private final List<float[]> quads = new ArrayList<>();
    private final Map<Integer,BrowserWorldSpriteCache.Result> unique = new HashMap<>();
    private long uniqueImageBytes;

    void add(BrowserWorldSpriteCache.Result image, float[] quad) {
        if(images.size()>=1024||image==null||image.id<1||image.width<1||image.height<1
            ||image.width>2048||image.height>2048||image.rgba==null
            ||image.rgba.length!=(long)image.width*image.height*4||quad==null||quad.length!=20)
            throw new IllegalStateException("Invalid bounded sprite frame image/quad");
        for(float value:quad)if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite sprite quad");
        BrowserWorldSpriteCache.Result previous=unique.get(image.id);
        if(previous!=null&&(previous.width!=image.width||previous.height!=image.height||previous.rgba!=image.rgba))
            throw new IllegalStateException("Sprite identity does not own one exact image");
        if(previous==null){
            if(uniqueImageBytes+image.rgba.length>16L*1024*1024)throw new IllegalStateException("Current sprite images exceed atlas byte capacity");
            uniqueImageBytes+=image.rgba.length;
        }
        images.add(image);quads.add(quad.clone());unique.put(image.id,image);
    }
    int size(){return images.size();}
    int[] descriptors(){
        int[] result=new int[images.size()*3];
        for(int n=0;n<images.size();n++){BrowserWorldSpriteCache.Result image=images.get(n);result[n*3]=image.id;result[n*3+1]=image.width;result[n*3+2]=image.height;}
        return result;
    }
    float[] vertices(){
        float[] result=new float[images.size()*20];
        for(int n=0;n<images.size();n++)System.arraycopy(quads.get(n),0,result,n*20,20);
        return result;
    }
    byte[] pixels(int[] missing){
        if(missing==null||missing.length>unique.size())throw new IllegalStateException("Invalid missing sprite request");
        long bytes=0;Set<Integer> seen=new HashSet<>();
        for(int id:missing){BrowserWorldSpriteCache.Result image=unique.get(id);if(image==null||!seen.add(id))throw new IllegalStateException("GPU requested invalid sprite identity");bytes+=image.rgba.length;}
        if(bytes>16L*1024*1024)throw new IllegalStateException("Sprite upload exceeds atlas capacity");
        byte[] result=new byte[(int)bytes];int offset=0;
        for(int id:missing){byte[] rgba=unique.get(id).rgba;System.arraycopy(rgba,0,result,offset,rgba.length);offset+=rgba.length;}
        return result;
    }
}
