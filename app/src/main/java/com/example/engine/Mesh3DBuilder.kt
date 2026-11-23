package com.example.engine

import com.example.model.AnimationMotion
import com.example.model.Model3DType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z)
        return if (len > 0.0001f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun rotateX(rad: Float): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(x, y * c - z * s, y * s + z * c)
    }

    fun rotateY(rad: Float): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(x * c + z * s, y, -x * s + z * c)
    }

    fun rotateZ(rad: Float): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(x * c - y * s, x * s + y * c, z)
    }
}

enum class ColorRole {
    PRIMARY,
    SECONDARY,
    ACCENT,
    EMISSIVE,
    DARK_METAL
}

data class Face3D(
    val vertices: List<Vec3>,
    val colorRole: ColorRole,
    val uvHint: Int = 0
)

object Mesh3DBuilder {

    fun buildAnimatedMesh(
        type: Model3DType,
        motion: AnimationMotion,
        timeSec: Float,
        scale: Float = 1.0f
    ): List<Face3D> {
        val rawFaces = when (type) {
            Model3DType.MECHA_VANGUARD -> buildMecha(timeSec, motion)
            Model3DType.CRYSTAL_DRAGON -> buildDragon(timeSec, motion)
            Model3DType.STARFIGHTER_ARWING -> buildStarfighter(timeSec, motion)
            Model3DType.CYBER_MASCOT -> buildCyberMascot(timeSec, motion)
            Model3DType.CONSOLE_TOTEM -> buildConsoleTotem(timeSec, motion)
            Model3DType.HOLO_CORE -> buildHoloCore(timeSec, motion)
        }

        val bobY = when (motion) {
            AnimationMotion.ZERO_G_FLOAT -> sin(timeSec * 2.2f) * 0.18f
            AnimationMotion.MARCH_CYCLE -> kotlin.math.abs(sin(timeSec * 5.0f)) * 0.14f
            AnimationMotion.HERO_ACTION_SLASH -> sin(timeSec * 4.0f) * 0.12f
            AnimationMotion.OVERDRIVE_PULSE -> sin(timeSec * 6.0f) * 0.08f
            else -> sin(timeSec * 1.6f) * 0.08f
        }
        val rollZ = when (motion) {
            AnimationMotion.BARREL_ROLL_FLIGHT -> timeSec * 2.4f
            AnimationMotion.HERO_ACTION_SLASH -> sin(timeSec * 3.2f) * 0.22f
            else -> 0f
        }
        val pulseScale = if (motion == AnimationMotion.OVERDRIVE_PULSE) {
            scale * (1.0f + 0.12f * sin(timeSec * 6.0f))
        } else {
            scale
        }

        return rawFaces.map { face ->
            face.copy(
                vertices = face.vertices.map { v ->
                    val scaled = v * pulseScale
                    val rolled = if (rollZ != 0f) scaled.rotateZ(rollZ) else scaled
                    Vec3(rolled.x, rolled.y + bobY, rolled.z)
                }
            )
        }
    }

    private fun addBox(
        out: MutableList<Face3D>,
        center: Vec3,
        rx: Float,
        ry: Float,
        rz: Float,
        roleFront: ColorRole,
        roleSide: ColorRole = roleFront,
        roleTop: ColorRole = roleFront,
        rotX: Float = 0f,
        rotY: Float = 0f,
        rotZ: Float = 0f
    ) {
        val localVerts = listOf(
            Vec3(-rx, -ry, rz),  // 0 front-bottom-left
            Vec3(rx, -ry, rz),   // 1 front-bottom-right
            Vec3(rx, ry, rz),    // 2 front-top-right
            Vec3(-rx, ry, rz),   // 3 front-top-left
            Vec3(-rx, -ry, -rz), // 4 back-bottom-left
            Vec3(rx, -ry, -rz),  // 5 back-bottom-right
            Vec3(rx, ry, -rz),   // 6 back-top-right
            Vec3(-rx, ry, -rz)   // 7 back-top-left
        ).map { v ->
            var p = v
            if (rotX != 0f) p = p.rotateX(rotX)
            if (rotY != 0f) p = p.rotateY(rotY)
            if (rotZ != 0f) p = p.rotateZ(rotZ)
            p + center
        }

        out += Face3D(listOf(localVerts[0], localVerts[1], localVerts[2], localVerts[3]), roleFront, 0)
        out += Face3D(listOf(localVerts[5], localVerts[4], localVerts[7], localVerts[6]), roleSide, 1)
        out += Face3D(listOf(localVerts[4], localVerts[0], localVerts[3], localVerts[7]), roleSide, 2)
        out += Face3D(listOf(localVerts[1], localVerts[5], localVerts[6], localVerts[2]), roleSide, 3)
        out += Face3D(listOf(localVerts[3], localVerts[2], localVerts[6], localVerts[7]), roleTop, 4)
        out += Face3D(listOf(localVerts[4], localVerts[5], localVerts[1], localVerts[0]), roleSide, 5)
    }

