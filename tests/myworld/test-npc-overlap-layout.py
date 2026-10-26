#!/usr/bin/env python3
"""Production grouping equivalence and bounded same-JVM baseline comparison."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
JAVA = r'''
package orsc;
import java.util.*;
public class NpcOverlapLayoutFixture {
    static volatile long sink;
    static void old(ORSCharacter[] npcs,int n,int tile,int[] ranks,int[] counts) {
        Arrays.fill(ranks,0,n,0); Arrays.fill(counts,0,n,0);
        for(int i=0;i<n;i++) if(npcs[i]!=null) for(int j=0;j<n;j++) if(npcs[j]!=null
            && Math.floorDiv(npcs[i].currentX-64,tile)==Math.floorDiv(npcs[j].currentX-64,tile)
            && Math.floorDiv(npcs[i].currentZ-64,tile)==Math.floorDiv(npcs[j].currentZ-64,tile)) {
            counts[i]++; if(npcs[j].serverIndex<npcs[i].serverIndex) ranks[i]++;
        }
    }
    static ORSCharacter[] scene(int n,boolean dense,Random random) {
        ORSCharacter[] npcs=new ORSCharacter[n];
        for(int i=0;i<n;i++) if(i%23!=0) {
            ORSCharacter npc=npcs[i]=new ORSCharacter(); npc.serverIndex=random.nextInt(Math.max(2,n/2));
            npc.currentX=dense?64:random.nextInt(n*128)-n*64;
            npc.currentZ=dense?64:random.nextInt(n*128)-n*64;
        }
        return npcs;
    }
    static long[] measure(ORSCharacter[] npcs,boolean grouped,int[] ranks,int[] counts) {
        long[] samples=new long[51];
        for(int sample=-100;sample<samples.length;sample++) {
            long start=System.nanoTime();
            for(int repeat=0;repeat<10;repeat++) {
                if(grouped) NpcOverlapLayout.calculate(npcs,npcs.length,128,ranks,counts);
                else old(npcs,npcs.length,128,ranks,counts);
                sink+=counts[npcs.length/2]+ranks[npcs.length/2];
            }
            if(sample>=0) samples[sample]=(System.nanoTime()-start)/10;
        }
        Arrays.sort(samples);return samples;
    }
    public static void main(String[] args) {
        Random random=new Random(18153);
        int cases=0;
        for(int n:new int[]{0,1,2,3,4,8,32,128,256,512,1024}) for(boolean dense:new boolean[]{false,true}) {
            ORSCharacter[] npcs=scene(n,dense,random); int[] ranks=new int[n],counts=new int[n],refR=new int[n],refC=new int[n];
            old(npcs,n,128,refR,refC); NpcOverlapLayout.calculate(npcs,n,128,ranks,counts);
            if(!Arrays.equals(ranks,refR)||!Arrays.equals(counts,refC)) throw new AssertionError("equivalence n="+n+" dense="+dense);
            cases++;
            if(n>=32) {
                long[] before=measure(npcs,false,ranks,counts),after=measure(npcs,true,ranks,counts);
                System.out.printf(Locale.ROOT,"OVERLAP n=%d scene=%s baseline_median_ns=%d baseline_p95_ns=%d grouped_median_ns=%d grouped_p95_ns=%d%n",n,dense?"dense":"sparse",before[25],before[48],after[25],after[48]);
            }
        }
        System.out.println("PASS overlap equivalence "+cases+" scenes; nulls, negative/moving tiles, equal indexes, dense/sparse");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='npc-overlap-') as directory:
    tmp=Path(directory)
    (tmp/'ORSCharacter.java').write_text('package orsc; public class ORSCharacter { public int serverIndex,currentX,currentZ; }')
    (tmp/'NpcOverlapLayoutFixture.java').write_text(JAVA)
    subprocess.run(['javac','-d',directory,str(tmp/'ORSCharacter.java'),str(ROOT/'Client_Base/src/orsc/NpcOverlapLayout.java'),str(tmp/'NpcOverlapLayoutFixture.java')],check=True)
    subprocess.run(['java','-Xms128m','-Xmx128m','-cp',directory,'orsc.NpcOverlapLayoutFixture'],check=True)
