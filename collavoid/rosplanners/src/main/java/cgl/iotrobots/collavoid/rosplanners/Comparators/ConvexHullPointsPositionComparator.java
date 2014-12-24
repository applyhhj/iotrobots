package cgl.iotrobots.collavoid.rosplanners.Comparators;

import cgl.iotrobots.collavoid.rosplanners.utils.ConvexHullPoint;
import cgl.iotrobots.collavoid.rosplanners.utils.Vector2;

import java.util.Comparator;


public class ConvexHullPointsPositionComparator implements Comparator<ConvexHullPoint>{
    public int compare(ConvexHullPoint chp1,ConvexHullPoint chp2){
        if (Vector2.absSqr(chp1.getX(), chp1.getY()) <= Vector2.absSqr(chp2.getX(), chp2.getY()))
            return -1;
        else
            return 1;
    }

}