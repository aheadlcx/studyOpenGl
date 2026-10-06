// glfuncs.h - minimal GL function loader for Windows (opengl32.dll only exports GL 1.1,
// everything from GL 2.0+ is loaded at runtime via wglGetProcAddress).
#pragma once
#include <windows.h>
#include <GL/gl.h>
#include <cstddef>

// ---- types missing from the MSVC SDK's GL 1.1 header (MinGW defines them) ----
#ifndef GL_VERSION_1_5
typedef ptrdiff_t GLsizeiptr;
typedef ptrdiff_t GLintptr;
#endif
#ifndef GL_VERSION_2_0
typedef char GLchar;
#endif

// ---- GL enums that are missing from the Windows SDK's GL 1.1 header ----
#ifndef GL_ARRAY_BUFFER
#define GL_ARRAY_BUFFER 0x8892
#endif
#ifndef GL_ELEMENT_ARRAY_BUFFER
#define GL_ELEMENT_ARRAY_BUFFER 0x8893
#endif
#ifndef GL_STATIC_DRAW
#define GL_STATIC_DRAW 0x88E4
#endif
#ifndef GL_DYNAMIC_DRAW
#define GL_DYNAMIC_DRAW 0x88E8
#endif
#ifndef GL_STREAM_DRAW
#define GL_STREAM_DRAW 0x88E0
#endif
#ifndef GL_DYNAMIC_COPY
#define GL_DYNAMIC_COPY 0x88EA
#endif
#ifndef GL_STENCIL_INDEX8
#define GL_STENCIL_INDEX8 0x8D48
#endif
#ifndef GL_BLEND_EQUATION
#define GL_BLEND_EQUATION 0x8009
#endif
#ifndef GL_FUNC_SUBTRACT
#define GL_FUNC_SUBTRACT 0x800A
#endif
#ifndef GL_FUNC_REVERSE_SUBTRACT
#define GL_FUNC_REVERSE_SUBTRACT 0x800B
#endif
#ifndef GL_MIN
#define GL_MIN 0x8007
#endif
#ifndef GL_MAX
#define GL_MAX 0x8008
#endif
#ifndef GL_RGBA8
#define GL_RGBA8 0x8058
#endif
#ifndef GL_DEPTH_COMPONENT24
#define GL_DEPTH_COMPONENT24 0x81A6
#endif
#ifndef GL_FRAMEBUFFER
#define GL_FRAMEBUFFER 0x8D40
#endif
#ifndef GL_READ_FRAMEBUFFER
#define GL_READ_FRAMEBUFFER 0x8CA8
#endif
#ifndef GL_DRAW_FRAMEBUFFER
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#endif
#ifndef GL_COLOR_ATTACHMENT0
#define GL_COLOR_ATTACHMENT0 0x8CE0
#endif
#ifndef GL_COLOR_ATTACHMENT1
#define GL_COLOR_ATTACHMENT1 0x8CE1
#endif
#ifndef GL_DEPTH_ATTACHMENT
#define GL_DEPTH_ATTACHMENT 0x8D00
#endif
#ifndef GL_RENDERBUFFER
#define GL_RENDERBUFFER 0x8D41
#endif
#ifndef GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif
#ifndef GL_TEXTURE_2D_ARRAY
#define GL_TEXTURE_2D_ARRAY 0x8C1A
#endif
#ifndef GL_TEXTURE_CUBE_MAP
#define GL_TEXTURE_CUBE_MAP 0x8513
#endif
#ifndef GL_TEXTURE_CUBE_MAP_POSITIVE_X
#define GL_TEXTURE_CUBE_MAP_POSITIVE_X 0x8515
#endif
#ifndef GL_TEXTURE_CUBE_MAP_NEGATIVE_X
#define GL_TEXTURE_CUBE_MAP_NEGATIVE_X 0x8516
#endif
#ifndef GL_TEXTURE_CUBE_MAP_POSITIVE_Y
#define GL_TEXTURE_CUBE_MAP_POSITIVE_Y 0x8517
#endif
#ifndef GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
#define GL_TEXTURE_CUBE_MAP_NEGATIVE_Y 0x8518
#endif
#ifndef GL_TEXTURE_CUBE_MAP_POSITIVE_Z
#define GL_TEXTURE_CUBE_MAP_POSITIVE_Z 0x8519
#endif
#ifndef GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
#define GL_TEXTURE_CUBE_MAP_NEGATIVE_Z 0x851A
#endif
#ifndef GL_TEXTURE_CUBE_MAP_SEAMLESS
#define GL_TEXTURE_CUBE_MAP_SEAMLESS 0x884F
#endif
#ifndef GL_PRIMITIVE_RESTART_FIXED_INDEX
#define GL_PRIMITIVE_RESTART_FIXED_INDEX 0x8D69
#endif
#ifndef GL_RASTERIZER_DISCARD
#define GL_RASTERIZER_DISCARD 0x8C89
#endif
#ifndef GL_TRANSFORM_FEEDBACK
#define GL_TRANSFORM_FEEDBACK 0x8E22
#endif
#ifndef GL_TRANSFORM_FEEDBACK_BUFFER
#define GL_TRANSFORM_FEEDBACK_BUFFER 0x8C8E
#endif
#ifndef GL_INTERLEAVED_ATTRIBS
#define GL_INTERLEAVED_ATTRIBS 0x8C8C
#endif
#ifndef GL_UNIFORM_BUFFER
#define GL_UNIFORM_BUFFER 0x8A11
#endif
#ifndef GL_MAP_WRITE_BIT
#define GL_MAP_WRITE_BIT 0x0002
#endif
#ifndef GL_MAP_INVALIDATE_BUFFER_BIT
#define GL_MAP_INVALIDATE_BUFFER_BIT 0x0008
#endif
#ifndef GL_MAX_SAMPLES
#define GL_MAX_SAMPLES 0x8D57
#endif
#ifndef GL_MULTISAMPLE
#define GL_MULTISAMPLE 0x809D
#endif
#ifndef GL_CLAMP_TO_EDGE
#define GL_CLAMP_TO_EDGE 0x812F
#endif
#ifndef GL_TEXTURE_WRAP_R
#define GL_TEXTURE_WRAP_R 0x8072
#endif
#ifndef GL_MIRRORED_REPEAT
#define GL_MIRRORED_REPEAT 0x8370
#endif
#ifndef GL_VERTEX_SHADER
#define GL_VERTEX_SHADER 0x8B31
#endif
#ifndef GL_FRAGMENT_SHADER
#define GL_FRAGMENT_SHADER 0x8B30
#endif
#ifndef GL_COMPILE_STATUS
#define GL_COMPILE_STATUS 0x8B81
#endif
#ifndef GL_LINK_STATUS
#define GL_LINK_STATUS 0x8B82
#endif
#ifndef GL_INFO_LOG_LENGTH
#define GL_INFO_LOG_LENGTH 0x8B84
#endif
#ifndef GL_TEXTURE0
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE1 0x84C1
#define GL_TEXTURE2 0x84C2
#define GL_TEXTURE3 0x84C3
#define GL_TEXTURE4 0x84C4
#define GL_TEXTURE5 0x84C5
#define GL_TEXTURE6 0x84C6
#define GL_TEXTURE7 0x84C7
#endif
#ifndef GL_TEXTURE_BASE_LEVEL
#define GL_TEXTURE_BASE_LEVEL 0x813C
#endif
#ifndef GL_TEXTURE_MAX_LEVEL
#define GL_TEXTURE_MAX_LEVEL 0x813D
#endif
#ifndef GL_TEXTURE_MIN_LOD
#define GL_TEXTURE_MIN_LOD 0x813A
#endif
#ifndef GL_TEXTURE_MAX_LOD
#define GL_TEXTURE_MAX_LOD 0x813B
#endif
#ifndef GL_TEXTURE_LOD_BIAS
#define GL_TEXTURE_LOD_BIAS 0x8501
#endif
#ifndef GL_BGRA
#define GL_BGRA 0x80E1
#endif
#ifndef GL_SAMPLES
#define GL_SAMPLES 0x80A9
#endif
#ifndef GL_STENCIL_ATTACHMENT
#define GL_STENCIL_ATTACHMENT 0x8D20
#endif
#ifndef GL_DEPTH_COMPONENT16
#define GL_DEPTH_COMPONENT16 0x81A5
#endif
#ifndef GL_MAP_READ_BIT
#define GL_MAP_READ_BIT 0x0001
#endif
#ifndef GL_MAP_UNSYNCHRONIZED_BIT
#define GL_MAP_UNSYNCHRONIZED_BIT 0x0020
#endif
#ifndef GL_MAX_DRAW_BUFFERS
#define GL_MAX_DRAW_BUFFERS 0x8824
#endif
#ifndef GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN
#define GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN 0x8C85
#endif

