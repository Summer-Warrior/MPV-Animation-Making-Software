package com.example.data

import com.example.model.AnimationMotion
import com.example.model.CameraMove
import com.example.model.Model3DType
import com.example.model.ParticleFxType
import com.example.model.ShowProject
import com.example.model.ShowScene
import com.example.model.SpriteActorType
import com.example.model.TexturePreset

data class DocTemplatePreset(
    val title: String,
    val badge: String,
    val preferredConsoleId: String,
    val documentationText: String
)

object StarterShowPresets {

    val documentationTemplates: List<DocTemplatePreset> = listOf(
        DocTemplatePreset(
            title = "Handheld & Console 3D GPU History Show",
            badge = "Hardware Doc",
            preferredConsoleId = "ps1_saturn",
            documentationText = """
                # Architecture Doc: Evolution of Console & Handheld 3D Rendering
                1. 8-Bit & Handheld Foundations (Game Boy DMG / NES): Tile-based PPU rendering with 4-shade STN dot-matrix LCD sprites and elite wireframe vectors.
                2. 16-Bit Co-Processors (SNES Super FX GSU-2 & Genesis SVP): Cartridge RISC chips rasterizing flat-shaded Starfighter polygons over Mode 7 affine ground planes.
                3. 32-Bit & 64-Bit Revolution (PS1 GTE & N64 RCP): PlayStation integer vertex snapping and affine UV textures vs Nintendo 64 Z-buffered Gouraud meshes and atmospheric fog.
                4. Portable 3D & Modern HD-2D (GBA, NDS, PSP, Switch/Steam Deck): Combining normal-mapped 2D pixel sprites with volumetric 3D lighting for online broadcast shows.
            """.trimIndent()
        ),
        DocTemplatePreset(
            title = "Zero-Downtime Microservices Raid Boss Doc",
            badge = "System Spec",
            preferredConsoleId = "switch_deck_hd2d",
            documentationText = """
                # RFC-409: Distributed Edge Gateway & Auto-Scaling Mesh
                - Ingress Load Balancer acts as a Vanguard RX-9 Mecha deflecting 100k RPS traffic spikes with an energy shield.
                - Stateful Database Shards are guarded by a Crystal Wyrm Dragon replicating transaction logs across 3 availability zones.
                - Worker Pod Sprites (Cyber Knight & Arcane Weaver) execute async queue jobs while Docu-Bot streams live telemetry metrics.
                - Circuit Breaker triggers Overdrive Pulse mode when p99 latency exceeds 45ms, rerouting packets through warp tunnels.
            """.trimIndent()
        ),
        DocTemplatePreset(
            title = "Retro RPG Patch v4.8 Broadcast Show Notes",
            badge = "Game DevLog",
            preferredConsoleId = "gba_advance",
            documentationText = """
                # ChronoQuest Online Show — Patch v4.8 Release Documentation
                - New Playable Handheld Companion: Volt-Draco Pocket Monster joins 32-bit GBA & Nintendo DS party formations with lightning breath frames.
                - Boss Encounter Overhaul: Bahamut Crystal Wyrm now transitions from 16-bit SNES Mode 7 sky phase into 128-bit Cel-Shaded overdrive.
                - Star-Viper Interceptor mini-game added to Arcade Lobby with barrel-roll evasion and synthwave neon UV grid shaders.
            """.trimIndent()
        )
    )

