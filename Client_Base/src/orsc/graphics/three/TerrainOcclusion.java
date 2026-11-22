package orsc.graphics.three;

/** Render-only terrain occlusion from a checked height field. */
public final class TerrainOcclusion {
    public static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int TILE = 128;

    private TerrainOcclusion() { }

    public static byte[] build(int[] heights, int width, int height) {
        byte[] result = new byte[width * height * 4];
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
            int at = z * width + x, y = heights[at];
            double occlusion = 0, bend = 0; int count = 0;
            if (y != UNKNOWN) for (int[] d : directions) {
                // Symmetric pairs cancel a planar slope: only terrain above its local tangent
                // or signed local bending contributes, rather than painting every slope dark.
                int nx = x+d[0], nz=z+d[1], px=x-d[0], pz=z-d[1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= height || px < 0 || px >= width || pz < 0 || pz >= height) continue;
                int n=heights[nz*width+nx], p=heights[pz*width+px];
                if (n == UNKNOWN || p == UNKNOWN) continue;
                double length = Math.hypot(d[0],d[1]) * TILE;
                double curve = ((double)y - (n+(double)p)*.5) / length; // positive = concave in Y-down
                double tangent = (p - (double)n) / (2 * length);
                double horizon = 0;
                for (int radius=1;radius<=4;radius++) {
                    int sx=x+d[0]*radius, sz=z+d[1]*radius;
                    if (sx<0 || sx>=width || sz<0 || sz>=height) break;
                    int sample=heights[sz*width+sx]; if(sample==UNKNOWN) break;
                    double aboveTangent=(y-(double)sample)/(length*radius)-tangent;
                    horizon=Math.max(horizon,aboveTangent);
                }
                occlusion += horizon; bend += curve; count++;
            }
            float ao=count==0?0:(float)Math.min(1,occlusion/8*1.6);
            float curve=count==0?0:(float)Math.max(-1,Math.min(1,bend/8*3));
            result[at*4]=(byte)Math.round(ao*255);
            result[at*4+1]=(byte)Math.round((curve*.5f+.5f)*255);
            result[at*4+2]=0; result[at*4+3]=(byte)(y==UNKNOWN?0:255);
        }
        return result;
    }
}
