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
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.meshforge.api.Packers;
import org.meshforge.api.Pipelines;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.pack.packer.MeshPacker;

import java.io.File;

public final class MeshViewerApp extends Application {
    private static final String[] SUPPORTED_EXT = new String[] {"*.obj", "*.stl", "*.ply", "*.off"};
    private static final double TARGET_RADIUS = 2.0;
    private static final double DEFAULT_CAMERA_Z = -8.0;
    private static final double MIN_CAMERA_Z = -0.8;
    private static final double MAX_CAMERA_Z = -300.0;

    private final Group world = new Group();
    private final Rotate rotX = new Rotate(-25, Rotate.X_AXIS);
    private final Rotate rotY = new Rotate(30, Rotate.Y_AXIS);
    private PerspectiveCamera camera;
    private Label status;

    private double dragStartX;
    private double dragStartY;
    private double startRotX;
    private double startRotY;

    @Override
    public void start(Stage stage) {
        world.getTransforms().addAll(rotX, rotY);

        status = new Label("Open a mesh file to view");
        Button openButton = new Button("Open Mesh");
        openButton.setOnAction(e -> openMesh(stage));

        ToolBar toolBar = new ToolBar(openButton, status);
        BorderPane root = new BorderPane();
        root.setTop(toolBar);
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
        AmbientLight ambient = new AmbientLight(Color.color(0.55, 0.55, 0.60));
        PointLight key = new PointLight(Color.color(1.0, 1.0, 1.0));
        key.setTranslateX(-6.0);
        key.setTranslateY(-5.0);
        key.setTranslateZ(-6.0);
        root3d.getChildren().addAll(ambient, key);

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
            mesh = Pipelines.realtimeFast(mesh);

            // Ensure pack path remains usable from UI flow.
            MeshPacker.pack(mesh, Packers.realtime());

            MeshView view = new MeshView(MeshFxBridge.toTriangleMesh(mesh));
            view.setMaterial(new PhongMaterial(Color.rgb(210, 218, 232)));
            // Show both winding directions to reduce "invisible mesh" cases from mixed winding.
            view.setCullFace(CullFace.NONE);
            view.setDrawMode(DrawMode.FILL);
            applyFraming(mesh, view);

            world.getChildren().setAll(view);
            int indexCount = mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length;
            int triangleCount = indexCount / 3;
            float radius = mesh.boundsOrNull() == null || mesh.boundsOrNull().sphere() == null
                ? Float.NaN
                : mesh.boundsOrNull().sphere().radius();
            status.setText(file.getName() + " | vertices=" + mesh.vertexCount() +
                " triangles=" + triangleCount + " indices=" + indexCount +
                " radius=" + String.format("%.4f", radius));
        } catch (Exception ex) {
            status.setText("Load failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void applyFraming(org.meshforge.core.mesh.MeshData mesh, MeshView view) {
        var bounds = mesh.boundsOrNull();
        if (bounds == null || bounds.sphere() == null) {
            return;
        }

        float cx = bounds.sphere().centerX();
        float cy = bounds.sphere().centerY();
        float cz = bounds.sphere().centerZ();
        float radius = Math.max(bounds.sphere().radius(), 1.0e-6f);

        double scale = TARGET_RADIUS / radius;
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setScaleZ(scale);
        view.setTranslateX(-cx * scale);
        view.setTranslateY(-cy * scale);
        view.setTranslateZ(-cz * scale);
        world.setTranslateX(0.0);
        world.setTranslateY(0.0);

        if (camera != null) {
            camera.setTranslateZ(DEFAULT_CAMERA_Z);
            camera.setNearClip(0.01);
            camera.setFarClip(100_000.0);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