    val defaultShowProjects: List<ShowProject> = listOf(
        ShowProject(
            id = 1L,
            title = "ChronoStage: Console & Handheld GPU Odyssey",
            subtitle = "4-Scene Online Tech Show • 8-Bit DMG to HD-2D Hybrid",
            sourceDocumentation = documentationTemplates[0].documentationText,
            scenes = listOf(
                ShowScene(
                    id = "s1",
                    index = 0,
                    title = "Pocket Dot-Matrix Genesis",
                    documentationExcerpt = "Game Boy DMG-01 160x144 4-shade olive LCD + OAM sprite tiles",
                    narratorScript = "Our journey begins on the 1989 Game Boy DMG-01—where 4 olive LCD shades and 40 hardware sprites brought portable worlds to life!",
                    consoleId = "gameboy_dmg",
                    model3D = Model3DType.CONSOLE_TOTEM,
                    modelMotion = AnimationMotion.ORBIT_SHOWCASE,
                    texturePreset = TexturePreset.PIXEL_BRICK,
                    primarySprite = SpriteActorType.POCKET_MONSTER,
                    secondarySprite = SpriteActorType.DOCU_BOT,
                    cameraMove = CameraMove.ORBIT_360,
                    particleFx = ParticleFxType.CODE_MATRIX_RAIN,
                    durationSeconds = 7,
                    modelScale = 1.05f,
                    lightAzimuth = 35f
                ),
                ShowScene(
                    id = "s2",
                    index = 1,
                    title = "Super FX Cartridge Co-Processor",
                    documentationExcerpt = "SNES GSU-2 RISC chip flat polygon rasterizer + Mode 7 plane",
                    narratorScript = "By embedding the Super FX RISC chip right inside the cartridge, 16-bit consoles launched delta-wing 3D starfighters across Mode 7 horizons!",
                    consoleId = "snes_superfx",
                    model3D = Model3DType.STARFIGHTER_ARWING,
                    modelMotion = AnimationMotion.BARREL_ROLL_FLIGHT,
                    texturePreset = TexturePreset.NEON_GRID,
                    primarySprite = SpriteActorType.BOSS_UFO,
                    secondarySprite = SpriteActorType.ARCADE_RACER,
                    cameraMove = CameraMove.HANDHELD_ACTION_CAM,
                    particleFx = ParticleFxType.STARFIELD_WARP,
                    durationSeconds = 7,
                    modelScale = 1.1f,
                    lightAzimuth = 60f
                ),
                ShowScene(
                    id = "s3",
                    index = 2,
                    title = "32-Bit Affine Wobble & Mecha Combat",
                    documentationExcerpt = "PS1 Geometry Transformation Engine (GTE) integer vertex snap",
                    narratorScript = "Enter the 32-bit era: PlayStation's GTE crunches 180,000 textured polygons per second with iconic integer vertex snapping and 2D sprite billboards!",
                    consoleId = "ps1_saturn",
                    model3D = Model3DType.MECHA_VANGUARD,
                    modelMotion = AnimationMotion.HERO_ACTION_SLASH,
                    texturePreset = TexturePreset.BRUSHED_TITANIUM,
                    primarySprite = SpriteActorType.CYBER_KNIGHT,
                    secondarySprite = SpriteActorType.DOCU_BOT,
                    cameraMove = CameraMove.DRAMATIC_PUSH_IN,
                    particleFx = ParticleFxType.PIXEL_SPARK_BURST,
                    durationSeconds = 8,
                    modelScale = 1.05f,
                    lightAzimuth = 45f
                ),
                ShowScene(
                    id = "s4",
                    index = 3,
                    title = "Modern HD-2D Hybrid Broadcast Stage",
                    documentationExcerpt = "Switch / Steam Deck HD-2D volumetric lighting + pixel sprites",
                    narratorScript = "Today, hybrid handhelds fuse dynamic PBR 3D dragon meshes with crisp 16-bit sprites and volumetric bloom for online broadcast shows!",
                    consoleId = "switch_deck_hd2d",
                    model3D = Model3DType.CRYSTAL_DRAGON,
                    modelMotion = AnimationMotion.OVERDRIVE_PULSE,
                    texturePreset = TexturePreset.CYBER_CIRCUIT,
                    primarySprite = SpriteActorType.PLASMA_MAGE,
                    secondarySprite = SpriteActorType.CYBER_KNIGHT,
                    cameraMove = CameraMove.WIDE_CRANE_SWEEP,
                    particleFx = ParticleFxType.HOLO_DATA_NODES,
                    durationSeconds = 8,
                    modelScale = 1.1f,
                    lightAzimuth = 75f
                )
            )
        ),
        ShowProject(
            id = 2L,
            title = "Cloud Architecture Explained as RPG Raid",
            subtitle = "3-Scene Technical Documentary • N64, NDS & HD-2D",
            sourceDocumentation = documentationTemplates[1].documentationText,
            scenes = listOf(
                ShowScene(
                    id = "c1",
                    index = 0,
                    title = "Ingress Gateway Mecha Shield",
                    documentationExcerpt = "Edge Load Balancer absorbs 100k RPS spike with zero packet loss",
                    narratorScript = "Incoming traffic spike detected! The Ingress Vanguard Mecha raises its anodized shield while Cyber Knight worker sprites route requests!",
                    consoleId = "nintendo_64",
                    model3D = Model3DType.MECHA_VANGUARD,
                    modelMotion = AnimationMotion.HERO_ACTION_SLASH,
                    texturePreset = TexturePreset.CYBER_CIRCUIT,
                    primarySprite = SpriteActorType.CYBER_KNIGHT,
                    secondarySprite = SpriteActorType.DOCU_BOT,
                    cameraMove = CameraMove.ISOMETRIC_TACTICAL,
                    particleFx = ParticleFxType.RETRO_LASER_GRID,
                    durationSeconds = 7
                ),
                ShowScene(
                    id = "c2",
                    index = 1,
                    title = "Quantum Consensus Core Sync",
                    documentationExcerpt = "Multi-region Raft consensus rings replicate state in <12ms",
                    narratorScript = "On the portable Nintendo DS 3D hardware pipeline, counter-rotating consensus rings lock distributed database shards into sync!",
                    consoleId = "nintendo_ds",
                    model3D = Model3DType.HOLO_CORE,
                    modelMotion = AnimationMotion.OVERDRIVE_PULSE,
                    texturePreset = TexturePreset.HOLO_SCANLINE,
                    primarySprite = SpriteActorType.DOCU_BOT,
                    secondarySprite = SpriteActorType.PLASMA_MAGE,
                    cameraMove = CameraMove.ORBIT_360,
                    particleFx = ParticleFxType.HOLO_DATA_NODES,
                    durationSeconds = 7
                ),
                ShowScene(
                    id = "c3",
                    index = 2,
                    title = "Auto-Scaler Dragon Overdrive",
                    documentationExcerpt = "Horizontal pod autoscaler spawns 64 compute nodes under load",
                    narratorScript = "Latency stays flat at 14ms as the 128-bit Cel-Shaded Crystal Wyrm unleashes full cluster auto-scaling across every edge zone!",
                    consoleId = "dreamcast_ps2_gc",
                    model3D = Model3DType.CRYSTAL_DRAGON,
                    modelMotion = AnimationMotion.ZERO_G_FLOAT,
                    texturePreset = TexturePreset.LAVA_MAGMA,
                    primarySprite = SpriteActorType.PLASMA_MAGE,
                    secondarySprite = SpriteActorType.POCKET_MONSTER,
                    cameraMove = CameraMove.DRAMATIC_PUSH_IN,
                    particleFx = ParticleFxType.PIXEL_SPARK_BURST,
                    durationSeconds = 8
                )
            )
        )
    )
}
