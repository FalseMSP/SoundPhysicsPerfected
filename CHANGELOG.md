## Versions

- **Minecraft 1.21.11 (NeoForge).** Needs NeoForge 21.11.45 or newer and YACL 3.8.1.

## Sound quality

- **Energy loudness** (new default Attenuation Mode, after Vercidium's raytraced audio). How loud a hidden sound is now follows how much of its energy actually reaches you, per frequency band: around corners, through doorways, off walls, or through them. A sound is at full volume once a tenth of your rays' energy reaches it, and soft surfaces take the treble first. Loudness is applied as a filter, so the sound keeps its vanilla volume, range and distance falloff: a sound in plain view is exactly as loud as without the mod (measured in game: within 0.3 dB at 4, 8 and 14 m).
- **Muffling you can hear.** Filters used to be attached on the next sound tick, so short sounds (footsteps, block breaks, hits) played their attack unmuffled and only their tail was filtered. They are now muffled from the first sample. Walls also stop much more treble than bass now, like real walls. Measured in game:

  | What's in the way | Bass | Treble |
  |---|---|---|
  | A freestanding stone wall (sound gets round and over it) | −5 dB | −15 dB |
  | Glass | −2 dB | −5 dB |
  | The water surface | −22 dB | −51 dB |

  A sealed stone wall takes about 20 dB more treble than bass. New **Muffling** option (0 to 300%).
- **Water.** Water surfaces reflect sound, so lakes and flooded caves ring. Sound barely crosses between air and water. With your head under water everything is muffled and sounds like being under water (new **Water Acoustics** option). Splashes, swimming and boat paddling happen at the surface and are heard from both sides of it; only sounds deeper down are under water.
- **Your own sounds** (footsteps, splashes, swimming, eating) are no longer traced: they come from your own body, so nothing is in the way. They still get the room's reverb.
- **Doppler effect.** Moving sounds rise in pitch as they come towards you and drop as they pass, and so do sounds you fly or ride past. The pitch follows the real source's movement, not the traced path, so a changing path doesn't warble. Music, UI and other head-relative sounds are not shifted. By default it is a gentle fifth of the physical shift, since the full effect sounded too strong in game. The new **Doppler Effect** option (0 to 500%, also on the Music & Sounds screen) goes up to fully physical at 500%.
- **Echogram decay.** Reverb decay now comes from how the rays' energy actually dies away, not only from average absorption. Stone walls over a carpeted floor keep ringing (1.1 s instead of 0.9 s), and rays aimed at an opening leave early. Uniform rooms sound as before.
- **Reverb now actually follows your surroundings.** OpenAL copies a reverb's settings into its effect slot only when the effect is attached, and it was never re-attached after its settings were updated, so every place in the game was heard with the start-up reverb (a fixed 15 second tail). Rooms, caves, corridors and open fields now each sound like themselves.
- **New physically based reverb model**, driven by the ray tracing:
  - room size from the distance between reflections, and openness from rays escaping to the sky
  - decay time per frequency band (Eyring RT60 with air absorption)
  - reverb level from the room's absorption, early reflections from the nearest walls
  - distinct echoes off far cliffs and canyon walls when you are outside
- **Real block materials.** Stone, deepslate, metal, glass, wood, wool, carpet, snow, dirt, sand, gravel, leaves and more each absorb sound differently, per frequency: wool rooms are dead and dull, stone rooms ring. (Previously stone was treated as the most absorbent surface in the game.)
- **Directional reverb.** Echoes come from where they really bounce: reflections lean towards a nearby wall, and standing at a cave mouth the cave's reverb comes from inside the cave. It follows the camera as you turn.
- **Reverb zones.** Sounds in a different space from you ring like that space: a zombie deep in a cave echoes like the cave while you stand outside, heard from the cave's direction and only as loud as that echo reaches you.
- **Walls filter by material.** Walls stop treble far more than bass, differently per material: through stone you hear a dull thump, through glass nearly everything, through wool almost nothing.
- **Rough surfaces scatter reflections** (cobblestone, leaves, wool), while glass and polished stone reflect like mirrors, for more natural reverb in irregular caves and buildings.
- **Smooth transitions** when walking between spaces, and muffled sounds now produce muffled reverb.
- **Better localisation.** Sounds heard through walls outdoors no longer come from a random direction, reflections lose energy according to the surface they bounce off, and blocks muffle correctly from every direction (rays travelling in negative directions used to read the wrong block).
- Dense blocks that explode easily (dirt, sand, gravel, glass, snow) now muffle sound properly with Material Based Absorption.
- Music is no longer given environmental reverb; distant sounds lose some high frequencies to the air (new **Air Absorption** option).

- **Blocks sound like they look.** Each block's collision shape was compared with its model as drawn, for every
  block state in Minecraft 1.21.11. A prebuilt table now gives every vanilla block what sound meets
  (`tools/block-acoustics/audit.md` lists them all):
  - **Gaps let sound through.** Leaves, copper grates, iron and copper bars, ladders, spawners, trial spawners, vaults,
    mangrove roots, trapdoors and door windows used to be solid walls. Sound now goes through their openings as often as
    you can see through them, bass more easily than treble (measured in game through a 4-high wall: stone takes 7 dB of
    bass and 20 dB of treble, copper grates none of the bass and 14 dB of treble, leaves nothing). Leaves follow real
    foliage: a tree canopy no longer sounds like a roof (reverb decay under a 2-thick canopy 0.36 s, open field 0.23 s,
    stone roof 0.62 s).
  - **Shapes as drawn.** Fences and fence gates are their post and rails, not the 1.5-high wall they collide as; walls
    no longer reach above their block.
  - **Drawn but not colliding.** Powder snow is a block of snow; snow layers are as high as drawn, so even one layer makes
    the ground snow; lava reflects like a liquid surface instead of letting sound straight through.
  - **Materials per block.** Beds are cloth, not wood; dirt, coarse dirt, podzol, farmland and clay are soil, not
    gravel; cactus and cake are not cloth; concrete, terracotta, bricks and polished stone are smooth; pumpkins, melons,
    hay and mushrooms are organic; slabs, stairs and walls match their block.
  - Modded blocks fall back to the rules: leaves, fences, gates and bars by family, the rest by sound type.

## Performance and latency

- New **Fast Raytracing** (on by default): rays walk the block grid directly instead of using Minecraft's generic ray casting. It switches itself off near Sable sub-levels.
- **Trail caching** (after Vercidium). While you stand still and no block that matters to sound changes, the rays' paths are reused. New sounds are only connected to them, and a sound at a spot already traced needs no tracing at all. The room is not re-measured either. Block changes and chunk loads near you are seen directly, so paths are kept for up to 10 seconds.
- **The tracer uses the CPU as needed.** It runs as many threads as the machine can spare (all cores but two, up to 16). Threads start when there is work and stop when idle, and a trace is split by its size, so a big trace spreads over every free core. On Ultra while walking past buzzing bees, traces take 3 ms instead of 5 ms (worst 5 ms instead of 9 ms), so new sounds still start at once.
- The bounces that only measure the room check whether you can see them just to steer the reverb's direction. They now skip that check when the ray has almost no energy left, or when Directional Reverb is off. Within one trace they share the answer per block face. That's 20–25% less CPU on Ultra while walking, and the direction stays within 0.03 of the exact result.
- While walking, the room is re-measured about 4 times a second instead of every half block (it is smoothed over 0.25 s anyway), and the in-between traces skip the echogram. That's about 17% less CPU on Ultra.
- Each tracer thread remembers the block states it has already classified. Looking a block's shape up used to be the single biggest cost of tracing.
- **A shared empty-cell memo.** Rays cross the same cells again and again: a cave trace on Ultra does about 4 million lookups in about 50 chunk sections. One bit per cell remembers "known empty", shared by every tracer thread and kept from trace to trace. Blocks that change and chunks that (re)load drop their part of it, and it resets itself every 20 seconds. Results are identical.
- **Batched re-traces.** Playing sounds (bees, minecarts, machines) are re-traced together in one trace, where each used to cost a trace of all rays. Traces with nothing to place and no room to measure are skipped.
- **Cheaper steps.**
  - A ray walking through air no longer re-checks its whole chunk section at every cell, nor does the bookkeeping that only water and blocks need.
  - The shared visibility answers sit in a lock-free table.
  - A walk from a bounce to a sound that is fully blocked (three blocks of wall or more, 75% of them) is shared with the other bounces on the same block face.
  - Results are the same (every difference is smaller than moving 5 cm), and clear and doorway paths are always walked exactly.
  - Measured on real chunk data: 21–26% less CPU per trace in caves on Ultra, 7% on Medium, 5–6% on plains.
- **Leaner echogram.** Its cost now grows with the number of bounces, not with 256 distance bins, which is 7x faster per ray outdoors. The voxel walk also works out a cell's exit distance only when it needs it.
- Measured in game with every feature on:

  | Test (Medium preset) | 1.17.5 | Now |
  |---|---|---|
  | 400 sounds in 23 s in a cave | 2220 ms of ray tracing CPU | about 170 ms (13x less) |
  | Walking past four bees with a new sound every 250 ms | | about 250 ms per 12 s |

  On Ultra, the walking test takes about 3.1 s. Standing still costs almost nothing, and traced sounds still start with no measurable delay on every preset.
- **Sounds start sooner.** Traced sounds used to start 60 to 140 ms after they were triggered; they now start with no measurable delay (new **Low Latency Budget** option, default 3 ms).
- No more blocking of the game thread while sounds are placed, and no more per-sound reverb recomputation every tick.
- Fixed a memory leak where every sound ever played stayed referenced for the whole session.
- Leaving a world no longer keeps it in memory while in the menus.
- Stopping a sound that is still being traced now works, and a failed trace or placement plays its sounds unprocessed instead of losing them. An error in the mod's per-tick code is logged instead of breaking the game's sound tick.
- On devices without EFX, the mod no longer retries (and logs) setting up effects on every sound.

## Config

- **Sound Physics section in Music & Sounds.** A Ray Tracing Quality preset and sliders for Rays, Bounces, Reverb Bounces, Ray Length and Muffling, so you can trade CPU for accuracy without leaving the game. Changes are heard straight away; More Settings... opens the full config.

  | Preset | Rays | Bounces | Reverb bounces | Ray length | Stress-test CPU |
  |---|---|---|---|---|---|
  | Low | 128 | 2 | 4 | 4 chunks | |
  | Medium (default) | 256 | 3 | 8 | 4 chunks | 0.3 s |
  | High | 512 | 4 | 12 | 6 chunks | 0.7 s |
  | Ultra | 2048 | 6 | 24 | 8 chunks | 2.8 s |

  Stress-test CPU is ray tracing time for 400 sounds in 23 seconds in a cave. Traced sounds start with no measurable delay on every preset.
- New configs default to the Medium preset: 256 rays, up from 200. The ray limit is raised to 4096.
- New: Fast Raytracing, Low Latency Budget, Air Absorption, Directional Reverb, Reverb Zones, Reverb Ray Bounces, Reverb Smoothing, Decay Time Multiplier, Max Decay Time, Early Reflections, Late Reverb, Distant Echoes, Muffling, Water Acoustics, Energy: Full Volume Share.
- The Attenuation Mode now defaults to Energy for new configs. Existing configs keep their mode; switch to Energy to try it.
- Removed the reverb tuning options of the old model (indoor/outdoor bias, room emphasis, density/diffusion/echo/modulation factors and so on). They only affected settings that were never applied. Global Reverb Intensity, Max Overall Gain, Sound Speed and the HF/LF references still work.
- The 1.0.0 and 1.4.0 legacy reverb modes sound as they always did.
- The Reverb Debug readout now also shows the trace time and ray tracer in use, and the Debug option shows each placed sound's bass and treble gain.

## Fixes

From a multi-agent code review, each finding checked before fixing:

- **The tracer could hang forever.** A ray running exactly along the edge where empty 16x16x16 sections meet, going in negative x and z, could be handed back and forth between them. That thread then spun at 100% and no sound was traced again until the next world change. Fixed, with a regression test, and checked against a brute-force reference on 80,000 random rays.
- **Moving sounds** (minecarts, bees, entity-bound sounds) kept their first traced position forever: every tick reset it. They now follow both their source and new traces.
- **Pitch no longer changes a tick after a sound starts.** Sounds with pitch variants picked a new random variant when replayed.
- **Reverb no longer restarts while you walk.** Tiny drifts in decay and density were sent every tick, and OpenAL Soft rebuilds its reverb for any change to those. They now change at most every 250 ms.
- **Switching effects off works on sounds already playing.** Turning reverb, air absorption or zones off now takes them off. Unchanged per-sound settings are also no longer re-sent every tick.
- **Water:**
  - Waterlogged blocks (slabs, stairs, fences) count as water.
  - Sounds from inside their own block (note blocks, jukeboxes) get the reverb of the room around them.
  - Head-relative ambience is not muffled again under water.
- **Sable sub-levels:**
  - Ships now block and reflect sound where they actually are, not in their plot 20 million blocks away.
  - Sounds on ships are range-checked and re-traced where they are.
- **Leaving a world, stopping all sounds, or the sound engine shutting down:**
  - Nothing of the old world is kept in memory.
  - Menus get no in-world effects.
  - Shutdown no longer deletes objects that are still in use.
  - A failure while starting a trace plays its sounds unprocessed instead of losing them.
- **Voice chat:**
  - The muffled voice volume now actually applies. Voice chat set its own gain afterwards, so it did nothing.
  - The reverb of a muffled voice is muffled too.
  - Several speakers at once no longer mix up each other's muffling.
  - The own-voice category shows its description.
- **Loaders and versions:**
  - The Fabric 1.21.9 build no longer claims 1.21.11, where it would crash.
  - The sound engine hook no longer depends on one constructor signature.
  - On 1.21.6+, traced sounds report "started" to other mods instead of null.
  - A Forge 1.20.1 dedicated server without YetAnotherConfigLib starts.
  - YetAnotherConfigLib loads before this mod.
  - Turkish translations show proper line breaks and the mod description.
- **Config:**
  - Sliders can reach their defaults again (Process Range, Permeation Absorption, Bounce Absorption, Permeation Step).
  - The HF/LF reference fields match what EAX accepts.
  - Bounce counts are labelled "bounces", not "rays".
- **Smaller fixes:**
  - The tick blacklist matches sound event ids, as the main blacklist does.
  - A ray that runs out of distance no longer counts as escaping to open air.
  - Block changes during the first trace are noticed.
  - Toggling Water Acoustics or other tracing options re-traces at once.

- NeoForge and Forge now see the mod's dependencies. The dependency tables were written as one quoted key, so a missing YetAnotherConfigLib crashed the game at the title screen instead of showing the loader's "missing dependency" screen. The Minecraft version range is now in the loaders' own format.

## Development

- The build no longer needs the fletching-table plugin; translations are plain flat JSON.
- `./gradlew test` runs offline tests of the ray tracer, reverb model and reverb zones.
- Architectury Loom 1.17 (1.21.11 needs it; every other version still builds and passes).
