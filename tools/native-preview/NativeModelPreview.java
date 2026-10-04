package dev.tabletop3d;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.bukkit.Material;

/** Orthographic projection of the actual native cuboid parts, not a client screenshot. */
public final class NativeModelPreview {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        java.util.List<String> tiles=new ArrayList<>();
        for(char suit:new char[]{'m','p','s'})for(int n=0;n<=9;n++)tiles.add(""+suit+n);
        for(int n=1;n<=7;n++)tiles.add("z"+n);
        for(int n=1;n<=8;n++)tiles.add("f"+n);
        StringBuilder counts=new StringBuilder("kind,face,standing_displays,flat_displays\n");
        render(out,"mahjong",tiles,counts);
        counts.append("furniture,round_table,").append(RoundCardTable.parts().size()).append(",\n");
        Files.writeString(out.resolve("counts.csv"),counts);
    }
    private static void render(Path out,String name,java.util.List<String> faces,StringBuilder counts) throws Exception {
        int cols=10,rows=(faces.size()+cols-1)/cols,w=108,h=170;
        BufferedImage image=new BufferedImage(cols*w,rows*h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(0x19262d));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int i=0;i<faces.size();i++) {
            String face=faces.get(i);var art=HandModels.of(face);
            int body=1,rear=1;
            counts.append(name).append(',').append(face).append(',').append(body+art.size()+rear).append(',').append(body+art.size()).append('\n');
            int x=(i%cols)*w+6,y=(i/cols)*h+6;var old=g.getTransform();
            g.translate(x,y);g.scale(3,3);
            g.setColor(color(Material.SMOOTH_QUARTZ));g.fillRect(0,0,32,48);
            art.stream().sorted(Comparator.comparingInt(HandModels.Part::layer)).forEach(p->part(g,p));
            g.setTransform(old);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.PLAIN,12));g.drawString(face+" · "+(body+art.size()+rear),x,y+158);
        }
        g.dispose();ImageIO.write(image,"png",out.resolve(name+".png").toFile());
    }
    private static void part(Graphics2D g,HandModels.Part part) {
        var old=g.getTransform();g.translate(part.x(),part.y());g.rotate(part.roll());g.setColor(color(part.material()));
        g.fill(new Rectangle2D.Double(-part.w()/2,-part.h()/2,part.w(),part.h()));g.setTransform(old);
    }
    private static Color color(Material material) {
        return new Color(switch(material){
            case SMOOTH_QUARTZ->0xfff5db;case BLACK_CONCRETE->0x15171b;case RED_CONCRETE->0xc43838;
            case BLUE_CONCRETE->0x263b80;case GREEN_CONCRETE->0x216a36;case YELLOW_CONCRETE->0xe3b530;
            case PURPLE_CONCRETE->0x844493;default->0xeeeeee;
        });
    }
}