// ---- function typedefs + extern declarations (X-macro keeps them in sync) ----
// NOTE: GL 1.1 functions exported by opengl32.dll and already declared by the
// SDK header (glDrawArrays/glDrawElements/glGenTextures/glDeleteTextures/
// glBindTexture/glTexImage2D/glTexParameteri) are called directly and are NOT
// listed here - redeclaring them as pointers is a redefinition error on MSVC.
#define GL_FUNCTIONS(X)                                                                              \
    X(glGenVertexArrays,          void,     (GLsizei n, GLuint* arrays))                             \
    X(glBindVertexArray,          void,     (GLuint array))                                          \
    X(glDeleteVertexArrays,       void,     (GLsizei n, const GLuint* arrays))                       \
    X(glGenBuffers,               void,     (GLsizei n, GLuint* buffers))                            \
    X(glDeleteBuffers,            void,     (GLsizei n, const GLuint* buffers))                      \
    X(glBindBuffer,               void,     (GLenum target, GLuint buffer))                          \
    X(glBufferData,               void,     (GLenum target, GLsizeiptr size, const void* data, GLenum usage)) \
    X(glBufferSubData,            void,     (GLenum target, GLintptr offset, GLsizeiptr size, const void* data)) \
    X(glMapBufferRange,           void*,    (GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access)) \
    X(glUnmapBuffer,              GLboolean,(GLenum target))                                         \
    X(glBindBufferBase,           void,     (GLenum target, GLuint index, GLuint buffer))            \
    X(glCreateShader,             GLuint,   (GLenum type))                                           \
    X(glShaderSource,             void,     (GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length)) \
    X(glCompileShader,            void,     (GLuint shader))                                         \
    X(glGetShaderiv,              void,     (GLuint shader, GLenum pname, GLint* params))            \
    X(glGetShaderInfoLog,         void,     (GLuint shader, GLsizei bufSize, GLsizei* length, GLchar* infoLog)) \
    X(glDeleteShader,             void,     (GLuint shader))                                         \
    X(glCreateProgram,            GLuint,   (void))                                                  \
    X(glAttachShader,             void,     (GLuint program, GLuint shader))                         \
    X(glLinkProgram,              void,     (GLuint program))                                        \
    X(glGetProgramiv,             void,     (GLuint program, GLenum pname, GLint* params))           \
    X(glGetProgramInfoLog,        void,     (GLuint program, GLsizei bufSize, GLsizei* length, GLchar* infoLog)) \
    X(glDeleteProgram,            void,     (GLuint program))                                        \
    X(glUseProgram,               void,     (GLuint program))                                        \
    X(glGetUniformLocation,       GLint,    (GLuint program, const GLchar* name))                    \
    X(glUniform1f,                void,     (GLint location, GLfloat v0))                            \
    X(glUniform2f,                void,     (GLint location, GLfloat v0, GLfloat v1))                \
    X(glUniform3f,                void,     (GLint location, GLfloat v0, GLfloat v1, GLfloat v2))    \
    X(glUniform4f,                void,     (GLint location, GLfloat v0, GLfloat v1, GLfloat v2, GLfloat v3)) \
    X(glUniform1i,                void,     (GLint location, GLint v0))                              \
    X(glUniform1fv,               void,     (GLint location, GLsizei count, const GLfloat* value))   \
    X(glUniformMatrix4fv,         void,     (GLint location, GLsizei count, GLboolean transpose, const GLfloat* value)) \
    X(glGetUniformBlockIndex,     GLuint,   (GLuint program, const GLchar* uniformBlockName))      \
    X(glUniformBlockBinding,      void,     (GLuint program, GLuint uniformBlockIndex, GLuint uniformBlockBinding)) \
    X(glVertexAttribPointer,      void,     (GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void* pointer)) \
    X(glEnableVertexAttribArray,  void,     (GLuint index))                                          \
    X(glVertexAttribDivisor,      void,     (GLuint index, GLuint divisor))                          \
    X(glDrawArraysInstanced,      void,     (GLenum mode, GLint first, GLsizei count, GLsizei instanceCount)) \
    X(glDrawElementsInstanced,    void,     (GLenum mode, GLsizei count, GLenum type, const void* indices, GLsizei instanceCount)) \
    X(glActiveTexture,            void,     (GLenum texture))                                        \
    X(glTexImage3D,               void,     (GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const void* pixels)) \
    X(glGenerateMipmap,           void,     (GLenum target))                                         \
    X(glGenFramebuffers,          void,     (GLsizei n, GLuint* framebuffers))                       \
    X(glDeleteFramebuffers,       void,     (GLsizei n, const GLuint* framebuffers))                 \
    X(glBindFramebuffer,          void,     (GLenum target, GLuint framebuffer))                     \
    X(glFramebufferTexture2D,     void,     (GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level)) \
    X(glGenRenderbuffers,         void,     (GLsizei n, GLuint* renderbuffers))                      \
    X(glDeleteRenderbuffers,      void,     (GLsizei n, const GLuint* renderbuffers))                \
    X(glBindRenderbuffer,         void,     (GLenum target, GLuint renderbuffer))                    \
    X(glRenderbufferStorage,          void, (GLenum target, GLenum internalformat, GLsizei width, GLsizei height)) \
    X(glRenderbufferStorageMultisample, void,(GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height)) \
    X(glFramebufferRenderbuffer,  void,     (GLenum target, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer)) \
    X(glCheckFramebufferStatus,   GLenum,   (GLenum target))                                         \
    X(glBlitFramebuffer,          void,     (GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter)) \
    X(glDrawBuffers,              void,     (GLsizei n, const GLenum* bufs))                         \
    X(glTransformFeedbackVaryings,void,     (GLuint program, GLsizei count, const GLchar* const* varyings, GLenum bufferMode)) \
    X(glGenTransformFeedbacks,    void,     (GLsizei n, GLuint* ids))                                \
    X(glDeleteTransformFeedbacks, void,     (GLsizei n, const GLuint* ids))                          \
    X(glBindTransformFeedback,    void,     (GLenum target, GLuint id))                              \
    X(glBeginTransformFeedback,   void,     (GLenum primitiveMode))                                  \
    X(glEndTransformFeedback,     void,     (void))                                                  \
    X(glBlendEquation,            void,     (GLenum mode))

#define GL_DECL_FN(name, ret, args) typedef ret (APIENTRY *PFN_##name) args;
#define GL_DECL_EXT(name, ret, args) extern PFN_##name name;
#define GL_DECL_DEF(name, ret, args) PFN_##name name = nullptr;

GL_FUNCTIONS(GL_DECL_FN)          // typedefs
GL_FUNCTIONS(GL_DECL_EXT)         // extern declarations

// Load every function above via wglGetProcAddress. Call once after wglMakeCurrent.
// Returns false (and prints which one is missing) when the driver is too old.
bool loadGLFunctions();

// Print pending GL errors to console (debug helper).
void glCheckError(const char* where);
