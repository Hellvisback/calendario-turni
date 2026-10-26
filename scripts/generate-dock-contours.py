"""Bake the approved liquid contour once; Android only interpolates cached heights."""
import math,struct
from pathlib import Path

def smooth(a,b,p):
 t=max(0,min(1,(p-a)/(b-a)));return t*t*(3-2*t)
def union(a,b,k):
 h=max(k-abs(a-b),0)/k;return min(a,b)-h*h*k*.25

def contour(p):
 pulse=math.sin(math.pi*p);travel=smooth(.06,.96,p);r=34
 radius=23*smooth(.04,.55,p);centers=[161.5,218.5]
 lobes=[(x,r*(1-.008*pulse),r)for x in centers]
 if radius>.12:lobes += [(centers[0]-71*travel,radius*(1+.2*pulse),radius*(1-.1*pulse)),(centers[1]+71*travel,radius*(1+.2*pulse),radius*(1-.1*pulse))]
 blend=max(9,4*(math.hypot(28.5,25)-34));side=28-23*smooth(.90,.995,p)
 def distance(lobe,x,y):
  cx,rx,ry=lobe;return (math.sqrt(((x-cx)/rx)**2+(y/ry)**2)-1)*min(rx,ry)
 def field(x,y):
  value=union(distance(lobes[0],x,y),distance(lobes[1],x,y),blend)
  for lobe in lobes[2:]:value=union(value,distance(lobe,x,y),side)
  return value
 heights=[]
 for i in range(761):
  x=i*.5
  if field(x,0)>0:heights.append(0);continue
  lo,hi=0.,45.
  for _ in range(11):
   mid=(lo+hi)/2
   if field(x,mid)<=0:lo=mid
   else:hi=mid
  heights.append((lo+hi)/2)
 return heights

if __name__=='__main__':
 target=Path(__file__).resolve().parents[1]/'android-app/app/src/main/res/raw/dock_contours.bin'
 target.parent.mkdir(parents=True,exist_ok=True)
 frames=[contour(i/60)for i in range(61)]
 assert all(0<=h<=45 and math.isfinite(h)for row in frames for h in row)
 target.write_bytes(struct.pack('>iiff',61,761,.5,92)+b''.join(struct.pack('>761f',*row)for row in frames))
 print('Baked',target.stat().st_size,'bytes')
