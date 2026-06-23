package com.lowdragmc.kilagraphdemo.client.model;

import com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewMeshBuilder;
import com.lowdragmc.kilagraph.rendertype.preview.PreviewVertex;
import net.minecraft.network.chat.Component;

/**
 * Builds the hologram's display geometry — quad / cube / sphere with a user-chosen subdivision count —
 * as {@link KGPreviewContent}s (the format-agnostic mesh KilaGraph's {@code PreviewRenderer} draws).
 * KilaGraph's built-in {@code KGPreviewContents} are fixed-resolution; these let a work pick how finely
 * its model is tessellated (more, smaller triangles), per requirement #6.
 *
 * <p>Geometry conventions match {@code KGPreviewContents}: unit shapes centred at the origin, faces CCW
 * from outside, uv 0..1.</p>
 */
public final class SubdividedContents {

    /** Supported primitive kinds (the {@code contentKey} stored in a work's model selection). */
    public static final String QUAD = "quad";
    public static final String CUBE = "cube";
    public static final String SPHERE = "sphere";

    private SubdividedContents() {
    }

    /** A content for {@code key} ({@link #QUAD}/{@link #CUBE}/{@link #SPHERE}) at the given subdivision. */
    public static KGPreviewContent of(String key, int subdivisions) {
        int sub = Math.max(1, subdivisions);
        return switch (key) {
            case QUAD -> content(QUAD, "kilagraphdemo.ui.editor.kind_quad", mb -> quad(mb, sub));
            case SPHERE -> content(SPHERE, "kilagraphdemo.ui.editor.kind_sphere", mb -> sphere(mb, sub));
            default -> content(CUBE, "kilagraphdemo.ui.editor.kind_cube", mb -> cube(mb, sub));
        };
    }

    private interface Builder {
        void build(PreviewMeshBuilder mb);
    }

    private static KGPreviewContent content(String key, String label, Builder builder) {
        return new KGPreviewContent() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public Component title() {
                return Component.translatable(label);
            }

            @Override
            public void build(PreviewMeshBuilder mb) {
                builder.build(mb);
            }
        };
    }

    // ---- geometry ----------------------------------------------------------------------------

    /** Flat unit quad in the XY plane (z=0), facing +Z, subdivided {@code n}×{@code n}. */
    private static void quad(PreviewMeshBuilder mb, int n) {
        face(mb,
                new float[]{-0.5f, -0.5f, 0f}, new float[]{0.5f, -0.5f, 0f},
                new float[]{0.5f, 0.5f, 0f}, new float[]{-0.5f, 0.5f, 0f},
                new float[]{0f, 0f, 1f}, n);
    }

    /** Unit cube centred at the origin; each of the 6 faces subdivided {@code n}×{@code n}. */
    private static void cube(PreviewMeshBuilder mb, int n) {
        float[][] c = {
                {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
                {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f}
        };
        int[][] faces = {{1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {4, 0, 1, 5}, {3, 7, 6, 2}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            face(mb, c[q[0]], c[q[1]], c[q[2]], c[q[3]], normals[f], n);
        }
    }

    /**
     * A quad face (corners CCW: a=uv(0,0), b=uv(1,0), cc=uv(1,1), d=uv(0,1)) subdivided into {@code n}×
     * {@code n} cells by bilinear interpolation of the corner positions.
     */
    private static void face(PreviewMeshBuilder mb, float[] a, float[] b, float[] cc, float[] d, float[] nrm, int n) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float s0 = (float) i / n, s1 = (float) (i + 1) / n;
                float t0 = (float) j / n, t1 = (float) (j + 1) / n;
                mb.quad(
                        corner(a, b, cc, d, nrm, s0, t0),
                        corner(a, b, cc, d, nrm, s1, t0),
                        corner(a, b, cc, d, nrm, s1, t1),
                        corner(a, b, cc, d, nrm, s0, t1));
            }
        }
    }

    /** Bilinear corner at parameter (s,t) over the quad a,b,cc,d, with uv=(s,t). */
    private static PreviewVertex corner(float[] a, float[] b, float[] cc, float[] d, float[] nrm, float s, float t) {
        float x = bilerp(a[0], b[0], cc[0], d[0], s, t);
        float y = bilerp(a[1], b[1], cc[1], d[1], s, t);
        float z = bilerp(a[2], b[2], cc[2], d[2], s, t);
        return new PreviewVertex(x, y, z, s, t, nrm[0], nrm[1], nrm[2]);
    }

    private static float bilerp(float a, float b, float cc, float d, float s, float t) {
        float bottom = a + (b - a) * s; // a->b along s at t=0
        float top = d + (cc - d) * s;   // d->cc along s at t=1
        return bottom + (top - bottom) * t;
    }

    /**
     * UV sphere (radius 0.5). Tessellation scales with {@code sub} as a quality knob: {@code 4*sub} latitude
     * rings and {@code 8*sub} longitude segments (so the default {@code sub=4} is a smooth 16×32 sphere, not a
     * coarse polyhedron). The minimums keep even {@code sub=1} recognizably round-ish.
     */
    private static void sphere(PreviewMeshBuilder mb, int sub) {
        int rings = Math.max(4, sub * 4);
        int segments = Math.max(8, sub * 8);
        float r = 0.5f;
        PreviewVertex[][] grid = new PreviewVertex[rings + 1][segments + 1];
        for (int i = 0; i <= rings; i++) {
            double phi = Math.PI * i / rings;
            double sinPhi = Math.sin(phi), cosPhi = Math.cos(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = 2 * Math.PI * j / segments;
                float nx = (float) (sinPhi * Math.cos(theta));
                float ny = (float) cosPhi;
                float nz = (float) (sinPhi * Math.sin(theta));
                grid[i][j] = new PreviewVertex(nx * r, ny * r, nz * r,
                        (float) j / segments, (float) i / rings, nx, ny, nz);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                PreviewVertex a = grid[i][j], b = grid[i][j + 1], cc = grid[i + 1][j + 1], d = grid[i + 1][j];
                if (i == 0) {
                    mb.tri(a, cc, d);
                } else if (i == rings - 1) {
                    mb.tri(a, b, cc);
                } else {
                    mb.quad(a, b, cc, d);
                }
            }
        }
    }
}
