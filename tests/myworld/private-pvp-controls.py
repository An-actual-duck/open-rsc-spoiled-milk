#!/usr/bin/env python3
"""Loopback controls for PrivatePvpServer; all game mutations run on the server tick."""
import argparse,json,os,subprocess
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
parser=argparse.ArgumentParser();parser.add_argument('root',type=Path);args=parser.parse_args()
ROOT=args.root.resolve();SUPPORT=ROOT/'test-support';PORT=47409
assert ROOT.is_relative_to(Path('/home/justin/Core-Framework/output/tests/pvp-readiness'))
assert (ROOT/'PRIVATE-PVP.json').is_file()
ACTIONS={
 'preset-melee':'Melee kit','preset-ranged':'Shuriken kit','preset-magic':'Magic kit','preset-cleric':'Cleric kit',
 'attack-melee':'Enemies use melee','attack-ranged':'Enemies use shurikens','attack-magic':'Enemies use magic',
 'pause':'Pause opponents','reset':'Reset scene and restock','allies':'Use premade teammates (default)',
 'no-allies':'Make all three enemies','friend-blue':'Friend Blue (your list only)','blue-friends-you':'Blue friends you (their list only)','no-friends':'Clear test friendships','edge':'Move everyone to Wilderness edge','safe-opponent':'Move Red into safety','safe-self':'Move yourself into safety'}
PAGE='''<!doctype html><meta charset="utf-8"><title>Private PvP test</title><style>
body{font:17px system-ui;background:#172122;color:#e7eeee;max-width:960px;margin:40px auto;padding:0 24px}h1{font-size:28px}button{font:inherit;color:inherit;background:#31474b;border:1px solid #637779;border-radius:6px;padding:12px;margin:5px;cursor:pointer}button:hover{background:#476368}section{margin:22px 0}#status{padding:18px;background:#253436;border-radius:6px;min-height:70px}small{color:#bfcecf}#reply{min-height:24px;color:#f2cf86}</style>
<h1>Private PvP test</h1><p>One game client, three real practice players. Changes here apply while logged in.</p>
<div id="status">Starting private server…</div><section>'''+''.join('<button data-action="'+key+'">'+title+'</button>' for key,title in ACTIONS.items())+'''</section>
<button id="client">Open private game client</button><p id="reply"></p>
<p>Every kit starts with Blue in your party and Gold in your clan. Attack Red to test combat. The enemy-attack buttons leave your teammates passive. No second client is needed.</p>
<p>Blue and Gold should be excluded from your splash damage. Blue also appears in your party panel and receives party Cleric buffs. Use Make all three enemies to test larger enemy groups. For boundary tests, move everyone to the edge, then move Red or yourself into safety while attacks are pending.</p>
<p>To test one-way friendship, choose Make all three enemies, then either friend control. Friendship protects the person on the attacker’s list; it does not grant immunity against somebody who has not friended you.</p>
<p><small>Reset restores health, equipment and opponents. Switching kits resets the scene and keeps your selected team mode. Your previous private items are banked. This uses a copied private database and map; the public server is untouched.</small></p>
<script>async function send(path,data){try{let r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data||{})});document.querySelector('#reply').textContent=(await r.json()).message}catch(e){document.querySelector('#reply').textContent=String(e)}}
for(let b of document.querySelectorAll('[data-action]'))b.onclick=()=>send('/action',{action:b.dataset.action});document.querySelector('#client').onclick=()=>send('/client');
async function refresh(){try{let s=await(await fetch('/status',{cache:'no-store'})).json();document.querySelector('[data-action=allies]').style.background=s.teammates?'#476368':'';document.querySelector('[data-action=no-allies]').style.background=s.teammates?'':'#476368';document.querySelector('#status').textContent=(s.online?'Logged in · ':'Log in to start · ')+(s.preset||'melee')+' kit · '+(s.message||'Starting')+' '+(s.opponents||[]).map(p=>p.name+' ['+(p.role||'Enemy')+']: '+p.hp+' HP ('+p.x+', '+p.y+')').join(' · ')}catch(e){}}refresh();setInterval(refresh,1000);</script>'''
def status():
 try:return json.loads((SUPPORT/'status.json').read_text())
 except (OSError,ValueError):return {}
class Handler(BaseHTTPRequestHandler):
 def respond(self,code,data,kind='application/json'):
  content=data.encode() if isinstance(data,str) else json.dumps(data).encode();self.send_response(code);self.send_header('Content-Type',kind+'; charset=utf-8');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(content)));self.end_headers();self.wfile.write(content)
 def do_GET(self):
  if self.path=='/':self.respond(200,PAGE,'text/html')
  elif self.path=='/status':self.respond(200,status())
  else:self.respond(404,{'message':'Not found'})
 def do_POST(self):
  if self.headers.get('Origin') not in (None,'http://127.0.0.1:'+str(PORT)):self.respond(403,{'message':'Local controls only'});return
  if self.path=='/client':
   pidfile=SUPPORT/'client-pid.txt'
   if pidfile.exists():
    try:
     proc=Path('/proc')/pidfile.read_text().strip()
     if (proc/'cwd').resolve()==ROOT/'Client_Base' and b'Open_RSC_Client.jar' in (proc/'cmdline').read_bytes():self.respond(200,{'message':'Private client is already open.'});return
    except OSError:pass
   with (SUPPORT/'client.log').open('ab') as log:p=subprocess.Popen(['bash',str(ROOT/'Open private client.sh')],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
   pidfile.write_text(str(p.pid));self.respond(200,{'message':'Private client opening.'});return
  if self.path!='/action':self.respond(404,{'message':'Not found'});return
  size=int(self.headers.get('Content-Length','0'))
  if not 0<=size<=512:self.respond(413,{'message':'Request too large'});return
  try:action=json.loads(self.rfile.read(size)).get('action')
  except (ValueError,AttributeError):self.respond(400,{'message':'Invalid command'});return
  if action not in ACTIONS:self.respond(400,{'message':'Unknown command'});return
  target=SUPPORT/'request.json'
  if target.exists():self.respond(409,{'message':'Previous change is still processing.'});return
  temp=SUPPORT/'request.next';temp.write_text(json.dumps({'action':action}));os.replace(temp,target)
  self.respond(200,{'message':'Applying: '+ACTIONS[action]})
if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',PORT),Handler).serve_forever()
