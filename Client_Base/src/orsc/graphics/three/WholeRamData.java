package orsc.graphics.three;

import java.io.*;
import java.util.*;

/** Private prepared-data codec. Immutable pool entries never become live model arrays. */
public final class WholeRamData {
    public static final long MAX_BYTES=1536L*1024*1024;
    public static final int MAX_ARRAY=16*1024*1024, MAX_ENTRIES=2000000;
    private WholeRamData(){}
    public static final class Pool {
        private final ArrayList<Object> entries=new ArrayList<Object>();
        private final HashMap<Key,Integer> index=new HashMap<Key,Integer>();
        private long bytes,retainedBytes;
        private final long maximumRetainedBytes;
        public Pool(){this(Long.MAX_VALUE);}
        /** Optional private online-template bound; default offline codec unchanged. */
        public Pool(long maximumRetainedBytes){if(maximumRetainedBytes<0)throw new IllegalArgumentException("Prepared retained budget");this.maximumRetainedBytes=maximumRetainedBytes;}
        public long retainedBytes(){return retainedBytes;}
        public int intern(Object value){
            if(value==null)return -1;
            if(!(value instanceof int[])&&!(value instanceof byte[])&&!(value instanceof float[])&&!(value instanceof long[]))throw new IllegalArgumentException("Prepared primitive type");
            Key key=new Key(value);Integer old=index.get(key);if(old!=null)return old;
            int length=java.lang.reflect.Array.getLength(value);long n=(long)length*(value instanceof byte[]?1:value instanceof long[]?8:4);
            if(length>MAX_ARRAY||entries.size()>=MAX_ENTRIES||bytes+n>MAX_BYTES||retainedBytes+n+256>maximumRetainedBytes)throw new IllegalStateException("Prepared RAM primitive budget exceeded");
            Object copy=copy(value);int id=entries.size();entries.add(copy);index.put(new Key(copy),id);bytes+=n;retainedBytes+=n+256;return id;
        }
        public Object get(int id){if(id<0||id>=entries.size())throw new IllegalStateException("Prepared array reference");return entries.get(id);}
        public int[] ints(int id){return (int[])get(id);}
        public long bytes(){return bytes;}
        public int size(){return entries.size();}
        public void discardIndex(){index.clear();}
        public void write(DataOutput out)throws IOException{out.writeInt(entries.size());for(Object value:entries){int type=value instanceof byte[]?1:value instanceof int[]?2:value instanceof float[]?3:4;out.writeInt(type);out.writeInt(java.lang.reflect.Array.getLength(value));if(type==1)out.write((byte[])value);else if(type==2)for(int v:(int[])value)out.writeInt(v);else if(type==3)for(float v:(float[])value)out.writeInt(Float.floatToRawIntBits(v));else for(long v:(long[])value)out.writeLong(v);}}
        public static Pool read(DataInput in)throws IOException{
            Pool p=new Pool();int count=bounded(in.readInt(),MAX_ENTRIES,"pool count");for(int i=0;i<count;i++){int type=in.readInt(),n=bounded(in.readInt(),MAX_ARRAY,"array length");long b=(long)n*(type==1?1:type==4?8:4);if(type<1||type>4||p.bytes+b>MAX_BYTES)throw new IOException("Prepared primitive budget/type");Object value;if(type==1){byte[] a=new byte[n];in.readFully(a);value=a;}else if(type==2){int[] a=new int[n];for(int j=0;j<n;j++)a[j]=in.readInt();value=a;}else if(type==3){float[] a=new float[n];for(int j=0;j<n;j++)a[j]=Float.intBitsToFloat(in.readInt());value=a;}else{long[] a=new long[n];for(int j=0;j<n;j++)a[j]=in.readLong();value=a;}p.entries.add(value);p.bytes+=b;p.retainedBytes+=b+256;}return p;
        }
    }
    public static int bounded(int n,int cap,String what)throws IOException{if(n<0||n>cap)throw new IOException("Prepared "+what+" outside bound");return n;}
    public static Object copy(Object value){if(value instanceof int[])return ((int[])value).clone();if(value instanceof float[])return ((float[])value).clone();if(value instanceof long[])return ((long[])value).clone();return ((byte[])value).clone();}
    private static final class Key {
        final Object value;final int hash;Key(Object v){value=v;hash=v instanceof int[]?Arrays.hashCode((int[])v):v instanceof float[]?rawHash((float[])v):v instanceof byte[]?Arrays.hashCode((byte[])v):Arrays.hashCode((long[])v);}
        public int hashCode(){return hash;}
        public boolean equals(Object o){if(!(o instanceof Key))return false;Object v=((Key)o).value;if(v.getClass()!=value.getClass())return false;if(v instanceof int[])return Arrays.equals((int[])v,(int[])value);if(v instanceof byte[])return Arrays.equals((byte[])v,(byte[])value);if(v instanceof long[])return Arrays.equals((long[])v,(long[])value);float[] a=(float[])v,b=(float[])value;if(a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(Float.floatToRawIntBits(a[i])!=Float.floatToRawIntBits(b[i]))return false;return true;}
        static int rawHash(float[] a){int h=1;for(float f:a)h=31*h+Float.floatToRawIntBits(f);return h;}
    }
    /** Per-model alias graph. Equal content in different groups remains different live arrays. */
    public static final class Graph {
        private final Pool pool;private final ArrayList<int[]> nodes=new ArrayList<int[]>();private final IdentityHashMap<Object,Integer> ids=new IdentityHashMap<Object,Integer>();
        public Graph(Pool p){pool=p;}
        public int capture(Object value){if(value==null)return -1;Integer old=ids.get(value);if(old!=null)return old;int id=nodes.size();ids.put(value,id);nodes.add(null);if(value instanceof int[][]){int[][] a=(int[][])value;int[] n=new int[a.length+1];n[0]=-1;for(int i=0;i<a.length;i++)n[i+1]=capture(a[i]);nodes.set(id,n);}else nodes.set(id,new int[]{pool.intern(value)});return id;}
        public int[] descriptor(int[] scalars,int[] roots){int size=3+scalars.length+roots.length;for(int[] n:nodes)size+=1+n.length;int[] out=new int[size];int at=0;out[at++]=scalars.length;for(int v:scalars)out[at++]=v;out[at++]=roots.length;for(int v:roots)out[at++]=v;out[at++]=nodes.size();for(int[] n:nodes){out[at++]=n.length;for(int v:n)out[at++]=v;}return out;}
    }
    /** Owned, preparsed immutable recipe metadata. Never contains live model arrays. */
    public static final class ModelLayout {
        private final int[] scalars,roots;private final int[][] nodes;
        public ModelLayout(int[] d,int scalarCount,int rootCount){int at=0;if(d.length<3||d[at++]!=scalarCount)throw new IllegalStateException("Prepared model schema");scalars=new int[scalarCount];System.arraycopy(d,at,scalars,0,scalarCount);at+=scalarCount;if(d[at++]!=rootCount)throw new IllegalStateException("Prepared model roots");roots=new int[rootCount];System.arraycopy(d,at,roots,0,rootCount);at+=rootCount;int n=d[at++];if(n<0||n>4096)throw new IllegalStateException("Prepared model graph");nodes=new int[n][];for(int i=0;i<n;i++){int len=d[at++];if(len<1||len>MAX_ARRAY||at+len>d.length)throw new IllegalStateException("Prepared model node");nodes[i]=Arrays.copyOfRange(d,at,at+len);at+=len;}if(at!=d.length)throw new IllegalStateException("Prepared model tail");}
        public long retainedBytes(){long n=256+scalars.length*4L+roots.length*4L+nodes.length*8L;for(int[] v:nodes)n+=64+v.length*4L;return n;}
    }
    public static final class ModelArrays {
        public final int[] scalars,roots;private final int[][] nodes;private final Object[] copies;private final Pool pool;public long copiedBytes;
        public ModelArrays(Pool p,int[] d,int scalarCount,int rootCount){this(p,new ModelLayout(d,scalarCount,rootCount));}
        public ModelArrays(Pool p,ModelLayout layout){pool=p;scalars=layout.scalars.clone();roots=layout.roots.clone();nodes=layout.nodes;copies=new Object[nodes.length];}
        public Object root(int i){return get(roots[i]);}
        private Object get(int id){if(id==-1)return null;if(id<0||id>=nodes.length)throw new IllegalStateException("Prepared model alias");if(copies[id]!=null)return copies[id];int[] node=nodes[id];if(node[0]>=0){if(node.length!=1)throw new IllegalStateException("Prepared primitive node");Object source=pool.get(node[0]);copiedBytes+=(long)java.lang.reflect.Array.getLength(source)*(source instanceof byte[]?1:source instanceof long[]?8:4);return copies[id]=copy(source);}if(node[0]!=-1)throw new IllegalStateException("Prepared model matrix");int[][] rows=new int[node.length-1][];copies[id]=rows;for(int i=0;i<rows.length;i++)rows[i]=(int[])get(node[i+1]);return rows;}
    }
}
