package dev.tabletop3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;

/** Original native cuboid artwork. Coordinates are centers on a 32 by 48 face. */
final class HandModels {
    record Part(double x,double y,double w,double h,double roll,int layer,Material material) {}
    private record Face(boolean mahjong,String value) {}
    private static final Map<Face,List<Part>> CACHE=new ConcurrentHashMap<>();
    private static final Material CREAM=Material.SMOOTH_QUARTZ,INK=Material.BLACK_CONCRETE,
        RED=Material.RED_CONCRETE,BLUE=Material.BLUE_CONCRETE,GREEN=Material.GREEN_CONCRETE;

    static List<Part> of(boolean mahjong,String face){
        return CACHE.computeIfAbsent(new Face(mahjong,face.isEmpty()?"back":face),key->{
            List<Part> parts=new ArrayList<>();
            if(key.mahjong())tile(parts,key.value());else card(parts,key.value());
            return List.copyOf(parts);
        });
    }
    static List<Part> cardBody(){
        List<Part> parts=new ArrayList<>();rounded(parts,16,24,32,48,2,0,CREAM);return List.copyOf(parts);
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
        for(int i=0;i<8;i++)box(p,x,y,2*r*Math.cos(Math.PI/16),2*r*Math.sin(Math.PI/16),i*Math.PI/8,layer,m);
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
    private static Material color(char c){return switch(c){
        case 'r'->RED;case 'b'->BLUE;case 'y'->Material.YELLOW_CONCRETE;case 'p'->Material.PURPLE_CONCRETE;default->INK;
    };}
    private static void digit(List<Part> p,int n,double x,double y,double w,double h,double t,int layer,Material m){
        switch(n){
            case 0->path(p,x,y,w,h,t,layer,m,.25,0,.75,0,1,.2,1,.8,.75,1,.25,1,0,.8,0,.2,.25,0);
            case 1->{path(p,x,y,w,h,t,layer,m,.1,.2,.55,0,.55,1);path(p,x,y,w,h,t,layer,m,.15,1,.95,1);}
            case 2->path(p,x,y,w,h,t,layer,m,0,.15,.25,0,.75,0,1,.2,1,.35,0,1,1,1);
            case 3->path(p,x,y,w,h,t,layer,m,0,0,.75,0,1,.18,1,.32,.7,.5,.25,.5,.7,.5,1,.68,1,.82,.75,1,0,1);
            case 4->{path(p,x,y,w,h,t,layer,m,.75,1,.75,0,0,.65,1,.65);}
            case 5->path(p,x,y,w,h,t,layer,m,1,0,0,0,0,.48,.75,.48,1,.66,1,.82,.75,1,0,1);
            case 6->path(p,x,y,w,h,t,layer,m,.85,0,.35,0,0,.25,0,.8,.25,1,.75,1,1,.8,1,.63,.75,.48,0,.48);
            case 7->path(p,x,y,w,h,t,layer,m,0,0,1,0,.35,1);
            case 8->path(p,x,y,w,h,t,layer,m,.25,.5,0,.32,0,.18,.25,0,.75,0,1,.18,1,.32,.75,.5,.25,.5,0,.68,0,.82,.25,1,.75,1,1,.82,1,.68,.75,.5);
            case 9->path(p,x,y,w,h,t,layer,m,1,.52,.25,.52,0,.37,0,.2,.25,0,.75,0,1,.2,1,.75,.65,1,.15,1);
        }
    }
    private static void number(List<Part> p,int number,double x,double y,double w,double h,double t){
        if(number==10){digit(p,1,x,y,w*.36,h,t,2,CREAM);digit(p,0,x+w*.55,y,w*.45,h,t,2,CREAM);}
        else digit(p,number,x,y,w,h,t,2,CREAM);
        if(number==6||number==9)line(p,x+w*.1,y+h+3,x+w*.9,y+h+3,t*.8,2,CREAM);
    }
    private static void reverse(List<Part> p,double x,double y,double w,double h,double t,int layer){
        path(p,x,y,w,h,t,layer,CREAM,0,.5,0,.2,.2,0,1,0,.72,-.15,1,0,.72,.15);
        path(p,x,y,w,h,t,layer,CREAM,1,.5,1,.8,.8,1,0,1,.28,.85,0,1,.28,1.15);
    }
    private static void skip(List<Part> p,double x,double y,double r,double t){
        ring(p,x,y,r,r,t,2,CREAM,12);line(p,x-r*.8,y+r*.8,x+r*.8,y-r*.8,t,2,CREAM);
    }
    private static void diamond(List<Part> p){
        Material[] colors={RED,BLUE,Material.YELLOW_CONCRETE,Material.PURPLE_CONCRETE};
        for(int i=0;i<4;i++)box(p,16+(i%2==0?-5:5),24+(i/2==0?-5:5),7,7,Math.PI/4,1,colors[i]);
    }
    private static void card(List<Part> p,String face){
        Material background=face.equals("wild")||face.equals("swap")||face.equals("back")?INK:color(face.charAt(0));
        rounded(p,16,24,27,43,2,0,background);
        if(face.equals("back")){diamond(p);return;}
        String value=face.equals("wild")?"8":face.equals("swap")?"Swap":face.substring(1);
        if(face.equals("wild"))diamond(p);
        if(value.matches("[0-9]+"))number(p,Integer.parseInt(value),value.equals("10")?7:11,16,value.equals("10")?18:10,16,2.4);
        else if(value.equals("Skip"))skip(p,16,24,7,2.1);
        else if(value.equals("Reverse"))reverse(p,9,16,14,16,2.1,2);
        else if(value.equals("Draw1")){
            line(p,9,17,15,17,1.8,2,CREAM);line(p,12,14,12,20,1.8,2,CREAM);digit(p,1,18,13,5,8,1.8,2,CREAM);
            box(p,16,31,10,13,-.18,1,CREAM);box(p,16,31,7,10,-.18,2,background);
        }else if(value.equals("Swap")){
            box(p,11,25,9,16,-.18,1,BLUE);box(p,21,27,9,16,.18,2,RED);
            reverse(p,9,17,14,16,1.8,3);
        }
        List<Part> corner=new ArrayList<>();
        if(value.matches("[0-9]+"))number(corner,Integer.parseInt(value),5,5,value.equals("10")?9:4,6,.95);
        else if(value.equals("Skip"))skip(corner,7.5,8,2.3,.85);
        else if(value.equals("Draw1")){
            line(corner,5,8,8,8,.8,2,CREAM);line(corner,6.5,6.5,6.5,9.5,.8,2,CREAM);digit(corner,1,10,5,2.4,6,.8,2,CREAM);
        }else reverse(corner,5,6,5,4,.8,2);
        p.addAll(corner);for(Part part:corner)p.add(new Part(32-part.x(),48-part.y(),part.w(),part.h(),part.roll()+Math.PI,part.layer(),part.material()));
        if(!face.equals("wild")&&!face.equals("swap"))switch(face.charAt(0)){
            case 'r'->box(p,6,18,4,4,Math.PI/4,2,CREAM);
            case 'b'->box(p,6,18,4,4,0,2,CREAM);
            case 'y'->{line(p,4,20,6,16,1,2,CREAM);line(p,6,16,8,20,1,2,CREAM);line(p,4,20,8,20,1,2,CREAM);}
            case 'p'->{line(p,3.5,18,8.5,18,1.2,2,CREAM);line(p,6,15.5,6,20.5,1.2,2,CREAM);}
        }
    }
    /** Hand-drawn character centerlines; each stroke is a rotated native cuboid. */
    private static void glyph(List<Part> p,char glyph,double x,double y,double w,double h,double t,Material m){
        double[][] strokes=switch(glyph){
            case '一'->new double[][]{{0,5.4,10,4.8}};
            case '二'->new double[][]{{1.3,2.7,8.8,2.3},{0,8,10,7.7}};
            case '三'->new double[][]{{.6,1.6,9.4,1.2},{1.5,5,8.4,4.6},{0,8.7,10,8.4}};
            case '四'->new double[][]{{.8,1,.8,9.1},{.8,1,9.2,.8,9.2,9.1},{.8,9.1,9.2,9.1},{3.6,1.3,3.5,4.5,2.1,6.5},{6.7,1.3,6.7,6,8.5,6}};
            case '五'->new double[][]{{1,1,9,1},{4.9,1,3.5,9},{1.5,4.8,8,4.5,7.8,9},{0,9.1,10,8.8}};
            case '六'->new double[][]{{4.5,.5,5.5,2.2},{0,3.4,10,3.1},{3.6,5.3,2.4,7.8,.7,9.5},{6.5,5.2,8.2,7.7,9.3,9.5}};
            case '七'->new double[][]{{0,4.4,10,2.8},{4.2,.3,4.1,8.1,4.7,9.3,8.8,9.3,9.4,8.1}};
            case '八'->new double[][]{{3.4,1,3,4.7,1.8,7.7,.3,9.4},{6.1,1,6.7,4.8,8,7.6,9.8,9.3}};
            case '九'->new double[][]{{.3,3.1,7.6,2.6,7.5,8.7,8.1,9.4,9.6,9.3,10,8.1},{3.5,.1,3.3,5.6,2.2,8.3,.3,9.8}};
            case '萬'->new double[][]{{0,1,10,.8},{2.6,0,2.6,1.9},{7.4,0,7.4,1.9},{1.4,2.6,8.6,2.6,8.6,5.7,1.4,5.7,1.4,2.6},{1.4,4.1,8.6,4.1},{5,2.6,5,8.8},{.5,9.7,.5,7,9.5,7,9.5,9.8,8.4,9.7},{1.9,8.8,8.1,8.8},{7.9,7.9,8.5,9.4}};
            case '東'->new double[][]{{0,1.8,10,1.5},{5,0,5,10},{1.5,3.3,8.5,3.3,8.5,6.5,1.5,6.5,1.5,3.3},{1.5,4.9,8.5,4.9},{4.9,6.4,3,8.3,0,9.7},{5.1,6.4,7,8.3,10,9.7}};
            case '南'->new double[][]{{0,1.6,10,1.4},{5,0,5,3.2},{.8,9.8,.8,3.2,9.2,3.2,9.2,9.8,8,9.6},{3,3.7,3.9,4.8},{7,3.6,6,4.9},{2.4,5.1,7.6,5.1},{2.4,7.1,7.6,7.1},{5,5.1,5,9.5}};
            case '西'->new double[][]{{0,.6,10,.4},{3.5,.5,3.4,5.5,2.1,7},{6.5,.5,6.5,6.8,8.4,6.8},{.7,3.1,9.3,3,9.3,9.6,.7,9.6,.7,3.1}};
            case '北'->new double[][]{{3.7,0,3.7,10},{0,3.8,3.7,3.5},{0,8.7,3.7,7.3},{6.1,0,6.1,8.8,6.9,9.5,9.4,9.4,9.8,8.2},{6.1,4.3,9.6,2}};
            case '發'->new double[][]{{1,.5,3.6,.5,2,2.3,0,3.9},{.7,1.4,2.3,2.4},{6.1,0,7.3,1.5,10,3.9},{7.3,1.5,8.7,.2},{8.2,2.7,9.7,1.4},{2.7,3.8,7.5,3.8},{.8,5,4.3,5,4.2,6.6,1.2,6.6,1.1,8.5,4.2,8.5,4.1,9.6,3,9.7},{5.9,4.8,8.5,4.8,8.5,6,9.8,6},{6,4.8,5.6,6.4},{5.2,7,9.2,7,7.8,8.6,5,10},{5.8,7.3,7.3,8.7,10,10}};
            case '中'->new double[][]{{1,2.5,9,2.3,9,6.9,1,7.1,1,2.5},{5,.1,5,10}};
            case '梅'->new double[][]{{2.4,0,2.4,10},{0,3,4.3,2.7},{2.4,3.4,0,7.2},{2.5,4.2,4,6.1},{6,.1,4.7,2.6},{5.5,1.3,10,1.1},{5.4,3,9.4,3,8.8,9.9,7.6,9.7},{5.4,3,4.9,8.2,9.6,8.2},{4.3,5.7,10,5.7},{6.7,3.8,7.6,4.7},{6.4,6.7,7.4,7.4}};
            case '蘭'->new double[][]{{0,1,10,.8},{2.6,0,2.6,2},{7.4,0,7.4,2},{.6,10,.6,2.8,4.1,2.8,4.1,5,.6,5},{.6,3.9,4.1,3.9},{5.9,2.8,9.4,2.8,9.4,10,8.5,9.8},{5.9,2.8,5.9,5,9.4,5},{5.9,3.9,9.4,3.9},{2.2,6,7.8,6,7.8,7.8,2.2,7.8,2.2,6},{5,5.7,5,9.9},{3.7,8.4,1.8,9.8},{6.2,8.4,8.3,9.8}};
            case '竹'->new double[][]{{2.2,0,.2,3.5},{1.5,1.6,4.4,1.4},{3.1,1.5,3.1,9.8},{4,2,4.7,3.2},{7,0,5.4,3.5},{6.4,1.6,10,1.4},{8.3,1.5,8.3,9.5,7.3,9.8}};
            case '菊'->new double[][]{{0,1,10,.8},{2.6,0,2.6,2},{7.4,0,7.4,2},{2.7,2.6,.1,5.4},{2,3.6,9.3,3.6,9.3,9.7,8,9.8},{2.4,5.3,3.5,6.6},{7.1,5.2,6.1,6.7},{1.4,7.2,8,7.2},{4.7,4.8,4.7,9.8},{4.6,7.4,1.3,9.5},{4.9,7.4,7.8,9.5}};
            case '春'->new double[][]{{1.2,1.4,8.8,1.2},{1.2,3,8.8,2.8},{0,4.6,10,4.4},{5,0,4.5,3.6,2.8,5.9,0,7.1},{6.2,4.5,8,6.1,10,7},{2.5,6.3,7.5,6.3,7.5,9.8,2.5,9.8,2.5,6.3},{2.5,8,7.5,8}};
            case '夏'->new double[][]{{0,.5,10,.3},{5,.4,4.2,1.6},{1.8,1.8,8.2,1.8,8.2,5.3,1.8,5.3,1.8,1.8},{1.8,3,8.2,3},{1.8,4.1,8.2,4.1},{3.6,5.3,1.9,6.6,.1,7.5},{2.9,6.3,8.5,6.3,6.1,8.3,0,10},{2.6,6.5,4.8,8.3,10,10}};
            case '秋'->new double[][]{{.5,1.5,4.4,.1},{2.5,1,2.5,10},{0,3.8,5,3.5},{2.4,4.1,.1,8},{2.6,4.8,4.2,6.6},{5.5,2.6,5,4.8},{9.7,2.6,8.8,4.5},{7.3,.1,7.2,5.8,6.3,8.4,4.2,10},{7.2,5.6,8.3,8.3,10,10}};
            case '冬'->new double[][]{{3.4,0,1.8,2.5,.1,3.8},{2.6,1.4,8.6,1.2,6.6,3.6,3.8,5.1,0,6.4},{2.1,2.7,4.5,4.7,7.4,5.8,10,6.2},{3.6,6.8,6.2,7.8},{3.7,9,6.1,9.9}};
            default->throw new IllegalArgumentException("Unknown tile glyph: "+glyph);
        };
        for(double[] stroke:strokes)path(p,x,y,w/10,h/10,t,1,m,stroke);
    }
    private static void pip(List<Part> p,double x,double y,double radius,Material m,int layer){
        ring(p,x,y,radius,radius,.85,layer,m,12);
        ring(p,x,y,radius*.56,radius*.56,.55,layer,m,8);box(p,x,y,.9,.9,Math.PI/4,layer,m);
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
            glyph(p,"一二三四五六七八九".charAt(n-1),6,5,20,14,1.25,red?RED:INK);
            glyph(p,'萬',5,24,22,20,1.15,RED);return;
        }
        if(suit=='z'&&n!=5){glyph(p,"東南西北白發中".charAt(n-1),5,9,22,30,1.35,n==6?GREEN:n==7?RED:INK);return;}
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
            glyph(p,"梅蘭竹菊春夏秋冬".charAt(n-1),5,5,22,22,.9,n<=4?BLUE:RED);line(p,15,43,18,32,1,1,GREEN);
            box(p,12,37,6,2.1,.5,1,GREEN);box(p,21,39,6,2.1,-.5,1,GREEN);
            Material petals=n<=4?Material.PURPLE_CONCRETE:Material.YELLOW_CONCRETE;
            for(int i=0;i<5;i++){double a=i*2*Math.PI/5;box(p,18+3*Math.cos(a),31+3*Math.sin(a),3.5,2.2,a,2,petals);}
            circle(p,18,31,1.2,3,RED);
        }
    }
    private HandModels(){}
}
