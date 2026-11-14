import pathlib,importlib.util,time,re
p=pathlib.Path(__file__).parent
s=importlib.util.spec_from_file_location('qa',p/'calendar_probe.py');q=importlib.util.module_from_spec(s);s.loader.exec_module(q)
def coords(ns,label):
    n=next(n for n in ns if label in (n.get('text'),n.get('content-desc')))
    a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));return (a+c)//2,(b+d)//2
q.tap('Modifica turni');q.tap('MAT');ns=q.nodes()
positions={d:coords(ns,str(d)) for d in [18,19,20,21]}
for d,(x,y) in positions.items():q.adb('shell','input','tap',str(x),str(y))
q.nodes();(q.ROOT/'painted-cells.png').write_bytes(q.adb('exec-out','screencap','-p'))
q.tap('Gomma');x,y=positions[21];q.adb('shell','input','swipe',str(x),str(y),str(x),str(y),'800')
q.nodes();(q.ROOT/'eraser-range.png').write_bytes(q.adb('exec-out','screencap','-p'))
print('Painted sequence and eraser anchor captured.')
