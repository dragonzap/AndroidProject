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

	// SETTINGS:
	static byte THRESH = 50;
	static byte THRESH_LEVEL = 1;
	static short MIN_SQUARE_SIZE = 1000;
	static float ASPECT_THRESH = 0.2f;

	public boolean mFound = false;
	public Point[] mMonitor = new Point[4];
	public double mWidth = 0;
	public double mHeight = 0;
	public double mDist = 0;
	public Point mCenter, scrCenter;

	private List<Point[]> result = new ArrayList<Point[]>();
	private Mat mImage, mOutImg;

	// Cache
	Mat gray, timg;

	public CamManager(int width, int height) {
		mCenter = new Point();
		scrCenter = new Point();
		scrCenter.x = width / 2;
		scrCenter.y = height / 2;
		mImage = new Mat(width, height, 0);
		mOutImg = new Mat(width, height, 0);
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

	public void drawSquars() {
		for (int s = 0; s < result.size(); s++) {
			for (int i = 0; i < 4; i++) {
				Core.line(mOutImg, result.get(s)[i],
						result.get(s)[(i + 1) % 4], new Scalar(0, 255, 255));
			}
		}
	}

	public boolean getMonitor()
	{
		if (scanSquares() && selectBestSquare())
		{
			mWidth  = (getDistance(mMonitor[0], mMonitor[1]) + getDistance(mMonitor[2], mMonitor[3])) / 2.f;
			mHeight = (getDistance(mMonitor[0], mMonitor[3]) + getDistance(mMonitor[1], mMonitor[2])) / 2.f;
		}
		return mFound;
	}

	public boolean getDistances(int _dist_step) {
		if (scanSquares() && selectBestSquare())
		{
			double aWidth = (getDistance(mMonitor[0], mMonitor[1]) + getDistance(mMonitor[2], mMonitor[3])) / 2.f;
			double aHeight = (getDistance(mMonitor[0], mMonitor[3]) + getDistance(mMonitor[1], mMonitor[2])) / 2.f;
			mDist = _dist_step * mWidth / (aWidth - mWidth);
			mDist += _dist_step * mHeight / (aHeight - mHeight);
			mDist /= 2.f;
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
				for (int i = 0; i < contours.size(); i++) {
					MatOfPoint2f newMat = new MatOfPoint2f(contours.get(i).toArray());
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
		for (int i = 0; i < result.size(); i++) {
			/* double widthTop = getDistance(result.get(i)[0], result.get(i)[1]);
			double widthBottom = getDistance(result.get(i)[2], result.get(i)[3]); */
			double heightLeft = getDistance(result.get(i)[0], result.get(i)[3]);
			double heightRight = getDistance(result.get(i)[1], result.get(i)[2]);

			// Kepernyo arany kiszamitasa
			double h1 = Math.min(heightLeft, heightRight); // kissebb oldal
			double h2 = Math.max(heightLeft, heightRight); // nagyobb oldal
			double d = Math.abs(result.get(i)[0].x + result.get(i)[3].x - result.get(i)[1].x - result.get(i)[2].x) / 2.f; // ket oldal kozott latott tavolsag
			double t = Math.tan(Math.asin((h2 - h1) / h2)) * d; // ket oldal kozti melysegi tavolsag

			double w; // felso el valodi hossza
			if (h2 != h1) // ha nem egyenlok akkor
				w = Math.sqrt(d * d + t * t);
			else
				w = getDistance(result.get(i)[0], result.get(i)[1]);

			double asp = w / h2; // keparany

			if (Math.abs(asp - 16.f / 9.f) < ASPECT_THRESH
					|| Math.abs(asp - 4.f / 3.f) < ASPECT_THRESH) // jo keparany
			{
				double area = w * h2; // terület
				if (area > maxArea) {
					mMonitor = result.get(i);
					maxArea = area;
					mFound = true;
				}
			}
		}
		if (mFound) {
			Log.v("ford", "Negyszog felso szele: " + Double.toString(getDistance(mMonitor[0], mMonitor[1])));
			Log.v("ford", "Negyszog jobb szele: " + Double.toString(getDistance(mMonitor[1], mMonitor[2])));
			Log.v("ford", "Negyszog also szele: " + Double.toString(getDistance(mMonitor[2], mMonitor[3])));
			Log.v("ford", "Negyszog bal szele: " + Double.toString(getDistance(mMonitor[3], mMonitor[0])));
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