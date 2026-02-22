package org.meshforge.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.AmbientLight;
import javafx.scene.PointLight;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.meshforge.api.Packers;
import org.meshforge.api.Ops;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.packer.MeshPacker;

import java.io.File;
import java.util.Arrays;

public final class MeshViewerApp extends Application {
    private static final String[] SUPPORTED_EXT = new String[] {"*.obj", "*.stl", "*.ply", "*.off"};
    private static final double TARGET_RADIUS = 2.0;
    private static final double DEFAULT_ROT_X = -25.0;
    private static final double DEFAULT_ROT_Y = 30.0;
    private static final double DEFAULT_CAMERA_Z = -8.0;
    private static final double MIN_CAMERA_Z = -0.8;
    private static final double MAX_CAMERA_Z = -300.0;
    private static final boolean DEFAULT_SHOW_WIREFRAME = false;
    private static final boolean DEFAULT_SHOW_DEBUG = false;

    private final Group world = new Group();
    private final Rotate rotX = new Rotate(DEFAULT_ROT_X, Rotate.X_AXIS);
    private final Rotate rotY = new Rotate(DEFAULT_ROT_Y, Rotate.Y_AXIS);
    private PerspectiveCamera camera;
    private Label status;
    private Label debugStatus;
    private boolean showWireframe = DEFAULT_SHOW_WIREFRAME;
    private boolean showDebug = DEFAULT_SHOW_DEBUG;
    private org.meshforge.core.mesh.MeshData currentMesh;
    private String currentFileName;

    private double dragStartX;
    private double dragStartY;
    private double startRotX;
    private double startRotY;

    @Override
    public void start(Stage stage) {
        world.getTransforms().addAll(rotX, rotY);

        status = new Label("Open a mesh file to view");
        debugStatus = new Label("");
        debugStatus.setVisible(false);
        debugStatus.setManaged(false);

        Button openButton = new Button("Open Mesh");
        openButton.setOnAction(e -> openMesh(stage));
        ToggleButton wireframeToggle = new ToggleButton("Wireframe");
        wireframeToggle.setSelected(showWireframe);
        wireframeToggle.setOnAction(e -> {
            showWireframe = wireframeToggle.isSelected();
            rerenderCurrentMesh();
        });
        ToggleButton debugToggle = new ToggleButton("Debug");
        debugToggle.setSelected(showDebug);
        debugToggle.setOnAction(e -> {
            showDebug = debugToggle.isSelected();
            debugStatus.setVisible(showDebug);
            debugStatus.setManaged(showDebug);
            rerenderCurrentMesh();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar toolBar = new ToolBar(openButton, wireframeToggle, debugToggle, spacer, status);
        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setBottom(debugStatus);
        root.setPadding(new Insets(8));

        SubScene subScene = createViewport();
        root.setCenter(subScene);

        Scene scene = new Scene(root, 1100, 760, true);
        stage.setTitle("MeshForge Demo Viewer");
        stage.setScene(scene);
        stage.show();
    }

    private SubScene createViewport() {
        Group root3d = new Group(world);
        AmbientLight ambient = new AmbientLight(Color.color(0.28, 0.30, 0.34));
        PointLight key = new PointLight(Color.color(1.0, 1.0, 1.0));
        key.setTranslateX(-8.0);
        key.setTranslateY(-6.0);
        key.setTranslateZ(-8.0);
        PointLight fill = new PointLight(Color.color(0.38, 0.42, 0.50));
        fill.setTranslateX(10.0);
        fill.setTranslateY(4.0);
        fill.setTranslateZ(-3.0);
        PointLight rim = new PointLight(Color.color(0.22, 0.24, 0.30));
        rim.setTranslateX(0.0);
        rim.setTranslateY(-4.0);
        rim.setTranslateZ(10.0);
        root3d.getChildren().addAll(ambient, key, fill, rim);

        SubScene sub = new SubScene(root3d, 1000, 700, true, null);
        sub.setFill(Color.rgb(28, 31, 38));

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.01);
        camera.setFarClip(10_000.0);
        camera.setTranslateZ(DEFAULT_CAMERA_Z);
        sub.setCamera(camera);

        sub.widthProperty().addListener((obs, oldV, newV) -> {});
        sub.heightProperty().addListener((obs, oldV, newV) -> {});

        sub.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            startRotX = rotX.getAngle();
            startRotY = rotY.getAngle();
        });

