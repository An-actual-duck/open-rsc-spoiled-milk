package orsc.graphics.three;

import orsc.util.FastMath;

/** Conservative fixed-point interval broad phase. Bounds are borrowed scratch,
 * never retained. Any possible legacy int overflow or near-plane crossing keeps
 * the complete original CPU path. This only rejects, never performs picking. */
final class BrowserStaticModelBounds {
    private static boolean integerRange(double value) {
        return value>=Integer.MIN_VALUE && value<=Integer.MAX_VALUE;
    }
    static boolean excludesMouse(double[] bounds,int shift,int rotX,int rotY,int rotZ,
        int near,int viewportY,int mouseX,int mouseY,int pickScale) {
        if(bounds==null || bounds.length<6 || shift<0 || shift>20 || near<1
            || rotX<0 || rotX>1023 || rotY<0 || rotY>1023 || rotZ<0 || rotZ>1023) return false;
        for(int n=0;n<6;n++)if(!integerRange(bounds[n]))return false;
        for(int n=0;n<6;n+=2)if(bounds[n]>bounds[n+1])return false;
        if(rotZ!=0 && !rotatePair(bounds,0,2,FastMath.trigTable1024[1024+rotZ],FastMath.trigTable1024[rotZ],
                -FastMath.trigTable1024[rotZ],FastMath.trigTable1024[1024+rotZ])) return false;
        if(rotY!=0 && !rotatePair(bounds,0,4,FastMath.trigTable1024[1024+rotY],FastMath.trigTable1024[rotY],
                -FastMath.trigTable1024[rotY],FastMath.trigTable1024[1024+rotY])) return false;
        if(rotX!=0 && !rotatePair(bounds,2,4,FastMath.trigTable1024[1024+rotX],-FastMath.trigTable1024[rotX],
                FastMath.trigTable1024[rotX],FastMath.trigTable1024[1024+rotX])) return false;
        if(bounds[4]<=near) return false;
        double scale=1<<shift;
        for(int n=0;n<4;n++)if(!integerRange(bounds[n]*scale))return false;
        // Positive depth: extrema of each interval quotient occur at corners.
        // Outward integer rounding covers the original truncation toward zero.
        double minX=Math.floor(Math.min(bounds[0]*scale/bounds[4],bounds[0]*scale/bounds[5]));
        double maxX=Math.ceil(Math.max(bounds[1]*scale/bounds[4],bounds[1]*scale/bounds[5]));
        double minY=Math.floor(Math.min(bounds[2]*scale/bounds[4],bounds[2]*scale/bounds[5]));
        double maxY=Math.ceil(Math.max(bounds[3]*scale/bounds[4],bounds[3]*scale/bounds[5]));
        if(!integerRange(minY+viewportY)||!integerRange(maxY+viewportY))return false;
        minY+=viewportY;maxY+=viewportY;
        int expansion=Math.max(1,pickScale);
        double padX=expansion<=1?2:Math.max(14,Math.ceil((maxX-minX)*(expansion-1.0)/2));
        double padY=expansion<=1?2:Math.max(14,Math.ceil((maxY-minY)*(expansion-1.0)/2));
        return mouseX<minX-padX || mouseX>maxX+padX || mouseY<minY-padY || mouseY>maxY+padY;
    }
    private static boolean rotatePair(double[] r,int first,int second,int a,int b,int c,int d) {
        double f0=linear(r[first],r[first+1],r[second],r[second+1],a,b,false);
        double f1=linear(r[first],r[first+1],r[second],r[second+1],a,b,true);
        double s0=linear(r[first],r[first+1],r[second],r[second+1],c,d,false);
        double s1=linear(r[first],r[first+1],r[second],r[second+1],c,d,true);
        if(Double.isNaN(f0)||Double.isNaN(f1)||Double.isNaN(s0)||Double.isNaN(s1))return false;
        r[first]=f0;r[first+1]=f1;r[second]=s0;r[second+1]=s1;return true;
    }
    private static double linear(double x0,double x1,double y0,double y1,int a,int b,boolean maximum) {
        double ax0=x0*a,ax1=x1*a,by0=y0*b,by1=y1*b;
        if(!integerRange(ax0)||!integerRange(ax1)||!integerRange(by0)||!integerRange(by1))return Double.NaN;
        double lo=Math.min(ax0,ax1)+Math.min(by0,by1),hi=Math.max(ax0,ax1)+Math.max(by0,by1);
        if(!integerRange(lo)||!integerRange(hi))return Double.NaN;
        return Math.floor((maximum?hi:lo)/32768.0);
    }
}
