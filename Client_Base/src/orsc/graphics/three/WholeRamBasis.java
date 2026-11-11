package orsc.graphics.three;

/** Immutable logical-source to legacy-carrier translation, in tiles, for one captured scope. */
public final class WholeRamBasis {
    public static final WholeRamBasis ZERO=new WholeRamBasis(0,0);
    public final int offsetTilesX,offsetTilesZ,sectionDeltaX,sectionDeltaZ,offsetUnitsX,offsetUnitsZ;
    public WholeRamBasis(int x,int z){
        if(Math.floorMod(x,48)!=0||Math.floorMod(z,48)!=0)throw new IllegalStateException("Whole-RAM carrier basis must align to48tiles: offset="+x+","+z);
        offsetTilesX=x;offsetTilesZ=z;sectionDeltaX=x/48;sectionDeltaZ=z/48;
        try{offsetUnitsX=Math.multiplyExact(x,128);offsetUnitsZ=Math.multiplyExact(z,128);}catch(ArithmeticException error){throw new IllegalStateException("Whole-RAM carrier basis exceeds pixel-coordinate range: offset="+x+","+z,error);}
    }
    public int logicalX(int carrier){return Math.subtractExact(carrier,sectionDeltaX);}
    public int logicalZ(int carrier){return Math.subtractExact(carrier,sectionDeltaZ);}
    public int carrierX(int logical){return Math.addExact(logical,sectionDeltaX);}
    public int carrierZ(int logical){return Math.addExact(logical,sectionDeltaZ);}
}
