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
COLORS = {"r": "#ba151d", "b": "#0865b4", "y": "#e5af00", "p": "#6c28a9"}
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
               "textures": textures, "elements": elements,
               "display": {"fixed": {"rotation": [0, 180, 0]}}})
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


DIGITS = {
    "0": ["01110","11011","11011","11011","11011","11011","01110"],
    "1": ["00110","01110","00110","00110","00110","00110","01111"],
    "2": ["01110","11011","00011","00110","01100","11000","11111"],
    "3": ["11110","00011","00011","01110","00011","00011","11110"],
    "4": ["00011","00111","01111","11011","11111","00011","00011"],
    "5": ["11111","11000","11000","11110","00011","00011","11110"],
    "6": ["01110","11000","11000","11110","11011","11011","01110"],
    "7": ["11111","00011","00110","00110","01100","01100","01100"],
    "8": ["01110","11011","11011","01110","11011","11011","01110"],
    "9": ["01110","11011","11011","01111","00011","00011","01110"],
    "+": ["00000","00100","00100","11111","00100","00100","00000"]}


def pixel_text(draw, center, text, scale, color=CREAM, shadow=True):
    width=(len(text)*6-1)*scale; x=center[0]-width//2; y=center[1]-7*scale//2
    for offset,ink in ((scale,"#07182c"),(0,color)) if shadow else ((0,color),):
        for n,ch in enumerate(text):
            for row,line in enumerate(DIGITS[ch]):
                for col,v in enumerate(line):
                    if v=="1":
                        left=x+(n*6+col)*scale+offset;top=y+row*scale+offset
                        draw.rectangle((left,top,left+scale-1,top+scale-1),fill=ink)


def person(draw,x,y,scale=1):
    draw.rectangle((x+2*scale,y,x+5*scale,y+3*scale),fill=CREAM)
    draw.rectangle((x,y+5*scale,x+7*scale,y+10*scale),fill=CREAM)
    for dx in (1,5):draw.rectangle((x+dx*scale,y+10*scale,x+(dx+1)*scale,y+14*scale),fill=CREAM)


def arrows(draw,x,y,scale=1):
    points=[(0,14),(17,0),(17,8),(29,8),(39,18),(39,29),(30,29),(30,20),(25,16),(17,16),(17,24)]
    for flip in (False,True):
        q=[(x+(39-px if flip else px)*scale,y+(49-py if flip else py)*scale) for px,py in points]
        draw.polygon(q,fill=CREAM)


