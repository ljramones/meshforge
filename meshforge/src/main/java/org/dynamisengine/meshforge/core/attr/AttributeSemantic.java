package org.dynamisengine.meshforge.core.attr;

/**
 * Standard attribute semantics. "setIndex" is used for UV0/UV1, COLOR0/COLOR1, etc.
 */
public enum AttributeSemantic {
    /** Vertex position. */
    POSITION,
    /** Vertex normal. */
    NORMAL,
    /** Vertex tangent. */
    TANGENT,
    /** Vertex bitangent (optional; often derived). */
    BITANGENT,
    /** Texture coordinates. */
    UV,
    /** Vertex color. */
    COLOR,
    /** Skinning joint indices. */
    JOINTS,
    /** Skinning joint weights. */
    WEIGHTS,
    /** Application-defined semantic. */
    CUSTOM
}
