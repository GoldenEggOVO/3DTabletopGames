"""Orthographic preview of actual generated model quads/textures, not a client screenshot."""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'target/resource-pack-build/assets/tabletop3d'
OUT=ROOT/'target/pack-preview'

def rotate(v,rotation):
    if not rotation:return v
    a=math.radians(rotation['angle']);o=rotation['origin'];x,y,z=[v[i]-o[i] for i in range(3)]
    c,s=math.cos(a),math.sin(a)
    axis=rotation.get('axis','y')
    if axis=='x': x,y,z=x,y*c-z*s,y*s+z*c
    elif axis=='y': x,y,z=x*c+z*s,y,-x*s+z*c
    else: x,y,z=x*c-y*s,x*s+y*c,z
    return (o[0]+x,o[1]+y,o[2]+z)

def render(name):
    model=json.loads((ASSETS/f'models/item/{name}.json').read_text())
    faces=[];scale=24 if name=='yacht_table' else 12 if name!='mahjong_panel' else 20
    for part in model['elements']:
        x,y,z=part['from'];X,Y,Z=part['to']
        vertices={'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
                  'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
                  'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
                  'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
                  'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
                  'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)]}
        normals={'up':(0,1,0),'down':(0,-1,0),'south':(0,0,1),'north':(0,0,-1),'east':(1,0,0),'west':(-1,0,0)}
        for side,face in part['faces'].items():
            a=math.radians(part.get('rotation',{}).get('angle',0));nx,ny,nz=normals[side]
            rotation=dict(part.get('rotation',{}))
            if rotation:rotation['origin']=[0,0,0]
            normal=rotate((nx,ny,nz),rotation)
            if normal[0]*.7+normal[1]*.9+normal[2]<=0:continue
            v=[rotate(p,part.get('rotation')) for p in vertices[side]]
            screen=[(512+(p[0]-p[2])*scale,280+((p[0]+p[2]-16)*.26-(p[1]-8)*.95)*scale) for p in v]
            depth=sum(.7*p[0]+.9*p[1]+p[2] for p in v)/4
            texture=model['textures'][face['texture'][1:]].split(':',1)[1]
            faces.append((depth,screen,ASSETS/('textures/'+texture+'.png'),face['uv']))
    canvas=Image.new('RGBA',(1024,720),'#17232e')
    for _,v,path,uv in sorted(faces,key=lambda f:f[0]):
        tex=Image.open(path).convert('RGBA');w,h=tex.size
        tex=tex.crop((min(uv[0],uv[2])*w/16,min(uv[1],uv[3])*h/16,max(uv[0],uv[2])*w/16,max(uv[1],uv[3])*h/16))
        if uv[0]>uv[2]:tex=tex.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if uv[1]>uv[3]:tex=tex.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        if tex.width==0 or tex.height==0:continue
        a,b,c=v[0],v[1],v[3];ux,uy=b[0]-a[0],b[1]-a[1];vx,vy=c[0]-a[0],c[1]-a[1]
        det=ux*vy-uy*vx
        if abs(det)<.01:continue
        coeff=(tex.width*vy/det,-tex.width*vx/det,tex.width*(vx*a[1]-vy*a[0])/det,
               -tex.height*uy/det,tex.height*ux/det,tex.height*(uy*a[0]-ux*a[1])/det)
        layer=tex.transform(canvas.size,Image.Transform.AFFINE,coeff,Image.Resampling.NEAREST)
        mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).polygon(v,fill=255)
        canvas.alpha_composite(Image.composite(layer,Image.new('RGBA',canvas.size),mask))
    draw=ImageDraw.Draw(canvas);draw.text((24,680),name+' / actual model geometry and textures / no client lighting',fill='white')
    canvas.convert('RGB').save(OUT/f'{name}-geometry.png')

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    for name in ('card_table','mahjong_table','mahjong_panel','doudizhu_table','liars_bar_table','texas_holdem_table','board_chess','board_connectfour','chess_white_knight','playing_spades_ace','yacht_table'):render(name)
