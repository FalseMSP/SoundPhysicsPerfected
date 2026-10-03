# Blocks that are drawn differently from how they collide
Measured on Minecraft 1.21.11: every block state's collision shape against its model as drawn (elements and texture transparency), along all three axes. "See-through" is the share of the collision shape you can see through. Regenerate with the steps in README.md.

## Gaps: collides where you can see through

Sound now goes through these as often as the openings say, bass more easily than treble.

| Family | Blocks | See-through where it collides | Now |
|---|---|---|---|
| MangroveRootsBlock | mangrove_roots | 14% | gaps: bass 78%, mid 44%, treble 22% |
| TintedParticleLeavesBlock | oak_leaves, spruce_leaves, birch_leaves, jungle_leaves, acacia_leaves, dark_oak_leaves | 19% | gaps: foliage (bass 99%, mid 97%, treble 94% per block) |
| UntintedParticleLeavesBlock | azalea_leaves, flowering_azalea_leaves | 8% | gaps: foliage (bass 99%, mid 97%, treble 94% per block) |
| SpawnerBlock | spawner | 27% | gaps: bass 81%, mid 51%, treble 33% |
| DoorBlock | oak_door, iron_door, jungle_door, acacia_door, cherry_door, bamboo_door, waxed_copper_door, waxed_exposed_copper_door (+2 more) | 20% | gaps per half: half=upper: 44% mid |
| LadderBlock | ladder | 59% | gaps: bass 90%, mid 73%, treble 63% |
| FenceBlock | oak_fence, nether_brick_fence, spruce_fence, birch_fence, jungle_fence, acacia_fence, cherry_fence, dark_oak_fence (+4 more) | 46% | drawn shape (post and rails) |
| TrapDoorBlock | oak_trapdoor, iron_trapdoor, waxed_copper_trapdoor, waxed_exposed_copper_trapdoor, waxed_oxidized_copper_trapdoor, waxed_weathered_copper_trapdoor | 15% | gaps: bass 78%, mid 42%, treble 19% |
| TrapDoorBlock | jungle_trapdoor, acacia_trapdoor, cherry_trapdoor, mangrove_trapdoor, bamboo_trapdoor, crimson_trapdoor, warped_trapdoor | 17% | gaps: bass 78%, mid 43%, treble 21% |
| IronBarsBlock | iron_bars, waxed_copper_bars, waxed_exposed_copper_bars, waxed_weathered_copper_bars, waxed_oxidized_copper_bars | 49% | gaps: bass 87%, mid 67%, treble 54% |
| WeatheringCopperBarsBlock | copper_bars, exposed_copper_bars, weathered_copper_bars, oxidized_copper_bars | 49% | gaps: bass 87%, mid 67%, treble 54% |
| FenceGateBlock | oak_fence_gate, spruce_fence_gate, birch_fence_gate, jungle_fence_gate, acacia_fence_gate, cherry_fence_gate, dark_oak_fence_gate, pale_oak_fence_gate (+3 more) | 53% | drawn shape (post and rails) |
| FenceGateBlock | bamboo_fence_gate | 53% | drawn shape (post and rails) |
| FenceBlock | bamboo_fence | 46% | drawn shape (post and rails) |
| WeatheringCopperDoorBlock | copper_door, exposed_copper_door, oxidized_copper_door, weathered_copper_door | 14% | gaps per half: half=upper: 44% mid |
| WeatheringCopperTrapDoorBlock | copper_trapdoor, exposed_copper_trapdoor, oxidized_copper_trapdoor, weathered_copper_trapdoor | 15% | gaps: bass 79%, mid 45%, treble 23% |
| WeatheringCopperGrateBlock | copper_grate, exposed_copper_grate, weathered_copper_grate, oxidized_copper_grate | 11% | gaps: bass 70%, mid 33%, treble 16% |
| WaterloggedTransparentBlock | waxed_copper_grate, waxed_exposed_copper_grate, waxed_weathered_copper_grate, waxed_oxidized_copper_grate | 11% | gaps: bass 70%, mid 33%, treble 16% |
| TrialSpawnerBlock | trial_spawner | 16% | gaps: bass 78%, mid 43%, treble 24% |
| VaultBlock | vault | 9% | gaps: bass 76%, mid 39%, treble 17% |

## Collides above what is drawn

Fences, gates and walls collide 1.5 blocks high so nothing jumps over them. Sound now meets only what is drawn: walls are cut to their own block, fences and gates use their drawn shape.

| Family | Blocks | See-through where it collides | Now |
|---|---|---|---|
| PistonHeadBlock | piston_head | 0% | cut to its block |
| FenceBlock | oak_fence, nether_brick_fence, spruce_fence, birch_fence, jungle_fence, acacia_fence, cherry_fence, dark_oak_fence (+4 more) | 46% | drawn shape |
| FenceGateBlock | oak_fence_gate, spruce_fence_gate, birch_fence_gate, jungle_fence_gate, acacia_fence_gate, cherry_fence_gate, dark_oak_fence_gate, pale_oak_fence_gate (+3 more) | 53% | drawn shape |
| WallBlock | resin_brick_wall, cobblestone_wall, mossy_cobblestone_wall, brick_wall, prismarine_wall, red_sandstone_wall, mossy_stone_brick_wall, granite_wall (+18 more) | 28% | cut to its block |
| FenceGateBlock | bamboo_fence_gate | 53% | drawn shape |
| FenceBlock | bamboo_fence | 46% | drawn shape |
| PitcherCropBlock | pitcher_crop | 0% | cut to its block |

