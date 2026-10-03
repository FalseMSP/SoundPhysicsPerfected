import os
import json, collections, re
R = json.load(open('analysis.json'))
by = collections.OrderedDict()
for r in R: by.setdefault(r['id'], []).append(r)

def block_summary(bid, rs):
    d = [r for r in rs if r['def']][0]
    withm = [r for r in rs if r['hasModel']]
    best = max(withm, key=lambda r: r.get('open', 0)) if withm else d
    ex = max(withm, key=lambda r: r.get('extra', 0)) if withm else d
    flags = []
    if withm and best.get('open', 0) >= 0.05 and not best['colEmpty']: flags.append('GAPS')
    if any(r['colTall'] for r in rs): flags.append('TALL')
    if not withm and any(not r['colEmpty'] for r in rs): flags.append('NO_MODEL')
    if withm and ex.get('extra', 0) >= 0.05: flags.append('VISIBLE_NO_COLLISION' if ex['colEmpty'] else 'VISIBLE_BEYOND_COLLISION')
    if d['fluid'] not in ('minecraft:empty',) : flags.append('FLUID:' + d['fluid'].split(':')[1])
    return {'id': bid, 'cls': d['cls'], 'supers': d['supers'], 'template': d['template'], 'layer': d['layer'], 'snd': d['snd'],
            'res': d['res'], 'flags': flags, 'open': round(best.get('open', 0), 3), 'openSide': round(best.get('openSide', 0), 3),
            'openState': best['props'], 'extra': round(ex.get('extra', 0), 3), 'extraState': ex['props'],
            'colFull': d['colFull'], 'colEmpty': all(r['colEmpty'] for r in rs), 'occ': d['occ'], 'states': len(rs)}

blocks = [block_summary(b, rs) for b, rs in by.items()]
fam = collections.OrderedDict()
for b in blocks:
    key = b['cls'] + ' | ' + b['template']
    fam.setdefault(key, []).append(b)
json.dump({'blocks': blocks, 'families': {k: [b['id'] for b in v] for k, v in fam.items()}}, open('families.json', 'w'), indent=0)
print('blocks', len(blocks), 'families', len(fam))
flagged = [(k, v) for k, v in fam.items() if any(b['flags'] for b in v)]
print('families with a mismatch flag', len(flagged))
for k, v in flagged:
    fl = sorted(set(f for b in v for f in b['flags']))
    o = max(b['open'] for b in v); os_ = max(b['openSide'] for b in v); e = max(b['extra'] for b in v)
    names = ', '.join(b['id'].split(':')[1] for b in v[:4]) + (f' (+{len(v)-4})' if len(v) > 4 else '')
    print(f"{k[:58]:<59} n={len(v):<3} {'/'.join(fl):<44} through={o:.2f} face={os_:.2f} extra={e:.2f}  {names}")
