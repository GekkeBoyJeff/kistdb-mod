#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Libreria: pixels whose alpha is 249-254 light up and move (enchant effects on a sword's edge, the same codes as on
// armour): 254 steady, 253 pulse, 252 flicker (flames), 251 flow (a glint running along), 250 twinkle, 249 shifting colour.
float libreriaHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 libreriaGlow(int code, vec3 rgb, vec2 uv) {
    float t = GameTime * 1200.0;
    vec2 px = floor(uv * vec2(textureSize(Sampler0, 0)));
    float h = libreriaHash(px);
    float b = 1.0;
    if (code == 253) b = 0.62 + 0.38 * sin(t * 2.4 + h * 6.2832);
    else if (code == 252) b = 0.55 + 0.45 * libreriaHash(px + vec2(floor(t * 9.0 + h * 3.0), 0.0));
    else if (code == 251) b = 0.25 + 0.75 * pow(0.5 + 0.5 * sin((px.x - px.y) * 0.55 - t * 6.0), 6.0);
    else if (code == 250) b = 0.3 + 0.7 * pow(max(0.0, sin(t * 2.2 + h * 40.0)), 10.0);
    else if (code == 249) {
        float a = t * 0.8 + h;
        rgb = clamp(rgb * vec3(0.6 + 0.4 * sin(a), 0.6 + 0.4 * sin(a + 2.094), 0.6 + 0.4 * sin(a + 4.189)) * 1.4, 0.0, 1.0);
    }
    return rgb * (0.35 + 0.95 * b);
}

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color, bool lit, bool glint) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    if (lit) color *= lightMapColor;

    #ifdef GLINT
    if (glint) {
        vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
        // Matches BlendFuntion.GLINT
        color.rgb += glintColor.rgb * glintColor.rgb;
    }
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    int libreriaCode = int(color.a * 255.0 + 0.5);
    bool libreriaFx = libreriaCode >= 249 && libreriaCode <= 254;
    // Libreria: a sword's aura (see-through pixels, never fully opaque) gets no enchantment glint: the aura is the effect
    bool libreriaAura = !libreriaFx && libreriaCode > 0 && libreriaCode <= 246;
    if (libreriaFx) color.a = 1.0;
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    if (libreriaFx) {
        // lit by itself: no lightmap, no shading from the side, only its own colour (and a tint)
        color.rgb = libreriaGlow(libreriaCode, color.rgb, texCoord0);
        color *= vec4(1.0, 1.0, 1.0, vertexColor.a) * ColorModulator;
    } else {
        color *= vertexColor * ColorModulator;
    }

    #ifdef GLINT
    if (!libreriaAura) color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color, !libreriaFx, !libreriaAura);
    #endif
}
