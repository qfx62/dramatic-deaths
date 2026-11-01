#version 330
#extension GL_ARB_separate_shader_objects : require

// Cinematic lens: chromatic aberration towards the edges, a soft vignette and a little contrast.

uniform sampler2D InSampler;

layout(std140) uniform LensConfig {
    // x = aberration strength, y = vignette strength, z = contrast
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 fromCenter = texCoord - 0.5;
    float dist = length(fromCenter);
    vec2 offset = fromCenter * dist * Params.x;

    float red = texture(InSampler, texCoord + offset).r;
    float green = texture(InSampler, texCoord).g;
    float blue = texture(InSampler, texCoord - offset).b;
    vec3 color = vec3(red, green, blue);

    color = (color - 0.5) * Params.z + 0.5;
    color *= 1.0 - Params.y * smoothstep(0.25, 0.85, dist);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