        sub.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            double dx = e.getSceneX() - dragStartX;
            double dy = e.getSceneY() - dragStartY;
            if (e.isPrimaryButtonDown()) {
                rotY.setAngle(startRotY + dx * 0.35);
                rotX.setAngle(startRotX - dy * 0.35);
            } else if (e.isSecondaryButtonDown()) {
                double panScale = Math.max(0.001, -camera.getTranslateZ() * 0.0012);
                world.setTranslateX(world.getTranslateX() + dx * panScale);
                world.setTranslateY(world.getTranslateY() + dy * panScale);
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();
            }
        });

        sub.addEventHandler(ScrollEvent.SCROLL, e -> {
            double zoomFactor = Math.exp(e.getDeltaY() * 0.0015);
            double nextZ = camera.getTranslateZ() / zoomFactor;
            camera.setTranslateZ(clamp(nextZ, MAX_CAMERA_Z, MIN_CAMERA_Z));
        });

        return sub;
    }

    private void openMesh(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Mesh File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Mesh Files (*.obj, *.stl, *.ply, *.off)", SUPPORTED_EXT
        ));

        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            var mesh = MeshLoaders.defaults().load(file.toPath());
            mesh = ensureConsistentWinding(mesh);
            // For viewer consistency, always regenerate normals instead of trusting source data.
            mesh = MeshPipeline.run(
                mesh,
                Ops.validate(),
                Ops.removeDegenerates(),
                Ops.normals(60f),
                Ops.bounds()
            );
            currentMesh = mesh;
            currentFileName = file.getName();
            renderCurrentMesh();
        } catch (Exception ex) {
            status.setText("Load failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            debugStatus.setText("Load exception: " + ex);
            ex.printStackTrace(System.err);
        }
    }

    private void rerenderCurrentMesh() {
        if (currentMesh != null) {
            renderCurrentMesh();
        }
    }

    private void renderCurrentMesh() {
        try {
            var mesh = currentMesh;
            if (mesh == null) {
                return;
            }

            // Ensure pack path remains usable from UI flow.
            MeshPacker.pack(mesh, Packers.realtime());

            var fxMeshes = MeshFxBridge.toTriangleMeshes(mesh);
            Group meshGroup = new Group();
            for (var fxMesh : fxMeshes) {
                MeshView view = new MeshView(fxMesh);
                PhongMaterial material = new PhongMaterial(Color.rgb(178, 188, 202));
                material.setSpecularColor(Color.rgb(64, 70, 80));
                material.setSpecularPower(24.0);
                view.setMaterial(material);
                // Show both winding directions to reduce "invisible mesh" cases from mixed winding.
                view.setCullFace(CullFace.NONE);
                view.setDrawMode(DrawMode.FILL);
                meshGroup.getChildren().add(view);

                if (showWireframe) {
                    MeshView wire = new MeshView(fxMesh);
                    wire.setMaterial(new PhongMaterial(Color.color(0.10, 0.10, 0.10)));
                    wire.setCullFace(CullFace.NONE);
                    wire.setDrawMode(DrawMode.LINE);
                    meshGroup.getChildren().add(wire);
                }
            }
            applyFraming(mesh, meshGroup);
            int indexCount = mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length;
            int triangleCount = indexCount / 3;
            float radius = mesh.boundsOrNull() == null || mesh.boundsOrNull().sphere() == null
                ? Float.NaN
                : mesh.boundsOrNull().sphere().radius();
            float viewRadius = estimateViewRadius(mesh);
            float[] center = estimateViewCenter(mesh);
            double scale = TARGET_RADIUS / Math.max(viewRadius, 1.0e-6f);
            world.getChildren().setAll(meshGroup);
            double camZ = camera == null ? Double.NaN : camera.getTranslateZ();

            status.setText(currentFileName + " | vertices=" + mesh.vertexCount() +
                " triangles=" + triangleCount + " indices=" + indexCount +
                " chunks=" + fxMeshes.size());
            debugStatus.setText(
                currentFileName +
                    " | radius=" + String.format("%.4f", radius) +
                    " viewRadius=" + String.format("%.4f", viewRadius) +
                    " center=(" + String.format("%.2f", center[0]) + "," +
                    String.format("%.2f", center[1]) + "," +
                    String.format("%.2f", center[2]) + ")" +
                    " scale=" + String.format("%.6f", scale) +
                    " camZ=" + String.format("%.3f", camZ)
            );
        } catch (Exception ex) {
            status.setText("Render failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            debugStatus.setText("Render exception: " + ex);
            ex.printStackTrace(System.err);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void applyFraming(org.meshforge.core.mesh.MeshData mesh, Group meshNode) {
        var bounds = mesh.boundsOrNull();
        if (bounds == null || bounds.sphere() == null) {
            return;
        }

        ViewFrame frame = estimateViewFrame(mesh);
        float cx = frame.centerX;
        float cy = frame.centerY;
        float cz = frame.centerZ;
        float radius = Math.max(frame.radius, 1.0e-6f);

        meshNode.setScaleX(1.0);
        meshNode.setScaleY(1.0);
        meshNode.setScaleZ(1.0);
        meshNode.setTranslateX(-cx);
        meshNode.setTranslateY(-cy);
        meshNode.setTranslateZ(-cz);
        world.setTranslateX(0.0);
        world.setTranslateY(0.0);
        rotX.setAngle(DEFAULT_ROT_X);
        rotY.setAngle(DEFAULT_ROT_Y);

        if (camera != null) {
            double halfFovRad = Math.toRadians(Math.max(1.0, camera.getFieldOfView()) * 0.5);
            double fitDistance = (radius / Math.tan(halfFovRad)) * 1.15;
            double nextZ = -Math.max(2.0, fitDistance);
            camera.setTranslateZ(nextZ);
            camera.setNearClip(0.01);
            camera.setFarClip(100_000.0);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float estimateViewRadius(org.meshforge.core.mesh.MeshData mesh) {
        return estimateViewFrame(mesh).radius;
    }

    private static float[] estimateViewCenter(org.meshforge.core.mesh.MeshData mesh) {
        ViewFrame f = estimateViewFrame(mesh);
        return new float[] {f.centerX, f.centerY, f.centerZ};
    }

    private static ViewFrame estimateViewFrame(org.meshforge.core.mesh.MeshData mesh) {
        var bounds = mesh.boundsOrNull();
        if (bounds == null || bounds.sphere() == null || !mesh.has(AttributeSemantic.POSITION, 0)) {
            return new ViewFrame(0.0f, 0.0f, 0.0f, 1.0f);
        }

        float fallbackCx = bounds.sphere().centerX();
        float fallbackCy = bounds.sphere().centerY();
        float fallbackCz = bounds.sphere().centerZ();
        float fallbackRadius = Math.max(bounds.sphere().radius(), 1.0e-6f);

        float[] positions = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        if (positions == null || positions.length < 3) {
            return new ViewFrame(fallbackCx, fallbackCy, fallbackCz, fallbackRadius);
        }

        int[] indices = mesh.indicesOrNull();
        int vertexCount = positions.length / 3;
        boolean[] usedVertices = new boolean[vertexCount];
        int usedCount = 0;
        if (indices != null && indices.length > 0) {
            for (int idx : indices) {
                if (idx >= 0 && idx < vertexCount && !usedVertices[idx]) {
                    usedVertices[idx] = true;
                    usedCount++;
                }
            }
        } else {
            Arrays.fill(usedVertices, true);
            usedCount = vertexCount;
        }

        if (usedCount == 0) {
            return new ViewFrame(fallbackCx, fallbackCy, fallbackCz, fallbackRadius);
        }

        int step = Math.max(1, usedCount / 4096);
        int sampleCount = (usedCount + step - 1) / step;
        float[] xs = new float[sampleCount];
        float[] ys = new float[sampleCount];
        float[] zs = new float[sampleCount];

        int s = 0;
        int seen = 0;
        for (int v = 0; v < vertexCount; v++) {
            if (!usedVertices[v]) {
                continue;
            }
            if ((seen % step) != 0) {
                seen++;
                continue;
            }
            seen++;
            int p = v * 3;
            float x = positions[p];
            float y = positions[p + 1];
            float z = positions[p + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                continue;
            }
            xs[s] = x;
            ys[s] = y;
            zs[s] = z;
            s++;
        }
        if (s == 0) {
            return new ViewFrame(fallbackCx, fallbackCy, fallbackCz, fallbackRadius);
        }
        Arrays.sort(xs, 0, s);
        Arrays.sort(ys, 0, s);
        Arrays.sort(zs, 0, s);
        float centerX = xs[(s - 1) / 2];
        float centerY = ys[(s - 1) / 2];
        float centerZ = zs[(s - 1) / 2];

        float[] distances = new float[s];
        for (int i = 0; i < s; i++) {
            float dx = xs[i] - centerX;
            float dy = ys[i] - centerY;
            float dz = zs[i] - centerZ;
            distances[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        Arrays.sort(distances);

        float p90 = distances[Math.max(0, (int) Math.floor((s - 1) * 0.90f))];
        float p95 = distances[Math.max(0, (int) Math.floor((s - 1) * 0.95f))];
        float p99 = distances[Math.max(0, (int) Math.floor((s - 1) * 0.99f))];
        float max = distances[s - 1];

        // If a few outliers dominate the radius, frame to p99 for visibility.
        if (p99 > 0.0f && max > p99 * 4.0f) {
            return new ViewFrame(centerX, centerY, centerZ, Math.max(p99, 1.0e-6f));
        }
        return new ViewFrame(centerX, centerY, centerZ, Math.max(p95 > 0.0f ? p95 : p90, 1.0e-6f));
    }

    private static final class ViewFrame {
        final float centerX;
        final float centerY;
        final float centerZ;
        final float radius;

        ViewFrame(float centerX, float centerY, float centerZ, float radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
        }
    }

    private static org.meshforge.core.mesh.MeshData ensureConsistentWinding(org.meshforge.core.mesh.MeshData mesh) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length < 3 || (indices.length % 3) != 0) {
            return mesh;
        }
        if (!mesh.has(AttributeSemantic.POSITION, 0)) {
            return mesh;
        }
        float[] pos = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        if (pos == null || pos.length < 3) {
            return mesh;
        }

        // Approximate signed volume for closed meshes. Negative suggests globally reversed winding.
        double signed6xVolume = 0.0;
        for (int i = 0; i < indices.length; i += 3) {
            int ia = indices[i] * 3;
            int ib = indices[i + 1] * 3;
            int ic = indices[i + 2] * 3;
            if (ia < 0 || ib < 0 || ic < 0 || ia + 2 >= pos.length || ib + 2 >= pos.length || ic + 2 >= pos.length) {
                continue;
            }

            double ax = pos[ia];
            double ay = pos[ia + 1];
            double az = pos[ia + 2];
            double bx = pos[ib];
            double by = pos[ib + 1];
            double bz = pos[ib + 2];
            double cx = pos[ic];
            double cy = pos[ic + 1];
            double cz = pos[ic + 2];

            double cpx = by * cz - bz * cy;
            double cpy = bz * cx - bx * cz;
            double cpz = bx * cy - by * cx;
            signed6xVolume += ax * cpx + ay * cpy + az * cpz;
        }

        // For near-zero/open meshes, keep as-is.
        if (Math.abs(signed6xVolume) < 1.0e-6) {
            return mesh;
        }
        if (signed6xVolume > 0.0) {
            return mesh;
        }

        int[] flipped = indices.clone();
        for (int i = 0; i < flipped.length; i += 3) {
            int t = flipped[i + 1];
            flipped[i + 1] = flipped[i + 2];
            flipped[i + 2] = t;
        }
        mesh.setIndices(flipped);
        return mesh;
    }
}
