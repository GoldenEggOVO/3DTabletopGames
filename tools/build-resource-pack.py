"""Build the isolated Tabletop pack and CraftEngine item registrations.

Requires Pillow and SoundFile (libsndfile); no CraftEngine binary is redistributed.
All item meshes are centered at (8,8,8), front towards +Z.
"""
import hashlib
import json
import math
import sys
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "target/resource-pack-build"
ASSETS = BUILD / "assets/tabletop3d"
COLORS = {"r": "#db533e", "b": "#397ab0", "y": "#dfa832", "p": "#8c5fa5"}
CREAM = "#fff5db"
RANKS = list(map(str, range(1, 11))) + ["draw", "skip", "reverse"]
SOUNDS = {"select": "click_pai", "countdown": "countdown5", "discard": "discard_tile",
          "meld": "fulu", "kan": "gang", "win": "hupai", "dora": "new_dora",
          "riichi": "put_liqi", "draw": "anpai_reverse"}
BASE = "https://files.riichi.moe/mjg/game%20resources%20and%20tools/Mahjong%20Soul/game%20files/"


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def font(size):
    candidates = ["C:/Windows/Fonts/seguisb.ttf", "C:/Windows/Fonts/segoeui.ttf",
                  "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
    return ImageFont.truetype(next(p for p in candidates if Path(p).exists()), size)


def centered(draw, point, text, size, color=CREAM):
    draw.text(point, text, font=font(size), fill=color, anchor="mm", stroke_width=max(0, size//90))


def texture(name, im):
    path = ASSETS / f"textures/item/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    return f"tabletop3d:item/{name}"


def cube(lo, hi, front="body", uv=None):
    faces = {side: {"uv": [0, 0, 16, 16], "texture": "#body"}
             for side in ("north", "south", "east", "west", "up", "down")}
    faces["south"] = {"uv": uv or [0, 0, 16, 16], "texture": "#"+front}
    return {"from": [round(v, 5) for v in lo], "to": [round(v, 5) for v in hi], "faces": faces}


CATALOG = []


def model(name, textures, elements):
    CATALOG.append(name)
    write_json(ASSETS / f"models/item/{name}.json", {"gui_light": "front", "ambientocclusion": False,
               "textures": textures, "elements": elements})
    write_json(ASSETS / f"items/{name}.json",
               {"model": {"type": "minecraft:model", "model": f"tabletop3d:item/{name}"}})


def face_model(name, image, rounded=False, back=None):
    face = texture("face/"+name, image)
    elements = []
    for i in range(48 if rounded else 1):
        y0, y1 = (i*16/48, (i+1)*16/48) if rounded else (0, 16)
        y = (y0+y1)/2
        edge = 0
        radius = 1.2
        if rounded and (y < radius or y > 16-radius):
            dy = radius-y if y < radius else y-(16-radius)
            edge = radius-math.sqrt(max(0, radius*radius-dy*dy))
        part = cube([edge, y0, 0], [16-edge, y1, 16], "face", [edge, 16-y1, 16-edge, 16-y0])
        if back:
            part["faces"]["north"] = {"uv": [edge, 16-y1, 16-edge, 16-y0], "texture": "#back"}
        elements.append(part)
    textures = {"body": "tabletop3d:item/surface/ivory", "face": face}
    if back:
        textures["back"] = back
    model(name, textures, elements)


def card_image(rank, color=None):
    im = Image.new("RGB", (512, 768), CREAM)
    d = ImageDraw.Draw(im)
    bg = COLORS.get(color, "#263a3e")
    d.rounded_rectangle((20, 20, 492, 748), radius=44, fill=bg)
    d.rounded_rectangle((31, 31, 481, 737), radius=36, outline=CREAM, width=4)
    # Decorative slanted inset, original B-style cream frame and color palette.
    for x in range(-600, 1000, 30):
        d.line((x, 730, x+330, 40), fill=ImageColor_mix(bg, CREAM, .08), width=2)
    d.ellipse((100, 135, 412, 633), outline=ImageColor_mix(bg, CREAM, .55), width=8)
    if rank in ("wild", "back"):
        for i, c in enumerate(COLORS.values()):
            x, y = 172+(i%2)*88, 305+(i//2)*100
            d.polygon([(x,y-60),(x+65,y),(x,y+60),(x-65,y)], fill=c)
    if rank.isdigit() or rank == "wild":
        value = "8" if rank == "wild" else rank
        centered(d, (256, 365), value, 212 if len(value)==1 else 164)
        if value in ("6", "9"):
            d.rounded_rectangle((198, 484, 314, 494), radius=4, fill=CREAM)
    elif rank == "skip":
        d.ellipse((165,294,347,476),outline=CREAM,width=23)
        d.line((175,461,335,306),fill=CREAM,width=23)
    elif rank in ("reverse", "swap"):
        if rank == "swap":
            d.rounded_rectangle((131,262,245,478),radius=12,fill=COLORS["b"],outline=CREAM,width=5)
            d.rounded_rectangle((267,296,381,512),radius=12,fill=COLORS["r"],outline=CREAM,width=5)
        for y, sign in ((323, 1), (444, -1)):
            d.line((145,y,367,y),fill=CREAM,width=23)
            x = 367 if sign==1 else 145
            d.polygon([(x,y),(x-sign*52,y-42),(x-sign*52,y+42)],fill=CREAM)
    elif rank == "draw":
        d.rounded_rectangle((187,366,307,526),radius=12,outline=CREAM,width=10)
        centered(d,(256,307),"+1",116)
    label = "8" if rank=="wild" else "↔" if rank in ("reverse","swap") else "+1" if rank=="draw" else "⊘" if rank=="skip" else rank
    if rank != "back":
        corner = Image.new("RGBA", (150,170))
        cd = ImageDraw.Draw(corner)
        if rank=="skip":
            cd.ellipse((40,30,110,100),outline=CREAM,width=8);cd.line((44,96,106,34),fill=CREAM,width=8)
        else:centered(cd,(75,64),label,90)
        if rank in ("6", "9"):
            cd.line((47,120,103,120),fill=CREAM,width=6)
        im.paste(corner,(32,40),corner)
        corner = corner.rotate(180)
        im.paste(corner,(330,550),corner)
    return im


def ImageColor_mix(a,b,ratio):
    from PIL import ImageColor
    a,b=ImageColor.getrgb(a),ImageColor.getrgb(b)
    return tuple(round(x*(1-ratio)+y*ratio) for x,y in zip(a,b))


def flower_image(n):
    im=Image.new("RGB",(256,384),"#fff9eb");d=ImageDraw.Draw(im)
    d.line((128,296,128,150),fill="#356648",width=7)
    for side in (-1,1):
        d.ellipse((128+side*55-30,220,128+side*55+30,254),fill="#44815d")
    petals=5 if n%4==1 else 8
    for i in range(petals):
        a=i*math.tau/petals;x=128+math.sin(a)*44;y=145+math.cos(a)*44
        d.ellipse((x-24,y-24,x+24,y+24),fill=["#cd5978","#9772bb","#6d9d72","#d4a840"][(n-1)%4])
    d.ellipse((106,123,150,167),fill="#dfbe5a")
    chars="梅蘭竹菊春夏秋冬"
    cjk=ImageFont.truetype("C:/Windows/Fonts/msyh.ttc",38)
    d.text((128,338),chars[n-1],font=cjk,anchor="mm",fill="#a0413d" if n<=4 else "#315783")
    return im


def disc(radius,y,height,tex,segments=48):
    parts=[]
    for i in range(segments):
        z0,z1=-radius+2*radius*i/segments,-radius+2*radius*(i+1)/segments
        # Inscribed strips stay inside the circular tabletop.
        x=math.sqrt(max(0,radius*radius-max(abs(z0),abs(z1))**2))
        if x<=0:
            x=math.sqrt(max(0,radius*radius-((z0+z1)/2)**2))*.5
        part=cube([8-x*16,8+y*16,8+z0*16],[8+x*16,8+(y+height)*16,8+z1*16])
        for f in part["faces"].values():
            f["texture"]="#"+tex
        part["faces"]["up"]["uv"]=[(radius-x)*8/radius,(radius+z0)*8/radius,(radius+x)*8/radius,(radius+z1)*8/radius]
        parts.append(part)
    return parts


def furniture():
    wood=Image.new("RGB",(128,128),"#69503a");d=ImageDraw.Draw(wood)
    for y in range(0,128,5):d.line((0,y,128,y+3),fill="#826449",width=2)
    texture("surface/wood",wood)
    felt=Image.new("RGB",(128,128),"#29634a");d=ImageDraw.Draw(felt)
    for x in range(0,128,4):d.line((x,0,x,128),fill="#2d694e")
    texture("surface/felt",felt)
    cloth=Image.open(ROOT/"resource-pack/source/mahjong/tablecloth.jpg").convert("RGB")
    texture("surface/tablecloth",cloth)
    tex={"body":"tabletop3d:item/surface/wood","felt":"tabletop3d:item/surface/felt","cloth":"tabletop3d:item/surface/tablecloth"}
    model("card_table",tex,disc(1.5,-.19,.19,"body",64)+disc(1.41,.001,.01,"felt",64)
          +disc(.25,-.55,.36,"body",16)+disc(.70,-.55,.08,"body",32))
    parts=[cube([-16,4.96,-16],[32,8,32]),cube([-14.56,8,-14.56],[30.56,8.10,30.56])]
    for face in parts[1]["faces"].values():face["texture"]="#cloth"
    for x in (-1.1,1.1):
        for z in (-1.1,1.1):parts.append(cube([8+(x-.07)*16,-.8,8+(z-.07)*16],[8+(x+.07)*16,4.96,8+(z+.07)*16]))
    model("mahjong_table",tex,parts)
    panel=Image.new("RGB",(512,512),"#25252d");d=ImageDraw.Draw(panel)
    d.rounded_rectangle((10,10,502,502),radius=44,fill="#40404b",outline="#14141a",width=10)
    d.polygon([(98,98),(414,98),(360,152),(152,152)],fill="#33333c")
    d.polygon([(98,414),(414,414),(360,360),(152,360)],fill="#33333c")
    d.rounded_rectangle((150,150,362,362),radius=12,fill="#070b10")
    for angle in range(4):
        bar=Image.new("RGBA",(512,512));bd=ImageDraw.Draw(bar);bd.rounded_rectangle((126,29,386,60),radius=15,fill="#717185")
        panel.paste(bar.rotate(90*angle),(0,0),bar.rotate(90*angle))
    ptex=texture("surface/panel",panel)
    part=cube([-.8,8,-.8],[16.8,8.192,16.8]);part["faces"]["up"]={"uv":[0,0,16,16],"texture":"#panel"}
    model("mahjong_panel",{"body":"tabletop3d:item/surface/dark","panel":ptex},[part])


def rings():
    for direction in (1,-1):
        im=Image.new("RGBA",(512,512));d=ImageDraw.Draw(im)
        for a in range(4):
            start=a*90-30;d.arc((24,24,488,488),start,start+60,fill=CREAM,width=16)
            tip=math.radians(start+60 if direction==1 else start)
            x,y=256+232*math.cos(tip),256+232*math.sin(tip)
            tangent=(-math.sin(tip)*direction,math.cos(tip)*direction)
            d.polygon([(x,y),(x-tangent[0]*42-math.cos(tip)*25,y-tangent[1]*42-math.sin(tip)*25),
                       (x-tangent[0]*42+math.cos(tip)*25,y-tangent[1]*42+math.sin(tip)*25)],fill=CREAM)
        tex=texture("surface/ring_"+str(direction),im)
        part=cube([.96,8,.96],[15.04,8.03,15.04]);part["faces"]={"up":{"uv":[0,0,16,16],"texture":"#ring"}}
        model("ring_forward" if direction==1 else "ring_reverse",{"body":"tabletop3d:item/surface/dark","ring":tex},[part])


def audio():
    # A local --target installation is supported, keeping the global runtime unchanged.
    sys.path.insert(0,str(ROOT/"target/audio-tools"))
    import soundfile as sf
    events={};durations={}
    for event,source in SOUNDS.items():
        data,rate=sf.read(ROOT/f"audio-source/{source}.mp3",dtype="float32",always_2d=True)
        data=data.mean(axis=1)  # Positional sounds must be mono.
        target=ASSETS/f"sounds/mahjong/{event}.ogg";target.parent.mkdir(parents=True,exist_ok=True)
        sf.write(target,data,rate,format="OGG",subtype="VORBIS")
        decoded,checked=sf.read(target)
        assert checked==rate and abs(len(decoded)-len(data))<=1
        durations[event]=round(len(data)/rate,4)
        events["mahjong."+event]={"sounds":[{"name":"tabletop3d:mahjong/"+event,"stream":False}]}
    write_json(ASSETS/"sounds.json",events)
    return durations


def previews():
    cards=[ASSETS/f"textures/item/face/card_{c}{r}.png" for c in COLORS for r in ("1","6","8","draw","skip","reverse")]
    tiles=[ASSETS/f"textures/item/face/mahjong_{s}{n}.png" for s in ("m","p","s") for n in range(1,10)]
    tiles += [ASSETS/f"textures/item/face/mahjong_z{n}.png" for n in range(1,8)]
    out=ROOT/"target/pack-preview";out.mkdir(parents=True,exist_ok=True)
    for name,paths,cols,size in (("color-eight",cards,6,(144,216)),("mahjong",tiles,9,(80,120))):
        board=Image.new("RGB",(cols*(size[0]+12)+12,math.ceil(len(paths)/cols)*(size[1]+28)+12),"#19262d")
        d=ImageDraw.Draw(board)
        for i,path in enumerate(paths):
            x,y=12+(i%cols)*(size[0]+12),12+(i//cols)*(size[1]+28)
            board.paste(Image.open(path).resize(size,Image.Resampling.LANCZOS),(x,y))
            d.text((x,y+size[1]+3),path.stem,font=font(11),fill="white")
        board.save(out/f"{name}.png")
    Image.open(ASSETS/"textures/item/surface/panel.png").save(out/"mahjong-panel.png")


def main():
    import shutil
    if not BUILD.resolve().is_relative_to((ROOT/"target").resolve()):
        raise ValueError("Build output must stay under project target")
    if BUILD.exists():shutil.rmtree(BUILD)
    texture("surface/ivory",Image.new("RGB",(16,16),"#fff9eb"))
    texture("surface/dark",Image.new("RGB",(16,16),"#25252d"))
    back=texture("surface/card-back",card_image("back"))
    atlas=Image.open(ROOT/"resource-pack/source/mahjong/hand_ui.png").convert("RGB")
    for suit,row in (("s",0),("m",1),("p",2)):
        for n in range(10):face_model(f"mahjong_{suit}{n}",atlas.crop((n*80+5,row*129+15,n*80+75,row*129+124)))
    for n in range(1,8):face_model(f"mahjong_z{n}",atlas.crop(((n-1)*80+5,402,(n-1)*80+75,511)))
    for n in range(1,9):face_model(f"mahjong_f{n}",flower_image(n))
    face_model("mahjong_back",Image.new("RGB",(128,192),"#edb036"))
    for color in COLORS:
        for rank in RANKS:face_model(f"card_{color}{rank}",card_image(rank,color),True,back)
    for rank in ("wild","swap","back"):face_model("card_"+rank,card_image(rank),True,back)
    for color in list(COLORS)+["pass"]:
        image=Image.new("RGB",(256,256),COLORS.get(color,"#47535c"))
        d=ImageDraw.Draw(image);d.rounded_rectangle((10,10,246,246),radius=50,outline=CREAM,width=6)
        face_model("button_"+color,image,True)
    furniture();rings();durations=audio()
    write_json(BUILD/"pack.mcmeta",{"pack":{"pack_format":88,"min_format":88,"max_format":88,"description":"Tabletop 3D · Mahjong & Color Eight"}})
    config=ROOT/"craftengine/resources/tabletop3d/configuration/items.yml";config.parent.mkdir(parents=True,exist_ok=True)
    config.write_text("items:\n"+"".join(f"  tabletop3d:{name}:\n    material: paper\n    item_model: tabletop3d:{name}\n" for name in sorted(CATALOG)),encoding="utf-8")
    (config.parent.parent/"pack.yml").write_text("name: Tabletop 3D\nauthor: Tabletop3D\nversion: 1.8.8-SNAPSHOT\n",encoding="utf-8")
    target=ROOT/"target/tabletop-resource-pack.zip"
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as z:
        for path in sorted(BUILD.rglob("*")):
            if path.is_file():z.write(path,path.relative_to(BUILD).as_posix())
    manifest={"models":sorted(CATALOG),"sounds_seconds":durations,"sha1":hashlib.sha1(target.read_bytes()).hexdigest(),
              "sha256":hashlib.sha256(target.read_bytes()).hexdigest(),"bytes":target.stat().st_size}
    write_json(ROOT/"target/resource-pack-manifest.json",manifest)
    previews();print(json.dumps({k:v for k,v in manifest.items() if k!="models"},indent=2))


if __name__=="__main__":main()
