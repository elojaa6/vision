#include <jni.h>
#include <string>

#include <opencv2/imgproc.hpp>
#include <opencv2/opencv.hpp>
#include <opencv2/videoio/videoio_c.h>

#include <opencv2/core.hpp>
#include <opencv2/features2d.hpp>
#include <vector>
#include <opencv2/objdetect.hpp>

using namespace cv;


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_vision_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_Grayscale(JNIEnv* env, jobject instance, jlong addrInput) {
    Mat* inputFrame = (Mat*)addrInput;

    cvtColor(*inputFrame, *inputFrame, COLOR_BGR2GRAY);

}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_ChangeColor(JNIEnv* env, jobject instance, jlong addrInput, jint targetColor, jint color) {
    Mat* inputFrame = (Mat*)addrInput;

    int width = inputFrame->cols;
    int height = inputFrame->rows;

    // Valor de umbral para determinar si un píxel es igual al color objetivo
    int threshold = 40; // Se puede ajustar este valor según la necesidad

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            Vec3b pixel = inputFrame->at<Vec3b>(y, x);
            int blueDiff = abs(pixel[0] - ((targetColor >> 16) & 0xFF));
            int greenDiff = abs(pixel[1] - ((targetColor >> 8) & 0xFF));
            int redDiff = abs(pixel[2] - (targetColor & 0xFF));

            if (blueDiff <= threshold && greenDiff <= threshold && redDiff <= threshold) {
                // El píxel coincide con el color objetivo, cámbialo a blanco
                inputFrame->at<Vec3b>(y, x)[0] = (color & 0xFF); // Blue
                inputFrame->at<Vec3b>(y, x)[1] = ((color >> 8) & 0xFF); // Green
                inputFrame->at<Vec3b>(y, x)[2] = ((color >> 16) & 0xFF); // Red
            }
        }
    }
}

extern "C" {
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_BorderColor(JNIEnv* env, jobject instance, jlong addrInput, jlong addrOutput) {
    Mat* inputFrame = (Mat*)addrInput;
    Mat* outputFrame = (Mat*)addrOutput;

    // Aplicar el filtro Canny para la detección de bordes en la imagen RGBA original
    Canny(*inputFrame, *outputFrame, 50, 150, 3);

    // Puedes ajustar los parámetros (umbral bajo y alto) según tus necesidades
}
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_BorderGris(JNIEnv* env, jobject instance, jlong addrInput, jlong addrOutput) {
    Mat* inputFrame = (Mat*)addrInput;
    Mat* outputFrame = (Mat*)addrOutput;

    // Aplicar el filtro Canny para la detección de bordes
    Mat grayFrame;
    cvtColor(*inputFrame, grayFrame, COLOR_RGBA2GRAY);

    // Aplicar un filtro de suavizado (por ejemplo, un filtro Gaussiano) para reducir el ruido
    GaussianBlur(grayFrame, grayFrame, Size(5, 5), 1.4, 1.4);

    // Aplicar el filtro Canny para la detección de bordes
    Canny(grayFrame, *outputFrame, 50, 150, 3);

    // Puedes ajustar los parámetros (umbral bajo y alto) según tus necesidades

    // Invertir el resultado para resaltar los bordes
    bitwise_not(*outputFrame, *outputFrame);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_CalculateHistogram(JNIEnv* env, jobject instance, jlong addrInput, jlong addrOutput) {
    Mat* inputImage = (Mat*)addrInput;
    Mat* outputImage = (Mat*)addrOutput;

    if (inputImage == nullptr || inputImage->empty()) {
        return;
    }

    // Separar la imagen en canales BGR
    std::vector<Mat> bgrChannels;
    split(*inputImage, bgrChannels);

    // Definir parámetros para el cálculo del histograma
    int histSize = 256; // Número de divisiones del histograma
    float range[] = {0, 256}; // Rango de valores de píxeles
    const float* histRange = {range};
    bool uniform = true;
    bool accumulate = false;

    Mat bHist, gHist, rHist;

    // Calcular histograma para cada canal BGR
    calcHist(&bgrChannels[0], 1, 0, Mat(), bHist, 1, &histSize, &histRange, uniform, accumulate);
    calcHist(&bgrChannels[1], 1, 0, Mat(), gHist, 1, &histSize, &histRange, uniform, accumulate);
    calcHist(&bgrChannels[2], 1, 0, Mat(), rHist, 1, &histSize, &histRange, uniform, accumulate);

    // Crear un lienzo para visualizar los histogramas
    int histWidth = 1000;
    int histHeight = 450;
    int binWidth = cvRound((double)histWidth / histSize);
    Mat histImage(histHeight, histWidth, CV_8UC3, Scalar(0, 0, 0));

    // Normalizar y dibujar los histogramas en el lienzo
    normalize(bHist, bHist, 0, histImage.rows, NORM_MINMAX, -1, Mat());
    normalize(gHist, gHist, 0, histImage.rows, NORM_MINMAX, -1, Mat());
    normalize(rHist, rHist, 0, histImage.rows, NORM_MINMAX, -1, Mat());

    for (int i = 1; i < histSize; i++) {
        line(histImage, Point(binWidth * (i - 1), histHeight - cvRound(bHist.at<float>(i - 1))),
             Point(binWidth * (i), histHeight - cvRound(bHist.at<float>(i))), Scalar(255, 0, 0), 2, 8, 0);
        line(histImage, Point(binWidth * (i - 1), histHeight - cvRound(gHist.at<float>(i - 1))),
             Point(binWidth * (i), histHeight - cvRound(gHist.at<float>(i))), Scalar(0, 255, 0), 2, 8, 0);
        line(histImage, Point(binWidth * (i - 1), histHeight - cvRound(rHist.at<float>(i - 1))),
             Point(binWidth * (i), histHeight - cvRound(rHist.at<float>(i))), Scalar(0, 0, 255), 2, 8, 0);
    }

    // Convierte la imagen de OpenCV a formato RGBA para visualización
    Mat rgbaImage;
    cvtColor(histImage, rgbaImage, COLOR_BGR2RGBA);

    // Copia los datos de imagen al buffer de salida
    if (outputImage != nullptr) {
        *outputImage = rgbaImage;
    }
}

CascadeClassifier face_cascade;
extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_InitFaceDetector(JNIEnv* jniEvn, jobject MainActivity, jstring jFilePath) {
    // TODO: implement InitFaceDetector()
    const char * jnamestr = jniEvn->GetStringUTFChars(jFilePath, NULL);
    std::string filePath(jnamestr);
    face_cascade.load(filePath);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_vision_MainActivity_DetectFaces(JNIEnv* jniEvn, jobject MainActivity, jlong addrGray, jlong addrRGBA){

    Mat* mGray = (Mat*)addrGray;
    Mat* mRGBA = (Mat*)addrRGBA;

    std::vector<Rect> faces;

    face_cascade.detectMultiScale(*mGray, faces);

    for (int i = 0; i < faces.size(); ++i) {
        rectangle(*mRGBA, Point(faces[i].x, faces[i].y), Point(faces[i].x+faces[i].width, faces[i].y+faces[i].height), Scalar(0, 255, 0), 2);
    }
    // TODO: implement FindFeatures()
}