    private fun addPyramid(
        out: MutableList<Face3D>,
        apex: Vec3,
        baseCenter: Vec3,
        bx: Float,
        bz: Float,
        role: ColorRole,
        altRole: ColorRole = role
    ) {
        val b0 = Vec3(baseCenter.x - bx, baseCenter.y, baseCenter.z + bz)
        val b1 = Vec3(baseCenter.x + bx, baseCenter.y, baseCenter.z + bz)
        val b2 = Vec3(baseCenter.x + bx, baseCenter.y, baseCenter.z - bz)
        val b3 = Vec3(baseCenter.x - bx, baseCenter.y, baseCenter.z - bz)
        out += Face3D(listOf(b0, b1, apex), role, 0)
        out += Face3D(listOf(b1, b2, apex), altRole, 1)
        out += Face3D(listOf(b2, b3, apex), role, 2)
        out += Face3D(listOf(b3, b0, apex), altRole, 3)
    }

    private fun buildMecha(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        val slashAngle = if (motion == AnimationMotion.HERO_ACTION_SLASH) {
            sin(timeSec * 5.0f) * 0.95f
        } else {
            sin(timeSec * 2.2f) * 0.35f
        }
        val strideAngle = sin(timeSec * 3.6f) * 0.45f

        // Core chest & cockpit
        addBox(faces, Vec3(0f, 0.35f, 0f), 0.52f, 0.42f, 0.32f, ColorRole.PRIMARY, ColorRole.SECONDARY, ColorRole.ACCENT)
        // Glowing cockpit hatch
        addBox(faces, Vec3(0f, 0.32f, 0.34f), 0.24f, 0.18f, 0.06f, ColorRole.EMISSIVE)
        // Head & V-fin antenna
        addBox(faces, Vec3(0f, 0.98f, 0f), 0.26f, 0.22f, 0.24f, ColorRole.PRIMARY, ColorRole.SECONDARY)
        addBox(faces, Vec3(0f, 1.00f, 0.25f), 0.22f, 0.07f, 0.04f, ColorRole.EMISSIVE)
        addPyramid(faces, Vec3(-0.38f, 1.42f, 0.12f), Vec3(-0.08f, 1.15f, 0.12f), 0.06f, 0.04f, ColorRole.ACCENT)
        addPyramid(faces, Vec3(0.38f, 1.42f, 0.12f), Vec3(0.08f, 1.15f, 0.12f), 0.06f, 0.04f, ColorRole.ACCENT)

        // Shoulder armor & articulated arms
        addBox(faces, Vec3(-0.76f, 0.56f, 0f), 0.24f, 0.22f, 0.28f, ColorRole.SECONDARY, ColorRole.ACCENT, rotZ = 0.2f)
        addBox(faces, Vec3(0.76f, 0.56f, 0f), 0.24f, 0.22f, 0.28f, ColorRole.SECONDARY, ColorRole.ACCENT, rotZ = -0.2f)

        // Left arm with shield
        addBox(faces, Vec3(-0.78f, 0.08f, 0.12f), 0.15f, 0.36f, 0.15f, ColorRole.DARK_METAL, ColorRole.PRIMARY, rotX = -slashAngle * 0.5f)
        addBox(faces, Vec3(-0.98f, 0.08f, 0.22f), 0.06f, 0.48f, 0.30f, ColorRole.ACCENT, ColorRole.SECONDARY, rotX = -slashAngle * 0.5f)

        // Right arm with Plasma Energy Blade
        addBox(faces, Vec3(0.78f, 0.12f, 0.20f), 0.15f, 0.36f, 0.15f, ColorRole.DARK_METAL, ColorRole.PRIMARY, rotX = slashAngle)
        addBox(faces, Vec3(0.82f, 0.35f, 0.78f), 0.05f, 0.08f, 0.65f, ColorRole.EMISSIVE, ColorRole.ACCENT, rotX = slashAngle)

        // Waist & articulated legs
        addBox(faces, Vec3(0f, -0.18f, 0f), 0.38f, 0.14f, 0.26f, ColorRole.DARK_METAL, ColorRole.ACCENT)
        addBox(faces, Vec3(-0.28f, -0.72f, sin(strideAngle) * 0.2f), 0.18f, 0.46f, 0.20f, ColorRole.PRIMARY, ColorRole.SECONDARY, rotX = strideAngle)
        addBox(faces, Vec3(0.28f, -0.72f, -sin(strideAngle) * 0.2f), 0.18f, 0.46f, 0.20f, ColorRole.PRIMARY, ColorRole.SECONDARY, rotX = -strideAngle)

        // Back vernier wing binders
        val wingFlare = 0.35f + 0.12f * sin(timeSec * 3.0f)
        addBox(faces, Vec3(-0.55f, 0.65f, -0.45f), 0.12f, 0.55f, 0.18f, ColorRole.ACCENT, ColorRole.EMISSIVE, rotZ = wingFlare)
        addBox(faces, Vec3(0.55f, 0.65f, -0.45f), 0.12f, 0.55f, 0.18f, ColorRole.ACCENT, ColorRole.EMISSIVE, rotZ = -wingFlare)

        return faces
    }

