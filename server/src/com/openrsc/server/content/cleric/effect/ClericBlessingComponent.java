package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.net.rsc.ClericBlessingContract;

/** Bounded component display values, authored by the authoritative effect catalog. */
public final class ClericBlessingComponent {
    private final int kind, magnitude, parameter1, parameter2;
    public ClericBlessingComponent(int kind,int magnitude,int parameter1,int parameter2){
        if(kind<1||kind>ClericBlessingContract.MAX_COMPONENTS||magnitude<1||magnitude>65535
            ||parameter1<0||parameter1>65535||parameter2<0||parameter2>65535)
            throw new IllegalArgumentException("Invalid blessing component");
        this.kind=kind;this.magnitude=magnitude;this.parameter1=parameter1;this.parameter2=parameter2;
    }
    public int getKind(){return kind;}
    public int getMagnitude(){return magnitude;}
    public int getParameter1(){return parameter1;}
    public int getParameter2(){return parameter2;}
}
