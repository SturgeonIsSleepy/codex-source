import json, re, urllib.request, urllib.error
from pathlib import Path

import argparse
parser=argparse.ArgumentParser(description='Read-only anonymous YTM metadata availability probe')
parser.add_argument('--output', required=True)
out=Path(parser.parse_args().output); out.mkdir(parents=True,exist_ok=True)
def request(url, payload=None, headers=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers={'User-Agent':'Mozilla/5.0', **(headers or {})})
    with urllib.request.urlopen(req, timeout=20) as response:
        content = response.read(2_000_001)
        assert len(content) <= 2_000_000, 'Response exceeds app size limit'
        return content.decode('utf-8')
try:
    html = request('https://music.youtube.com/')
    version = re.search(r'"(?:INNERTUBE_CLIENT_VERSION|INNERTUBE_CONTEXT_CLIENT_VERSION|clientVersion)"\s*:\s*"([^"]+)"', html)
    assert version, 'Bootstrap clientVersion missing'
    visitor = re.search(r'"VISITOR_DATA"\s*:\s*"([^"]+)"', html)
    client={'clientName':'WEB_REMIX','clientVersion':version[1],'hl':'en','gl':'US'}
    if visitor: client['visitorData']=visitor[1]
    headers={'Origin':'https://music.youtube.com','Content-Type':'application/json','X-Youtube-Client-Name':'67','X-Youtube-Client-Version':version[1]}
    if visitor: headers['X-Goog-Visitor-Id']=visitor[1]
    def call(endpoint, body):
        return json.loads(request('https://music.youtube.com/youtubei/v1/'+endpoint+'?prettyPrint=false', {'context':{'client':client},**body}, headers))
    search=call('search', {'query':'YOASOBI アイドル','params':'EgWKAQIIAWoMEA4QChADEAQQCRAF'})
    def renderers(node, name):
        if isinstance(node, dict):
            for k,v in node.items():
                if k==name: yield v
                else: yield from renderers(v,name)
        elif isinstance(node,list):
            for v in node: yield from renderers(v,name)
    rows=list(renderers(search,'musicResponsiveListItemRenderer'))
    video=None
    for row in rows:
        video=row.get('playlistItemData',{}).get('videoId')
        if video: break
        for ep in renderers(row,'watchEndpoint'):
            video=ep.get('videoId')
            if video: break
        if video: break
    assert video, 'No song search renderer with videoId'
    radio=call('next', {'videoId':video,'playlistId':'RDAMVM'+video,'params':'wAEB','isAudioOnly':True,'enablePersistentPlaylistPanel':True})
    panel=list(renderers(radio,'playlistPanelVideoRenderer'))
    assert panel, 'No radio song renderers'
    # Public song renderer fixtures only. No bootstrap visitor identifiers are retained.
    (out/'search-renderers.json').write_text(json.dumps({'contents':[{'musicResponsiveListItemRenderer':r} for r in rows]},ensure_ascii=False),encoding='utf-8')
    (out/'radio-renderers.json').write_text(json.dumps({'contents':[{'playlistPanelVideoRenderer':r} for r in panel]},ensure_ascii=False),encoding='utf-8')
    result={'success':True,'searchRenderers':len(rows),'radioRenderers':len(panel),'seed':video,'authenticated':False,'playlistWrites':False}
except Exception as e:
    result={'success':False,'error':type(e).__name__+': '+str(e),'authenticated':False,'playlistWrites':False}
(out/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False))
