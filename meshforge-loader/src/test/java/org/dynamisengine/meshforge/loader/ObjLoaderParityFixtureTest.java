package org.dynamisengine.meshforge.loader;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjLoaderParityFixtureTest {
    @TestFactory
    List<DynamicTest> fastAndLegacyParityOnBaselineFixtures() throws IOException {
        Path fixtureDir = resolveFixtureDir();
        Assumptions.assumeTrue(Files.isDirectory(fixtureDir), "fixtures/baseline directory not found");

        List<Path> fixtures = Files.list(fixtureDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".obj"))
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
            .toList();

        Assumptions.assumeTrue(!fixtures.isEmpty(), "No baseline OBJ fixtures found");

        MeshLoaders legacy = MeshLoaders.defaultsLegacy();
        MeshLoaders fast = MeshLoaders.defaultsFast();
        List<DynamicTest> tests = new ArrayList<>();
        for (Path fixture : fixtures) {
            tests.add(DynamicTest.dynamicTest("Parity " + fixture.getFileName(), () -> {
                var l = legacy.load(fixture);
                var f = fast.load(fixture);

                assertEquals(l.vertexCount(), f.vertexCount(), "vertexCount");
                assertEquals(indexCount(l), indexCount(f), "indexCount");

                int[] li = l.indicesOrNull();
                int[] fi = f.indicesOrNull();
                if (li == null || fi == null) {
                    assertEquals(li, fi, "indices presence");
                } else {
                    assertTrue(Arrays.equals(li, fi), "index buffer mismatch");
                }

                float[] lp = l.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
                float[] fp = f.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
                assertNotNull(lp, "legacy POSITION");
                assertNotNull(fp, "fast POSITION");
                assertEquals(lp.length, fp.length, "position length");
                for (int i = 0; i < lp.length; i++) {
                    assertTrue(Math.abs(lp[i] - fp[i]) <= 1.0e-4f, "position mismatch at " + i);
                }

                float[] lb = boundsFromPositions(lp);
                float[] fb = boundsFromPositions(fp);
                for (int i = 0; i < lb.length; i++) {
                    assertTrue(Math.abs(lb[i] - fb[i]) <= 1.0e-4f, "bounds mismatch at component " + i);
                }
            }));
        }
        return tests;
    }

    private static int indexCount(org.dynamisengine.meshforge.core.mesh.MeshData mesh) {
        return mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length;
    }

    private static float[] boundsFromPositions(float[] p) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < p.length; i += 3) {
            float x = p[i];
            float y = p[i + 1];
            float z = p[i + 2];
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }
        return new float[] {minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static Path resolveFixtureDir() {
        Path direct = Path.of("fixtures", "baseline");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("..", "fixtures", "baseline");
    }
}
