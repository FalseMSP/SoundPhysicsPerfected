"""Visual vs collision coverage for every block state of 1.21.11.

For each state: the blockstate's models (variants/multipart), their elements and face textures (alpha), rotated
like the game does, splatted onto a 32x32 grid (half-pixel cells) for rays along X, Y and Z. A cell is visually
blocked when an opaque texel of a face facing along that axis lands in it. Compared with the collision shape's
projection (clipped to the block's own cell)."""
import os
import json, zipfile, io, math, sys, collections
import numpy as np
from PIL import Image

JAR = os.environ['MC_CLIENT_JAR']
Z = zipfile.ZipFile(JAR)
G = 32  # grid cells per block side
ALPHA_CUT = 26  # cutout discards alpha < 0.1

def jload(path):
    try: return json.loads(Z.read(path))
    except KeyError: return None

def rl(name, kind):
    ns, p = (name.split(':', 1) if ':' in name else ('minecraft', name))
    return f'assets/{ns}/{kind}/{p}'

_models = {}
def model(name):
    if name in _models: return _models[name]
    m = jload(rl(name, 'models') + '.json') or {}
    textures, elements, parents = {}, None, [name]
    if 'parent' in m:
        pm = model(m['parent'])
        textures.update(pm['textures']); elements = pm['elements']; parents = [name] + pm['parents']
    textures.update(m.get('textures', {}))
    if 'elements' in m: elements = m['elements']
    r = {'textures': textures, 'elements': elements, 'parents': parents}
    _models[name] = r
    return r

def resolve_tex(textures, ref, depth=0):
    while ref and ref.startswith('#') and depth < 10:
        ref = textures.get(ref[1:]); depth += 1
    return ref

_tex = {}
def texture(name):
    if name in _tex: return _tex[name]
    try:
        im = Image.open(io.BytesIO(Z.read(rl(name, 'textures') + '.png'))).convert('RGBA')
        a = np.array(im)[:, :, 3]
        w = a.shape[1]
        if a.shape[0] > w: a = a[:w]  # animated: first frame
        _tex[name] = a >= ALPHA_CUT
    except KeyError:
        _tex[name] = None
    return _tex[name]

FACES = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}

