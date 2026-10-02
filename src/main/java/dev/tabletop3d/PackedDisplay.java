package dev.tabletop3d;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.*;

final class PackedDisplay {
    static ItemDisplay spawn(Tabletop3D plugin,Room room,TableAudience audience,Location at,NamespacedKey tag,
                             String id,Vector3f scale,Quaternionf rotation){
        ItemDisplay entity=at.getWorld().spawn(at,ItemDisplay.class,d->{
            d.setVisibleByDefault(false);d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);
            d.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|@board");
            d.setBrightness(new Display.Brightness(15,15));d.setViewRange(.35f);d.setTeleportDuration(2);d.setInterpolationDuration(2);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);d.setItemStack(plugin.pack.item(id));
            d.setTransformation(new Transformation(new Vector3f(),rotation,scale,new Quaternionf()));
        });
        audience.add(entity,true);return entity;
    }
}
