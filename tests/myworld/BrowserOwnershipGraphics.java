package orsc.graphics.two;

import java.util.ArrayList;
import java.util.List;

/** Headless drawing hook: Scene still creates, sorts and picks real sprite anchors. */
public final class BrowserOwnershipGraphics extends GraphicsController {
    public final List<Integer> entities=new ArrayList<Integer>();
    public BrowserOwnershipGraphics(){super(512,384,1);}
    @Override public void drawEntity(int id,int x,int y,int width,int height,int movement,int skew,int pick){entities.add(id);}
}
