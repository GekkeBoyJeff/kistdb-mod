#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif

#ifndef EMISSIVE
layout(location = 4) in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif

layout(location = 6) in vec2 texCoord0;
#ifdef GLINT
layout(location = 7) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Libreria: pixels whose alpha is 249-254 light up and move (enchant effects on armour). The code says how:
// 254 steady, 253 pulse, 252 flicker (flames), 251 flow (bands moving up), 250 twinkle, 249 shifting colour.
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
    else if (code == 251) b = 0.4 + 0.6 * pow(0.5 + 0.5 * sin(px.y * 0.8 - t * 5.0 + px.x * 0.15), 2.0);
    else if (code == 250) b = 0.3 + 0.7 * pow(max(0.0, sin(t * 2.2 + h * 40.0)), 10.0);
    else if (code == 249) {
        float a = t * 0.8 + h;
        rgb = clamp(rgb * vec3(0.6 + 0.4 * sin(a), 0.6 + 0.4 * sin(a + 2.094), 0.6 + 0.4 * sin(a + 4.189)) * 1.4, 0.0, 1.0);
    }
    return rgb * (0.35 + 0.95 * b);
}

vec4 calculateFinalColor(vec4 color, bool lit) {
    #ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    #endif

    #ifndef EMISSIVE
    if (lit) color *= lightMapColor;
    #endif

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    int libreriaCode = int(color.a * 255.0 + 0.5);
    bool libreriaFx = libreriaCode >= 249 && libreriaCode <= 254;
    if (libreriaFx) color.a = 1.0;

    #ifdef OIT_ADDITIVE
    color.a = min(0.99, color.a);
    #endif

    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    #else
    vec4 faceVertexColor = vertexColor;
    #endif

    #ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency
    faceVertexColor.a = 1.0;
    #endif

    if (libreriaFx) {
        color.rgb = libreriaGlow(libreriaCode, color.rgb, texCoord0);
        color *= vec4(1.0, 1.0, 1.0, faceVertexColor.a) * ColorModulator;
    } else {
        color *= faceVertexColor * ColorModulator;
    }

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color, !libreriaFx);
    #endif
}
