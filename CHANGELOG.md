## Sound quality

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

- New **Fast Raytracing** (on by default): rays walk the block grid directly instead of using Minecraft's generic ray casting. Measured in game with every new feature on, the ray tracing threads use about 6x less CPU when idle and about 3x less under heavy sound load. It switches itself off near Sable sub-levels.
- **Sounds start sooner.** Traced sounds used to start 60 to 140 ms after they were triggered; they now start with no measurable delay (new **Low Latency Budget** option, default 3 ms).
- No more blocking of the game thread while sounds are placed, and no more per-sound reverb recomputation every tick.
- Fixed a memory leak where every sound ever played stayed referenced for the whole session.
- Stopping a sound that is still being traced now works, and a failed trace plays its sounds unprocessed instead of losing them.

## Config

- **Sound Physics section in Music & Sounds.** A Ray Tracing Quality preset (Low, Medium, High, Ultra) and sliders for Rays, Bounces, Reverb Bounces, Ray Length and Muffling, so you can trade CPU for accuracy without leaving the game. Changes are heard straight away; More Settings... opens the full config.
- New: Fast Raytracing, Low Latency Budget, Air Absorption, Directional Reverb, Reverb Zones, Reverb Ray Bounces, Reverb Smoothing, Decay Time Multiplier, Max Decay Time, Early Reflections, Late Reverb, Distant Echoes.
- Removed the reverb tuning options of the old model (indoor/outdoor bias, room emphasis, density/diffusion/echo/modulation factors and so on). They only affected settings that were never applied. Global Reverb Intensity, Max Overall Gain, Sound Speed and the HF/LF references still work.
- The 1.0.0 and 1.4.0 legacy reverb modes sound as they always did.
- The Reverb Debug readout now also shows the trace time and ray tracer in use.

## Development

- The build no longer needs the fletching-table plugin; translations are plain flat JSON.
- `./gradlew test` runs offline tests of the ray tracer, reverb model and reverb zones.