def card_image(rank, color=None):
    # Author at pixel resolution; nearest-neighbor enlargement preserves the B silhouette.
    im=Image.new("RGB",(128,192),"#07182c");d=ImageDraw.Draw(im)
    d.rounded_rectangle((2,2,125,189),radius=8,fill=CREAM)
    bg=COLORS.get(color,"#0c1c2c")
    d.rounded_rectangle((8,8,119,183),radius=5,fill=bg)
    if rank in ("wild","back"):
        for c,pts in zip(COLORS.values(),[[(64,44),(64,91),(21,91)],[(68,95),(107,95),(68,139)],
                                             [(21,95),(64,95),(64,139)],[(68,44),(107,91),(68,91)]]):
            d.polygon(pts,fill=c)
    if rank.isdigit() or rank=="wild":
        value="8" if rank=="wild" else rank
        pixel_text(d,(64,95),value,12 if len(value)==1 else 8)
        if value in ("6","9"):d.rectangle((39,143,88,147),fill=CREAM)
    elif rank=="reverse":arrows(d,25,62,2)
    elif rank=="draw":
        pixel_text(d,(64,78),"+1",7)
        for x in (26,56,86):
            person(d,x,128,2);d.polygon([(x+7,106),(x,115),(x+4,115),(x+4,122),(x+10,122),(x+10,115),(x+14,115)],fill=CREAM)
    elif rank=="skip":
        d.line([(27,114),(30,96),(43,81),(61,75),(85,75)],fill=CREAM,width=10)
        d.polygon([(85,60),(107,80),(85,98)],fill=CREAM)
        person(d,55,124,2)
        for x,y in ((34,122),(43,113),(60,107),(80,113),(92,122)):
            d.rectangle((x,y,x+5,y+5),fill=CREAM)
    elif rank=="swap":
        arrows(d,25,46,2)
        for x,y,c in ((27,82,"r"),(72,96,"b")):
            d.rectangle((x-3,y-3,x+30,y+48),fill="#07182c")
            d.rectangle((x,y,x+27,y+45),fill=CREAM)
            d.rectangle((x+4,y+4,x+23,y+41),fill=COLORS[c])
    if color:
        corner=Image.new("RGBA",(31,46));cd=ImageDraw.Draw(corner)
        if rank.isdigit():pixel_text(cd,(15,13),rank,3 if len(rank)==1 else 2,shadow=False)
        elif rank=="draw":pixel_text(cd,(15,13),"+1",2,shadow=False)
        elif rank=="reverse":arrows(cd,7,1,.4)
        elif rank=="skip":cd.polygon([(7,7),(24,14),(7,21)],fill=CREAM)
        if rank in ("6","9"):cd.rectangle((9,25,21,26),fill=CREAM)
        accent=ImageColor_mix(COLORS[color],CREAM,.25)
        if color=="r":cd.polygon([(15,29),(23,37),(15,45),(7,37)],fill=accent)
        elif color=="b":cd.rectangle((8,30,22,44),fill=accent)
        elif color=="y":cd.polygon([(15,29),(23,44),(7,44)],fill=accent)
        else:
            cd.rectangle((8,34,22,40),fill=accent);cd.rectangle((12,30,18,44),fill=accent)
        im.paste(corner,(10,11),corner);corner=corner.rotate(180)
        im.paste(corner,(87,135),corner)
    return im.resize((512,768),Image.Resampling.NEAREST)


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


def cap(radius,y,tex,down=False):
    return {"from":[8-radius*16,8+y*16,8-radius*16],"to":[8+radius*16,8+y*16,8+radius*16],
            "faces":{"down" if down else "up":{"uv":[0,0,16,16],"texture":"#"+tex}}}


def disc(radius,y,height,tex,cap_tex,segments=96):
    # Only exterior faces: no internal strip walls or coplanar block tops.
    parts=[cap(radius,y+height,cap_tex),cap(radius,y,cap_tex,True)]
    half=radius*math.sin(math.pi/segments);z=radius*math.cos(math.pi/segments)
    for i in range(segments):
        parts.append({"from":[8-half*16,8+y*16,8+z*16],"to":[8+half*16,8+(y+height)*16,8+z*16],
            "rotation":{"origin":[8,8,8],"axis":"y","angle":360*i/segments},
            "faces":{"south":{"uv":[0,0,16,16],"texture":"#"+tex}}})
    return parts


def rounded_square(radius,corner,y,height,tex,cap_tex):
    points=[]
    for cx,cz,start in ((radius-corner,radius-corner,0),(-radius+corner,radius-corner,90),
                        (-radius+corner,-radius+corner,180),(radius-corner,-radius+corner,270)):
        for i in range(9):
            a=math.radians(start+i*90/8);points.append((cx+corner*math.cos(a),cz+corner*math.sin(a)))
    parts=[cap(radius,y+height,cap_tex),cap(radius,y,cap_tex,True)]
    for i,(x,z) in enumerate(points):
        nx,nz=points[(i+1)%len(points)];mx,mz=(x+nx)/2,(z+nz)/2
        half=math.hypot(nx-x,nz-z)/2;angle=180-math.degrees(math.atan2(nz-z,nx-x))
        # Long vertical edges author along Z so their unrotated bounds remain within [-16,32].
        vertical=abs(nz-z)>abs(nx-x)
        parts.append({"from":[8+mx*16 if vertical else 8+(mx-half)*16,8+y*16,8+(mz-half)*16 if vertical else 8+mz*16],
            "to":[8+mx*16 if vertical else 8+(mx+half)*16,8+(y+height)*16,8+(mz+half)*16 if vertical else 8+mz*16],
            "rotation":{"origin":[8+mx*16,8,8+mz*16],"axis":"y","angle":angle-90 if vertical else angle},
            "faces":{"east" if vertical else "south":{"uv":[0,0,16,16],"texture":"#"+tex}}})
    return parts


