#!/usr/bin/env python3
"""Same-JVM-settings production registry traversal comparison, no gameplay claims."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
BASE='96cdabf534a123b31db9c763d7a079d3ae812361'
JAVA=r'''
import com.openrsc.server.util.EntityList;
import com.openrsc.server.model.entity.Entity;
import java.util.*;
import java.lang.management.ManagementFactory;
public class RegistryScanBenchmark {
 static volatile long sink;
 static final com.sun.management.ThreadMXBean memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static void report(String label,EntityList<Entity> list,boolean snapshot) {
  long[] samples=new long[61]; long bytes=0; final long[] sum={0};
  for(int sample=-200;sample<61;sample++) {
   long allocated=memory.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
   for(int repeat=0;repeat<50;repeat++) {
    if(snapshot) for(Entity entity:list) sum[0]+=entity.getIndex();
    else list.forEachLive(entity->sum[0]+=entity.getIndex());
   }
   long elapsed=(System.nanoTime()-start)/50;
   if(sample>=0) {samples[sample]=elapsed;bytes+=memory.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;}
  }
  sink=sum[0]; Arrays.sort(samples);
  System.out.printf(Locale.ROOT,"SCAN fixture=%s count=%d mode=%s median_ns=%d p95_ns=%d allocated_bytes_per_pass=%.1f%n",label,list.size(),snapshot?"snapshot":"live",samples[30],samples[57],bytes/(61.0*50));
 }
 public static void main(String[] args) {
  for(int n:new int[]{4000,8000,16384}) {
   EntityList<Entity> list=new EntityList<>(16384); for(int i=0;i<n;i++)list.add(new Entity());
   report("packed",list,false);report("packed",list,true);
  }
  EntityList<Entity> low=new EntityList<>(16384);for(int i=0;i<64;i++)low.add(new Entity());report("sparse-low",low,false);
  EntityList<Entity> scattered=new EntityList<>(16384);for(int i=0;i<16384;i++)scattered.add(new Entity());
  for(int i=0;i<16384;i++)if(i%256!=0)scattered.remove(i); report("sparse-scattered",scattered,false);
 }
}
'''
with tempfile.TemporaryDirectory(prefix='registry-scan-') as directory:
 tmp=Path(directory)
 stub=tmp/'Entity.java';stub.write_text('package com.openrsc.server.model.entity; public class Entity {private int index=-1; public int getIndex(){return index;} public void setIndex(int value){index=value;} }')
 harness=tmp/'RegistryScanBenchmark.java';harness.write_text(JAVA)
 cp=str(ROOT/'server/lib/guava-30.1.1-jre.jar')
 for version in ['baseline','current']:
  dest=tmp/version;dest.mkdir(); source=dest/'EntityList.java'
  source.write_text(subprocess.check_output(['git','show',BASE+':server/src/com/openrsc/server/util/EntityList.java'],cwd=ROOT,text=True) if version=='baseline' else (ROOT/'server/src/com/openrsc/server/util/EntityList.java').read_text())
  subprocess.run(['javac','-cp',cp,'-d',str(dest),str(stub),str(source),str(harness)],check=True)
  print('VERSION '+version,flush=True)
  subprocess.run(['java','-Xms256m','-Xmx256m','-cp',str(dest)+':'+cp,'RegistryScanBenchmark'],check=True)
