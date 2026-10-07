import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

class ClientTransformProbe {
    private static Object decoder(Class<?> type) throws Exception {var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);return ctor.newInstance();}
    public static void main(String[] args) throws Exception {
        Gson gson=new GsonBuilder().registerTypeAdapter(ItemTransforms.class,decoder(Class.forName("net.minecraft.client.resources.model.cuboid.ItemTransforms$Deserializer")))
            .registerTypeAdapter(ItemTransform.class,decoder(Class.forName("net.minecraft.client.resources.model.cuboid.ItemTransform$Deserializer"))).create();
        ItemTransforms ignored=gson.fromJson("{\"none\":{\"rotation\":[0,180,0]}}",ItemTransforms.class);
        if(ignored.getTransform(ItemDisplayContext.NONE).rotation().lengthSquared()!=0)throw new AssertionError("NONE must be ignored");
        for(String name:new String[]{"mahjong_m1","mahjong_z1","card_r3","card_wild","button_b"}){
            var model=JsonParser.parseString(Files.readString(Path.of(args[0],name+".json"))).getAsJsonObject();
            ItemTransform transform=gson.fromJson(model.get("display"),ItemTransforms.class).getTransform(ItemDisplayContext.FIXED);
            Vector3f front=new Quaternionf().rotateY((float)Math.PI).transform(new Quaternionf()
                .rotationXYZ((float)Math.toRadians(transform.rotation().x()),(float)Math.toRadians(transform.rotation().y()),(float)Math.toRadians(transform.rotation().z()))
                .transform(new Vector3f(0,0,1)));
            if(front.z()<.999)throw new AssertionError(name+" faces away");
            if(new Quaternionf().rotateX((float)-Math.PI/2).transform(new Vector3f(front)).y()<.999)throw new AssertionError(name+" faces down");
        }
        int models = 0;
        try (var paths = Files.list(Path.of(args[0]))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    if (CuboidModel.fromStream(reader).geometry() == null)
                        throw new AssertionError(path + " has no geometry");
                }
                models++;
            }
        }
        System.out.println("CLIENT_26_2_TRANSFORM_PASS actual_deserializer=true models=" + models
            + " none_ignored=true fixed_faces_owner_and_up=true client_visual_test=false");
    }
}
