package com.metaport.xr.core.gl

/**
 * Every shader MetaPort uses. All content is procedural — the APK ships no
 * textures, models or audio files, so the whole app is a few hundred KB.
 *
 * GLSL ES 3.00 (OpenGL ES 3.0 context, which ARCore also requires).
 */
object Shaders {

    private const val COMMON_VS_HEAD = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;
"""

    // ---------------------------------------------------------------- lit PBR-ish
    val LIT_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vN;
out vec3 vW;
out vec2 vUv;
out vec4 vCol;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vW = wp.xyz;
    vN = normalize(mat3(uModel) * aNormal);
    vUv = aUv;
    vCol = aColor;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val LIT_FS = """#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
in vec3 vN;
in vec3 vW;
in vec2 vUv;
in vec4 vCol;

uniform vec4  uBaseColor;
uniform vec3  uEmissiveColor;
uniform float uEmissive;
uniform float uRoughness;
uniform float uMetallic;
uniform float uAlphaMul;
uniform float uRimStrength;
uniform float uRimPower;
uniform int   uUseTex;
uniform sampler2D uTex;
uniform vec3  uCamPos;
uniform vec3  uLightDir[3];
uniform vec3  uLightColor[3];
uniform vec3  uAmbientTop;
uniform vec3  uAmbientBottom;
uniform vec4  uFog;          // rgb + density
uniform float uTime;
uniform float uAlphaTex;     // >0: modulate alpha by texture alpha (decals, portals)
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }

void main() {
    vec4 base = uBaseColor * vCol;
    float alpha = base.a * uAlphaMul;
    vec3 albedo = base.rgb;

    if (uUseTex == 1) {
        vec4 t = texture(uTex, vUv);
        albedo *= t.rgb;
        if (uAlphaTex > 0.5) alpha *= t.a;
    }

    vec3 N = normalize(vN);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCamPos - vW);

    // Hemisphere ambient.
    float hemi = N.y * 0.5 + 0.5;
    vec3 col = albedo * mix(uAmbientBottom, uAmbientTop, hemi);

    for (int i = 0; i < 3; i++) {
        vec3 L = normalize(-uLightDir[i]);
        float ndl = max(dot(N, L), 0.0);
        vec3 H = normalize(L + V);
        float ndh = max(dot(N, H), 0.0);
        float specPow = mix(256.0, 8.0, clamp(uRoughness, 0.0, 1.0));
        float spec = pow(ndh, specPow) * (1.0 - uRoughness * 0.85);
        vec3 diff = albedo * (1.0 - uMetallic * 0.75);
        vec3 specCol = mix(vec3(spec), albedo, uMetallic);
        col += (diff + specCol) * uLightColor[i] * ndl;
    }

    col += uEmissiveColor * uEmissive * albedo;

    float rim = pow(1.0 - max(dot(N, V), 0.0), max(uRimPower, 0.5));
    col += uEmissiveColor * rim * uRimStrength;

    float dist = length(uCamPos - vW);
    float fog = 1.0 - exp(-pow(dist * uFog.w, 2.0));
    col = mix(col, uFog.rgb, clamp(fog, 0.0, 1.0));

    fragColor = vec4(col, alpha);
}
"""

    // ---------------------------------------------------------------- unlit / glow
    val UNLIT_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
uniform mat4 uModel;
out vec2 vUv;
out vec4 vCol;
out vec3 vLocal;
void main() {
    vUv = aUv;
    vCol = aColor;
    vLocal = aPos;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val UNLIT_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vCol;
in vec3 vLocal;
uniform vec4 uBaseColor;
uniform float uAlphaMul;
uniform float uGlow;
uniform int uMode;      // 0 solid, 1 additive core falloff, 2 scanline energy
uniform float uTime;
out vec4 fragColor;
void main() {
    vec4 c = uBaseColor * vCol;
    float a = c.a * uAlphaMul;
    if (uMode == 1) {
        float d = length(vLocal);
        a *= clamp(1.0 - d, 0.0, 1.0);
        c.rgb *= 1.0 + uGlow * (1.0 - d);
    } else if (uMode == 2) {
        float s = sin(vUv.y * 90.0 - uTime * 6.0) * 0.5 + 0.5;
        c.rgb *= 0.75 + 0.55 * s;
        a *= 0.65 + 0.35 * s;
    }
    fragColor = vec4(c.rgb, a);
}
"""

    // ---------------------------------------------------------------- spatial UI panel
    val UI_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