## Drawn solid, collides with nothing

| Block | Now |
|---|---|
| powder_snow | a full block of snow |
| snow (layers) | as high as drawn: one layer covers the ground in snow (it collided with nothing, so floors sounded like the grass under it) |
| lava | a hard reflecting surface at its height (sound went straight through) |

## See-through but solid

Glass, glass panes and the beacon's glass shell are clear, not open: they stay solid glass.

## Drawn by a block entity (no block model)

Their collision matches what is drawn; only their materials were reviewed (beds are cloth now).

| Family | Blocks | See-through where it collides | Now |
|---|---|---|---|
| BedBlock | white_bed, orange_bed, magenta_bed, light_blue_bed, yellow_bed, lime_bed, pink_bed, gray_bed (+8 more) | 0% | unchanged shape |
| ChestBlock | chest | 0% | unchanged shape |
| WallHangingSignBlock | oak_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | spruce_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | birch_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | acacia_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | cherry_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | jungle_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | dark_oak_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | pale_oak_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | mangrove_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | crimson_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | warped_wall_hanging_sign | 0% | unchanged shape |
| WallHangingSignBlock | bamboo_wall_hanging_sign | 0% | unchanged shape |
| EnderChestBlock | ender_chest | 0% | unchanged shape |
| SkullBlock | skeleton_skull, zombie_head, creeper_head, dragon_head, piglin_head | 0% | unchanged shape |
| WallSkullBlock | skeleton_wall_skull, zombie_wall_head, creeper_wall_head, dragon_wall_head | 0% | unchanged shape |
| WitherSkullBlock | wither_skeleton_skull | 0% | unchanged shape |
| WitherWallSkullBlock | wither_skeleton_wall_skull | 0% | unchanged shape |
| PlayerHeadBlock | player_head | 0% | unchanged shape |
| PlayerWallHeadBlock | player_wall_head | 0% | unchanged shape |
| PiglinWallSkullBlock | piglin_wall_head | 0% | unchanged shape |
| TrappedChestBlock | trapped_chest | 0% | unchanged shape |
| BarrierBlock | barrier | 0% | unchanged shape |
| ShulkerBoxBlock | shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | white_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | orange_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | magenta_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | light_blue_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | yellow_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | lime_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | pink_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | gray_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | light_gray_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | cyan_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | purple_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | blue_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | brown_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | green_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | red_shulker_box | 0% | unchanged shape |
| ShulkerBoxBlock | black_shulker_box | 0% | unchanged shape |
| ConduitBlock | conduit | 0% | unchanged shape |
| WeatheringCopperChestBlock | copper_chest | 0% | unchanged shape |
| WeatheringCopperChestBlock | exposed_copper_chest | 0% | unchanged shape |
| WeatheringCopperChestBlock | weathered_copper_chest | 0% | unchanged shape |
| WeatheringCopperChestBlock | oxidized_copper_chest | 0% | unchanged shape |
| CopperChestBlock | waxed_copper_chest | 0% | unchanged shape |
| CopperChestBlock | waxed_exposed_copper_chest | 0% | unchanged shape |
| CopperChestBlock | waxed_weathered_copper_chest | 0% | unchanged shape |
| CopperChestBlock | waxed_oxidized_copper_chest | 0% | unchanged shape |
| WeatheringCopperGolemStatueBlock | copper_golem_statue | 0% | unchanged shape |
| WeatheringCopperGolemStatueBlock | exposed_copper_golem_statue | 0% | unchanged shape |
| WeatheringCopperGolemStatueBlock | weathered_copper_golem_statue | 0% | unchanged shape |
| WeatheringCopperGolemStatueBlock | oxidized_copper_golem_statue | 0% | unchanged shape |
| CopperGolemStatueBlock | waxed_copper_golem_statue | 0% | unchanged shape |
| CopperGolemStatueBlock | waxed_exposed_copper_golem_statue | 0% | unchanged shape |
| CopperGolemStatueBlock | waxed_weathered_copper_golem_statue | 0% | unchanged shape |
| CopperGolemStatueBlock | waxed_oxidized_copper_golem_statue | 0% | unchanged shape |
| DecoratedPotBlock | decorated_pot | 0% | unchanged shape |

## Small details, left as they collide

Candles, turtle eggs, sea pickles, amethyst buds, end rods, lightning rods, pointed dripstone, chorus plants, the brewing stand, the dragon egg, bamboo, chains, campfires, lily pads, scaffolding and big dripleaf differ from their collision by a few pixels: too small to hear.

## Drawn but no collision

Plants, crops, rails, fire, vines, glow lichen, sculk veins, cobwebs and portals stay air to sound, which is what they are.