def furniture():
    wood=Image.new("RGB",(128,128),"#38251d");d=ImageDraw.Draw(wood)
    for y in range(0,128,5):d.line((0,y,128,y+3),fill="#50352a",width=2)
    texture("surface/wood",wood)
    top=Image.new("RGBA",(1024,1024));d=ImageDraw.Draw(top)
    d.ellipse((1,1,1022,1022),fill="#38251d")
    for inset,col in ((8,"#684833"),(14,"#38251d"),(27,"#99704c"),(32,"#1d4938"),(39,"#286346")):
        d.ellipse((inset,inset,1023-inset,1023-inset),fill=col)
    # Low-contrast weave and sparse four-color inlays keep the playing area quiet.
    for y in range(50,974,4):
        half=math.sqrt(max(0,472**2-(y-512)**2));d.line((512-half,y,512+half,y),fill="#296548")
    for i,c in enumerate(COLORS.values()):
        a=i*math.pi/2;x,y=512+490*math.sin(a),512+490*math.cos(a)
        d.rectangle((x-5,y-5,x+5,y+5),fill=c)
    texture("surface/card-top",top)
    foot=Image.new("RGBA",(128,128));ImageDraw.Draw(foot).ellipse((0,0,127,127),fill="#38251d")
    texture("surface/wood-disc",foot)
    tex={"body":"tabletop3d:item/surface/wood","card":"tabletop3d:item/surface/card-top",
         "disc":"tabletop3d:item/surface/wood-disc"}
    model("card_table",tex,disc(1.5,-.19,.19,"body","card")
          +disc(.25,-.95125,.76125,"body","disc",32)+disc(.70,-1.03125,.08,"body","disc",64))
    cloth=ImageOps.colorize(ImageOps.grayscale(Image.open(ROOT/"resource-pack/source/mahjong/tablecloth.jpg")),"#213344","#34495a").resize((936,936))
    mj=Image.new("RGBA",(1024,1024));d=ImageDraw.Draw(mj)
    d.rounded_rectangle((0,0,1023,1023),radius=48,fill="#38251d")
    d.rounded_rectangle((12,12,1011,1011),radius=39,outline="#92684b",width=4)
    mask=Image.new("L",(936,936));ImageDraw.Draw(mask).rounded_rectangle((0,0,935,935),radius=30,fill=255)
    mj.paste(cloth,(44,44),mask);d=ImageDraw.Draw(mj)
    d.rounded_rectangle((53,53,970,970),radius=25,outline="#415768",width=2)
    texture("surface/mahjong-top",mj)
    tex["cloth"]="tabletop3d:item/surface/mahjong-top"
    parts=rounded_square(1.5,.14,-.19,.19,"body","cloth")
    for x in (-1.25,1.25):
        for z in (-1.25,1.25):parts.append(cube([8+(x-.075)*16,8-1.03125*16,8+(z-.075)*16],
                                                             [8+(x+.075)*16,8-.19*16,8+(z+.075)*16]))
    model("mahjong_table",tex,parts)
    panel=Image.new("RGBA",(512,512));d=ImageDraw.Draw(panel)
    d.rounded_rectangle((1,1,510,510),radius=28,fill="#171d27",outline="#080e18",width=6)
    d.polygon([(50,50),(462,50),(360,152),(152,152)],fill="#3c404b")
    d.polygon([(50,462),(462,462),(360,360),(152,360)],fill="#3c404b")
    d.polygon([(50,50),(152,152),(152,360),(50,462)],fill="#303540")
    d.polygon([(462,50),(360,152),(360,360),(462,462)],fill="#303540")
    d.rounded_rectangle((151,151,361,361),radius=10,fill="#070c14")
    for angle in range(4):
        layer=Image.new("RGBA",(512,512));ld=ImageDraw.Draw(layer)
        ld.rounded_rectangle((146,23,366,37),radius=6,fill="#5b5f71") # Outer riichi slot, radius .49.
        ld.rounded_rectangle((173,69,339,79),radius=4,fill="#1c3330") # Green turn bar, radius .39.
        layer=layer.rotate(angle*90);panel.alpha_composite(layer)
    d=ImageDraw.Draw(panel)
    for x in (42,470):
        for y in (42,470):d.rounded_rectangle((x-27,y-27,x+27,y+27),radius=6,fill="#676775",outline="#0c1420",width=3)
    ptex=texture("surface/panel",panel)
    model("mahjong_panel",{"body":"tabletop3d:item/surface/dark","panel":ptex},
          rounded_square(.55,.06,0,.012,"body","panel"))


