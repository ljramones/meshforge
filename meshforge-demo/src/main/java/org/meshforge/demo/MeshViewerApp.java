package org.meshforge.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.meshforge.api.Ops;
import org.meshforge.api.Packers;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.ops.pipeline.MeshOp;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.packer.MeshPacker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MeshViewerApp extends Application {
    private static final String[] SUPPORTED_EXT = new String[] {"*.obj", "*.stl", "*.ply", "*.off"};

    private final Group world = new Group();
    private final Rotate rotX = new Rotate(-25, Rotate.X_AXIS);
    private final Rotate rotY = new Rotate(30, Rotate.Y_AXIS);
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
        SubScene sub = new SubScene(root3d, 1000, 700, true, null);
        sub.setFill(Color.rgb(28, 31, 38));

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.01);
        camera.setFarClip(10_000.0);
        camera.setTranslateZ(-8.0);
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
            rotY.setAngle(startRotY + dx * 0.35);
            rotX.setAngle(startRotX - dy * 0.35);
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
            List<MeshOp> ops = new ArrayList<>();
            ops.add(Ops.validate());
            ops.add(Ops.removeDegenerates());
            ops.add(Ops.weld(1.0e-6f));
            ops.add(Ops.normals(60f));
            if (mesh.has(AttributeSemantic.UV, 0)) {
                ops.add(Ops.tangents());
            }
            ops.add(Ops.optimizeVertexCache());
            ops.add(Ops.bounds());
            mesh = MeshPipeline.run(mesh, ops.toArray(MeshOp[]::new));

            // Ensure pack path remains usable from UI flow.
            MeshPacker.pack(mesh, Packers.realtime());

            MeshView view = new MeshView(MeshFxBridge.toTriangleMesh(mesh));
            view.setMaterial(new PhongMaterial(Color.rgb(210, 218, 232)));
            view.setCullFace(CullFace.BACK);
            view.setDrawMode(DrawMode.FILL);

            world.getChildren().setAll(view);
            status.setText(file.getName() + " | vertices=" + mesh.vertexCount() +
                " indices=" + (mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length));
        } catch (Exception ex) {
            status.setText("Load failed: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
