"""Writes audit.md: every block whose collision differs from how it is drawn, and what the mod does about it."""
import os
import json, collections
D = json.load(open('families.json')); B = {b['id']: b for b in D['blocks']}
F = D['families']
table = json.load(open(os.environ['TABLE']))['blocks']
def names(bs, n=8):
    s = ', '.join(b['id'].split(':')[1] for b in bs[:n])
    return s + (f' (+{len(bs) - n} more)' if len(bs) > n else '')
def section(title, note, pred, action):
    rows = []
    for k, ids in F.items():
        hit = [B[i] for i in ids if pred(B[i])]
        if hit: rows.append((k, hit))
    if not rows: return ''
    out = [f'## {title}\n', note + '\n', '| Family | Blocks | See-through where it collides | Now |', '|---|---|---|---|']
    for k, hit in rows:
        o = max(b['open'] for b in hit)
        out.append(f"| {k.split(' | ')[0]} | {names(hit)} | {o:.0%} | {action(hit)} |")
    return '\n'.join(out) + '\n\n'
def gaps_of(hit):
    g = table.get(hit[0]['id'])
    if isinstance(g, dict) and 'gaps' in g:
        v = g['gaps']
        if v == 'foliage': return 'gaps: foliage (bass 99%, mid 97%, treble 94% per block)'
        if isinstance(v, list): return f'gaps: bass {v[0]:.0%}, mid {v[1]:.0%}, treble {v[2]:.0%}'
        return 'gaps per half: ' + '; '.join(f"{k}: {x[1]:.0%} mid" for k, x in v.items())
    if 'Fence' in hit[0]['cls']: return 'drawn shape (post and rails)'
    return 'solid (as drawn)'
SOLID_CLEAR = {'GLASS', 'glass_pane', 'beacon'}
SMALL = ('Candle', 'Turtle', 'SeaPickle', 'Amethyst', 'EndRod', 'LightningRod', 'PointedDripstone', 'Chorus', 'Brewing',
         'DragonEgg', 'Bamboo', 'Bell', 'HeavyCore', 'Campfire', 'Waterlily', 'Chain', 'Scaffolding', 'BigDripleaf')
md = ['# Blocks that are drawn differently from how they collide\n',
      'Measured on Minecraft 1.21.11: every block state\'s collision shape against its model as drawn (elements and '
      'texture transparency), along all three axes. "See-through" is the share of the collision shape you can see '
      'through. Regenerate with the steps in README.md.\n\n']
md.append(section('Gaps: collides where you can see through',
                  'Sound now goes through these as often as the openings say, bass more easily than treble.',
                  lambda b: 'GAPS' in b['flags'] and not any(s in b['cls'] for s in SMALL) and b['id'] not in
                  ('minecraft:glass', 'minecraft:glass_pane', 'minecraft:beacon') and b['cls'] != 'WallBlock', gaps_of))
md.append(section('Collides above what is drawn',
                  'Fences, gates and walls collide 1.5 blocks high so nothing jumps over them. Sound now meets only '
                  'what is drawn: walls are cut to their own block, fences and gates use their drawn shape.',
                  lambda b: 'TALL' in b['flags'], lambda h: 'drawn shape' if 'Fence' in h[0]['cls'] else 'cut to its block'))
md.append('## Drawn solid, collides with nothing\n\n| Block | Now |\n|---|---|\n'
          '| powder_snow | a full block of snow |\n| snow (layers) | as high as drawn: one layer covers the ground in snow '
          '(it collided with nothing, so floors sounded like the grass under it) |\n| lava | a hard reflecting surface '
          'at its height (sound went straight through) |\n\n')
md.append('## See-through but solid\n\nGlass, glass panes and the beacon\'s glass shell are clear, not open: they stay '
          'solid glass.\n\n')
md.append(section('Drawn by a block entity (no block model)',
                  'Their collision matches what is drawn; only their materials were reviewed (beds are cloth now).',
                  lambda b: 'NO_MODEL' in b['flags'], lambda h: 'unchanged shape'))
md.append('## Small details, left as they collide\n\nCandles, turtle eggs, sea pickles, amethyst buds, end rods, '
          'lightning rods, pointed dripstone, chorus plants, the brewing stand, the dragon egg, bamboo, chains, '
          'campfires, lily pads, scaffolding and big dripleaf differ from their collision by a few pixels: too '
          'small to hear.\n\n')
md.append('## Drawn but no collision\n\nPlants, crops, rails, fire, vines, glow lichen, sculk veins, cobwebs and portals '
          'stay air to sound, which is what they are.\n')
open('audit.md', 'w').write(''.join(md))
print('audit.md', sum(1 for _ in open('audit.md')), 'lines')
