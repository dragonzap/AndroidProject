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
	static double CAM_H_FOV = 0.58318276339d;	// tg( 60.5° / 2 )
	static double CAM_V_FOV = 0.44732161718d;		// tg( 48.2° / 2 )

	public boolean mFound = false;
	public Point[] mMonitor = new Point[4];
	public double mWidth = 0;	// px
	public double mHeight = 0;	// px
	public double mDist = 0;	// cm
	public double mSize = 0;	// col
	public double mDir = 0;		// radian
	public Point mCenter, scrCenter;

    /*
    |
    | camera
    |/
    *------------------|
    |                \ | mDistS
    |                 \|
    |------------------* - monitor
    |      mDist       \
    |                    \

     */

	private List<Point[]> result = new ArrayList<Point[]>();
	private Mat mImage, mOutImg;
	
	private double pHeight1 = 0;
	private double pHeight2 = 0;

	// Cache
	Mat gray, timg;

	public CamManager(int _w, int _h) {
        CAM_WIDTH = _w;
        CAM_HEIGHT = _h;
		mCenter = new Point();
		scrCenter = new Point();
		scrCenter.x = CAM_WIDTH / 2;
		scrCenter.y = CAM_HEIGHT / 2;
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

	public boolean getMonitor()
	{
		boolean b = scanSquares() && selectBestSquare();
		for (short i = 0;mFound && !b && i < 3; i++)	// ha nem talal megprobalja meg 3x
		{
			b = scanSquares() && selectBestSquare();
		}

		if (b)
		{
			mWidth  = (getDistance(mMonitor[0], mMonitor[1]) + getDistance(mMonitor[2], mMonitor[3])) / 2.f;
			pHeight1 = getDistance(mMonitor[0], mMonitor[3]);
			pHeight2 = getDistance(mMonitor[1], mMonitor[2]);
			mHeight = (pHeight1 + pHeight2) / 2.f;
		}
		else
			mFound = false;
		
		return mFound;
	}

    public double getMonitorXDistance()
    {
        double _dist = 0;
        if (mFound) {
            //Kozeppont megadasa
            mCenter.x = 0;
            mCenter.y = 0;
            for (int p = 0; p < 4; p++) {
                mCenter.x += mMonitor[p].x;
                mCenter.y += mMonitor[p].y;
            }
            mCenter.x /= 4;
            mCenter.y /= 4;

            _dist = (CAM_WIDTH / 2.d) - mCenter.x;
        }
        return _dist;
    }

	public boolean getDistances(int _dist_step) {
		if (scanSquares() && selectBestSquare())
		{
			/* Kamera tavolsaganak kiszamolasa a regi es az uj kep segitsegevel
                 Megnezi hogy a vizsgalt targy DIST_STEP meretu kozelitesre mennyivel no meg. */
			double aHeight1 = getDistance(mMonitor[0], mMonitor[3]);
			double aHeight2 = getDistance(mMonitor[1], mMonitor[2]);

            Log.v("ford", "Monitor bal szele: " + Double.toString(pHeight1));
            Log.v("ford", "Monitor jobb szele: " + Double.toString(pHeight2));

			mDist = _dist_step * mHeight / ((aHeight1 + aHeight2)/2 - mHeight);

			// Megnezi kulon mind ket oldalt is
			pHeight1 = _dist_step * pHeight1 / (aHeight1 - pHeight1);
			pHeight2 = _dist_step * pHeight2 / (aHeight2 - pHeight2);

            Log.v("ford", "Monitor bal szele: " + Double.toString(pHeight1));
            Log.v("ford", "Monitor jobb szele: " + Double.toString(pHeight2));

            // Atlagolas
            mDistF = (pHeight1 + pHeight2 + mDist) / 3;

			// Milyen szelesnek latszik a kepernyo
			double proj_height = Math.abs(mMonitor[0].x + mMonitor[3].x - mMonitor[1].x - mMonitor[2].x) / 2.f;

			// Kiszamolja hogy a kamera sikjara levetitve hany centi szeles a kepernyo
			mWidth = (proj_height / CAM_WIDTH) * mDist * CAM_H_FOV;

			// Kiszamolja a szoget a ket oldal tavolsaganak a kulombsege / vetitett szelesseg
            mDir = Math.asin((aHeight1 - aHeight2) / aHeight2);

			mDir += Math.atan((pHeight1 - pHeight2) / mWidth);

			// Monitor valodi meretei
			mWidth = Math.sqrt( Math.pow(mWidth, 2) + Math.pow(pHeight1 - pHeight2, 2));
            mDir += Math.asin((pHeight1 - pHeight2) / mWidth);
            mDir /=3;

            Log.v("ford", "a: " + Double.toString(pHeight1 - pHeight2));
            Log.v("ford", "c: " + Double.toString(mWidth));
            //mDir /= 2;
          /*  mDir = Math.toDegrees(mDir);
            mDir = Math.PI/4 - Math.abs(mDir);
            mDir = Math.toRadians(mDir);*/

			mHeight = (aHeight1 + aHeight2)/2;
			mHeight = (mHeight / CAM_HEIGHT) * mDist * CAM_V_FOV;
			mSize = Math.sqrt(Math.pow(mWidth, 2) + Math.pow(mHeight,2)) / 2.54d;	// atvaltas colba
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
			int ch[] = { 1, 0 };
			MatOfInt fromto = new MatOfInt(ch);
			Core.mixChannels(timgL, grayL, fromto);

			// kulombozo kuszobszintek
			for (int l = 0; l < THRESH_LEVEL; l++) {
				Mat output = grayL.get(0);
				if (l == 0)
				{
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
			/* double widthTop = getDistance(result.get(i)[0], result.get(i)[1]);
			double widthBottom = getDistance(result.get(i)[2], result.get(i)[3]); */
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
		/*if (mFound) {
			Log.v("ford", "Negyszog felso szele: " + Double.toString(getDistance(mMonitor[0], mMonitor[1])));
			Log.v("ford", "Negyszog jobb szele: " + Double.toString(getDistance(mMonitor[1], mMonitor[2])));
			Log.v("ford", "Negyszog also szele: " + Double.toString(getDistance(mMonitor[2], mMonitor[3])));
			Log.v("ford", "Negyszog bal szele: " + Double.toString(getDistance(mMonitor[3], mMonitor[0])));
		}*/
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