def default_uv(face, f, t):
    return {'down': [f[0], 16 - t[2], t[0], 16 - f[2]], 'up': [f[0], f[2], t[0], t[2]],
            'north': [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], 'south': [f[0], 16 - t[1], t[0], 16 - f[1]],
            'west': [f[2], 16 - t[1], t[2], 16 - f[1]], 'east': [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[face]

def face_points(face, f, t, n=64):
    """Sample points on the face (block px) and their (s, t) in 0..1 along the face's texture axes."""
    a = (np.arange(n) + 0.5) / n
    S, T = np.meshgrid(a, a, indexing='ij'); S = S.ravel(); T = T.ravel()
    fx, fy, fz = f; tx, ty, tz = t
    if face == 'north': X = tx - S * (tx - fx); Y = ty - T * (ty - fy); Zc = np.full_like(S, fz)
    elif face == 'south': X = fx + S * (tx - fx); Y = ty - T * (ty - fy); Zc = np.full_like(S, tz)
    elif face == 'west': Zc = fz + S * (tz - fz); Y = ty - T * (ty - fy); X = np.full_like(S, fx)
    elif face == 'east': Zc = tz - S * (tz - fz); Y = ty - T * (ty - fy); X = np.full_like(S, tx)
    elif face == 'up': X = fx + S * (tx - fx); Zc = fz + T * (tz - fz); Y = np.full_like(S, ty)
    else: X = fx + S * (tx - fx); Zc = tz - T * (tz - fz); Y = np.full_like(S, fy)
    return np.stack([X, Y, Zc], 1), S, T

def rot_matrix(axis, deg):
    r = math.radians(deg); c, s = math.cos(r), math.sin(r)
    if axis == 'x': return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y': return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

_masks = {}
def model_masks(name, rx, ry):
    """Three GxG boolean masks (rays along x, y, z) for a model under a blockstate rotation; None: no geometry."""
    key = (name, rx, ry)
    if key in _masks: return _masks[key]
    m = model(name)
    els = m['elements']
    if not els:
        _masks[key] = None
        return None
    masks = [np.zeros((G, G), bool) for _ in range(6)]  # x, y, z union, then + side per axis (- = union minus nothing)
    sides = [[np.zeros((G, G), bool), np.zeros((G, G), bool)] for _ in range(3)]
    # Blockstate rotation: x first, then y (negative angles, about the centre), as the game's model rotation does.
    R = rot_matrix('y', -ry) @ rot_matrix('x', -rx)
    for e in els:
        f, t = e['from'], e['to']
        er = e.get('rotation')
        for face, fd in e.get('faces', {}).items():
            tex = resolve_tex(m['textures'], fd.get('texture'))
            if not tex: continue
            img = texture(tex)
            if img is None: continue
            pts, S, T = face_points(face, f, t)
            uv = fd.get('uv') or default_uv(face, f, t)
            rot = fd.get('rotation', 0) % 360
            s2, t2 = {0: (S, T), 90: (T, 1 - S), 180: (1 - S, 1 - T), 270: (1 - T, S)}[rot]
            u = uv[0] + (uv[2] - uv[0]) * s2; v = uv[1] + (uv[3] - uv[1]) * t2
            h, w = img.shape
            iu = np.clip((u / 16 * w).astype(int), 0, w - 1); iv = np.clip((v / 16 * h).astype(int), 0, h - 1)
            opaque = img[iv, iu]
            normal = np.array(FACES[face], float)
            p = pts.astype(float)
            if er and er.get('angle', 0):
                o = np.array(er['origin'], float)
                Re = rot_matrix(er['axis'], er['angle'])
                p = (p - o) @ Re.T + o
                normal = Re @ normal
            p = (p - 8.0) @ R.T + 8.0
            normal = R @ normal
            p = p[opaque]
            if len(p) == 0: continue
            for ax in range(3):
                if abs(normal[ax]) <= 0.5: continue  # edge-on to rays along this axis
                o1, o2 = [i for i in range(3) if i != ax]
                a = np.clip((p[:, o1] / 16 * G).astype(int), 0, G - 1)
                b = np.clip((p[:, o2] / 16 * G).astype(int), 0, G - 1)
                inside = (p[:, o1] >= -0.01) & (p[:, o1] <= 16.01) & (p[:, o2] >= -0.01) & (p[:, o2] <= 16.01)
                masks[ax][a[inside], b[inside]] = True
                sides[ax][0 if normal[ax] > 0 else 1][a[inside], b[inside]] = True
    masks = masks[:3] + [sides]
    _masks[key] = masks
    return masks

def box_masks(boxes):
    masks = [np.zeros((G, G), bool) for _ in range(3)]
    for bx in boxes:
        lo = [max(0.0, min(1.0, v)) for v in bx[:3]]; hi = [max(0.0, min(1.0, v)) for v in bx[3:]]
        if any(hi[i] - lo[i] <= 1e-6 for i in range(3)):
            # flat boxes still block rays across them
            pass
        for ax in range(3):
            o1, o2 = [i for i in range(3) if i != ax]
            if hi[ax] - lo[ax] < 1e-6 and False: continue
            a0, a1 = int(round(lo[o1] * G)), int(round(hi[o1] * G)); b0, b1 = int(round(lo[o2] * G)), int(round(hi[o2] * G))
            if a1 > a0 and b1 > b0: masks[ax][a0:a1, b0:b1] = True
    return masks

def parse_props(s):
    return dict(kv.split('=', 1) for kv in s.split(',')) if s else {}

def matches(cond, props):
    if 'OR' in cond: return any(matches(c, props) for c in cond['OR'])
    if 'AND' in cond: return all(matches(c, props) for c in cond['AND'])
    for k, v in cond.items():
        val = str(v).lower()
        neg = False
        if val.startswith('!'): neg = True; val = val[1:]
        ok = props.get(k) in val.split('|')
        if ok == neg: return False
    return True

def state_models(bid, props):
    bs = jload(rl(bid, 'blockstates') + '.json')
    if bs is None: return None
    out = []
    if 'variants' in bs:
        for key, val in bs['variants'].items():
            cond = parse_props(key) if key else {}
            if all(props.get(k) == v for k, v in cond.items()):
                v = val[0] if isinstance(val, list) else val
                out.append((v['model'], v.get('x', 0), v.get('y', 0)))
                break
    for part in bs.get('multipart', []):
        if 'when' not in part or matches(part['when'], props):
            v = part['apply'][0] if isinstance(part['apply'], list) else part['apply']
            out.append((v['model'], v.get('x', 0), v.get('y', 0)))
    return out

def root_parent(models):
    if not models: return '-'
    ps = model(models[0][0])['parents']
    # the first ancestor that defines elements is the shape template
    for p in ps:
        m = jload(rl(p, 'models') + '.json') or {}
        if 'elements' in m: return p.replace('minecraft:', '')
    return ps[-1].replace('minecraft:', '')

rows = []
for line in open('states.jsonl'):
    s = json.loads(line)
    if s['air']: continue
    props = parse_props(s['props'])
    models = state_models(s['id'], props)
    vis = None
    if models:
        for (mn, rx, ry) in models:
            mm = model_masks(mn, rx, ry)
            if mm is None: continue
            if vis is None:
                vis = [mm[i].copy() for i in range(3)] + [[[mm[3][a][0].copy(), mm[3][a][1].copy()] for a in range(3)]]
            else:
                for i in range(3): vis[i] |= mm[i]
                for a in range(3):
                    for k in range(2): vis[3][a][k] |= mm[3][a][k]
    col = box_masks(s['col']); outl = box_masks(s['out'])
    r = {'id': s['id'], 'props': s['props'], 'def': s['def'], 'cls': s['cls'][0], 'supers': s['cls'], 'layer': s['layer'],
         'snd': s['snd'], 'res': s['res'], 'fluid': s['fluid'], 'fh': s['fh'], 'occ': s['occ'],
         'template': root_parent(models), 'hasModel': vis is not None,
         'colTall': any(b[4] > 1.0001 or b[1] < -0.0001 for b in s['col']),
         'colFull': s['col'] == [[0, 0, 0, 1, 1, 1]] or s['col'] == [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]],
         'colEmpty': not s['col']}
    for ax, name in enumerate('xyz'):
        c = col[ax]; v = vis[ax] if vis is not None else None
        r['c' + name] = c.mean()
        if v is not None:
            r['v' + name] = v.mean()
            r['gap' + name] = (c & ~v).sum() / max(c.sum(), 1)  # collision footprint that is see-through
            r['extra' + name] = (v & ~c).mean()                # visible where nothing collides
    if vis is not None:
        cs = sum(col[a].sum() for a in range(3))
        r['open'] = sum((col[a] & ~vis[a]).sum() for a in range(3)) / cs if cs else 0.0
        r['extra'] = sum((vis[a] & ~col[a]).sum() for a in range(3)) / (3 * G * G)
        # One layer: the open part of each side on its own, where the collision shape is (a perforated sheet).
        r['openSide'] = sum((col[a] & ~vis[3][a][k]).sum() for a in range(3) for k in range(2)) / (2 * cs) if cs else 0.0
    rows.append(r)
json.dump(rows, open('analysis.json', 'w'))
print('states analysed', len(rows), 'with model geometry', sum(1 for r in rows if r['hasModel']))
