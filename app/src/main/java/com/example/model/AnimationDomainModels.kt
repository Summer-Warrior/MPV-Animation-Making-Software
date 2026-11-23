package com.example.model

enum class Model3DType(
    val id: String,
    val displayName: String,
    val polyCount: Int,
    val description: String
) {
    MECHA_VANGUARD(
        id = "MECHA_VANGUARD",
        displayName = "Vanguard RX-9 Mecha",
        polyCount = 420,
        description = "Articulated 3D bipedal mecha with glowing visor, shoulder thrusters, and energy blade."
    ),
    CRYSTAL_DRAGON(
        id = "CRYSTAL_DRAGON",
        displayName = "Bahamut Crystal Wyrm",
        polyCount = 580,
        description = "Winged low-poly boss dragon with flapping geometric wings and crystalline spine."
    ),
    STARFIGHTER_ARWING(
        id = "STARFIGHTER_ARWING",
        displayName = "Star-Viper Interceptor",
        polyCount = 310,
        description = "Delta-wing space superiority fighter with twin G-diffuser engines and plasma cannons."
    ),
    CYBER_MASCOT(
        id = "CYBER_MASCOT",
        displayName = "ChronoBot 3D Mascot",
        polyCount = 490,
        description = "Expressive platformer hero with CRT monitor head, jetpack, and articulated limbs."
    ),
    CONSOLE_TOTEM(
        id = "CONSOLE_TOTEM",
        displayName = "Handheld & Cartridge Monument",
        polyCount = 360,
        description = "3D rotating portable handheld console & gold ROM cartridge with illuminated screen."
    ),
    HOLO_CORE(
        id = "HOLO_CORE",
        displayName = "Quantum Architecture Core",
        polyCount = 640,
        description = "Multi-ring gyroscope and octahedron crystal for visualizing system architecture docs."
    );

    companion object {
        fun fromId(id: String): Model3DType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: MECHA_VANGUARD
    }
}

enum class SpriteActorType(
    val id: String,
    val displayName: String,
    val frameCount: Int,
    val eraTag: String
) {
    CYBER_KNIGHT("CYBER_KNIGHT", "Cyber Knight (Sword Slash)", 4, "16-Bit Action"),
    PLASMA_MAGE("PLASMA_MAGE", "Arcane Weaver (Spell Cast)", 4, "SNES / GBA RPG"),
    POCKET_MONSTER("POCKET_MONSTER", "Volt-Draco (Handheld Partner)", 4, "GB / GBC / NDS"),
    BOSS_UFO("BOSS_UFO", "Dreadnought Cruiser (Beam)", 4, "Arcade / Saturn"),
    DOCU_BOT("DOCU_BOT", "Docu-Anchor AI Host (Lip-Sync)", 4, "Online Show Host"),
    ARCADE_RACER("ARCADE_RACER", "OutRun Turbo Interceptor", 4, "Super Scaler 2D");

    companion object {
        fun fromId(id: String): SpriteActorType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: CYBER_KNIGHT
    }
}

enum class TexturePreset(
    val id: String,
    val displayName: String,
    val shaderNote: String
) {
    NEON_GRID("NEON_GRID", "Synthwave UV Grid", "Emissive cyan/magenta wireframe UV map"),
    CYBER_CIRCUIT("CYBER_CIRCUIT", "Motherboard PCB Trace", "Gold & emerald logic traces"),
    CHECKER_UV("CHECKER_UV", "Sega/Amiga UV Checker", "Classic 3D calibration checkerboard"),
    LAVA_MAGMA("LAVA_MAGMA", "Volcanic Magma Core", "Animated emissive orange/crimson veins"),
    BRUSHED_TITANIUM("BRUSHED_TITANIUM", "Mecha Anodized Alloy", "Specular metallic panel seams"),
    PIXEL_BRICK("PIXEL_BRICK", "Dungeon 8-Bit Cobble", "Quantized retro pixel dungeon stone"),
    HOLO_SCANLINE("HOLO_SCANLINE", "Holographic Telemetry", "Translucent scanline projection"),
    AI_CUSTOM_BITMAP("AI_CUSTOM_BITMAP", "AI Synthesized Texture", "Live Nano Banana 2.1 texture/sprite map");

    companion object {
        fun fromId(id: String): TexturePreset =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: NEON_GRID
    }
}

enum class AnimationMotion(
    val id: String,
    val displayName: String
) {
    ORBIT_SHOWCASE("ORBIT_SHOWCASE", "360° Turntable Showcase"),
    HERO_ACTION_SLASH("HERO_ACTION_SLASH", "Combat Strike & Recoil"),
    ZERO_G_FLOAT("ZERO_G_FLOAT", "Zero-G Hover & Breath"),
    BARREL_ROLL_FLIGHT("BARREL_ROLL_FLIGHT", "High-G Banking & Roll"),
    MARCH_CYCLE("MARCH_CYCLE", "Rhythmic Stride & Bob"),
    OVERDRIVE_PULSE("OVERDRIVE_PULSE", "Hyper-Core Harmonic Pulse");

    companion object {
        fun fromId(id: String): AnimationMotion =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: ORBIT_SHOWCASE
    }
}

enum class CameraMove(
    val id: String,
    val displayName: String
) {
    ORBIT_360("ORBIT_360", "Cinematic 360° Orbit"),
    DRAMATIC_PUSH_IN("DRAMATIC_PUSH_IN", "Dramatic Dolly Push-In"),
    WIDE_CRANE_SWEEP("WIDE_CRANE_SWEEP", "Broadcast Crane Sweep"),
    ISOMETRIC_TACTICAL("ISOMETRIC_TACTICAL", "Tactical Isometric Lock"),
    HANDHELD_ACTION_CAM("HANDHELD_ACTION_CAM", "Dynamic Action Tracking");

    companion object {
        fun fromId(id: String): CameraMove =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: ORBIT_360
    }
}

enum class ParticleFxType(
    val id: String,
    val displayName: String
) {
    CODE_MATRIX_RAIN("CODE_MATRIX_RAIN", "Documentation Telemetry Stream"),
    STARFIELD_WARP("STARFIELD_WARP", "Parallax Starfield Warp"),
    PIXEL_SPARK_BURST("PIXEL_SPARK_BURST", "16-Bit Hit Sparks & Embers"),
    RETRO_LASER_GRID("RETRO_LASER_GRID", "Horizon Vector Laser Beams"),
    HOLO_DATA_NODES("HOLO_DATA_NODES", "Floating Architecture Nodes");

    companion object {
        fun fromId(id: String): ParticleFxType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: STARFIELD_WARP
    }
}

data class ShowScene(
    val id: String,
    val index: Int,
    val title: String,
    val documentationExcerpt: String,
    val narratorScript: String,
    val consoleId: String,
    val model3D: Model3DType,
    val modelMotion: AnimationMotion,
    val texturePreset: TexturePreset,
    val primarySprite: SpriteActorType,
    val secondarySprite: SpriteActorType,
    val cameraMove: CameraMove,
    val particleFx: ParticleFxType,
    val durationSeconds: Int = 7,
    val modelScale: Float = 1.0f,
    val lightAzimuth: Float = 45f
)

data class ShowProject(
    val id: Long = 0,
    val title: String,
    val subtitle: String,
    val sourceDocumentation: String,
    val scenes: List<ShowScene>,
    val createdAt: Long = System.currentTimeMillis()
)
