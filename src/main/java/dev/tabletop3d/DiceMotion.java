package dev.tabletop3d;

import java.util.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A short deterministic throw; the supplied face, never the animation seed, decides the result. */
final class DiceMotion {
    static final int FRAMES=24;
    private static final double CLEARANCE=.006;
    record Pose(double x,double y,double z,Quaternionf rotation) {}
    private final double size;
    private final Pose start,end;
    private final double[] x=new double[FRAMES+1],z=new double[FRAMES+1];
    private final Vector3f spin;
    private final Quaternionf landing;

    DiceMotion(double size,double halfWidth,Pose start,int face,long seed) {
        this.size=size;this.start=start;
        Random random=new Random(seed);
        landing=new Quaternionf().rotateY((float)(random.nextDouble()*Math.PI*2)).mul(TableView.faceRotation(face).invert());
        spin=new Vector3f(random.nextBoolean()?1:-1,.55f+random.nextFloat()*.35f,random.nextBoolean()?.55f:-.55f);
        double angle=random.nextDouble()*Math.PI*2,velocity=.12*halfWidth/.61;
        double vx=Math.cos(angle)*velocity,vz=Math.sin(angle)*velocity;
        // A circumscribed sphere also leaves room for the shallow raised pips at every orientation.
        double limit=halfWidth-size*Math.sqrt(3)/2-CLEARANCE;
        x[0]=start.x();z[0]=start.z();
        for(int frame=1;frame<=FRAMES;frame++) {
            x[frame]=x[frame-1]+vx;z[frame]=z[frame-1]+vz;
            if(x[frame]>limit){x[frame]=2*limit-x[frame];vx=-Math.abs(vx)*.76;}
            else if(x[frame]<-limit){x[frame]=-2*limit-x[frame];vx=Math.abs(vx)*.76;}
            if(z[frame]>limit){z[frame]=2*limit-z[frame];vz=-Math.abs(vz)*.76;}
            else if(z[frame]<-limit){z[frame]=-2*limit-z[frame];vz=Math.abs(vz)*.76;}
            double drag=frame<18?.90:.90*(FRAMES-frame)/(FRAMES-18.0);
            vx*=drag;vz*=drag;
        }
        end=new Pose(x[FRAMES],size/2+CLEARANCE,z[FRAMES],new Quaternionf(landing));
    }

    static Pose rest(double size,int face) {
        return new Pose(0,size/2+CLEARANCE,0,TableView.faceRotation(face).invert());
    }

    Pose pose(double frame) {
        if(frame<=0)return start;
        if(frame>=FRAMES)return end;
        int lower=(int)frame;double fraction=frame-lower;
        Quaternionf rotation=rotation(frame);
        Vector3f a=new Vector3f(1,0,0).rotate(rotation),b=new Vector3f(0,1,0).rotate(rotation),c=new Vector3f(0,0,1).rotate(rotation);
        double support=size/2*(Math.abs(a.y)+Math.abs(b.y)+Math.abs(c.y));
        return new Pose(x[lower]+(x[lower+1]-x[lower])*fraction,support+CLEARANCE+lift(frame),
            z[lower]+(z[lower+1]-z[lower])*fraction,rotation);
    }

    boolean impact(int frame){return frame==8||frame==13||frame==17||frame==21;}

    private Quaternionf rotation(double frame) {
        if(frame<14)return tumble(frame);
        if(frame<21) {
            float u=(float)((frame-14)/7),ease=u*u*(3-2*u);
            return tumble(14).slerp(landing,ease);
        }
        float u=(float)((frame-21)/3),rock=(float)(Math.sin(u*Math.PI*2)*.025*(1-u));
        return new Quaternionf(landing).rotateX(rock).rotateZ(rock*.6f);
    }

    private Quaternionf tumble(double frame) {
        float angle=(float)(frame*1.12-frame*frame*.023);
        return new Quaternionf(start.rotation()).rotateXYZ(spin.x*angle,spin.y*angle,spin.z*angle);
    }

    private double lift(double frame) {
        int from,to;double height;
        if(frame<=8){from=0;to=8;height=.50;}
        else if(frame<=13){from=8;to=13;height=.18;}
        else if(frame<=17){from=13;to=17;height=.075;}
        else if(frame<=21){from=17;to=21;height=.025;}
        else{from=21;to=24;height=.006;}
        double u=(frame-from)/(to-from);
        return 4*height*(size/.28)*u*(1-u);
    }
}
