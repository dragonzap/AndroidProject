package org.android.project.type;

import java.util.ArrayList;
import java.util.List;

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

import android.os.AsyncTask;
import android.util.Log;

public class CamManager {
    public String mDEBUG = "";

    // SETTINGS:
    static int CAM_WIDTH = 1280;        // px
    static int CAM_HEIGHT = 800;        // px
    static short MIN_SQUARE_SIZE = 1000;// px^2
    static byte MONITOR_SCAN = 5;       // nincs azonostitott monitor
    static byte MONITOR_RESCAN = 5;     // van azonositott monitor
    static float ASPECT_THRESH = 0.2f;
    static byte THRESH = 50;
    static byte THRESH_LEVEL = 1;
    static double CAM_H_FOV = 0.58318276339d;    // tg( 60.5° / 2 )
    //static double CAM_V_FOV = 0.44732161718d;  // tg( 48.2° / 2 )


    private byte rescan = 0;
    private boolean busy = false;
    private boolean found = false;

    private double distance = 0;    // cm
    private double direction = 0;   // radians

    private List<Point[]> result = new ArrayList<Point[]>();
    //  private Point[] lastMonitor = new Point[4];
    private Point[] newMonitor = new Point[4];
    private Mat mImage, mOutImg;
    private double oldHeight1 = 0;  // px
    private double oldHeight2 = 0;  // px
    private double newHeight1 = 0;  // px
    private double newHeight2 = 0;  // px

    private double dispDistance = 0, dispAngle = 0;
    private boolean img_proc = false;

    private MainManager mMain;

    // Cache
    Mat gray, timg;

    public CamManager(int _w, int _h, MainManager _m) {
        CAM_WIDTH = _w;
        CAM_HEIGHT = _h;
        mMain = _m;
        mImage = new Mat(CAM_WIDTH, CAM_HEIGHT, 0);
        mOutImg = new Mat(CAM_WIDTH, CAM_HEIGHT, 0);
        gray = new Mat();
        timg = new Mat();
    }

    // Tisztitas
    public void clear() {
        mImage.release();
        mOutImg.release();
    }

    // Kamera kep frissitese
    public void setFrame(CvCameraViewFrame _frame) {
        if (img_proc)
            return;

        mImage.release();
        mImage = _frame.rgba();
        mOutImg.release();
        mOutImg = mImage.clone();
    }

    public Mat getDebugFrame() {
        drawDebug();
        return mOutImg;
    }

    public boolean isReady() {
        return !busy;
    }

    public boolean isFound() {
        return found;
    }

    //Monitor tavolsaga cm-ben
    public double getDistance() {
        return distance;
    }

    // Monitor elfordultsaga ranianba
    public double getDirection() {
        return direction;
    }

    // Monitor keresese
    public void scanMonitor(double _d, double _a) {
        // Ha nincs még feldolgozás alatt
        if (!busy) {
            rescan = found ? MONITOR_RESCAN : MONITOR_SCAN;

            if (found) {
                dispDistance += _d;
                dispAngle += _a;
            } else {
                dispDistance = 0;
                dispAngle = 0;
            }
            new ImageScanner().execute();
        }
    }

    // Monitor vizszintes tavolsaga a kepernyo kozepetol ( px )
    public double getMonitorXDistance() {
        double _dist = 0;
        if (found) {
            //Kozeppont megadasa
            for (int p = 0; p < 4; p++) {
                _dist += newMonitor[p].x;
            }
            _dist = (CAM_WIDTH / 2.d) - _dist / 4;
        }
        return _dist;
    }