    private fun buildDragon(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        val flap = sin(timeSec * 4.2f) * 0.65f

        // Dragon chest & crystalline core
        addBox(faces, Vec3(0f, 0.15f, 0f), 0.46f, 0.34f, 0.65f, ColorRole.PRIMARY, ColorRole.SECONDARY, ColorRole.ACCENT)
        addBox(faces, Vec3(0f, 0.22f, 0.68f), 0.22f, 0.22f, 0.12f, ColorRole.EMISSIVE)

        // Neck & Dragon Head
        addBox(faces, Vec3(0f, 0.62f, 0.72f), 0.20f, 0.32f, 0.20f, ColorRole.SECONDARY, ColorRole.PRIMARY, rotX = 0.35f)
        addBox(faces, Vec3(0f, 0.92f, 0.98f), 0.26f, 0.20f, 0.34f, ColorRole.PRIMARY, ColorRole.ACCENT)
        addBox(faces, Vec3(0f, 0.96f, 1.28f), 0.18f, 0.08f, 0.14f, ColorRole.EMISSIVE)
        // Horns
        addPyramid(faces, Vec3(-0.32f, 1.38f, 0.72f), Vec3(-0.16f, 1.10f, 0.88f), 0.07f, 0.07f, ColorRole.ACCENT)
        addPyramid(faces, Vec3(0.32f, 1.38f, 0.72f), Vec3(0.16f, 1.10f, 0.88f), 0.07f, 0.07f, ColorRole.ACCENT)

        // Articulated Flapping Crystal Wings
        addBox(
            faces,
            Vec3(-0.98f, 0.38f + sin(flap) * 0.35f, 0.05f),
            0.62f, 0.06f, 0.48f,
            ColorRole.EMISSIVE, ColorRole.ACCENT,
            rotZ = flap
        )
        addBox(
            faces,
            Vec3(0.98f, 0.38f + sin(flap) * 0.35f, 0.05f),
            0.62f, 0.06f, 0.48f,
            ColorRole.EMISSIVE, ColorRole.ACCENT,
            rotZ = -flap
        )

        // Undulating Tail segments
        for (i in 1..3) {
            val sway = sin(timeSec * 3.5f - i * 0.8f) * 0.22f * i
            val zPos = -0.65f - i * 0.38f
            val size = 0.24f - i * 0.04f
            addBox(faces, Vec3(sway, 0.05f - i * 0.08f, zPos), size, size, 0.22f, ColorRole.SECONDARY, ColorRole.ACCENT)
        }

        // Dorsal crystal spikes
        addPyramid(faces, Vec3(0f, 0.82f, 0.15f), Vec3(0f, 0.48f, 0.15f), 0.12f, 0.16f, ColorRole.EMISSIVE)
        addPyramid(faces, Vec3(0f, 0.76f, -0.30f), Vec3(0f, 0.48f, -0.30f), 0.10f, 0.14f, ColorRole.EMISSIVE)

        return faces
    }

