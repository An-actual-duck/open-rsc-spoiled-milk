import java.io.*;
import java.nio.file.*;
import orsc.graphics.three.*;

/** Exact immutable query metadata/coordinate adaptation, no gameplay or GPU. */
public final class BrowserWholeRamBasisFixture {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("basis check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        WholeRamPrepared p;try(InputStream in=new BufferedInputStream(Files.newInputStream(Paths.get(args[0])))){p=WholeRamPrepared.read(in);check(in.read()==-1);}
        Renderer3DWorldChunkFrame zero=p.queryFrame(2,13);
        for(int[] offset:new int[][]{{0,0},{2304,1776},{-96,48},{432,-528}}){
            WholeRamBasis b=new WholeRamBasis(offset[0],offset[1]);check(b.logicalX(b.carrierX(2))==2);check(b.logicalZ(b.carrierZ(13))==13);
            Renderer3DWorldChunkFrame adapted=p.queryFrame(2,13,offset[0],offset[1]);check(adapted.getChunkCount()==zero.getChunkCount());
            for(int i=0;i<zero.getChunkCount();i++){
                Renderer3DWorldChunkFrame.ChunkMesh a=zero.getChunks().get(i),c=adapted.getChunks().get(i);
                check(c.getCenterSectionX()==a.getCenterSectionX()+b.sectionDeltaX);check(c.getCenterSectionY()==a.getCenterSectionY()+b.sectionDeltaZ);
                check(c.getOriginWorldX()==a.getOriginWorldX()+b.offsetUnitsX);check(c.getOriginWorldZ()==a.getOriginWorldZ()+b.offsetUnitsZ);
                check(c.getLogicalWorldOffsetX()==a.getLogicalWorldOffsetX()+b.offsetUnitsX);check(c.getLogicalWorldOffsetZ()==a.getLogicalWorldOffsetZ()+b.offsetUnitsZ);
                check(c.getVertexOffsetX()==a.getVertexOffsetX());check(c.getVertexOffsetZ()==a.getVertexOffsetZ());check(c.getStorageSignature()==a.getStorageSignature());
                check((offset[0]==0&&offset[1]==0)==(c.getSignature()==a.getSignature()));
                check(c.getVertexCount()==a.getVertexCount());check(c.getIndexCount()==a.getIndexCount());check(c.getRoofCoverageAxis()==a.getRoofCoverageAxis());
                for(int v=0;v<a.getVertexCount();v++){for(int axis=0;axis<3;axis++)check(c.getVertexCoord(v*3+axis)==a.getVertexCoord(v*3+axis));check(c.getVertexLight(v)==a.getVertexLight(v));check(c.getVertexNormalX(v)==a.getVertexNormalX(v));check(c.getVertexNormalY(v)==a.getVertexNormalY(v));check(c.getVertexNormalZ(v)==a.getVertexNormalZ(v));}
                for(int t=0;t<a.getIndexCount();t++)check(c.getIndex(t)==a.getIndex(t));
                for(int x=0;x<a.getRoofCoverageAxis();x++)for(int z=0;z<a.getRoofCoverageAxis();z++)check(c.isRoofCoveredTile(x,z)==a.isRoofCoveredTile(x,z));
            }
        }
        check(new WholeRamBasis(2304,1776).carrierX(2)==50);check(new WholeRamBasis(2304,1776).carrierZ(13)==50);
        for(int[] bad:new int[][]{{1,0},{0,-1},{-49,48},{2147483616,0}}){boolean refused=false;try{new WholeRamBasis(bad[0],bad[1]);}catch(IllegalStateException expected){refused=true;}check(refused);}
        System.out.println("PASS basis checks="+checks+"; immutable prepared arrays unchanged; no World/GPU/performance claim");
    }
}