    // Utolso elmozdulas cm
    private void calculate() {
        /* Kamera tavolsaganak kiszamolasa a regi es az uj kep segitsegevel
                 Megnezi hogy a vizsgalt targy DIST_STEP meretu kozelitesre mennyivel no meg. */
        double newHeightAVG = (newHeight1 + newHeight2) / 2.f;   // px (AVG)
        double oldHeightAVG = (oldHeight1 + oldHeight2) / 2.f;   // px (AVG)
        double oldDist = distance;
        double oldDir = direction;
        mDEBUG = "old/new: " + Double.toString(oldHeightAVG) + "/" + Double.toString(newHeightAVG);

        // Ha tul kicsi a kulombseg,nem lehet tavolsagot becsulni
        if (dispDistance == 0 || dispAngle != 0 || oldHeightAVG == newHeightAVG || newHeight1 == oldHeight1 || newHeight2 == oldHeight2) {
            distance = 0;
            direction = 0;
//            if (dispDistance != 0 && dispAngle == 0) {
                mDEBUG = "Hiba az emlozdulasnal: disp:" + Double.toString(dispDistance) + "cm;" + Double.toString(dispAngle) + "deg height dif.:" + Double.toString(newHeight1 - oldHeight1) + "px;"+ Double.toString(newHeightAVG - oldHeightAVG) + "px;"+ Double.toString(newHeight2 - oldHeight2) + "px";
                Log.v("ford", mDEBUG);
           // }
            return;
        }

        // Kiszamolja a tavolsagokat
        distance = dispDistance * oldHeightAVG / (newHeightAVG - oldHeightAVG);  // cm kozepe
        double d1 = dispDistance * oldHeight1 / (newHeight1 - oldHeight1);   // cm bal oldal
        double d2 = dispDistance * oldHeight2 / (newHeight2 - oldHeight2);   // cm jobb oldal
        mDEBUG += " dist.: " + Double.toString(d1) + ";" + Double.toString(distance) + ";" + Double.toString(d2);

        // Milyen szelesnek latszik a kepernyo
        double projWidth = Math.abs(newMonitor[0].x + newMonitor[3].x - newMonitor[1].x - newMonitor[2].x) / 2.f; //px

        // Kiszamolja hogy a kamera sikjara levetitve hany centi szeles a kepernyo
        projWidth = (projWidth / CAM_WIDTH) * distance * CAM_H_FOV * 2; // cm

            /*
                 camera         \--------
                /     mDist      \      |
               *------------------|     | projWidth
               |        d          \    |
               |--------------------\----
            */
        // Elfordultsag kiszamitasa (nem bizonyitjuk :P )
        direction = Math.atan((Math.max(d1, d2) - distance) / (projWidth / 2)); // radian
        if (d1 > d2)
            direction = Math.PI / 2 + direction;
        else
            direction = Math.PI / 2 - direction;

        if (oldDist != 0) {
            Log.v("ford", "Tavolsag becsles hibaja:" + Double.toString(oldDist - distance - dispDistance));
            Log.v("ford", "Elfordulas becsles hibaja:" + Double.toString(Math.toDegrees(oldDir) - Math.toDegrees(direction)));
        }
    }

    private double getDistance(Point a, Point b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    private class ImageScanner extends AsyncTask<Void, Integer, Boolean> {
        protected Boolean doInBackground(Void... n) {
            img_proc = true;
            Mat img = mImage.clone();
            img_proc = false;
            busy = true;
            if (scanSquares(img)) {
                if (selectMonitor())
                    return true;
            }
            return false;
        }

        protected void onPostExecute(Boolean result) {
            rescan--;
            if (result) {
                rescan = 0;
                oldHeight1 = newHeight1;
                oldHeight2 = newHeight2;
                newHeight1 = getDistance(newMonitor[0], newMonitor[3]);
                newHeight2 = getDistance(newMonitor[1], newMonitor[2]);
                calculate();

                busy = false;
            } else if (rescan > 0)
                new ImageScanner().execute();
            else
                busy = false;

            mMain.nextStatus();
        }
    }

    // negyszogek keresese _img kepen
    private boolean scanSquares(Mat _img) {
        if (_img.empty())
            return false;

        // zajtalanitas
        Imgproc.pyrDown(_img, timg, new Size(_img.cols() / 2.0, _img.rows() / 2));
        Imgproc.pyrUp(timg, timg, _img.size());

        List<Mat> timgL = new ArrayList<Mat>(), grayL = new ArrayList<Mat>();
        timgL.add(timg);
        grayL.add(new Mat(_img.size(), CvType.CV_8U));

        result.clear();
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
                    if (points.toArray().length == 4 && Math.abs(Imgproc.contourArea(approx)) > MIN_SQUARE_SIZE && Imgproc.isContourConvex(points))
                        result.add(sortPoints(points));
                }
            }
        }
        timg.release();
        return result.size() != 0;
    }

    // Megkeresi a legidealisabb negyszoget
    private boolean selectMonitor() {
        found = false;
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
                   /* if (!found)
                        lastMonitor = newMonitor;*/

                    newMonitor = i;
                    maxArea = area;
                    found = true;
                }
            }
        }
        return found;
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

    private void drawDebug() {
        // Negyszogek
        for (Point[] s : result) {
            for (int i = 0; i < 4; i++) {
                Core.line(mOutImg, s[i],
                        s[(i + 1) % 4], new Scalar(0, 255, 255));
            }
        }

        // Monitor
        if (found) {
            for (int i = 0; i < 4; i++) {
                Core.line(mOutImg, newMonitor[i],
                        newMonitor[(i + 1) % 4], new Scalar(255, 255, 0));
            }
        }
    }
}