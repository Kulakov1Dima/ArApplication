package com.example.iarsecuritysystem;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.common.helpers.CameraPermissionHelper;
import com.common.helpers.DisplayRotationHelper;
import com.common.helpers.FullScreenHelper;
import com.common.helpers.InstantPlacementSettings;
import com.common.helpers.TapHelper;
import com.common.helpers.TrackingStateHelper;
import com.common.samplerender.Framebuffer;
import com.common.samplerender.GLError;
import com.common.samplerender.Mesh;
import com.common.samplerender.SampleRender;
import com.common.samplerender.Shader;
import com.common.samplerender.Texture;
import com.common.samplerender.VertexBuffer;
import com.common.samplerender.arcore.BackgroundRenderer;
import com.common.samplerender.arcore.PlaneRenderer;
import com.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ARFrame extends AppCompatActivity implements SampleRender.Renderer {

    private static final float[] sphericalHarmonicFactors = {
            0.282095f,
            -0.325735f,
            0.325735f,
            -0.325735f,
            0.273137f,
            -0.273137f,
            0.078848f,
            -0.273137f,
            0.136569f,
    };

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;
    private GLSurfaceView surfaceView;
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;
    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;

    private SpecularCubemapFilter cubemapFilter;

    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

    // Point Cloud
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    private long lastPointCloudTimestamp = 0;
    // Virtual object (ARCore pawn)
    private Mesh virtualObjectMesh1;
    private Shader virtualObjectShader1;
    private Texture virtualObjectAlbedoTexture1;
    private Session session;

    private Mesh virtualObjectMesh2;
    private Shader virtualObjectShader2;
    private Texture virtualObjectAlbedoTexture2;

    private Mesh virtualObjectMesh3;
    private Shader virtualObjectShader3;
    private Texture virtualObjectAlbedoTexture3;

    private boolean installRequested = false;
    private boolean hasSetTextureNames = false;

    private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16]; // view x model
    private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4]; // view x world light direction
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;
    private Texture virtualObjectAlbedoInstantPlacementTexture;

    private int textureMode = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arframe);
        surfaceView = findViewById(R.id.surfaceview);

        displayRotationHelper = new DisplayRotationHelper(/* context= */ this);
        tapHelper = new TapHelper(/* context= */ this);
        surfaceView.setOnTouchListener(tapHelper);

        SampleRender render = new SampleRender(surfaceView, this, getAssets());
        instantPlacementSettings.onCreate(this);
        myCode();
    }

    private void myCode() {
        Button button1 = findViewById(R.id.button1);
        Button button2 = findViewById(R.id.button2);
        Button button3 = findViewById(R.id.button3);

        button1.setOnClickListener(v -> textureMode = 1);

        button2.setOnClickListener(v -> textureMode = 2);

        button3.setOnClickListener(v -> textureMode = 3);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }
    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, /* width= */ 1, /* height= */ 1);

            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
            // Load DFG lookup table for environmental lighting
            /* useMipmaps= */
            // Environmental HDR
            Texture dfgTexture = new Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    /* useMipmaps= */ false);
            // The dfg.raw file is a raw half-float texture with two channels.
            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer =
                    ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    /* level= */ 0,
                    GLES30.GL_RG16F,
                    /* width= */ dfgResolution,
                    /* height= */ dfgResolution,
                    /* border= */ 0,
                    GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT,
                    buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

            // Point cloud
            pointCloudShader =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/point_cloud.vert",
                                    "shaders/point_cloud.frag",
                                    /* defines= */ null)
                            .setVec4(
                                    "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                            .setFloat("u_PointSize", 5.0f);
            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                    new VertexBuffer(render, /* numberOfEntriesPerVertex= */ 4, /* entries= */ null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh =
                    new Mesh(
                            render, Mesh.PrimitiveMode.POINTS, /* indexBuffer= */ null, pointCloudVertexBuffers);

            String png1 = "models/pc.png";
            virtualObjectAlbedoTexture1 =
                    Texture.createFromAsset(
                            render,
                            png1,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            String png2 = "models/pawn_albedo.png";
            virtualObjectAlbedoTexture2 =
                    Texture.createFromAsset(
                            render,
                            png2,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            String png3 = "models/Cube.png";
            virtualObjectAlbedoTexture3 =
                    Texture.createFromAsset(
                            render,
                            png3,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);

            String obj1 = "models/pc.obj";
            virtualObjectMesh1 = Mesh.createFromAsset(render, obj1);
            String obj2 = "models/pawn.obj";
            virtualObjectMesh2 = Mesh.createFromAsset(render, obj2);
            String obj3 = "models/untitled.obj";
            virtualObjectMesh3 = Mesh.createFromAsset(render, obj3);

            virtualObjectShader1 =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/environmental_hdr.vert",
                                    "shaders/environmental_hdr.frag",
                                    new HashMap<String, String>() {
                                        {
                                            put(
                                                    "NUMBER_OF_MIPMAP_LEVELS",
                                                    Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture1)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);

            virtualObjectShader2 =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/environmental_hdr.vert",
                                    "shaders/environmental_hdr.frag",
                                    new HashMap<String, String>() {
                                        {
                                            put(
                                                    "NUMBER_OF_MIPMAP_LEVELS",
                                                    Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture2)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);
            virtualObjectShader3 =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/environmental_hdr.vert",
                                    "shaders/environmental_hdr.frag",
                                    new HashMap<String, String>() {
                                        {
                                            put(
                                                    "NUMBER_OF_MIPMAP_LEVELS",
                                                    Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture3)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            //messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        }

    }
    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }
    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) {
            return;
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        // Obtain the current frame from the AR Session. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            //messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            return;
        }
        Camera camera = frame.getCamera();

        // Update BackgroundRenderer state to match the depth settings.
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, false);
            backgroundRenderer.setUseOcclusion(render, false);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            //messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
            return;
        }
        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING) {
            try (Image depthImage = frame.acquireDepthImage16Bits()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }

        // Handle one tap per frame.
        handleTap(frame, camera);

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
        // -- Draw background

        if (frame.getTimestamp() != 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render);
        }

        // If not tracking, don't draw 3D objects.
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }
        // -- Draw non-occluded virtual objects (planes, point cloud)

        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0);

        // Visualize tracked points.
        // Use try-with-resources to automatically release the point cloud.
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.getPoints());
                lastPointCloudTimestamp = pointCloud.getTimestamp();
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            render.draw(pointCloudMesh, pointCloudShader);
        }

        // Visualize planes.
        planeRenderer.drawPlanes(
                render,
                session.getAllTrackables(Plane.class),
                camera.getDisplayOrientedPose(),
                projectionMatrix);

        // -- Draw occluded virtual objects

        // Update lighting parameters in the shader
        updateLightEstimation(frame.getLightEstimate(), viewMatrix);

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
        for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
            Anchor anchor = wrappedAnchor.getAnchor();
            Trackable trackable = wrappedAnchor.getTrackable();
            if (anchor.getTrackingState() != TrackingState.TRACKING) {
                continue;
            }

            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to session.update() as ARCore refines its estimate of the world.
            anchor.getPose().toMatrix(modelMatrix, 0);

            // Calculate model/view/projection matrices
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

            // Update shader properties and draw
            virtualObjectShader1.setMat4("u_ModelView", modelViewMatrix);
            virtualObjectShader1.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            virtualObjectShader2.setMat4("u_ModelView", modelViewMatrix);
            virtualObjectShader2.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            virtualObjectShader3.setMat4("u_ModelView", modelViewMatrix);
            virtualObjectShader3.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            if (trackable instanceof InstantPlacementPoint
                    && ((InstantPlacementPoint) trackable).getTrackingMethod()
                    == InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
                virtualObjectShader1.setTexture(
                        "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
                virtualObjectShader2.setTexture(
                        "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
                virtualObjectShader3.setTexture(
                        "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
            } else {
                virtualObjectShader1.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture1);
                virtualObjectShader2.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture2);
                virtualObjectShader3.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture3);
            }

            if(wrappedAnchor.isNew() == 1)render.draw(virtualObjectMesh1, virtualObjectShader1, virtualSceneFramebuffer);
            if(wrappedAnchor.isNew() == 2)render.draw(virtualObjectMesh2, virtualObjectShader2, virtualSceneFramebuffer);
            if(wrappedAnchor.isNew() == 3)render.draw(virtualObjectMesh3, virtualObjectShader3, virtualSceneFramebuffer);
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }
    /** Update state based on the current frame's light estimation. */
    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader1.setBool("u_LightEstimateIsValid", false);
            virtualObjectShader2.setBool("u_LightEstimateIsValid", false);
            virtualObjectShader3.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader1.setBool("u_LightEstimateIsValid", true);
        virtualObjectShader2.setBool("u_LightEstimateIsValid", true);
        virtualObjectShader3.setBool("u_LightEstimateIsValid", true);

        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader1.setMat4("u_ViewInverse", viewInverseMatrix);
        virtualObjectShader2.setMat4("u_ViewInverse", viewInverseMatrix);
        virtualObjectShader3.setMat4("u_ViewInverse", viewInverseMatrix);

        updateMainLight(
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(),
                viewMatrix);
        updateSphericalHarmonicsCoefficients(
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }
    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader1.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader1.setVec3("u_LightIntensity", intensity);
        virtualObjectShader2.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader2.setVec3("u_LightIntensity", intensity);
        virtualObjectShader3.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader3.setVec3("u_LightIntensity", intensity);
    }
    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException(
                    "The given coefficients array must be of length 27 (3 components per 9 coefficients");
        }

        // Apply each factor to every component of each coefficient
        for (int i = 0; i < 9 * 3; ++i) {
            sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
        }
        virtualObjectShader1.setVec3Array(
                "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
        virtualObjectShader2.setVec3Array(
                "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
        virtualObjectShader3.setVec3Array(
                "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
    }
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            List<HitResult> hitResultList;
            if (instantPlacementSettings.isInstantPlacementEnabled()) {
                hitResultList =
                        frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS);
            } else {
                hitResultList = frame.hitTest(tap);
            }
            for (HitResult hit : hitResultList) {
                // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
                Trackable trackable = hit.getTrackable();
                // If a plane was hit, check that it was hit inside the plane polygon.
                // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                        || (trackable instanceof InstantPlacementPoint)
                        || (trackable instanceof DepthPoint)) {
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (wrappedAnchors.size() >= 20) {
                        wrappedAnchors.get(0).getAnchor().detach();
                        wrappedAnchors.remove(0);
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable, textureMode));
                    // For devices that support the Depth API, shows a dialog to suggest enabling
                    // depth-based occlusion. This dialog needs to be spawned on the UI thread.
                    //this.runOnUiThread(this::showOcclusionDialogIfNeeded);

                    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                    // Instant Placement Point.
                    break;
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            try {
                // Create the session.
                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException e) {
                //message = "Please install ARCore";
                //exception = e;
            } catch (UnavailableApkTooOldException e) {
                // message = "Please update ARCore";
                //exception = e;
            } catch (UnavailableSdkTooOldException e) {
                //message = "Please update this app";
                //exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                // message = "This device does not support AR";
                // exception = e;
            } catch (Exception e) {
                //  message = "Failed to create AR session";
                //  exception = e;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recordingConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDatasetUri(Uri)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            //messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }
    /** Configures the session with feature settings. */
    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
        }
        session.configure(config);
    }
    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }
    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        super.onDestroy();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

}
