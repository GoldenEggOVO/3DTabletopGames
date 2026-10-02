"""Original board meshes, standard playing cards, and card-game furniture."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

MATERIALS = {
    "SMOOTH_QUARTZ":"#f3ead6", "POLISHED_BLACKSTONE":"#252b34", "BLACK_CONCRETE":"#19202b",
    "WHITE_CONCRETE":"#f5f0de", "GOLD_BLOCK":"#dab551", "COPPER_BLOCK":"#9b6144",
    "STRIPPED_BIRCH_WOOD":"#dfc595", "RED_CONCRETE":"#c64a45", "LIGHT_BLUE_CONCRETE":"#4598c1",
    "GREEN_CONCRETE":"#4eab7b", "YELLOW_CONCRETE":"#e2b64b", "PURPLE_CONCRETE":"#9b72c2",
    "PINK_CONCRETE":"#e88ca4", "BLUE_CONCRETE":"#245886", "POLISHED_DEEPSLATE":"#343b43",
}


def build(root, texture, export_model, cube, disc_mesh, rounded_square, face_model, pixel_text):
    source = root / "target/board-model-source"
    if not (source/"meshes.json").exists():
        raise ValueError("Run Maven -Dtest=BoardModelExportTest test before building the pack")
    material_textures = {name:texture("surface/material-"+name.lower(),Image.new("RGB",(16,16),color))
                         for name,color in MATERIALS.items()}
    wood = "tabletop3d:item/surface/wood"

    def round_image(image):
        image=image.convert("RGBA")
        mask=Image.new("L",image.size)
        ImageDraw.Draw(mask).ellipse((0,0,image.width-1,image.height-1),fill=255)
        from PIL import ImageChops
        image.putalpha(ImageChops.multiply(image.getchannel("A"),mask))
        return image

    def disc(radius,y,height,side,cap,segments=64):
        parts=disc_mesh(radius,y,height,side,cap,segments)
        for part in parts:
            for direction in ("up","down"):
                if direction in part["faces"]:
                    part["faces"][direction]["texture"]="#"+cap+"_cap"
        return parts

    def model(name,textures,parts):
        textures=dict(textures)
        caps={face["texture"][1:] for part in parts for face in part["faces"].values()
              if face["texture"].endswith("_cap")}
        for cap in caps:
            original=textures[cap[:-4]]
            path=root/"target/resource-pack-build/assets"/(original.replace(":","/textures/")+".png")
            textures[cap]=texture("surface/round-"+original.split("/")[-1],round_image(Image.open(path)))
        export_model(name,textures,parts)

    def block(x,y,z,w,h,d,material="STRIPPED_BIRCH_WOOD",scale=1):
        part = cube([8+(x-w/2)*16/scale,8+y*16/scale,8+(z-d/2)*16/scale],
                    [8+(x+w/2)*16/scale,8+(y+h)*16/scale,8+(z+d/2)*16/scale])
        for face in part["faces"].values(): face["texture"]="#"+material
        return part

    # The central art spans precisely two blocks, matching TableGeometry's maps.
    for path in sorted(source.glob("*.png")):
        art = texture("surface/board-"+path.stem,Image.open(path).resize((512,512),Image.Resampling.NEAREST))
        parts = rounded_square(1.125,.10,-.19,.19,"wood","wood")
        top = cube([-8,8.02,-8],[24,8.025,24])
        top["faces"]={"up":{"uv":[0,0,16,16],"texture":"#board"}}
        parts.append(top)
        for x in (-.99,.99):
            for z in (-.99,.99):
                leg=block(x,-1.03125,z,.15,.84125,.15)
                for face in leg["faces"].values():face["texture"]="#wood"
                parts.append(leg)
        model("board_"+path.stem,{"body":wood,"wood":wood,"board":art},parts)

    meshes = json.loads((source/"meshes.json").read_text(encoding="utf-8"))
    glyphs = {"general":("\u5e05","\u5c06"),"advisor":("\u4ed5","\u58eb"),
              "elephant":("\u76f8","\u8c61"),"horse":("\u99ac","\u99ac"),
              "rook":("\u8f66","\u8f66"),"cannon":("\u70ae","\u7832"),"pawn":("\u5175","\u5352")}
    for name, native in meshes.items():
        textures = dict(material_textures)
        textures["body"] = wood
        if name.startswith("chess_"):
            side,piece = name.split("_")[1:]
            body = "SMOOTH_QUARTZ" if side=="white" else "POLISHED_BLACKSTONE"
            trim = "GOLD_BLOCK" if side=="white" else "COPPER_BLOCK"
            parts = disc(.38,0,.12,body,body,24)+disc(.30,.12,.12,body,body,24)
            parts += disc(.17,.24,.30,body,body,16)
            if piece=="pawn": parts += disc(.23,.54,.22,body,body,16)
            elif piece=="knight":
                parts += [block(0,.53,.05,.28,.32,.30,body),block(0,.73,-.13,.28,.20,.45,body)]
                ear=block(0,.91,.02,.25,.12,.11,body)
                ear["rotation"]={"origin":[8,22.56,8.32],"axis":"x","angle":-22.5}
                parts.append(ear)
            elif piece=="rook":
                parts += disc(.29,.54,.17,body,body,24)
                for x in (-.20,.20):
                    for z in (-.20,.20):parts.append(block(x,.71,z,.15,.15,.15,body))
            elif piece=="bishop":
                parts += disc(.24,.54,.08,trim,trim,24)+disc(.18,.62,.19,body,body,16)
                parts += [block(-.07,.81,0,.12,.13,.18,body),block(.07,.81,0,.12,.09,.18,body)]
            elif piece=="queen":
                parts += disc(.22,.54,.24,body,body,16)+disc(.29,.78,.07,trim,trim,24)
                for x,z in ((-.19,0),(.19,0),(0,-.19),(0,.19)):parts.append(block(x,.85,z,.12,.13,.12,body))
            else:
                parts += disc(.23,.54,.25,body,body,16)
                parts += [block(0,.79,0,.15,.32,.15,trim),block(0,.93,0,.40,.10,.15,trim)]
        elif name.startswith(("stone_","draught_","xiangqi_","reversi_","connectfour_")):
            if name=="reversi_disc":
                parts=disc(.38,0,.065,"WHITE_CONCRETE","WHITE_CONCRETE",24)+disc(.38,.065,.065,"BLACK_CONCRETE","BLACK_CONCRETE",24)
            elif name.startswith("connectfour_"):
                material="RED_CONCRETE" if name.endswith("red") else "YELLOW_CONCRETE"
                # Model's Y axis rotates to face the vertical rack; bottom stays at Y=0.
                parts=[]
                for element in disc(.12,-.0425,.085,material,material,32):
                    for key in ("from","to"):
                        v=element[key];element[key]=[v[0],8+(v[2]-8)+.12*16,8+(v[1]-8)]
                    element["faces"]={ {"up":"south","down":"north","south":"up"}.get(k,k):v for k,v in element["faces"].items()}
                    if "rotation" in element:
                        element["rotation"]["axis"]="z";element["rotation"]["origin"]=[8,8+.12*16,8]
                    parts.append(element)
            else:
                material=native[0]["material"]
                height=.19 if name.startswith(("draught_","xiangqi_")) else .13
                parts=disc(.39 if name.startswith("xiangqi_") else .38,0,height,material,material,32)
                if name.endswith("_dead"):
                    parts += [block(0,.16,0,.65,.035,.10,"RED_CONCRETE"),block(0,.16,0,.10,.035,.65,"RED_CONCRETE")]
                if name.endswith("_king"):
                    parts += disc(.27,.19,.11,"GOLD_BLOCK","GOLD_BLOCK",24)
                    for x,z in ((-.16,0),(.16,0),(0,-.16),(0,.16)):parts.append(block(x,.30,z,.12,.10,.12,material))
                if name.startswith("xiangqi_"):
                    side,piece=name.split("_")[1:]
                    image=Image.new("RGB",(128,128),"#dfc595");draw=ImageDraw.Draw(image)
                    ink="#b52324" if side=="red" else "#22242b"
                    draw.ellipse((5,5,122,122),outline=ink,width=3)
                    draw.text((64,63),glyphs[piece][0 if side=="red" else 1],font=ImageFont.truetype("C:/Windows/Fonts/msyh.ttc",82),fill=ink,anchor="mm")
                    textures["engraving"]=texture("surface/"+name,round_image(image))
                    parts[0]["faces"]["up"]["texture"]="#engraving"
        else:
            parts=[block(p["x"],p["y"],p["z"],p["w"],p["h"],p["d"],p["material"]) for p in native]
        model(name,textures,parts)

    # Open rack: two transparent face sheets, actual inner walls, and solid outer frame.
    rack=Image.new("RGBA",(512,440),"#245886");draw=ImageDraw.Draw(rack)
    for col in range(7):
        for row in range(6):
            x=(.16+col*.28)/2*512;y=(1.74-(.17+row*.28))/1.72*440
            rx,ry=.119/2*512,.119/1.72*440
            draw.ellipse((x-rx,y-ry,x+rx,y+ry),fill=(0,0,0,0))
    # Export a sample at the nearest central hole for the contract test.
    textures=dict(material_textures);textures["body"]=wood
    textures["rack"]=texture("surface/connectfour-rack",rack)
    parts=[]
    for z,face in ((.061,"south"),(-.061,"north")):
        part=cube([0,8+.02*8,8+z*8],[16,8+1.74*8,8+z*8])
        part["faces"]={face:{"uv":[0,0,16,16],"texture":"#rack"}}
        parts.append(part)
    for col in range(7):
        for row in range(6):
            x,y=(col-3)*.28,.17+row*.28
            for step in range(16):
                a=step*2*math.pi/16
                wall=cube([8+(x+.119*math.cos(a))*8,8+(y-.119*math.sin(a)-.024)*8,8-.061*8],
                          [8+(x+.119*math.cos(a))*8,8+(y-.119*math.sin(a)+.024)*8,8+.061*8])
                wall["rotation"]={"origin":[8+x*8,8+y*8,8],"axis":"z","angle":-step*360/16}
                # Author each strip at the right side and rotate about its hole center.
                wall["from"][0]=wall["to"][0]=8+(x+.119)*8
                wall["from"][1]=8+(y-.024)*8;wall["to"][1]=8+(y+.024)*8
                wall["faces"]={"west":{"uv":[0,0,16,16],"texture":"#BLUE_CONCRETE"}}
                parts.append(wall)
    parts += [block(-1.06,-.04,0,.12,1.83,.20,"BLUE_CONCRETE",2),
              block(1.06,-.04,0,.12,1.83,.20,"BLUE_CONCRETE",2),
              block(0,1.74,0,2.25,.09,.20,"BLUE_CONCRETE",2)]
    for x in (-1.06,1.06):parts.append(block(x,-.04,0,.26,.10,.65,"POLISHED_DEEPSLATE",2))
    base=cube([-1,8-.19*8,-1],[17,8-.05*8,17]);parts.append(base)
    for x in (-.99,.99):
        for z in (-.99,.99):parts.append(block(x,-1.03125,z,.15,.84125,.15,"STRIPPED_BIRCH_WOOD",2))
    model("board_connectfour",textures,parts)

    def suit(draw,kind,x,y,r,color):
        if kind=="diamonds":draw.polygon([(x,y-r),(x+r,y),(x,y+r),(x-r,y)],fill=color)
        elif kind=="hearts":
            draw.polygon([(x-r,y-r//3),(x-r,y+r//4),(x,y+r),(x+r,y+r//4),(x+r,y-r//3),(x+r//2,y-r),(x,y-r//2),(x-r//2,y-r)],fill=color)
        elif kind=="spades":
            draw.polygon([(x,y-r),(x+r,y),(x+r//2,y+r//2),(x+r//4,y+r//3),(x+r//3,y+r),(x-r//3,y+r),(x-r//4,y+r//3),(x-r//2,y+r//2),(x-r,y)],fill=color)
        else:
            for dx,dy in ((0,-r//2),(-r//2,r//4),(r//2,r//4)):draw.ellipse((x+dx-r//2,y+dy-r//2,x+dx+r//2,y+dy+r//2),fill=color)
            draw.rectangle((x-r//5,y,x+r//5,y+r),fill=color)
    back=Image.new("RGB",(128,192),"#091b2c");d=ImageDraw.Draw(back)
    d.rounded_rectangle((2,2,125,189),8,outline="#fff5db",width=5)
    d.rounded_rectangle((10,10,117,181),4,outline="#49657d",width=2)
    for y in range(25,177,16):
        for x in range(23,113,16):suit(d,"diamonds",x,y,4,"#3c566e")
    backtex=texture("surface/playing-back",back)
    ranks=["ace"]+list(map(str,range(2,11)))+["jack","queen","king"]
    for kind in ("spades","hearts","diamonds","clubs"):
        ink="#b52324" if kind in ("hearts","diamonds") else "#142638"
        for rank in ranks:
            image=Image.new("RGB",(128,192),"#142638");d=ImageDraw.Draw(image)
            d.rounded_rectangle((2,2,125,189),8,fill="#fff5db")
            d.rounded_rectangle((9,9,118,182),4,outline="#e1cfad",width=2)
            value=rank[0].upper() if not rank.isdigit() else rank
            pixel_text(d,(23,26),value,3 if value!="10" else 2,ink,False)
            suit(d,kind,23,51,9,ink);suit(d,kind,64,101,31,ink)
            corner=image.crop((10,13,37,66)).rotate(180)
            image.paste(corner,(91,126))
            face_model("playing_"+kind+"_"+rank,image,True,backtex)
    for size,color in (("small","#142638"),("big","#b52324")):
        image=Image.new("RGB",(128,192),"#142638");d=ImageDraw.Draw(image)
        d.rounded_rectangle((2,2,125,189),8,fill="#fff5db")
        d.polygon([(28,76),(36,51),(55,73),(64,43),(74,73),(95,51),(101,77)],fill=color)
        d.ellipse((34,84,95,144),outline=color,width=7);d.rectangle((48,110,55,116),fill=color);d.rectangle((75,110,82,116),fill=color)
        d.line((48,132,82,132),fill=color,width=4)
        pixel_text(d,(23,26),"J",3,color,False)
        face_model("playing_joker_"+size,image,True,backtex)
    face_model("playing_back",back,True,backtex)
    for name,color,squash in (("doudizhu_table","#286346",1),("liars_bar_table","#473129",1),("texas_holdem_table","#245346",.76)):
        im=Image.new("RGBA",(512,512));d=ImageDraw.Draw(im)
        d.ellipse((0,0,511,511),fill="#38251d");d.ellipse((19,19,492,492),fill=color,outline="#bd9955",width=2)
        if name=="doudizhu_table":
            for angle in (0,120,240):
                x=256+178*math.sin(math.radians(angle));y=256+178*math.cos(math.radians(angle))
                d.ellipse((x-15,y-15,x+15,y+15),outline="#b3c995",width=2)
            for x in (209,256,303):d.rounded_rectangle((x-18,164,x+18,216),4,outline="#89a282",width=2)
        elif name=="liars_bar_table":
            d.rounded_rectangle((213,206,299,322),7,outline="#bd9955",width=2)
            for i in range(6):
                angle=math.radians(i*60);x=256+93*math.sin(angle);y=256+93*math.cos(angle)
                d.ellipse((x-8,y-8,x+8,y+8),outline="#96795b",width=2)
        else:
            for x in (154,205,256,307,358):
                d.rounded_rectangle((x-20,224,x+20,294),4,outline="#87ac92",width=2)
            for angle in range(0,360,60):
                x=256+177*math.sin(math.radians(angle));y=256+177*math.cos(math.radians(angle))
                d.ellipse((x-11,y-11,x+11,y+11),outline="#bd9955",width=2)
        toptex=texture("surface/"+name,im)
        parts=disc(1.5,-.19,.19,"wood","top")+disc(.25,-.95125,.76125,"wood","wood",32)+disc(.70,-1.03125,.08,"wood","wood",48)
        if squash != 1:
            # Bake the stretched ellipse as chords; rotating a squashed circle distorts its wall.
            parts = []
            for radius,y,height,segments,cap_texture in ((1.5,-.19,.19,96,"top"),(.25,-.95125,.76125,32,"wood"),(.70,-1.03125,.08,48,"wood")):
                for down,level in ((False,y+height),(True,y)):
                    cap=cube([8-radius*16,8+level*16,8-radius*squash*16],[8+radius*16,8+level*16,8+radius*squash*16])
                    cap["faces"]={"down" if down else "up":{"uv":[0,0,16,16],"texture":"#"+cap_texture}}
                    parts.append(cap)
                for i in range(segments):
                    a,b=2*math.pi*i/segments,2*math.pi*(i+1)/segments
                    x,z=radius*math.sin(a),radius*squash*math.cos(a)
                    X,Z=radius*math.sin(b),radius*squash*math.cos(b)
                    mx,mz=(x+X)/2,(z+Z)/2
                    half=math.hypot(X-x,Z-z)/2
                    vertical=abs(Z-z)>abs(X-x)
                    wall=cube([8+mx*16 if vertical else 8+(mx-half)*16,8+y*16,8+(mz-half)*16 if vertical else 8+mz*16],
                              [8+mx*16 if vertical else 8+(mx+half)*16,8+(y+height)*16,8+(mz+half)*16 if vertical else 8+mz*16])
                    wall["rotation"]={"origin":[8+mx*16,8,8+mz*16],"axis":"y","angle":(90 if vertical else 0)-math.degrees(math.atan2(Z-z,X-x))}
                    wall["faces"]={"west" if vertical else "south":{"uv":[0,0,16,16],"texture":"#wood"}}
                    parts.append(wall)
        model(name,{"body":wood,"wood":wood,"top":toptex},parts)
    chip=texture("surface/chip",Image.new("RGB",(16,16),"#d7b458"))
    model("poker_chips",{"body":chip,"chip":chip},disc(.09,0,.10,"chip","chip",16))
    marker=Image.new("RGB",(64,64),"#fff5db");ImageDraw.Draw(marker).text((32,32),"D",fill="#142638",font=ImageFont.truetype("C:/Windows/Fonts/seguisb.ttf",42),anchor="mm")
    model("poker_dealer",{"body":wood,"top":texture("surface/dealer",marker)},disc(.075,0,.015,"body","top",24))
