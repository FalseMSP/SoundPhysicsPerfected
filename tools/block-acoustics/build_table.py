"""Builds block_acoustics.json: every vanilla block's material, and the gaps of blocks that are open where they collide."""
import os
import json, math, collections, sys, zipfile
A = json.load(open(os.path.join(os.path.dirname(__file__), 'materials-reviewed.json')))
D = json.load(open('families.json')); B = {b['id'].split(':')[1]: b for b in D['blocks']}
R = json.load(open('analysis.json'))
states = collections.defaultdict(list)
for r in R: states[r['id'].split(':')[1]].append(r)
Z = zipfile.ZipFile(os.environ['MC_MERGED_JAR'])
def tag(name, seen=None):
    d = json.loads(Z.read(f'data/minecraft/tags/block/{name}.json')); out = set()
    for v in d['values']:
        v = v if isinstance(v, str) else v['id']
        out |= tag(v[1:].replace('minecraft:', '')) if v.startswith('#') else {v.replace('minecraft:', '')}
    return out

mat = dict(A)
# Reviewed corrections to the per-block pass.
mat.update({b: 'stone' for b in ['raw_iron_block', 'raw_copper_block', 'raw_gold_block', 'ancient_debris', 'sandstone', 'red_sandstone',
                                  'furnace', 'smoker', 'blast_furnace']})
mat.update({b: 'porous_stone' for b in ['nether_gold_ore', 'nether_quartz_ore']})
mat.update({b: 'smooth_stone' for b in ['resin_bricks', 'cut_sandstone', 'cut_red_sandstone', 'purpur_pillar', 'quartz_pillar',
                                         'polished_basalt', 'smooth_basalt', 'obsidian', 'crying_obsidian', 'flower_pot']})
mat.update({'heavy_core': 'metal', 'petrified_oak_slab': 'wood', 'sponge': 'cloth'})
# Slabs, stairs and walls are their base block.
for b in list(mat):
    for suf in ('_slab', '_stairs', '_wall'):
        if not b.endswith(suf) or b == 'petrified_oak_slab': continue
        stem = b[:-len(suf)]
        for base in (stem, stem + 's', stem + '_block', stem + '_planks'):
            if base in mat and base not in ('stone_slab',):
                mat[b] = mat[base]; break

# Gaps: share of sound through the openings where the block collides, per band (bass, mid, treble).
D_BAND = (0.75, 0.35, 0.10)  # how much of the blocked part bass/mid/treble still gets round: holes are small next to bass
def gaps(sigma, layers=1.0):
    return [round((sigma + (1 - sigma) * d) ** layers, 2) for d in D_BAND]
def rep(bid, need=()):
    rs = [r for r in states[bid] if r['hasModel'] and not r['colEmpty'] and all(w in r['props'].split(',') for w in need)]
    return max(rs, key=lambda r: r.get('open', 0)) if rs else None

table = {}
leaves = tag('leaves')
for bid in sorted(mat):
    table[bid] = mat[bid]
for bid in sorted(leaves):
    table[bid] = {'material': mat.get(bid, 'foliage'), 'gaps': 'foliage'}
hollow = ['copper_grate', 'exposed_copper_grate', 'weathered_copper_grate', 'oxidized_copper_grate', 'waxed_copper_grate',
          'waxed_exposed_copper_grate', 'waxed_weathered_copper_grate', 'waxed_oxidized_copper_grate', 'spawner', 'trial_spawner',
          'vault', 'mangrove_roots']
for bid in hollow:
    r = rep(bid); s, t = r['openSide'], r['open']
    layers = 1.0 if t >= s - 1e-6 else min(2.0, max(1.0, math.log(t) / math.log(s)))
    table[bid] = {'material': mat[bid], 'gaps': gaps(s, layers)}
thin = [b for b in B if B[b]['cls'] in ('IronBarsBlock', 'WeatheringCopperBarsBlock') and mat.get(b) != 'glass'] + ['ladder']
for bid in thin:
    r = rep(bid, ('east=true', 'west=true') if 'bars' in bid else ())
    if r and r['open'] >= 0.05: table[bid] = {'material': mat[bid], 'gaps': gaps(r['open'])}
for bid in [b for b in B if B[b]['cls'] in ('TrapDoorBlock', 'WeatheringCopperTrapDoorBlock')]:
    r = rep(bid, ('open=false', 'half=bottom'))
    if r and r['open'] >= 0.05: table[bid] = {'material': mat[bid], 'gaps': gaps(r['open'])}
for bid in [b for b in B if B[b]['cls'] in ('DoorBlock', 'WeatheringCopperDoorBlock')]:
    by = {}
    for half in ('upper', 'lower'):
        r = rep(bid, ('half=' + half, 'open=false'))
        if r and r['open'] >= 0.05: by['half=' + half] = gaps(r['open'])
    if by: table[bid] = {'material': mat[bid], 'gaps': by}

out = {'_about': ['Acoustics of every vanilla block, built from Minecraft 1.21.11 data (see tools/block-acoustics).',
                  'material: the surface sound reflects off. gaps: share of sound that gets through the openings where the',
                  'block collides, per band [bass, mid, treble] ("foliage": leaves), from its model as drawn.'],
       'blocks': {'minecraft:' + k: v for k, v in sorted(table.items())}}
json.dump(out, open(sys.argv[1], 'w'), indent=1)
porous = {k: v['gaps'] for k, v in table.items() if isinstance(v, dict)}
print('blocks', len(table), 'with gaps', len(porous))
for k, v in porous.items(): print(f'  {k:<34} {v}')
