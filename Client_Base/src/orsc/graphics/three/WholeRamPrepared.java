package orsc.graphics.three;

import java.io.*;
import java.util.*;

/** Complete or explicitly partial immutable CPU product set, shared by baker and runtime. */
public final class WholeRamPrepared {
    public final WholeRamData.Pool pool;
    private final Map<String,World.PreparedActivation> recipes=new HashMap<String,World.PreparedActivation>();
    private final Map<String,Renderer3DWorldChunkFrame.ChunkMesh> cells=new HashMap<String,Renderer3DWorldChunkFrame.ChunkMesh>();
    private final Map<String,Integer> recipeIds=new TreeMap<String,Integer>(),cellIds=new TreeMap<String,Integer>();
    public WholeRamPrepared(WholeRamData.Pool pool){this.pool=pool;}
    public static String key(int p,int x,int y){return p+":"+x+":"+y;}
    public void addRecipe(World.PreparedActivation recipe){String k=key(0,recipe.sectionX,recipe.sectionY);if(recipeIds.put(k,recipe.descriptor())!=null)throw new IllegalStateException("Duplicate RAM recipe");recipes.put(k,recipe);}
    public void addQueryCell(int p,int x,int y,int descriptor){String k=key(p,x,y);if(cellIds.put(k,descriptor)!=null)throw new IllegalStateException("Duplicate RAM query cell");cells.put(k,Renderer3DWorldChunkFrame.ChunkMesh.restoreWholeRamCell(pool,descriptor));}
    public int recipes(){return recipes.size();}public int queryCells(){return cells.size();}
    public World.PreparedActivation recipe(int x,int y){return recipes.get(key(0,x,y));}
    public Set<String> recipeKeys(){return Collections.unmodifiableSet(recipes.keySet());}
    public Set<String> queryKeys(){return Collections.unmodifiableSet(cells.keySet());}
    public int modelTemplates(){Set<Integer> ids=new HashSet<Integer>();for(Integer rid:recipeIds.values()){int[] d=pool.ints(rid);for(int i=456;i<744;i++)if(d[i]>=0)ids.add(d[i]);}return ids.size();}
    public Renderer3DWorldChunkFrame queryFrame(int sx,int sy){return queryFrame(sx,sy,0,0);}
    public Renderer3DWorldChunkFrame queryFrame(int sx,int sy,int offsetTilesX,int offsetTilesZ){
        WholeRamBasis basis=new WholeRamBasis(offsetTilesX,offsetTilesZ);
        World.PreparedActivation recipe=recipe(sx,sy);if(recipe==null)throw new IllegalStateException("No prepared global L0 activation at "+sx+":"+sy);
        ArrayList<Renderer3DWorldChunkFrame.ChunkMesh> out=new ArrayList<Renderer3DWorldChunkFrame.ChunkMesh>(75);
        Renderer3DWorldChunkFrame.ChunkMesh first=cells.get(key(0,sx,sy));if(first==null)throw new IllegalStateException("Missing primary RAM query cell");out.add(first.rebaseWholeRamQuery(0,0,recipe.roofBits(),143,recipe.roofCount(),basis.offsetTilesX,basis.offsetTilesZ));
        for(int p=0;p<3;p++)for(int x=sx-2;x<=sx+2;x++)for(int y=sy-2;y<=sy+2;y++){if(p==0&&x==sx&&y==sy)continue;Renderer3DWorldChunkFrame.ChunkMesh c=cells.get(key(p,x,y));if(c!=null)out.add(c.rebaseWholeRamQuery(Math.multiplyExact(x-sx,6144),Math.multiplyExact(y-sy,6144),null,0,0,basis.offsetTilesX,basis.offsetTilesZ));}
        return Renderer3DWorldChunkFrame.fromChunks(out);
    }
    public void write(OutputStream stream)throws IOException{WholeRamIO.Output out=new WholeRamIO.Output(stream);out.writeInt(0x57525031);out.writeInt(1);pool.write(out);writeRows(out,recipeIds);writeRows(out,cellIds);}
    private static void writeRows(DataOutput out,Map<String,Integer> rows)throws IOException{out.writeInt(rows.size());for(Map.Entry<String,Integer> row:rows.entrySet()){String[] k=row.getKey().split(":");out.writeInt(Integer.parseInt(k[0]));out.writeInt(Integer.parseInt(k[1]));out.writeInt(Integer.parseInt(k[2]));out.writeInt(row.getValue());}}
    public static WholeRamPrepared read(InputStream stream)throws IOException{
        WholeRamIO.Input in=new WholeRamIO.Input(stream);if(in.readInt()!=0x57525031||in.readInt()!=1)throw new IOException("WRP1 header/version");WholeRamPrepared set=new WholeRamPrepared(WholeRamData.Pool.read(in));int n=WholeRamData.bounded(in.readInt(),445,"recipe count");for(int i=0;i<n;i++){int p=in.readInt(),x=in.readInt(),y=in.readInt(),id=in.readInt();if(p!=0)throw new IOException("Prepared recipe plane");World.PreparedActivation r=World.PreparedActivation.restore(set.pool,id);if(r.sectionX!=x||r.sectionY!=y)throw new IOException("Prepared recipe identity");String k=key(0,x,y);if(set.recipeIds.put(k,id)!=null)throw new IOException("Duplicate recipe");set.recipes.put(k,r);}
        n=WholeRamData.bounded(in.readInt(),1330,"query count");for(int i=0;i<n;i++){int p=in.readInt(),x=in.readInt(),y=in.readInt(),id=in.readInt();if(p<0||p>2)throw new IOException("Prepared query level");Renderer3DWorldChunkFrame.ChunkMesh c=Renderer3DWorldChunkFrame.ChunkMesh.restoreWholeRamCell(set.pool,id);if(c.getPlane()!=p||c.getCenterSectionX()!=x||c.getCenterSectionY()!=y)throw new IOException("Prepared query identity");String k=key(p,x,y);if(set.cellIds.put(k,id)!=null)throw new IOException("Duplicate query cell");set.cells.put(k,c);}
        java.util.Set<Integer> validatedModels=new java.util.HashSet<Integer>();for(World.PreparedActivation recipe:set.recipes.values())recipe.validate(validatedModels);
        return set;
    }
}
