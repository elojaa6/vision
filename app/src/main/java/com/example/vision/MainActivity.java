package com.example.vision;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;
import java.util.Arrays;
import java.util.List;

import android.Manifest;




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
    public native void Grayscale(long addrRGBA);
    public native void ChangeColor(long addrInput, int pixelColor, int selectedColor);
    public native void BorderColor(long addrInput, long addrOutput);
    public native void BorderGris(long addrInput, long addrOutput);
    public native void CalculateHistogram(long addrInput, long addrOutput);

    //Pixels
    private int pixelColor;
    private Mat frame;

    //Histograma
    private SurfaceView histogramSurfaceView;
    private SurfaceHolder histogramSurfaceHolder;
    private Button showHistogramButton;
    private boolean histogramVisible = false;

    //Color
    private Button colorPickerButton;
    private int selectedColor = Color.GRAY; // Color predeterminado

    //Grabar
    private VideoWriter videoWriter;
    private boolean isRecording = false;
    private Button startRecordingButton;
    private Button stopRecordingButton;
    private String videoFilePath; // Ruta del archivo de video

    private static final int REQUEST_STORAGE_PERMISSION = 1;

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

        // Verifica si el permiso de escritura en el almacenamiento externo no está otorgado
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Solicita el permiso al usuario
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE_PERMISSION);
        }

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

        //Grabar

        startRecordingButton = findViewById(R.id.grabar);
        stopRecordingButton = findViewById(R.id.detener);
        //videoFilePath = Environment.getExternalStorageDirectory() + "/videocapture.mp4"; // Ruta de destino del archivo de video
        videoFilePath = "C:/Users/elvis/AndroidStudioProjects/vision/app/src/main/res/drawable/videocapture.mp4"; // Ruta de destino del archivo de video

        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("PixelInfo", "Entra");

                startRecording();
            }
        });

        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

    }

    //Grabar

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // El usuario otorgó el permiso de escritura en el almacenamiento externo.
                // Puedes realizar las operaciones de escritura en el almacenamiento externo aquí.
            } else {
                // El usuario denegó el permiso. Puedes mostrar un mensaje de que la aplicación no puede funcionar sin el permiso.
            }
        }
    }

    private void startRecording() {
        Log.d("PixelInfo", "Entra2");
        Log.d("PixelInfo", videoFilePath);

        videoWriter = new VideoWriter(videoFilePath, VideoWriter.fourcc('M', 'J', 'P', 'G'), 30, new Size(mOpenCvCameraView.getWidth(), mOpenCvCameraView.getHeight()), true);
        Log.d("PixelInfo", "Entra3");

        if (videoWriter.isOpened()) {
            isRecording = true;
            startRecordingButton.setVisibility(View.GONE);
            stopRecordingButton.setVisibility(View.VISIBLE);
        }
    }

    private void stopRecording() {
        if (videoWriter != null && videoWriter.isOpened()) {
            isRecording = false;
            videoWriter.release();
            startRecordingButton.setVisibility(View.VISIBLE);
            stopRecordingButton.setVisibility(View.GONE);
        }
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

            // Obtiene la matriz (Mat) de la imagen actual
            frame = mRgba;

            //Grayscale(mRgba.getNativeObjAddr());
            ChangeColor(mRgba.getNativeObjAddr(), pixelColor, selectedColor);

            Mat edges = new Mat(frame.size(), CvType.CV_8UC1);
            BorderGris(frame.getNativeObjAddr(), edges.getNativeObjAddr());
            //BorderColor(mRgba.getNativeObjAddr(), mRgba.getNativeObjAddr());

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
        stopRecording(); // Detener la grabación y liberar recursos
    }

}