package dev.tabletop3d.render.dice;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiceMotionTest {
    @Test void eachAuthoritativeFaceEndsUpwardRegardlessOfTheThrowSeed() {
        Vector3f[] normals={new Vector3f(0,1,0),new Vector3f(1,0,0),new Vector3f(0,0,1),
            new Vector3f(0,0,-1),new Vector3f(-1,0,0),new Vector3f(0,-1,0)};
        for(int face=1;face<=6;face++)for(long seed:new long[]{0,1,92,-18,Long.MAX_VALUE}) {
            DiceMotion motion=new DiceMotion(.28,.61,DiceMotion.rest(.28,1),face,seed);
            DiceMotion.Pose end=motion.pose(DiceMotion.FRAMES);
            Vector3f normal=new Vector3f(normals[face-1]).rotate(end.rotation());
            assertEquals(1,normal.y,1e-5,"Authoritative face "+face+" must be on top");
            assertEquals(0,normal.x,1e-5);assertEquals(0,normal.z,1e-5);
            assertEquals(end,motion.pose(DiceMotion.FRAMES+10),"A finished throw must stay still");
        }
    }

    @Test void tumblingCornersRemainInsideTheRimAndAboveTheFelt() {
        for(boolean compact:new boolean[]{false,true})for(long seed=0;seed<30;seed++) {
            double size=compact?.17:.28,halfWidth=compact?.21:.61;
            DiceMotion motion=new DiceMotion(size,halfWidth,DiceMotion.rest(size,4),6,seed);
            for(double frame=0;frame<=DiceMotion.FRAMES;frame+=.25) {
                DiceMotion.Pose pose=motion.pose(frame);
                for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                    Vector3f corner=new Vector3f((float)(x*size/2),(float)(y*size/2),(float)(z*size/2)).rotate(pose.rotation());
                    assertTrue(Math.abs(pose.x()+corner.x)<=halfWidth+1e-6,"Die crossed a side rim");
                    assertTrue(Math.abs(pose.z()+corner.z)<=halfWidth+1e-6,"Die crossed an end rim");
                    assertTrue(pose.y()+corner.y>=0,"Die penetrated the felt");
                }
            }
        }
    }

    @Test void throwMovesAcrossTheTrayBouncesAndLosesAngularSpeed() {
        DiceMotion.Pose start=DiceMotion.rest(.28,2);
        DiceMotion motion=new DiceMotion(.28,.61,start,5,37);
        assertEquals(start,motion.pose(0),"Throw must start from the existing die");
        double travel=0,maxHeight=0;int landings=0;
        for(int frame=1;frame<=DiceMotion.FRAMES;frame++) {
            DiceMotion.Pose p=motion.pose(frame),previous=motion.pose(frame-1);
            travel+=Math.hypot(p.x()-previous.x(),p.z()-previous.z());maxHeight=Math.max(maxHeight,p.y());
            if(motion.impact(frame))landings++;
        }
        assertTrue(travel>.5,"A throw needs translation, not just rotation in place");
        assertTrue(maxHeight>.5,"The die must leave the felt");
        assertTrue(landings>=2,"The die must bounce before settling");
        assertTrue(angle(motion.pose(1).rotation(),motion.pose(2).rotation())>.2);
        assertTrue(angle(motion.pose(23).rotation(),motion.pose(24).rotation())<.04,"Final rocking must settle gently");
    }

    @Test void aNewThrowStartsAtThePreviousRestingPosition() {
        DiceMotion first=new DiceMotion(.28,.61,DiceMotion.rest(.28,1),3,14);
        DiceMotion.Pose previous=first.pose(DiceMotion.FRAMES);
        DiceMotion next=new DiceMotion(.28,.61,previous,4,81);
        assertEquals(previous,next.pose(0));
        assertNotEquals(first.pose(5),next.pose(5),"Different throws should follow different paths");
    }

    private static double angle(Quaternionf a,Quaternionf b) {
        return 2*Math.acos(Math.min(1,Math.abs(a.dot(b))));
    }
}
