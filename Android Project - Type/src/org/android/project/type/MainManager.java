package org.android.project.type;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import android.util.Log;

public class MainManager {

	private enum STATUS_ENUM {
		SCAN_SQUARES, MONITOR_DETECT, ALIGN_CENTER, FACE_TO_FACE, CLOSE_TO, END, UPDATE_IMAGE
	}

	private STATUS_ENUM status = STATUS_ENUM.SCAN_SQUARES;

	private CamManager mCam;
	private RobotManager mRobot;
	private double height;
	public String mDEBUG_TEXT;
	private double spiralVar = 0;

	public MainManager(int _w, int _h) {
		height = _h;
		mCam = new CamManager(_w, _h);
		mRobot = new RobotManager();
		mDEBUG_TEXT = "Keres";
	}

	public void update() {
		switch (status) {
		case SCAN_SQUARES:
			Log.v("ford", "Negyszogek keresese");
			if (mCam.scanSquares()) {
				spiralVar = 0;
				status = STATUS_ENUM.MONITOR_DETECT;
				update();
			} else {
				Log.v("ford", "Nincs negyszog, spiralban haladas");
				goSpiral();
			}
			break;
		case MONITOR_DETECT:
			Log.v("ford", "Monitor kivalasztasa");
			if (mCam.selectBestSquare()) {
				status = STATUS_ENUM.ALIGN_CENTER;
				update();
			} else {
				mDEBUG_TEXT = "Nincs megfelelo negyszog";
				Log.v("ford", mDEBUG_TEXT);
				status = STATUS_ENUM.SCAN_SQUARES;
			}
			break;
		case ALIGN_CENTER:
			Log.v("ford", "Kozepre navigalas");
			if (centerBestSquare()) {
				status = STATUS_ENUM.FACE_TO_FACE;
				update();
			} else {
				status = STATUS_ENUM.UPDATE_IMAGE;
			}
			break;
		case FACE_TO_FACE:
			Log.v("ford", "Szembe allitas");
			if (frontBestSquare()) {
				status = STATUS_ENUM.CLOSE_TO;
				update();
			} else {
				status = STATUS_ENUM.UPDATE_IMAGE;
			}
			break;
		case CLOSE_TO:
			Log.v("ford", "Monitor megkozelitese");
			if (closerToTheSquare()) {
				status = STATUS_ENUM.END;
				mDEBUG_TEXT = "Kesz";
			} else {
				status = STATUS_ENUM.UPDATE_IMAGE;
			}
			break;
		case UPDATE_IMAGE:
			if (mCam.scanSquares())
			{
				status = STATUS_ENUM.MONITOR_DETECT;
				update();
			}
			break;
		default:

			break;

		}
	}

	public void clear() {
		mCam.clear();
	}

	public Mat drawDebug(CvCameraViewFrame inputFrame) {
		mCam.setFrame(inputFrame);

		// Debug draw
		if (mCam.mFound) {
			for (int i = 0; i < 4; i++) {
				Core.line(mCam.getDebugFrame(), mCam.mMonitor[i],
						mCam.mMonitor[(i + 1) % 4], new Scalar(255, 255, 0));
			}
		}

		// Draw robot parameters
		Core.putText(mCam.getDebugFrame(), mDEBUG_TEXT, new Point(0, 60), 1, 3,
				new Scalar(255, 0, 0));

		return mCam.getDebugFrame();
	}

	// konstans adatok:
	private double faultLimit = 100.;
	private double monitorHeight = 19; // cm
	private double monitorWidth = 34; // cm
	private double monitorHeightPx = 694;
	private double monitorWidthPx = 1254 - 36.6;
	private double pxToCm = 36.6; // 1 cm ennyi pixel 50 cmrõl