def rings():
    for direction in (1,-1):
        scale=4
        im=Image.new("RGBA",(512*scale,512*scale));d=ImageDraw.Draw(im)
        for a in range(4):
            # One closed outline joins the head to the arc, without overlapping seams.
            tip=a*90-30 if direction==1 else a*90+30
            def point(angle,radius):
                rad=math.radians(angle)
                return ((256+radius*math.cos(rad))*scale,(256+radius*math.sin(rad))*scale)
            outline=[point(tip+direction*angle,240) for angle in range(60,9,-1)]
            outline += [point(tip+direction*10,258),point(tip,232),point(tip+direction*10,206)]
            outline += [point(tip+direction*angle,224) for angle in range(10,61)]
            d.polygon(outline,fill=CREAM)
        im=im.resize((512,512),Image.Resampling.LANCZOS)
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
    from pack_furniture_preview import render
    for name in ("card_table","mahjong_table","mahjong_panel"):render(name)


def main():
    import shutil
    if not BUILD.resolve().is_relative_to((ROOT/"target").resolve()):
        raise ValueError("Build output must stay under project target")
    if BUILD.exists():shutil.rmtree(BUILD)
    texture("surface/ivory",Image.new("RGB",(16,16),"#fff9eb"))
    texture("surface/dark",Image.new("RGB",(16,16),"#25252d"))
    back=texture("surface/card-back",card_image("back"))
    tile_back_image=Image.new("RGB",(128,192),"#176a53")
    tile_back_draw=ImageDraw.Draw(tile_back_image)
    tile_back_draw.rectangle((5,5,122,186),outline="#319878",width=4)
    tile_back=texture("surface/mahjong-back",tile_back_image)
    atlas=Image.open(ROOT/"resource-pack/source/mahjong/hand_ui.png").convert("RGB")
    for suit,row in (("s",0),("m",1),("p",2)):
        for n in range(10):face_model(f"mahjong_{suit}{n}",atlas.crop((n*80+5,row*129+15,n*80+75,row*129+124)),back=tile_back)
    for n in range(1,8):face_model(f"mahjong_z{n}",atlas.crop(((n-1)*80+5,402,(n-1)*80+75,511)),back=tile_back)
    for n in range(1,9):face_model(f"mahjong_f{n}",flower_image(n),back=tile_back)
    face_model("mahjong_back",tile_back_image,back=tile_back)
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
    (config.parent.parent/"pack.yml").write_text("name: Tabletop 3D\nauthor: Tabletop3D\nversion: 1.9.0-SNAPSHOT\n",encoding="utf-8")
    target=ROOT/"target/tabletop-resource-pack.zip"
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as z:
        for path in sorted(BUILD.rglob("*")):
            if path.is_file():z.write(path,path.relative_to(BUILD).as_posix())
    manifest={"models":sorted(CATALOG),"sounds_seconds":durations,"sha1":hashlib.sha1(target.read_bytes()).hexdigest(),
              "sha256":hashlib.sha256(target.read_bytes()).hexdigest(),"bytes":target.stat().st_size}
    write_json(ROOT/"target/resource-pack-manifest.json",manifest)
    previews();print(json.dumps({k:v for k,v in manifest.items() if k!="models"},indent=2))


if __name__=="__main__":main()