    private fun buildStarfighter(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        val bankAngle = sin(timeSec * 2.5f) * 0.38f

        // Nose cone & Fuselage
        addPyramid(faces, Vec3(0f, 0.0f, 1.55f), Vec3(0f, 0.0f, 0.45f), 0.34f, 0.34f, ColorRole.PRIMARY, ColorRole.SECONDARY)
        addBox(faces, Vec3(0f, 0.0f, -0.15f), 0.34f, 0.20f, 0.65f, ColorRole.PRIMARY, ColorRole.DARK_METAL, ColorRole.SECONDARY, rotZ = bankAngle * 0.3f)

        // Glowing Canopy
        addBox(faces, Vec3(0f, 0.22f, 0.05f), 0.20f, 0.12f, 0.42f, ColorRole.EMISSIVE, ColorRole.ACCENT)

        // Swept Delta Wings + Blue G-Diffuser Pods
        addBox(faces, Vec3(-0.95f, -0.04f, -0.25f), 0.68f, 0.05f, 0.38f, ColorRole.PRIMARY, ColorRole.ACCENT, rotZ = 0.12f)
        addBox(faces, Vec3(0.95f, -0.04f, -0.25f), 0.68f, 0.05f, 0.38f, ColorRole.PRIMARY, ColorRole.ACCENT, rotZ = -0.12f)
        addBox(faces, Vec3(-0.56f, -0.02f, -0.20f), 0.14f, 0.14f, 0.52f, ColorRole.ACCENT, ColorRole.SECONDARY)
        addBox(faces, Vec3(0.56f, -0.02f, -0.20f), 0.14f, 0.14f, 0.52f, ColorRole.ACCENT, ColorRole.SECONDARY)

        // Twin Vertical Stabilizers
        addBox(faces, Vec3(-0.32f, 0.32f, -0.52f), 0.05f, 0.28f, 0.26f, ColorRole.SECONDARY, ColorRole.ACCENT, rotZ = 0.18f)
        addBox(faces, Vec3(0.32f, 0.32f, -0.52f), 0.05f, 0.28f, 0.26f, ColorRole.SECONDARY, ColorRole.ACCENT, rotZ = -0.18f)

        // Pulsing Plasma Thruster Plume
        val plumeLen = 0.35f + 0.18f * kotlin.math.abs(sin(timeSec * 12f))
        addBox(faces, Vec3(0f, 0f, -0.92f), 0.22f, 0.16f, plumeLen, ColorRole.EMISSIVE, ColorRole.ACCENT)

        return faces
    }