out vec2 vUv;
out vec4 vCol;
out vec3 vN;
out vec3 vW;
uniform mat4 uModel;
void main() {
    vUv = aUv;
    vCol = aColor;
    vN = normalize(mat3(uModel) * aNormal);
    vW = (uModel * vec4(aPos, 1.0)).xyz;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val UI_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vCol;
in vec3 vN;
in vec3 vW;

uniform vec4  uFillColor;    // panel body tint
uniform vec4  uEdgeColor;    // border / accent
uniform vec2  uSize;         // world-space panel size (for aspect-correct corners)
uniform float uRadius;       // corner radius, world units
uniform float uBorder;       // border thickness, world units
uniform float uOpacity;
uniform float uHover;        // 0..1 hover highlight
uniform float uActive;       // 0..1 pressed highlight
uniform float uTime;
uniform vec3  uCamPos;
uniform float uGradient;     // vertical gradient strength
out vec4 fragColor;

float roundedBoxSdf(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + vec2(r);
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    vec2 p = (vUv - 0.5) * uSize;
    vec2 halfSize = uSize * 0.5;
    float r = min(uRadius, min(halfSize.x, halfSize.y) - 0.001);
    float d = roundedBoxSdf(p, halfSize, r);

    float aa = fwidth(d) * 1.5 + 0.0008;
    float inside = 1.0 - smoothstep(-aa, aa, d);
    if (inside < 0.004) discard;

    float edge = 1.0 - smoothstep(uBorder - aa, uBorder + aa, abs(d));

    // Frosted glass body: subtle vertical gradient + fine noise.
    float n = fract(sin(dot(floor(vUv * 260.0), vec2(12.9898, 78.233))) * 43758.5453);
    vec3 body = uFillColor.rgb * (1.0 - uGradient * (vUv.y - 0.5));
    body += (n - 0.5) * 0.02;

    // Fresnel-ish sheen so panels read as physical glass in stereo.
    vec3 V = normalize(uCamPos - vW);
    float fres = pow(1.0 - abs(dot(normalize(vN), V)), 3.0);

    vec3 col = body;
    col = mix(col, uEdgeColor.rgb, edge * uEdgeColor.a);
    col += uEdgeColor.rgb * fres * 0.35;
    col += uEdgeColor.rgb * uHover * 0.35;
    col += vec3(1.0) * uActive * 0.30;

    float a = inside * uOpacity * (uFillColor.a + edge * uEdgeColor.a * 0.8 + fres * 0.4);
    a = clamp(a + uHover * 0.08, 0.0, 1.0);

    fragColor = vec4(col, a);
}
"""

    // ---------------------------------------------------------------- text
    val TEXT_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
out vec2 vUv;
out vec4 vCol;
void main() {
    vUv = aUv;
    vCol = aColor;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val TEXT_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vCol;
uniform sampler2D uAtlas;
uniform vec4 uColor;
uniform float uGlow;
uniform float uAlphaMul;
out vec4 fragColor;
void main() {
    float a = texture(uAtlas, vUv).a;
    if (a * uAlphaMul < 0.01) discard;
    vec3 col = uColor.rgb * vCol.rgb;
    col += uColor.rgb * uGlow * 0.6;
    fragColor = vec4(col, a * uColor.a * vCol.a * uAlphaMul);
}
"""

    // ---------------------------------------------------------------- sky / environment dome
    val SKY_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
out vec3 vDir;
out vec2 vUv;
void main() {
    vDir = aPos;
    vUv = aUv;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val SKY_FS = """#version 300 es
precision highp float;
in vec3 vDir;
in vec2 vUv;
uniform int uMode;      // 0 nebula, 1 cyber city, 2 space station, 3 forest, 4 desert, 5 dojo, 6 black(MR)
uniform float uTime;
uniform vec3 uTint;
uniform vec3 uHorizon;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float vnoise(vec3 x) {
    vec3 i = floor(x); vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0,0,0)), hash(i + vec3(1,0,0)), f.x),
                   mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                   mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float a = 0.5; float s = 0.0;
    for (int i = 0; i < 5; i++) { s += a * vnoise(p); p *= 2.03; a *= 0.5; }
    return s;
}

void main() {
    vec3 d = normalize(vDir);
    float h = d.y * 0.5 + 0.5;
    vec3 col;

    if (uMode == 6) {
        col = vec3(0.0);
    } else if (uMode == 0) {
        // Nebula void: deep space clouds + stars.
        vec3 base = mix(vec3(0.015, 0.01, 0.05), vec3(0.05, 0.02, 0.12), h);
        float cloud = fbm(d * 3.0 + vec3(0.0, uTime * 0.01, 0.0));
        cloud = pow(clamp(cloud - 0.35, 0.0, 1.0), 1.7);
        base += uTint * cloud * 1.5;
        base += uHorizon * pow(clamp(1.0 - abs(d.y), 0.0, 1.0), 6.0) * 0.5;
        float star = pow(vnoise(d * 260.0), 42.0) * 6.0;
        float twinkle = 0.65 + 0.35 * sin(uTime * 2.0 + star * 40.0);
        base += vec3(star) * twinkle;
        col = base;
    } else if (uMode == 1) {
        // Cyber city: magenta/cyan haze + vertical light shafts.
        col = mix(vec3(0.02, 0.01, 0.06), vec3(0.10, 0.02, 0.16), h);
        col += uHorizon * pow(clamp(1.0 - abs(d.y), 0.0, 1.0), 8.0);
        float shaft = pow(abs(sin(atan(d.z, d.x) * 9.0)), 26.0);
        col += uTint * shaft * clamp(1.0 - h * 1.4, 0.0, 1.0) * 0.9;
        col += vec3(0.05, 0.35, 0.5) * pow(max(-d.y, 0.0), 2.0) * 0.5;
    } else if (uMode == 2) {
        // Orbital station: black + planet limb.
        col = vec3(0.005, 0.006, 0.012);
        float planet = smoothstep(0.06, 0.0, length(d - normalize(vec3(-0.4, -0.85, -0.3))));
        col += vec3(0.10, 0.28, 0.42) * planet;
        float limb = smoothstep(0.10, 0.055, length(d - normalize(vec3(-0.4, -0.85, -0.3))));
        col += uTint * limb * 0.9;
        float star = pow(vnoise(d * 300.0), 40.0) * 5.0;
        col += vec3(star);
    } else if (uMode == 3) {
        // Forest valley: warm sky, sun disc, green haze.
        col = mix(vec3(0.42, 0.60, 0.72), vec3(0.10, 0.28, 0.45), pow(h, 0.7));
        float sun = pow(max(dot(d, normalize(vec3(0.5, 0.42, -0.7))), 0.0), 220.0);
        col += uTint * sun * 2.2;
        col += uHorizon * pow(clamp(1.0 - abs(d.y), 0.0, 1.0), 5.0);
        col = mix(col, vec3(0.13, 0.24, 0.14), smoothstep(0.52, 0.40, d.y));
    } else if (uMode == 4) {
        // Desert oasis: hot sand haze.
        col = mix(vec3(0.95, 0.72, 0.42), vec3(0.35, 0.55, 0.80), pow(clamp(h * 1.3, 0.0, 1.0), 0.6));
        float sun = pow(max(dot(d, normalize(vec3(-0.3, 0.55, -0.75))), 0.0), 300.0);
        col += uTint * sun * 2.5;
        col += uHorizon * pow(clamp(1.0 - abs(d.y), 0.0, 1.0), 9.0);
    } else {
        // Dojo: dark wood + paper lantern glow.
        col = mix(vec3(0.10, 0.07, 0.06), vec3(0.03, 0.02, 0.03), h);
        float lantern = pow(max(dot(d, normalize(vec3(0.0, 0.35, -1.0))), 0.0), 40.0);
        col += uTint * lantern * 1.6;
        col += uHorizon * pow(clamp(1.0 - abs(d.y), 0.0, 1.0), 4.0) * 0.4;
    }

    fragColor = vec4(col, 1.0);
}
"""

    // ---------------------------------------------------------------- ARCore camera passthrough (MR)
    val PASSTHROUGH_VS = COMMON_VS_HEAD + """
out vec2 vUv;
void main() {
    vUv = aUv;
    gl_Position = vec4(aPos.xy, 0.999, 1.0);
}
"""

    val PASSTHROUGH_FS = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
precision highp samplerExternalOES;
in vec2 vUv;
uniform samplerExternalOES uCameraTex;
uniform vec3 uTint;
uniform float uBrightness;
uniform float uContrast;
uniform float uVignette;
uniform float uScanline;
uniform float uTime;
out vec4 fragColor;
void main() {
    vec3 c = texture(uCameraTex, vUv).rgb;
    c = (c - 0.5) * uContrast + 0.5;
    c *= uBrightness;
    c *= uTint;
    if (uScanline > 0.001) {
        float s = sin(vUv.y * 700.0 + uTime * 3.0) * 0.5 + 0.5;
        c *= 1.0 - uScanline * 0.25 * s;
        c += vec3(0.0, 0.12, 0.16) * uScanline * 0.35;
    }
    vec2 q = vUv - 0.5;
    float vig = 1.0 - uVignette * dot(q, q) * 2.2;
    fragColor = vec4(c * clamp(vig, 0.0, 1.0), 1.0);
}
"""

    // ---------------------------------------------------------------- Cardboard lens distortion
    val DISTORT_VS = COMMON_VS_HEAD + """
out vec2 vUv;
void main() {
    vUv = aUv;
    gl_Position = vec4(aPos.xy, 0.0, 1.0);
}
"""

    val DISTORT_FS = """#version 300 es
precision highp float;
in vec2 vUv;
uniform sampler2D uScene;
uniform vec2 uLensCenter;   // in eye-texture space, 0..1
uniform vec2 uScale;        // scale from texture uv to lens space
uniform vec2 uScaleIn;      // inverse
uniform vec2 uEyeOffset;    // which half of the stereo target to sample
uniform vec2 uEyeSize;      // size of one eye in uv units
uniform vec2 uK;            // radial distortion coefficients k1, k2
uniform float uChroma;      // chromatic aberration strength
uniform float uVignette;
uniform float uEnabled;
out vec4 fragColor;

vec2 distort(vec2 r) {
    float r2 = dot(r, r);
    float f = 1.0 + uK.x * r2 + uK.y * r2 * r2;
    return r * f;
}

void main() {
    if (uEnabled < 0.5) {
        fragColor = texture(uScene, uEyeOffset + vUv * uEyeSize);
        return;
    }
    vec2 theta = (vUv - uLensCenter) * uScale * 2.0;
    vec2 tc = distort(theta) * uScaleIn * 0.5 + uLensCenter;

    float ca = uChroma * 0.0035 * length(theta);
    vec2 tcR = distort(theta * (1.0 + ca)) * uScaleIn * 0.5 + uLensCenter;
    vec2 tcB = distort(theta * (1.0 - ca)) * uScaleIn * 0.5 + uLensCenter;

    bool validR = all(greaterThanEqual(tcR, vec2(0.0))) && all(lessThanEqual(tcR, vec2(1.0)));
    bool validG = all(greaterThanEqual(tc, vec2(0.0))) && all(lessThanEqual(tc, vec2(1.0)));
    bool validB = all(greaterThanEqual(tcB, vec2(0.0))) && all(lessThanEqual(tcB, vec2(1.0)));

    if (!validG) { fragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }

    vec3 col;
    col.r = validR ? texture(uScene, uEyeOffset + tcR * uEyeSize).r : 0.0;
    col.g = texture(uScene, uEyeOffset + tc * uEyeSize).g;
    col.b = validB ? texture(uScene, uEyeOffset + tcB * uEyeSize).b : 0.0;

    float d = length(theta);
    float vig = 1.0 - uVignette * smoothstep(0.75, 1.6, d);
    fragColor = vec4(col * clamp(vig, 0.0, 1.0), 1.0);
}
"""

    // ---------------------------------------------------------------- grid / floor
    val GRID_VS = COMMON_VS_HEAD + """
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vW;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vW = wp.xyz;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    val GRID_FS = """#version 300 es
precision highp float;
in vec3 vW;
uniform vec4 uLineColor;
uniform vec4 uLineColor2;
uniform float uCell;
uniform float uThickness;
uniform float uFade;
uniform vec3 uCamPos;
uniform float uTime;
uniform float uPulse;
out vec4 fragColor;
void main() {
    vec2 g = vW.xz / uCell;
    vec2 f = abs(fract(g - 0.5) - 0.5) / max(fwidth(g), vec2(1e-5));
    float line = 1.0 - min(min(f.x, f.y), 1.0);

    vec2 g2 = vW.xz / (uCell * 5.0);
    vec2 f2 = abs(fract(g2 - 0.5) - 0.5) / max(fwidth(g2), vec2(1e-5));
    float line2 = 1.0 - min(min(f2.x, f2.y), 1.0);

    float dist = length(uCamPos - vW);
    float fade = exp(-dist * uFade);

    float ring = 0.0;
    if (uPulse > 0.001) {
        float r = length(vW.xz);
        float w = fract(r * 0.12 - uTime * 0.25);
        ring = smoothstep(0.9, 1.0, w) * uPulse;
    }

    vec3 col = mix(uLineColor.rgb, uLineColor2.rgb, clamp(dist * 0.05, 0.0, 1.0));
    col += uLineColor2.rgb * ring;
    float a = (line * uThickness + line2 * uThickness * 1.5 + ring) * fade * uLineColor.a;
    if (a < 0.004) discard;
    fragColor = vec4(col, clamp(a, 0.0, 1.0));
}
"""
}