	public boolean centerBestSquare() {
		mCam.mCenter.x = 0;
		mCam.mCenter.y = 0;
		for (int p = 0; p < 4; p++) {
			mCam.mCenter.x += mCam.mMonitor[p].x;
			mCam.mCenter.y += mCam.mMonitor[p].y;
		}
		mCam.mCenter.x /= 4;
		mCam.mCenter.y /= 4;

		double distX = mCam.scrCenter.x - mCam.mCenter.x; // a képernyõ közepe a
															// monitor közepétõl
		// double distY = mCam.scrCenter.y - mCam.mCenter.y;
		double heightLeft = mCam
				.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]); // a monitor
																	// bal
																	// oldalának
																	// magassága
		double heightRight = mCam.getDistance(mCam.mMonitor[1],
				mCam.mMonitor[2]); // a monitor jobb oldalának magassága

		double mainSide = (heightLeft + heightRight) / 2;
		double tav;
		tav = (50 * mainSide) / monitorHeightPx; // monitor-kamera távolság
		double pxToCmVar;
		pxToCmVar = (pxToCm * tav) / 50;
		double oldalra;
		oldalra = Math.round(distX / pxToCmVar); // egy cm-et kell oldalra menni
		Log.v("ford", "Monitor-kamera tavolsag: " + Double.toString(tav));

		if (Math.abs(distX) > faultLimit) {
			mDEBUG_TEXT = mRobot.toMove(distX < 0 ? "jobbra" : "balra",
					Math.abs(oldalra));
		} else {
			Log.v("ford", "A monitor a kep kozepen van");
			return true;
		}
		return false;
	}

	public boolean frontBestSquare() {
		double heightLeft = mCam
				.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]);
		double heightRight = mCam.getDistance(mCam.mMonitor[1],
				mCam.mMonitor[2]);
		double widthTop = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[1]);
		double widthBottom = mCam.getDistance(mCam.mMonitor[2],
				mCam.mMonitor[3]);
		// double currentWidth = (mCam.getDistance(mCam.mMonitor[0],
		// mCam.mMonitor[1]) + mCam.getDistance(mCam.mMonitor[2],
		// mCam.mMonitor[3]))/2;
		double currentWidth = (widthTop + widthBottom) / 2;
		/*
		 * if (widthTop > widthBottom) { currentWidth = widthTop; } else {
		 * currentWidth = widthBottom; }
		 */

		if (Math.abs(heightLeft - heightRight) < 20) {
			Log.v("ford", "A kamera szemben van a monitorral");
			return true;
		}

		double mainSide = (heightLeft + heightRight) / 2;
		double tav;
		tav = (50 * mainSide) / monitorHeightPx; // monitor-kamera távolság
		double maxWidth = (tav * monitorWidthPx) / 50;

		// logok
		Log.v("ford", "Monitor-kamera tavolsag: " + Double.toString(tav));
		Log.v("ford", "currentWidth: " + Double.toString(currentWidth));
		Log.v("ford", "maxWidth: " + Double.toString(maxWidth));

		double ford = Math.asin(currentWidth / maxWidth);
		ford = ford * (360 / (2 * Math.PI));
		Log.v("ford", Double.toString(ford));
		double oldal = Math.round(Math.tan(90 - ford) * tav);
		ford = 90 - ford;

		if (heightLeft < heightRight) {
			mDEBUG_TEXT = mRobot.toMove("Jobbra: ", Math.abs(oldal));
			mRobot.rot(ford);
			mDEBUG_TEXT += " ,elfordulok " + Double.toString(ford);
		} else {
			mDEBUG_TEXT = mRobot.toMove("Balra: ", Math.abs(oldal));
			mRobot.rot(-ford);
			mDEBUG_TEXT += " ,elfordulok " + Double.toString(-ford);
		}
		return false;
	}

	public boolean closerToTheSquare() {
		double heightLeft = mCam
				.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]);
		double heightRight = mCam.getDistance(mCam.mMonitor[1],
				mCam.mMonitor[2]);
		double mainSide = (heightLeft + heightRight) / 2;
		double tav;
		tav = (50 * mainSide) / monitorHeightPx; // monitor-kamera távolság
		double forward = 0;

		if (mainSide / height < .8f)// (tav > 60)
		{
			forward = 5;
			mRobot.forward(forward);
			mDEBUG_TEXT = "Menj elore " + Double.toString(forward) + "cm-t";
			return false;
		} else {
			Log.v("ford", "A monitor a megfelelo tavolsagban van");
			return true;
		}
	}

	public void goSpiral() {
		spiralVar += 10;
		mRobot.forward(spiralVar);
		mRobot.rot(90);
		mDEBUG_TEXT = "Fordulj el 90 fokot es menj elore "
				+ Double.toString(spiralVar) + "cm-t";
	}
}