    private fun buildCyberMascot(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        val wave = sin(timeSec * 4.5f) * 0.6f

        // Retro CRT Monitor Head
        addBox(faces, Vec3(0f, 0.68f, 0f), 0.52f, 0.42f, 0.42f, ColorRole.PRIMARY, ColorRole.SECONDARY, ColorRole.ACCENT)
        // Illuminated CRT Screen Visor
        addBox(faces, Vec3(0f, 0.68f, 0.43f), 0.40f, 0.28f, 0.04f, ColorRole.EMISSIVE)
        // Mascot Antenna
        addBox(faces, Vec3(0f, 1.24f, 0f), 0.05f, 0.18f, 0.05f, ColorRole.ACCENT)
        addBox(faces, Vec3(0f, 1.44f, 0f), 0.12f, 0.12f, 0.12f, ColorRole.EMISSIVE)

        // Torso & Jetpack
        addBox(faces, Vec3(0f, -0.02f, 0f), 0.36f, 0.34f, 0.28f, ColorRole.SECONDARY, ColorRole.PRIMARY)
        addBox(faces, Vec3(0f, 0.02f, 0.29f), 0.18f, 0.16f, 0.04f, ColorRole.ACCENT)
        addBox(faces, Vec3(0f, 0.02f, -0.34f), 0.26f, 0.26f, 0.14f, ColorRole.DARK_METAL, ColorRole.EMISSIVE)

        // Expressive Arms & Oversized Platformer Boots
        addBox(faces, Vec3(-0.56f, 0.08f, 0.10f), 0.14f, 0.28f, 0.14f, ColorRole.ACCENT, ColorRole.PRIMARY, rotX = wave, rotZ = 0.25f)
        addBox(faces, Vec3(0.56f, 0.08f, 0.10f), 0.14f, 0.28f, 0.14f, ColorRole.ACCENT, ColorRole.PRIMARY, rotX = -wave, rotZ = -0.25f)
        addBox(faces, Vec3(-0.22f, -0.68f, sin(wave) * 0.16f), 0.18f, 0.28f, 0.26f, ColorRole.ACCENT, ColorRole.SECONDARY)
        addBox(faces, Vec3(0.22f, -0.68f, -sin(wave) * 0.16f), 0.18f, 0.28f, 0.26f, ColorRole.ACCENT, ColorRole.SECONDARY)

        return faces
    }

    private fun buildConsoleTotem(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        val cartFloat = sin(timeSec * 3.0f) * 0.18f

        // Handheld Shell Body
        addBox(faces, Vec3(0f, 0.0f, 0f), 0.68f, 0.92f, 0.22f, ColorRole.PRIMARY, ColorRole.SECONDARY, ColorRole.ACCENT)
        // Glowing Screen Display
        addBox(faces, Vec3(0f, 0.32f, 0.23f), 0.50f, 0.38f, 0.04f, ColorRole.EMISSIVE, ColorRole.DARK_METAL)
        // D-Pad Cross
        addBox(faces, Vec3(-0.32f, -0.36f, 0.24f), 0.16f, 0.06f, 0.04f, ColorRole.DARK_METAL)
        addBox(faces, Vec3(-0.32f, -0.36f, 0.24f), 0.06f, 0.16f, 0.04f, ColorRole.DARK_METAL)
        // Action Buttons A / B
        addBox(faces, Vec3(0.36f, -0.30f, 0.24f), 0.09f, 0.09f, 0.05f, ColorRole.ACCENT)
        addBox(faces, Vec3(0.18f, -0.42f, 0.24f), 0.09f, 0.09f, 0.05f, ColorRole.SECONDARY)

        // Levitating ROM Cartridge slotting into the top
        addBox(faces, Vec3(0f, 1.12f + cartFloat, -0.06f), 0.46f, 0.30f, 0.10f, ColorRole.ACCENT, ColorRole.EMISSIVE)

        return faces
    }

    private fun buildHoloCore(timeSec: Float, motion: AnimationMotion): List<Face3D> {
        val faces = mutableListOf<Face3D>()
        // Central Crystalline Octahedron
        val pulse = 1.0f + 0.15f * sin(timeSec * 4.0f)
        addPyramid(faces, Vec3(0f, 0.62f * pulse, 0f), Vec3(0f, 0f, 0f), 0.42f, 0.42f, ColorRole.EMISSIVE, ColorRole.PRIMARY)
        addPyramid(faces, Vec3(0f, -0.62f * pulse, 0f), Vec3(0f, 0f, 0f), 0.42f, 0.42f, ColorRole.ACCENT, ColorRole.SECONDARY)

        // Orbital Telemetry Nodes (8 segments forming two counter-rotating gimbal rings)
        for (i in 0 until 6) {
            val angle1 = timeSec * 1.4f + i * (PI.toFloat() / 3f)
            val nx = cos(angle1) * 1.08f
            val nz = sin(angle1) * 1.08f
            addBox(
                faces,
                Vec3(nx, sin(angle1 * 2f) * 0.24f, nz),
                0.14f, 0.14f, 0.14f,
                if (i % 2 == 0) ColorRole.PRIMARY else ColorRole.EMISSIVE,
                ColorRole.ACCENT,
                rotY = -angle1
            )
        }
        return faces
    }
}
