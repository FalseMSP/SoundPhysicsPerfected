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
- **Doppler effect.** Moving sounds rise in pitch as they come towards you and drop as they pass, and so do sounds you fly or ride past. The pitch follows the real source's movement, not the traced path, so a changing path doesn't warble. Music, UI and other head-relative sounds are not shifted. Measured in game: +1.9% / −2.2% pitch walking towards / away from a note block, matching the physics for that speed. New **Doppler Effect** option (0 to 300%, also on the Music & Sounds screen).
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

## Performance and latency

- New **Fast Raytracing** (on by default): rays walk the block grid directly instead of using Minecraft's generic ray casting. It switches itself off near Sable sub-levels.
- **Trail caching** (after Vercidium). While you stand still and no block that matters to sound changes, the rays' paths are reused. New sounds are only connected to them, and a sound at a spot already traced needs no tracing at all. The room is not re-measured either. Paths are re-traced at least every 2 seconds.
- **The tracer uses the CPU as needed.** It runs as many threads as the machine can spare (all cores but two, up to 16). Threads start when there is work and stop when idle, and a trace is split by its size, so a big trace spreads over every free core. On Ultra while walking past buzzing bees, traces take 3 ms instead of 5 ms (worst 5 ms instead of 9 ms), so new sounds still start at once.
- While walking, the room is re-measured about 4 times a second instead of every half block (it is smoothed over 0.25 s anyway), and the in-between traces skip the echogram. That's about 17% less CPU on Ultra.
- Each tracer thread remembers the block states it has already classified. Looking a block's shape up used to be the single biggest cost of tracing.
- Measured in game with every feature on: 400 sounds in 23 s in a cave used 2220 ms of ray tracing CPU in 1.17.5, and now use about 200 ms (11x less). Standing still costs almost nothing.
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

- NeoForge and Forge now see the mod's dependencies. The dependency tables were written as one quoted key, so a missing YetAnotherConfigLib crashed the game at the title screen instead of showing the loader's "missing dependency" screen. The Minecraft version range is now in the loaders' own format.

## Development

- The build no longer needs the fletching-table plugin; translations are plain flat JSON.
- `./gradlew test` runs offline tests of the ray tracer, reverb model and reverb zones.
