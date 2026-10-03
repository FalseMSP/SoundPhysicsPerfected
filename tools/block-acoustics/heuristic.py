"""The mod's current material rule (AcousticMaterials.classifyMaterial), run over every 1.21.11 block."""
import os
import json, zipfile
Z = zipfile.ZipFile(os.environ['MC_MERGED_JAR'])
_tags = {}
def tag(name):
    if name in _tags: return _tags[name]
    _tags[name] = set()
    try: d = json.loads(Z.read(f'data/minecraft/tags/block/{name}.json'))
    except KeyError: return _tags[name]
    out = set()
    for v in d['values']:
        v = v if isinstance(v, str) else v['id']
        if v.startswith('#'): out |= tag(v[1:].replace('minecraft:', ''))
        else: out.add(v if ':' in v else 'minecraft:' + v)
    _tags[name] = out
    return out

def has(s, *n): return any(x in s for x in n)
def classify(bid, snd):
    if bid in tag('wool') or bid in tag('wool_carpets') or bid in tag('dampens_vibrations'): return 'cloth'
    if bid in tag('leaves'): return 'foliage'
    if bid in tag('ice'): return 'smooth_stone'
    if bid in tag('impermeable'): return 'glass'
    if bid == 'minecraft:bookshelf': return 'books'
    s = snd
    if has(s, 'snow'): return 'snow'
    if has(s, 'wool', 'sponge', 'cobweb'): return 'cloth'
    if has(s, 'moss', 'sculk', 'wart_block', 'shroomlight', 'fungus', 'froglight', 'azalea', 'vine', 'crop', 'dripleaf', 'spore', 'lichen', 'petals', 'roots', 'stem'):
        return 'wood' if has(s, 'stem') else 'organic'
    if has(s, 'slime', 'honey', 'frogspawn'): return 'goo'
    if has(s, 'glass'): return 'glass'
    if has(s, 'chiseled_bookshelf'): return 'books'
    if has(s, 'metal', 'anvil', 'chain', 'lantern', 'netherite', 'copper', 'iron', 'heavy_core', 'vault', 'spawner', 'bell'): return 'metal'
    if has(s, 'wood', 'bamboo', 'ladder', 'scaffolding', 'hanging_sign', 'shelf'): return 'wood'
    if has(s, 'gravel'): return 'gravel'
    if has(s, 'soul_soil'): return 'soil'
    if has(s, 'sand'): return 'sand'
    if has(s, 'bricks', 'tiles', 'polished', 'calcite', 'decorated_pot', 'amethyst', 'packed_mud'): return 'smooth_stone'
    if has(s, 'grass', 'mud', 'dirt', 'hay', 'nylium'): return 'soil'
    if has(s, 'netherrack', 'nether_ore', 'nether_gold_ore', 'coral'): return 'porous_stone'
    if has(s, 'stone', 'deepslate', 'tuff', 'dripstone', 'basalt', 'bone', 'debris', 'ore', 'resin', 'trial'): return 'stone'
    return 'default'

D = json.load(open('families.json'))
out = []
for b in D['blocks']:
    b['heuristic'] = classify(b['id'], b['snd'])
    b['dampens'] = b['id'] in tag('dampens_vibrations') or b['id'] in tag('occludes_vibration_signals')
json.dump(D, open('families.json', 'w'), indent=0)
import collections
print(collections.Counter(b['heuristic'] for b in D['blocks'] if not b['colEmpty']))
