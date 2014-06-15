package org.android.project.type;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class CamManager {
    // komment :D
    // SETTINGS:
    static byte THRESH = 50;
    static byte THRESH_LEVEL = 1;
    static short MIN_SQUARE_SIZE = 1000;
    static float ASPECT_THRESH = 0.2f;
    static int CAM_WIDTH = 1280;
    static int CAM_HEIGHT = 800;
    static double CAM_H_FOV = 0.58318276339d;    // tg( 60.5° / 2 )
    //static double CAM_V_FOV = 0.44732161718d;        // tg( 48.2° / 2 )

    public boolean mFound = false;
    public Point[] mMonitor = new Point[4];
    public double mWidth = 0;    // px
    public double mHeight = 0;    // px
    public double mDist = 0;    // cm
    public double mSize = 0;    // col
    public double mDir = 0;        // radian
    public Point mCenter;

    private List<Point[]> result = new ArrayList<Point[]>();
    private Mat mImage, mOutImg;
    private double oldHeight1 = 0;
    private double oldHeight2 = 0;
    private int last_dist_step = 0;

    // Cache
    Mat gray, timg;

    public CamManager(int _w, int _h) {
        CAM_WIDTH = _w;
        CAM_HEIGHT = _h;
        mCenter = new Point();
        mImage = new Mat(CAM_WIDTH, CAM_HEIGHT, 0);
        mOutImg = new Mat(CAM_WIDTH, CAM_HEIGHT, 0);
        gray = new Mat();
        timg = new Mat();
    }

    public void clear() {
        mImage.release();
        mOutImg.release();
    }

    public void setFrame(CvCameraViewFrame _frame) {
        mImage.release();
        mImage = _frame.rgba();
        mOutImg.release();
        mOutImg = mImage.clone();
    }

    public Mat getDebugFrame() {
        return mOutImg;
    }

    public void drawSquares() {
        for (Point[] s : result) {
            for (int i = 0; i < 4; i++) {
                Core.line(mOutImg, s[i],
                        s[(i + 1) % 4], new Scalar(0, 255, 255));
            }
        }
    }

    public boolean getMonitor() {
        boolean b = scanSquares() && selectBestSquare();
        for (short i = 0; mFound && !b && i < 3; i++)    // ha nem talal megprobalja meg 3x
        {
            b = scanSquares() && selectBestSquare();
        }

        if (b) {
            mWidth = (getDistance(mMonitor[0], mMonitor[1]) + getDistance(mMonitor[2], mMonitor[3])) / 2.f;
            oldHeight1 = getDistance(mMonitor[0], mMonitor[3]);
            oldHeight2 = getDistance(mMonitor[1], mMonitor[2]);
            last_dist_step = 0;
            mHeight = (oldHeight1 + oldHeight2) / 2.f;
        } else
            mFound = false;

        return mFound;
    }

    // Monitor vizszintes tavolsaga a kepernyo kozepetol ( px )
    public double getMonitorXDistance() {
        double _dist = 0;
        if (mFound) {
            //Kozeppont megadasa
            for (int p = 0; p < 4; p++) {
                _dist += mMonitor[p].x;
            }
            _dist = (CAM_WIDTH / 2.d) - _dist / 4;
        }
        return _dist;
    }

    public boolean getDistances(int _dist_step) {
        // Tavolsag meghatarozasa ( ha volt mar sikertelen osszehasonlitas akkor meg kozelebb megy )
        last_dist_step += _dist_step;
        if (scanSquares() && selectBestSquare()) {
            /* Kamera tavolsaganak kiszamolasa a regi es az uj kep segitsegevel
                 Megnezi hogy a vizsgalt targy DIST_STEP meretu kozelitesre mennyivel no meg. */
            double newHeight1 = getDistance(mMonitor[0], mMonitor[3]);
            double newHeight2 = getDistance(mMonitor[1], mMonitor[2]);

            // Ha tul kicsi a kulombseg,nem lehet tavolsagot becsulni
            if (Math.abs(mHeight - newHeight1) < 2)
                return false;

            mDist = last_dist_step * mHeight / ((newHeight1 + newHeight2) / 2 - mHeight);

            // Megnezi kulon mind ket oldalt is
            oldHeight1 = last_dist_step * oldHeight1 / (newHeight1 - oldHeight1);
            oldHeight2 = last_dist_step * oldHeight2 / (newHeight2 - oldHeight2);
            last_dist_step = 0;

            // Milyen szelesnek latszik a kepernyo
            double proj_width = Math.abs(mMonitor[0].x + mMonitor[3].x - mMonitor[1].x - mMonitor[2].x) / 2.f;

            // Kiszamolja hogy a kamera sikjara levetitve hany centi szeles a kepernyo
            mWidth = (proj_width / CAM_WIDTH) * mDist * CAM_H_FOV * 2;

            /*
                 camera         \--------
                /     mDist      \      |
               *------------------|     | mWidth
               |    oldHeight1     \    |
               |--------------------\----
            */
            //Elfordultsag kiszamitasa (nem bizonyitjuk :P )
            mDir = Math.atan((Math.max(oldHeight1, oldHeight2) - mDist) / (mWidth / 2));
            if (oldHeight1 > oldHeight2)
                mDir = Math.PI / 2 + mDir;
            else
                mDir = Math.PI / 2 - mDir;

            return true;
        }
        return false;
    }

    public double getDistance(Point a, Point b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    private boolean scanSquares() {
        if (mImage.empty())
            return false;

        result.clear();

        // zajtalanitas
        Imgproc.pyrDown(mImage, timg, new Size(mImage.cols() / 2.0, mImage.rows() / 2));
        Imgproc.pyrUp(timg, timg, mImage.size());

        List<Mat> timgL = new ArrayList<Mat>(), grayL = new ArrayList<Mat>();
        timgL.add(timg);
        grayL.add(new Mat(mImage.size(), CvType.CV_8U));

        for (int c = 0; c < 3; c++) {
            int ch[] = {1, 0};
            MatOfInt fromto = new MatOfInt(ch);
            Core.mixChannels(timgL, grayL, fromto);

            // kulombozo kuszobszintek
            for (int l = 0; l < THRESH_LEVEL; l++) {
                Mat output = grayL.get(0);
                if (l == 0) {
                    Imgproc.Canny(output, gray, THRESH, 5);
                    Imgproc.dilate(gray, gray, new Mat(), new Point(-1, -1), 1);
                } else {
                    Imgproc.threshold(output, gray, (l + 1) * 255 / THRESH_LEVEL, 255, Imgproc.THRESH_BINARY);
                }

                // korvonal lista
                List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
                Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
                MatOfPoint2f approx = new MatOfPoint2f();

                // konturok ellenorzese
                for (MatOfPoint i : contours) {
                    MatOfPoint2f newMat = new MatOfPoint2f(i.toArray());
                    Imgproc.approxPolyDP(newMat, approx, Imgproc.arcLength(newMat, true) * 0.02f, true);
                    MatOfPoint points = new MatOfPoint(approx.toArray());

                    // hasznos negyszogek kivalogatasa
                    if (points.toArray().length == 4 && Math.abs(Imgproc.contourArea(approx)) > MIN_SQUARE_SIZE && Imgproc.isContourConvex(points)) {
                        result.add(sortPoints(points));
                    }
                }
            }
        }
        timg.release();
        return result.size() != 0;
    }

    public boolean selectBestSquare() {
        mFound = false;
        double maxArea = 10;
        for (Point[] i : result) {
            double heightLeft = getDistance(i[0], i[3]);
            double heightRight = getDistance(i[1], i[2]);

            // Kepernyo arany kiszamitasa
            double h1 = Math.min(heightLeft, heightRight); // kissebb oldal
            double h2 = Math.max(heightLeft, heightRight); // nagyobb oldal
            double d = Math.abs(i[0].x + i[3].x - i[1].x - i[2].x) / 2.f; // ket oldal kozott latott tavolsag
            double dir = Math.asin((h2 - h1) / h2);
            double t = Math.tan(dir) * d; // ket oldal kozti melysegi tavolsag

            double w; // felso el valodi hossza
            if (h2 != h1) // ha nem egyenlok akkor
                w = Math.sqrt(d * d + t * t);
            else
                w = getDistance(i[0], i[1]);

            double asp = w / h2; // keparany

            if (Math.abs(asp - 16.f / 9.f) < ASPECT_THRESH
                    || Math.abs(asp - 4.f / 3.f) < ASPECT_THRESH) // jo keparany
            {
                double area = w * h2; // terület
                if (area > maxArea) {
                    mMonitor = i;
                    mDir = dir; // TODO: torolni kell mert elrontja a szamitas
                    maxArea = area;
                    mFound = true;
                }
            }
        }
        return mFound;
    }

    /*
     * 0------------1 Sorba rendezi a pontokat
     * |			| így osszehasonlithatok
     * |			| lesznek a negyszogek
     * 3------------2
     */
    private Point[] sortPoints(MatOfPoint _points) {
        Point tmp;
        Point[] sort = _points.toArray();
        for (int i = 0; i < 4; i++) { // Rendezes y szerint
            for (int j = i + 1; j < 4; j++) {
                if (sort[i].y > sort[j].y) { // csere
                    tmp = sort[j];
                    sort[j] = sort[i];
                    sort[i] = tmp;
                }
            }
        }

        // Rendezes x szerint
        if (sort[1].x > sort[0].x) { // csere
            tmp = sort[1];
            sort[1] = sort[0];
            sort[0] = tmp;
        }
        if (sort[2].x > sort[3].x) { // csere
            tmp = sort[3];
            sort[3] = sort[2];
            sort[2] = tmp;
        }
        return sort;
    }
}