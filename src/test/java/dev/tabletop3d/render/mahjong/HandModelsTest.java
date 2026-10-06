package dev.tabletop3d.render.mahjong;

import dev.tabletop3d.render.cards.RoundCardTable;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.awt.geom.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HandModelsTest {
    @Test void overlappingNativePartsNeverShareTheSameSurfacePlane(){
        List<List<HandModels.Part>> meshes=new ArrayList<>();meshes.add(HandModels.of("back"));
        for(char suit:new char[]{'m','p','s'})for(int n=0;n<=9;n++)meshes.add(HandModels.of(""+suit+n));
        for(int n=1;n<=7;n++)meshes.add(HandModels.of("z"+n));
        for(int n=1;n<=8;n++)meshes.add(HandModels.of("f"+n));
        for(var parts:meshes)for(var part:parts){
            assertTrue(part.relief()-part.layer()*.0008<=.0022+1e-9,"Native relief must remain bounded: "+part);
            assertTrue(part.plane()<=5,"Fine relief must stay within the next artwork layer: "+part);
        }
        for(var parts:meshes)for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++){
            var a=parts.get(i);var b=parts.get(j);
            if(Math.abs(a.relief()-b.relief())>1e-9)continue;
            Area overlap=area(a);overlap.intersect(area(b));
            assertTrue(overlap.isEmpty()||overlap.getBounds2D().getWidth()<.001||overlap.getBounds2D().getHeight()<.001,
                    "Overlapping native surfaces: "+a+" / "+b);
        }
    }
    @Test void detailedPatternsUseFewerNativeEntitiesWithoutShrinkingFaces(){
        assertTrue(HandModels.of("p9").size()<=140);
        assertTrue(RoundCardTable.parts().size()<=94);
    }
    @Test void overlappingColorsHaveDifferentDepths(){
        for(String face:List.of("s1","p1","f1","f2","f3","f4","f5","f6","f7","f8")){
            var parts=HandModels.of(face);
            for(int i=0;i<parts.size();i++)for(int j=i+1;j<parts.size();j++){
                var a=parts.get(i);var b=parts.get(j);
                if(Math.abs(a.relief()-b.relief())>1e-9||a.material()==b.material())continue;
                Area overlap=area(a);overlap.intersect(area(b));
                assertTrue(overlap.isEmpty()||overlap.getBounds2D().getWidth()<.001||overlap.getBounds2D().getHeight()<.001,face+" coplanar colors: "+a+" / "+b);
            }
        }
    }
    private static Area area(HandModels.Part part){
        AffineTransform transform=AffineTransform.getTranslateInstance(part.x(),part.y());transform.rotate(part.roll());
        return new Area(transform.createTransformedShape(new Rectangle2D.Double(-part.w()/2,-part.h()/2,part.w(),part.h())));
    }
    @Test void nativePatternsStayOnTheTileFaceWithBoundedEntityCounts(){
        List<String> faces=new ArrayList<>();
        for(char suit:new char[]{'m','p','s'})for(int n=0;n<=9;n++)faces.add(""+suit+n);
        for(int n=1;n<=7;n++)faces.add("z"+n);
        for(int n=1;n<=8;n++)faces.add("f"+n);
        for(String face:faces){
            var parts=HandModels.of(face);assertFalse(parts.isEmpty(),face);assertTrue(parts.size()<=225,face);
            for(var part:parts){
                double rx=Math.abs(Math.cos(part.roll()))*part.w()/2+Math.abs(Math.sin(part.roll()))*part.h()/2;
                double ry=Math.abs(Math.sin(part.roll()))*part.w()/2+Math.abs(Math.cos(part.roll()))*part.h()/2;
                assertTrue(part.x()-rx>=0&&part.x()+rx<=32&&part.y()-ry>=0&&part.y()+ry<=48,face+" "+part);
            }
        }
    }
    @Test void redFivesRemainRecognizableAndHonorGlyphsKeepTheirInk(){
        for(String face:List.of("p0","s0")){
            assertTrue(HandModels.of(face).stream().anyMatch(p->p.material()==Material.RED_CONCRETE));
            assertTrue(HandModels.of(face).stream().noneMatch(p->p.material()==Material.BLUE_CONCRETE||p.material()==Material.GREEN_CONCRETE));
            assertNotEquals(HandModels.of(face),HandModels.of(face.charAt(0)+"5"));
        }
        for(String face:List.of("m1","m9","z1","z2","z3","z4","z6","z7")){
            var parts=HandModels.of(face);
            if(!face.equals("z7"))assertTrue(parts.stream().anyMatch(p->Math.abs(Math.sin(p.roll()*2))>.1),face+" needs angled character strokes");
            assertTrue(parts.stream().anyMatch(p->p.material()==(face.equals("z6")?Material.GREEN_CONCRETE:face.equals("z7")?Material.RED_CONCRETE:Material.BLACK_CONCRETE)),face);
            if(face.charAt(0)=='m')assertTrue(parts.stream().anyMatch(p->p.material()==Material.RED_CONCRETE),"Wan glyph stays red");
        }
    }
}
