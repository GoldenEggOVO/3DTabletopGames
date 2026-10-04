package dev.tabletop3d;

import dev.tabletop3d.ui.GameSymbols;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;

/** Original native cuboid artwork. Coordinates are centers on a 32 by 48 face. */
final class HandModels {
    record Part(double x,double y,double w,double h,double roll,int layer,Material material,int plane) {
        Part(double x,double y,double w,double h,double roll,int layer,Material material){this(x,y,w,h,roll,layer,material,0);}
        double relief(){return layer*.0012+plane*.0002;}
    }
    private static final Map<String,List<Part>> CACHE=new ConcurrentHashMap<>();
    private static final Material CREAM=Material.SMOOTH_QUARTZ,INK=Material.BLACK_CONCRETE,
        RED=Material.RED_CONCRETE,BLUE=Material.BLUE_CONCRETE,GREEN=Material.GREEN_CONCRETE;
    static List<Part> of(String face){
        return CACHE.computeIfAbsent(face.isEmpty()?"back":face,key->{
            List<Part> parts=new ArrayList<>();
            tile(parts,key);
            return separatePlanes(parts);
        });
    }
    private static List<Part> separatePlanes(List<Part> source){
        // Reuse a relief plane for disjoint strokes; intersecting strokes get distinct depths.
        List<Part> parts=new ArrayList<>();List<Area> footprints=new ArrayList<>();
        for(Part part:new LinkedHashSet<>(source)){
            AffineTransform transform=AffineTransform.getTranslateInstance(part.x(),part.y());transform.rotate(part.roll());
            Area footprint=new Area(transform.createTransformedShape(new Rectangle2D.Double(-part.w()/2,-part.h()/2,part.w(),part.h())));
            BitSet occupied=new BitSet();
            for(int i=0;i<parts.size();i++){
                Part previous=parts.get(i);if(previous.layer()!=part.layer())continue;
                Area overlap=new Area(footprint);overlap.intersect(footprints.get(i));
                if(!overlap.isEmpty()&&overlap.getBounds2D().getWidth()>=.001&&overlap.getBounds2D().getHeight()>=.001)occupied.set(previous.plane());
            }
            parts.add(new Part(part.x(),part.y(),part.w(),part.h(),part.roll(),part.layer(),part.material(),occupied.nextClearBit(0)));
            footprints.add(footprint);
        }
        return List.copyOf(parts);
    }
    private static void box(List<Part> p,double x,double y,double w,double h,double roll,int layer,Material m){
        p.add(new Part(x,y,w,h,roll,layer,m));
    }
    private static void line(List<Part> p,double x1,double y1,double x2,double y2,double thickness,int layer,Material m){
        box(p,(x1+x2)/2,(y1+y2)/2,Math.hypot(x2-x1,y2-y1)+thickness/3,thickness,Math.atan2(y2-y1,x2-x1),layer,m);
    }
    private static void path(List<Part> p,double x,double y,double w,double h,double thickness,int layer,Material m,double... points){
        for(int i=2;i<points.length;i+=2)line(p,x+points[i-2]*w,y+points[i-1]*h,x+points[i]*w,y+points[i+1]*h,thickness,layer,m);
    }
    private static void circle(List<Part> p,double x,double y,double r,int layer,Material m){
        for(int i=0;i<4;i++)box(p,x,y,2*r*Math.cos(Math.PI/8),2*r*Math.sin(Math.PI/8),i*Math.PI/4,layer,m);
    }
    private static void ring(List<Part> p,double x,double y,double rx,double ry,double thickness,int layer,Material m,int segments){
        for(int i=0;i<segments;i++){
            double a=i*2*Math.PI/segments,b=(i+1)*2*Math.PI/segments;
            line(p,x+rx*Math.cos(a),y+ry*Math.sin(a),x+rx*Math.cos(b),y+ry*Math.sin(b),thickness,layer,m);
        }
    }
    private static void rounded(List<Part> p,double x,double y,double w,double h,double radius,int layer,Material m){
        box(p,x,y,w-2*radius,h,0,layer,m);box(p,x,y,w,h-2*radius,0,layer,m);
        for(int dx:new int[]{-1,1})for(int dy:new int[]{-1,1})circle(p,x+dx*(w/2-radius),y+dy*(h/2-radius),radius,layer,m);
    }
    /** Hand-drawn character centerlines; each stroke is a rotated native cuboid. */
    private static void glyph(List<Part> p,char glyph,double x,double y,double w,double h,double t,Material m){
        double[][] strokes=switch(String.valueOf(glyph)){
            case GameSymbols.ONE->new double[][]{{0,5.4,10,4.8}};
            case GameSymbols.TWO->new double[][]{{1.3,2.7,8.8,2.3},{0,8,10,7.7}};
            case GameSymbols.THREE->new double[][]{{.6,1.6,9.4,1.2},{1.5,5,8.4,4.6},{0,8.7,10,8.4}};
            case GameSymbols.FOUR->new double[][]{{.8,1,.8,9.1},{.8,1,9.2,.8,9.2,9.1},{.8,9.1,9.2,9.1},{3.6,1.3,3.5,4.5,2.1,6.5},{6.7,1.3,6.7,6,8.5,6}};
            case GameSymbols.FIVE->new double[][]{{1,1,9,1},{4.9,1,3.5,9},{1.5,4.8,8,4.5,7.8,9},{0,9.1,10,8.8}};
            case GameSymbols.SIX->new double[][]{{4.5,.5,5.5,2.2},{0,3.4,10,3.1},{3.6,5.3,2.4,7.8,.7,9.5},{6.5,5.2,8.2,7.7,9.3,9.5}};
            case GameSymbols.SEVEN->new double[][]{{0,4.4,10,2.8},{4.2,.3,4.1,8.1,4.7,9.3,8.8,9.3,9.4,8.1}};
            case GameSymbols.EIGHT->new double[][]{{3.4,1,3,4.7,1.8,7.7,.3,9.4},{6.1,1,6.7,4.8,8,7.6,9.8,9.3}};
            case GameSymbols.NINE->new double[][]{{.3,3.1,7.6,2.6,7.5,8.7,8.1,9.4,9.6,9.3,10,8.1},{3.5,.1,3.3,5.6,2.2,8.3,.3,9.8}};
            case GameSymbols.CHARACTERS->new double[][]{{0,1,10,.8},{2.6,0,2.6,1.9},{7.4,0,7.4,1.9},{1.4,2.6,8.6,2.6,8.6,5.7,1.4,5.7,1.4,2.6},{1.4,4.1,8.6,4.1},{5,2.6,5,8.8},{.5,9.7,.5,7,9.5,7,9.5,9.8,8.4,9.7},{1.9,8.8,8.1,8.8},{7.9,7.9,8.5,9.4}};
            case GameSymbols.EAST->new double[][]{{0,1.8,10,1.5},{5,0,5,10},{1.5,3.3,8.5,3.3,8.5,6.5,1.5,6.5,1.5,3.3},{1.5,4.9,8.5,4.9},{4.9,6.4,3,8.3,0,9.7},{5.1,6.4,7,8.3,10,9.7}};
            case GameSymbols.SOUTH->new double[][]{{0,1.6,10,1.4},{5,0,5,3.2},{.8,9.8,.8,3.2,9.2,3.2,9.2,9.8,8,9.6},{3,3.7,3.9,4.8},{7,3.6,6,4.9},{2.4,5.1,7.6,5.1},{2.4,7.1,7.6,7.1},{5,5.1,5,9.5}};
            case GameSymbols.WEST->new double[][]{{0,.6,10,.4},{3.5,.5,3.4,5.5,2.1,7},{6.5,.5,6.5,6.8,8.4,6.8},{.7,3.1,9.3,3,9.3,9.6,.7,9.6,.7,3.1}};
            case GameSymbols.NORTH->new double[][]{{3.7,0,3.7,10},{0,3.8,3.7,3.5},{0,8.7,3.7,7.3},{6.1,0,6.1,8.8,6.9,9.5,9.4,9.4,9.8,8.2},{6.1,4.3,9.6,2}};
            case GameSymbols.GREEN_DRAGON->new double[][]{{1,.5,3.6,.5,2,2.3,0,3.9},{.7,1.4,2.3,2.4},{6.1,0,7.3,1.5,10,3.9},{7.3,1.5,8.7,.2},{8.2,2.7,9.7,1.4},{2.7,3.8,7.5,3.8},{.8,5,4.3,5,4.2,6.6,1.2,6.6,1.1,8.5,4.2,8.5,4.1,9.6,3,9.7},{5.9,4.8,8.5,4.8,8.5,6,9.8,6},{6,4.8,5.6,6.4},{5.2,7,9.2,7,7.8,8.6,5,10},{5.8,7.3,7.3,8.7,10,10}};
            case GameSymbols.RED_DRAGON->new double[][]{{1,2.5,9,2.3,9,6.9,1,7.1,1,2.5},{5,.1,5,10}};
            case GameSymbols.PLUM->new double[][]{{2.4,0,2.4,10},{0,3,4.3,2.7},{2.4,3.4,0,7.2},{2.5,4.2,4,6.1},{6,.1,4.7,2.6},{5.5,1.3,10,1.1},{5.4,3,9.4,3,8.8,9.9,7.6,9.7},{5.4,3,4.9,8.2,9.6,8.2},{4.3,5.7,10,5.7},{6.7,3.8,7.6,4.7},{6.4,6.7,7.4,7.4}};
            case GameSymbols.ORCHID->new double[][]{{0,1,10,.8},{2.6,0,2.6,2},{7.4,0,7.4,2},{.6,10,.6,2.8,4.1,2.8,4.1,5,.6,5},{.6,3.9,4.1,3.9},{5.9,2.8,9.4,2.8,9.4,10,8.5,9.8},{5.9,2.8,5.9,5,9.4,5},{5.9,3.9,9.4,3.9},{2.2,6,7.8,6,7.8,7.8,2.2,7.8,2.2,6},{5,5.7,5,9.9},{3.7,8.4,1.8,9.8},{6.2,8.4,8.3,9.8}};
            case GameSymbols.BAMBOO->new double[][]{{2.2,0,.2,3.5},{1.5,1.6,4.4,1.4},{3.1,1.5,3.1,9.8},{4,2,4.7,3.2},{7,0,5.4,3.5},{6.4,1.6,10,1.4},{8.3,1.5,8.3,9.5,7.3,9.8}};
            case GameSymbols.CHRYSANTHEMUM->new double[][]{{0,1,10,.8},{2.6,0,2.6,2},{7.4,0,7.4,2},{2.7,2.6,.1,5.4},{2,3.6,9.3,3.6,9.3,9.7,8,9.8},{2.4,5.3,3.5,6.6},{7.1,5.2,6.1,6.7},{1.4,7.2,8,7.2},{4.7,4.8,4.7,9.8},{4.6,7.4,1.3,9.5},{4.9,7.4,7.8,9.5}};
            case GameSymbols.SPRING->new double[][]{{1.2,1.4,8.8,1.2},{1.2,3,8.8,2.8},{0,4.6,10,4.4},{5,0,4.5,3.6,2.8,5.9,0,7.1},{6.2,4.5,8,6.1,10,7},{2.5,6.3,7.5,6.3,7.5,9.8,2.5,9.8,2.5,6.3},{2.5,8,7.5,8}};
            case GameSymbols.SUMMER->new double[][]{{0,.5,10,.3},{5,.4,4.2,1.6},{1.8,1.8,8.2,1.8,8.2,5.3,1.8,5.3,1.8,1.8},{1.8,3,8.2,3},{1.8,4.1,8.2,4.1},{3.6,5.3,1.9,6.6,.1,7.5},{2.9,6.3,8.5,6.3,6.1,8.3,0,10},{2.6,6.5,4.8,8.3,10,10}};
            case GameSymbols.AUTUMN->new double[][]{{.5,1.5,4.4,.1},{2.5,1,2.5,10},{0,3.8,5,3.5},{2.4,4.1,.1,8},{2.6,4.8,4.2,6.6},{5.5,2.6,5,4.8},{9.7,2.6,8.8,4.5},{7.3,.1,7.2,5.8,6.3,8.4,4.2,10},{7.2,5.6,8.3,8.3,10,10}};
            case GameSymbols.WINTER->new double[][]{{3.4,0,1.8,2.5,.1,3.8},{2.6,1.4,8.6,1.2,6.6,3.6,3.8,5.1,0,6.4},{2.1,2.7,4.5,4.7,7.4,5.8,10,6.2},{3.6,6.8,6.2,7.8},{3.7,9,6.1,9.9}};
            default->throw new IllegalArgumentException("Unknown tile glyph: "+glyph);
        };
        for(double[] stroke:strokes)path(p,x,y,w/10,h/10,t,1,m,stroke);
    }
    private static void pip(List<Part> p,double x,double y,double radius,Material m,int layer){
        ring(p,x,y,radius,radius,.85,layer,m,8);
        ring(p,x,y,radius*.56,radius*.56,.55,layer,m,6);box(p,x,y,.9,.9,Math.PI/4,layer,m);
    }
    private static void bamboo(List<Part> p,double x,double y,Material m){
        line(p,x-.4,y-4,x+.4,y+4,1.9,1,m);
        for(int i=-1;i<=1;i++)line(p,x-2,y+i*3-1,x+2,y+i*3-.6,1.1,1,m);
        line(p,x-.3,y-2,x-.1,y+2,.45,2,m==RED?CREAM:Material.LIME_CONCRETE);
    }
    private static void bird(List<Part> p){
        circle(p,15,26,6.5,1,GREEN);circle(p,20,15,3.3,1,GREEN);
        path(p,0,0,1,1,2.1,1,GREEN,20,17,20,22,17,26);
        for(int i=0;i<4;i++)line(p,13+i,27,6+i*2,39+i*.8,1.3,1,GREEN);
        for(int i=0;i<4;i++)line(p,11+i*2,25,15+i*1.4,31,1.1,2,BLUE);
        line(p,23,15,26,16,1.1,2,RED);line(p,18,12,17,9,1.1,2,RED);line(p,20,12,21,9,1.1,2,RED);
        line(p,15,32,15,40,1,2,RED);line(p,18,32,20,39,1,2,RED);
        line(p,12,40,17,40,.8,2,RED);line(p,18,39,23,39,.8,2,RED);
        circle(p,21,14,.9,2,CREAM);box(p,21.2,14,.5,.5,0,3,INK);
    }
    private static void tile(List<Part> p,String face){
        if(face.equals("back")){rounded(p,16,24,27,43,2,0,GREEN);return;}
        char suit=face.charAt(0);int n=face.charAt(1)-'0';boolean red=n==0;if(red)n=5;
        if(suit=='m'){
            glyph(p,GameSymbols.NUMERALS.charAt(n-1),6,5,20,14,1.25,red?RED:INK);
            glyph(p,GameSymbols.CHARACTERS.charAt(0),5,24,22,20,1.15,RED);return;
        }
        if(suit=='z'&&n!=5){glyph(p,GameSymbols.HONORS.charAt(n-1),5,9,22,30,1.35,n==6?GREEN:n==7?RED:INK);return;}
        if(suit=='p'&&n==1){
            pip(p,16,24,11,BLUE,1);ring(p,16,24,8.2,8.2,.7,1,BLUE,16);pip(p,16,24,4.7,RED,2);
            for(int i=0;i<8;i++){double a=i*Math.PI/4;box(p,16+6.5*Math.cos(a),24+6.5*Math.sin(a),1.2,2.7,a,1,BLUE);}
        }else if(suit=='s'&&n==1)bird(p);
        else if(suit=='p'||suit=='s'){
            int[][] points=HandArt.positions(n);
            for(int i=0;i<points.length;i++){
                Material m=red?RED:suit=='p'?(n==5&&i==4?RED:BLUE):(n==7&&i==0?RED:GREEN);
                if(suit=='p')pip(p,points[i][0],points[i][1],n>=7?3.2:4.2,m,1);else bamboo(p,points[i][0],points[i][1],m);
            }
        }else if(suit=='z'){
            path(p,0,0,1,1,1.4,1,BLUE,7,9,25,9,25,39,7,39,7,9);
            path(p,0,0,1,1,.7,1,BLUE,10,12,22,12,22,36,10,36,10,12);
            for(int y=13;y<=34;y+=5){line(p,7,y,10,y+2,.6,1,BLUE);line(p,22,y,25,y+2,.6,1,BLUE);}
        }else if(suit=='f'){
            glyph(p,GameSymbols.FLOWERS.charAt(n-1),5,5,22,22,.9,n<=4?BLUE:RED);line(p,15,43,18,32,1,1,GREEN);
            box(p,12,37,6,2.1,.5,1,GREEN);box(p,21,39,6,2.1,-.5,1,GREEN);
            Material petals=n<=4?Material.PURPLE_CONCRETE:Material.YELLOW_CONCRETE;
            for(int i=0;i<5;i++){double a=i*2*Math.PI/5;box(p,18+3*Math.cos(a),31+3*Math.sin(a),3.5,2.2,a,2,petals);}
            circle(p,18,31,1.2,3,RED);
        }
    }
    private HandModels(){}
}
