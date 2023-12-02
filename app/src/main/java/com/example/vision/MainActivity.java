package com.example.vision;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends CameraActivity {

    // Used to load the 'vision' library on application startup.
    static {
        System.loadLibrary("vision");
    }
    private static String LOGTAG = "OpenCV_Log";

    // Camara
    private CameraBridgeViewBase mOpenCvCameraView;

    // Duplicado
    private SurfaceView secondSurfaceView;
    private SurfaceHolder secondSurfaceHolder;

    //Funciones
    public native void ChangeColor(long addrInput, int pixelColor, int selectedColor);
    public native void BorderGris(long addrInput, long addrOutput);
    public native void CalculateHistogram(long addrInput, long addrOutput);

    //DetectFaces
    public native void InitFaceDetector(String filePath);
    public native void DetectFaces(long addrGray, long addrRGBA);
    private File cascadeFile;
    private boolean detectBoolean = false;


    //Pixels
    private int pixelColor;
    private Mat frame;

    //Histograma
    private SurfaceView histogramSurfaceView;
    private SurfaceHolder histogramSurfaceHolder;
    private Button showHistogramButton;
    private boolean histogramVisible = true;

    //Color
    private Button colorPickerButton;
    private int selectedColor = Color.GRAY; // Color predeterminado

    private Button changeFuntionButton;



    private BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:{
                    Log.v(LOGTAG, "OpenCV Loaded");
                    mOpenCvCameraView.enableView();
                }break;
                default:
                {
                    super.onManagerConnected(status);
                }break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cvCameraViewListener);

        // Obtiene una referencia al SurfaceView Duplicado
        secondSurfaceView = findViewById(R.id.duplicado);
        secondSurfaceHolder = secondSurfaceView.getHolder();

        // Inicializa la SurfaceView para el histograma
        histogramSurfaceView = findViewById(R.id.histogram_image);
        showHistogramButton = findViewById(R.id.show_histogram_button);
        showHistogramButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                histogramVisible = !histogramVisible;
                if(histogramVisible){
                    histogramSurfaceView.setVisibility(SurfaceView.VISIBLE);
                }else{
                    histogramSurfaceView.setVisibility(SurfaceView.GONE);
                }
            }
        });
        histogramSurfaceHolder = histogramSurfaceView.getHolder();

        // Configuramos un Listener de Toque en tu CameraBridgeViewBase
        mOpenCvCameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // Obténemos el color del píxel en las coordenadas (x, y)
                    pixelColor = getColorAtPixel(x, y);
                    //Log.d("PixelInfo", "Coordenadas (x, y): (" + x + ", " + y + "), Valor del píxel: " + Integer.toHexString(pixelColor));

                }
                return true;
            }
        });

        //Color seleccionado

        colorPickerButton = findViewById(R.id.colorPickerButton);
        colorPickerButton.setBackgroundColor(selectedColor); // Establece el color de fondo predeterminado

        colorPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorPickerDialog();
            }
        });

        //DetectFaces

        try{
            cascadeFile = new File(getCacheDir(), "haarcascade_frontalface_default.xml");
            if (!cascadeFile.exists()){
                InputStream inputStream = getAssets().open("haarcascade_frontalface_default.xml");
                FileOutputStream outputStream = new FileOutputStream(cascadeFile);
                byte[] buffer = new byte[2048];
                int bytesRead = -1;
                while((bytesRead = inputStream.read(buffer)) != -1){
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
            }
            InitFaceDetector(cascadeFile.getAbsolutePath());
        }catch (IOException e){
            e.printStackTrace();
        }

        //Grabar

        changeFuntionButton = findViewById(R.id.grabar);

        changeFuntionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectBoolean = !detectBoolean;
            }
        });


    }

    // Función para obtener el color en las coordenadas (x, y)
    private int getColorAtPixel(int x, int y) {

        if (frame != null) {
            // Asegúrate de que las coordenadas estén dentro de los límites de la imagen
            if (x >= 0 && x < frame.cols() && y >= 0 && y < frame.rows()) {
                // Obten el color del píxel en las coordenadas (x, y)
                double[] pixelColor = frame.get(y, x);
                // Convierte el color de doble a entero
                int color = Color.rgb((int) pixelColor[0], (int) pixelColor[1], (int) pixelColor[2]);
                return color;
            }
        }

        // Si no se pudo obtener el color, devuelve un valor predeterminado
        return Color.TRANSPARENT;
    }

    //Paleta de Colors
    private void showColorPickerDialog() {
        final String[] colorNames = {"Rojo", "Verde", "Azul", "Amarillo", "Magenta", "Cian"};
        final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona un color");
        builder.setItems(colorNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // El usuario ha seleccionado un color
                selectedColor = colors[which];
                colorPickerButton.setBackgroundColor(selectedColor);
                selectedColor = convertColorARGBtoBGR(selectedColor);
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private int convertColorARGBtoBGR(int argbColor) {
        int alpha = Color.alpha(argbColor);
        int red = Color.red(argbColor);
        int green = Color.green(argbColor);
        int blue = Color.blue(argbColor);

        // Convierte ARGB a BGR
        return Color.rgb(blue, green, red);
    }

    //Camara

    @Override
    protected List<?extends CameraBridgeViewBase> getCameraViewList(){
        return Arrays.asList(mOpenCvCameraView);
    }

    private CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2(){

        @Override
        public void onCameraViewStarted(int width, int height){
        }

        @Override
        public void onCameraViewStopped(){

        }

        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
            Mat mRgba = inputFrame.rgba();
            Mat gray = inputFrame.gray();

            // Obtiene la matriz (Mat) de la imagen actual
            frame = mRgba;

            //Grayscale(mRgba.getNativeObjAddr());
            if(detectBoolean){
                DetectFaces(gray.getNativeObjAddr(), mRgba.getNativeObjAddr());
            }else{
                ChangeColor(mRgba.getNativeObjAddr(), pixelColor, selectedColor);
            }

            //

            Mat edges = new Mat(frame.size(), CvType.CV_8UC1);
            BorderGris(frame.getNativeObjAddr(), edges.getNativeObjAddr());

            // Dibujar el resultado en el SurfaceView
            if (secondSurfaceHolder.getSurface().isValid()) {
                Canvas canvas = secondSurfaceHolder.lockCanvas();
                if (canvas != null) {
                    Bitmap tempBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(edges, tempBitmap);

                    canvas.drawBitmap(tempBitmap, 0, 0, null);
                    secondSurfaceHolder.unlockCanvasAndPost(canvas);
                }
            }

            // Calcula el histograma
            Mat histogram = new Mat(mRgba.size(), CvType.CV_8UC1);
            CalculateHistogram(mRgba.getNativeObjAddr(), histogram.getNativeObjAddr());

            if (histogramSurfaceHolder.getSurface().isValid()){
                Canvas canvasHistograma = histogramSurfaceHolder.lockCanvas();
                    histogramSurfaceView.setVisibility(View.VISIBLE);
                    if (canvasHistograma != null) {
                        Bitmap tempBitmap = Bitmap.createBitmap(histogram.cols(), histogram.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(histogram, tempBitmap);
                        canvasHistograma.drawBitmap(tempBitmap, 0, 0, null);
                        histogramSurfaceHolder.unlockCanvasAndPost(canvasHistograma);
                    }
            }

            return mRgba;
        }
    };


    public void onPause(){
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    public void onResume(){
        super.onResume();
        if (!OpenCVLoader.initDebug()){
            Log.i(LOGTAG, "OpenCV not found, Initializing");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallBack);
        }else{
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy(){
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }

    }

}