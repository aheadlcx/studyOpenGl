// shaders.h - shared GLSL sources (desktop #version 330, mirrors the app's ES3 shaders)
#pragma once

namespace shaders {

// flat color: draws any mesh in a single color (u_psize for GL_POINTS)
static const char* VS_FLAT = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_mvp;
uniform float u_psize = 1.0;
void main() {
    gl_Position = u_mvp * vec4(a_pos, 1.0);
    gl_PointSize = u_psize;
}
)";
static const char* FS_FLAT = R"(#version 330
uniform vec4 u_color = vec4(1.0);
out vec4 fragColor;
void main() { fragColor = u_color; }
)";

// vertex-color triangle/mesh (pos3 + color3)
static const char* VS_TRI = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
uniform float u_psize = 1.0;
out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
    gl_PointSize = u_psize;
}
)";
static const char* FS_TRI = R"(#version 330
in vec3 v_color;
uniform vec4 u_tint = vec4(1.0);
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0) * u_tint; }
)";

// textured mesh (pos3 + uv2)
static const char* VS_TEX = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
uniform mat4 u_mvp;
uniform float u_uvScale = 1.0;
out vec2 v_uv;
void main() {
    v_uv = a_uv * u_uvScale;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
static const char* FS_TEX = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_tex;
out vec4 fragColor;
void main() { fragColor = texture(u_tex, v_uv); }
)";

// normal + uv mesh for lighting chapters (pos3 + normal3 + uv2)
static const char* VS_LIT = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_normal;
layout(location=2) in vec2 a_uv;
uniform mat4 u_mvp;
uniform mat4 u_model;
out vec3 v_normal;
out vec3 v_worldPos;
out vec2 v_uv;
void main() {
    v_normal = mat3(u_model) * a_normal;
    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;
    v_uv = a_uv;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
static const char* FS_PHONG = R"(#version 330
in vec3 v_normal;
in vec3 v_worldPos;
in vec2 v_uv;
uniform sampler2D u_diffuse;
uniform vec3 u_lightPos;
uniform vec3 u_viewPos;
uniform vec3 u_lightColor;
uniform float u_ambient   = 0.15;
uniform float u_diffuseK  = 0.9;
uniform float u_specularK = 0.6;
uniform float u_shininess = 32.0;
out vec4 fragColor;
void main() {
    vec3 N = normalize(v_normal);
    vec3 L = normalize(u_lightPos - v_worldPos);
    vec3 V = normalize(u_viewPos - v_worldPos);
    vec3 R = reflect(-L, N);
    vec3 albedo = texture(u_diffuse, v_uv).rgb;
    vec3 ambient  = u_ambient * u_lightColor;
    vec3 diffuse  = u_diffuseK * max(dot(N, L), 0.0) * u_lightColor;
    float spec = pow(max(dot(R, V), 0.0), u_shininess);
    vec3 specular = u_specularK * spec * u_lightColor;
    fragColor = vec4((ambient + diffuse + specular) * albedo, 1.0);
}
)";

} // namespace shaders
