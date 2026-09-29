package dev.tabletop3d;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;

final class HandSprites {
    record Rect(int x,int y,int width,int height,Material material) {}
    private record Face(boolean mahjong,String value) {}
    private record Run(int x,int width,Material material) {}
    private record Color(int rgb,Material material) {}
    private static final List<Color> PALETTE=List.of(
        new Color(0xfff7dc,Material.SMOOTH_QUARTZ),
        new Color(0x23354b,Material.BLACK_CONCRETE),
        new Color(0xc72f43,Material.RED_CONCRETE),
        new Color(0x187447,Material.GREEN_CONCRETE),
        new Color(0x439365,Material.LIME_CONCRETE),
        new Color(0x255eac,Material.BLUE_CONCRETE),
        new Color(0xe6ad25,Material.YELLOW_CONCRETE),
        new Color(0x8643b6,Material.PURPLE_CONCRETE),
        new Color(0x2389c7,Material.LIGHT_BLUE_CONCRETE),
        new Color(0x373a3e,Material.GRAY_CONCRETE));
    private static final Map<Face,List<Rect>> CACHE=new ConcurrentHashMap<>();

    private HandSprites() {}

    static List<Rect> of(boolean mahjong,String face) {
        String value=face.isEmpty()?"back":face;
        boolean legal=value.equals("back")||(mahjong?value.matches("[mps][0-9]|z[1-7]|f[1-8]")
            :value.matches("[rbyp]([1-8]|Skip|Reverse|Draw2|Draw3)|wild"));
        if(!legal)return rectangles(HandArt.draw(mahjong,value));
        return CACHE.computeIfAbsent(new Face(mahjong,value),key->rectangles(HandArt.draw(key.mahjong(),key.value())));
    }

    static List<Rect> rectangles(BufferedImage image) {
        List<Rect> result=new ArrayList<>();
        Map<Run,Integer> previous=new HashMap<>();
        for(int y=0;y<image.getHeight();y++) {
            Map<Run,Integer> current=new HashMap<>();
            for(int x=0;x<image.getWidth();) {
                Material material=material(image.getRGB(x,y));
                if(material==null){x++;continue;}
                int start=x++;
                while(x<image.getWidth()&&material(image.getRGB(x,y))==material)x++;
                Run run=new Run(start,x-start,material);
                Integer index=previous.get(run);
                if(index==null) {
                    index=result.size();result.add(new Rect(start,y,run.width(),1,material));
                }else {
                    Rect rect=result.get(index);
                    result.set(index,new Rect(rect.x(),rect.y(),rect.width(),rect.height()+1,material));
                }
                current.put(run,index);
            }
            previous=current;
        }
        return List.copyOf(result);
    }

    private static Material material(int argb) {
        if((argb>>>24)==0)return null;
        Material nearest=null;int distance=Integer.MAX_VALUE;
        for(Color color:PALETTE) {
            int red=((argb>>>16)&255)-((color.rgb()>>>16)&255);
            int green=((argb>>>8)&255)-((color.rgb()>>>8)&255);
            int blue=(argb&255)-(color.rgb()&255);
            int candidate=red*red+green*green+blue*blue;
            if(candidate<distance){distance=candidate;nearest=color.material();}
        }
        return nearest;
    }
}
