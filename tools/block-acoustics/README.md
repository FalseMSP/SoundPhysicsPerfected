# Block acoustics table

Builds `src/main/resources/assets/sound_physics_perfected/block_acoustics.json`: every vanilla block's surface material,
and the gaps sound gets through for blocks that are drawn more open than they collide (leaves, grates, bars, ladders,
door windows...). `audit.md` lists every block whose collision differs from how it is drawn.

How it is made:

1. `Dump.java` boots Minecraft and writes every block state's collision and outline boxes, render layer, sound and
   blast resistance (`states.jsonl`).
2. `analyze.py` draws every state's model (elements and texture transparency, rotated like the game does) onto a grid
   along each axis and compares it with the collision shape (`analysis.json`).
3. `families.py` groups blocks by class and model template and flags mismatches; `heuristic.py` adds the mod's
   sound-based material guess (`families.json`).
4. `materials-reviewed.json` holds each block's material: assigned per block by light agents in family batches, from
   the block's name, class and sound. `build_table.py` applies the reviewed corrections (slabs, stairs and walls take
   their base block's material, and a few fixes), computes the gaps from the measurements, and writes the table.
5. `audit.py` writes `audit.md`.

Gaps per band come from the measured open share of a face, `s`, and the number of faces a ray crosses, `n`:
`(s + (1 - s) * d)^n` with `d` = 0.75 (bass), 0.35 (mid), 0.10 (treble). Openings of a few pixels are small next to
bass wavelengths, so bass gets round the solid parts too; treble only gets through the openings. Leaves use foliage
values instead (ISO 9613-2, see `BlockAcoustics.FOLIAGE`).

To regenerate for a new Minecraft version (needs Java 21, Python 3 with numpy and pillow):

```sh
# A Mojang-mapped vanilla jar and its libraries, e.g. remapped with tiny-remapper from Loom's cache.
javac -cp "$MC_JAR:$LIBS" -d out Dump.java
java -cp "out:$MC_JAR:$LIBS" Dump states.jsonl
export MC_CLIENT_JAR=.../minecraft-client.jar MC_MERGED_JAR=.../minecraft-merged.jar
python analyze.py && python families.py && python heuristic.py
python build_table.py ../../src/main/resources/assets/sound_physics_perfected/block_acoustics.json
TABLE=../../src/main/resources/assets/sound_physics_perfected/block_acoustics.json python audit.py
```

New blocks need a line in `materials-reviewed.json`; blocks missing from the table fall back to the mod's rules at
runtime (tags, block families, sound type).
