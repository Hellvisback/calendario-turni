import subprocess, pathlib, sys, time, json, xml.etree.ElementTree as ET, re, os, shutil
from datetime import date
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[2]/'qa-artifacts/calendar'
ROOT.mkdir(parents=True,exist_ok=True)
FIXTURE=pathlib.Path(__file__).parent/'fixtures/calendar-presentation.json'
ADB=os.environ.get('ADB') or shutil.which('adb')
if not ADB:
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk: ADB=str(pathlib.Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
if not ADB: raise SystemExit('Set ANDROID_HOME or put adb on PATH.')
PKG='com.turni.calendario.setupqa'
def adb(*args,**kw):return subprocess.check_output([ADB,*args],**kw)
def nodes():
    result=adb('shell','uiautomator','dump','/sdcard/calendar-morph-qa.xml').decode()
    if 'dumped' not in result:raise RuntimeError(result)
    raw=adb('shell','cat','/sdcard/calendar-morph-qa.xml')
    (ROOT/'ui.xml').write_bytes(raw)
    return list(ET.fromstring(raw).iter('node'))
def tap(label):
    n=next(n for n in nodes() if label in (n.get('text'),n.get('content-desc')))
    b=list(map(int,re.findall(r'\d+',n.get('bounds'))))
    adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
def burst(name,x,y):
    before=time.monotonic()
    adb('shell','input','tap',str(x),str(y))
    for i in range(5):
        data=adb('exec-out','screencap','-p')
        elapsed=round((time.monotonic()-before)*1000)
        (ROOT/f'{name}-{i}-{elapsed}ms.png').write_bytes(data)
    print(name,round((time.monotonic()-before)*1000),'ms')
if __name__=='__main__':
    if sys.argv[1]=='seed':
        fixture=json.loads(FIXTURE.read_text(encoding='utf-8'))
        for row in fixture.get('dayExtraInfo',[]):
            row['date']=date.today().replace(day=int(row['date'][-2:])).isoformat()
        fixture['enabledHolidayIds']=['capodanno','natale','ognissanti']
        adb('shell','am','force-stop',PKG)
        adb('shell','run-as',PKG,'mkdir','-p','files','shared_prefs')
        subprocess.run([ADB,'exec-in','run-as',PKG,'tee','files/turnisync-database-v3.json'],input=json.dumps(fixture).encode(),stdout=subprocess.DEVNULL,check=True)
        prefs=b'<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="state">complete</string><int name="step" value="0" /></map>'
        subprocess.run([ADB,'exec-in','run-as',PKG,'tee','shared_prefs/turnisync_first_run_v1.xml'],input=prefs,stdout=subprocess.DEVNULL,check=True)
        print(adb('shell','am','start','-n',PKG+'/com.turni.calendario.MainActivity').decode())
    elif sys.argv[1]=='list':
        for n in nodes():
            if n.get('text') or n.get('content-desc'):print(n.get('text') or n.get('content-desc'),n.get('bounds'))
    elif sys.argv[1]=='tap':tap(sys.argv[2])
    elif sys.argv[1]=='burst':burst(sys.argv[2],*map(int,sys.argv[3:5]))
    elif sys.argv[1]=='shot':(ROOT/sys.argv[2]).write_bytes(adb('exec-out','screencap','-p'))
    elif sys.argv[1]=='db':
        raw=adb('exec-out','run-as',PKG,'cat','files/turnisync-database-v3.json')
        (ROOT/sys.argv[2]).write_bytes(raw)
        data=json.loads(raw);print('assignments',len(data['calendarShifts']))
