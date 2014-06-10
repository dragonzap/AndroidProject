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
	public boolean mFound = false;
	public Point[] mMonitor = new Point[4];
	public Point mCenter, scrCenter;
	public String cDEBUG_TEXT; 
	
	private List<Point[]> result = new ArrayList<Point[]>();
	private Mat mImage, mOutImg;
	
	
	// Cache
	Mat gray, timg;

	public CamManager(int width, int height) {
		mCenter = new Point();
		scrCenter = new Point();
		scrCenter.x = width/2;
		scrCenter.y = height/2;
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
				Core.line(mOutImg, result.get(s)[i], result.get(s)[(i + 1) % 4],
						new Scalar(0, 255, 255));
			}
		}
	}

	public boolean selectBestSquare() {
		mFound = false;
		double maxArea = 10;
		for (int i = 0; i < result.size(); i++)
		{
			double widthTop = getDistance(result.get(i)[0], result.get(i)[1]);
			double widthBottom = getDistance(result.get(i)[2], result.get(i)[3]);
			double heightLeft = getDistance(result.get(i)[1], result.get(i)[3]);
			double heightRight = getDistance(result.get(i)[1], result.get(i)[2]);
			
			// TODO: sokminden
			
			//if (widthTop+widthBottom > heightLeft+heightRight)
				//MatOfPoint points = new MatOfPoint(result.get(i));
			
				double area = widthTop * heightRight;
				if (area > maxArea)
				{
					mMonitor = result.get(i);
					maxArea = area;
					mFound = true;
					
				}
		}
		Log.v("ford", "Negyszog felso szele: "+Double.toString(getDistance(mMonitor[0], mMonitor[1])));
		Log.v("ford", "Negyszog jobb szele: "+Double.toString(getDistance(mMonitor[2], mMonitor[3])));
		Log.v("ford", "Negyszog also szele: "+Double.toString(getDistance(mMonitor[1], mMonitor[3])));
		Log.v("ford", "Negyszog bal szele: "+Double.toString(getDistance(mMonitor[1], mMonitor[2])));
		return mFound;
	}
	
	public double getDistance(Point a, Point b)
	{
		return Math.sqrt(Math.pow(a.x - b.x,2) + Math.pow(a.y - b.y,2));  
	}

	public boolean scanSquare() {
		result.clear();

		int thresh = 50, N = 1;
		
		if (mImage.empty())
		{
			return false;
		}

		// down-scale and upscale the image to filter out the noise
		Imgproc.pyrDown(mImage, timg, new Size(mImage.cols() / 2.0, mImage.rows() / 2));
		Imgproc.pyrUp(timg, timg, mImage.size());

		List<Mat> timgL = new ArrayList<Mat>();
		List<Mat> grayL = new ArrayList<Mat>();
		timgL.add(timg);
		grayL.add(new Mat(mImage.size(), CvType.CV_8U));


		for (int c = 0; c < 3; c++) {
			int ch[] = { 1, 0 };
			MatOfInt fromto = new MatOfInt(ch);
			Core.mixChannels(timgL, grayL, fromto);

			// try several threshold levels
			for (int l = 0; l < N; l++) {
				Mat output = grayL.get(0);
				// hack: use Canny instead of zero threshold level.
				// Canny helps to catch squares with gradient shading
				if (l == 0) {
					// apply Canny. Take the upper threshold from slider
					// and set the lower to 0 (which forces edges merging)
					Imgproc.Canny(output, gray, thresh, 5);
					// dilate canny output to remove potential
					// holes between edge segments
					Imgproc.dilate(gray, gray, new Mat(), new Point(-1, -1), 1);
				} else {
					// output = output >= (i+1)*255/N;
					Imgproc.threshold(output, gray, (l + 1) * 255 / N, 255,
							Imgproc.THRESH_BINARY);
				}

				// find contours and store them all as a list
				List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
				Imgproc.findContours(gray, contours, new Mat(),
						Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

				MatOfPoint2f approx = new MatOfPoint2f();

				// test each contour
				for (int i = 0; i < contours.size(); i++) {
					MatOfPoint2f newMat = new MatOfPoint2f(contours.get(i)
							.toArray());

					// approximate contour with accuracy proportional
					// to the contour perimeter
					Imgproc.approxPolyDP(newMat, approx,
							Imgproc.arcLength(newMat, true) * 0.02f, true);

					// square contours should have 4 vertices after
					// approximation
					// relatively large area (to filter out noisy contours)
					// and be convex.
					// Note: absolute value of an area is used because
					// area may be positive or negative - in accordance with the
					// contour orientation
					MatOfPoint points = new MatOfPoint(approx.toArray());
					if (points.toArray().length == 4
							&& (Math.abs(Imgproc.contourArea(approx)) > 1000)
							&& Imgproc.isContourConvex(points)) {

						result.add(sortPoints(points));
					}
				}
			}
		}
		
		timg.release();
		//gray0.release();
		return result.size() != 0;
	}
	
	/*
	 * 0------------1 Sorba rendezi a pontokat 
	 * |			| így összehasonlíthatók
	 * |			| lesznek a nögyszögek
	 * 3------------2
	 */
	private Point[] sortPoints(MatOfPoint _points) {
		Point tmp;

		Point[] sort = _points.toArray();
		for (int i = 0; i < 4; i++) {	//Rendezés y szerint
			for (int j = i+1; j < 4; j++) {
				if (sort[i].y > sort[j].y)
				{	//csere
					tmp = sort[j];
					sort[j] = sort[i];
					sort[i] = tmp;
				}
			}
		}
		
		//Rendezés x szerint
		if (sort[1].x > sort[0].x)
		{	//csere
			tmp = sort[1];
			sort[1] = sort[0];
			sort[0] = tmp;
		}
		if (sort[2].x > sort[3].x)
		{	//csere
			tmp = sort[3];
			sort[3] = sort[2];
			sort[2] = tmp;
		}
		return sort;
	}
}