package org.android.project.type;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import android.util.Log;

public class MainManager {

    // SETTINGS:
    private int DIST_STEP = 5;

    // konstans adatok:
    private double faultLimit = 100.;

    private enum STATUS_ENUM {
        SCAN_SQUARES, MONITOR_DETECT, DISTANCE, ALIGN_CENTER, FACE_TO_FACE, CLOSE_TO, END, UPDATE_IMAGE
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
                Log.v("ford", "Monitor keresese");
                if (mCam.getMonitor()) {
                    spiralVar = 0;
                    status = STATUS_ENUM.ALIGN_CENTER;
                    mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.FORWARD, DIST_STEP);
                } else {
                    Log.v("ford", "Nincs monitor, spiralban haladas");
                    goSpiral();
                }
                break;
            case ALIGN_CENTER:
                Log.v("ford", "Kozepre navigalas");
                if (alignCenter()) {
                    status = STATUS_ENUM.DISTANCE;
                    update();
                } else {
                    status = STATUS_ENUM.UPDATE_IMAGE;
                }
                break;
            case DISTANCE:
                Log.v("ford", "Monitor meretenek es tavolsaganak kiszamolasa");
                if (mCam.getDistances(DIST_STEP)) {
                    mDEBUG_TEXT = "Monitor becsult tavolsaga : " + Double.toString(mCam.mDistF);
                    status = STATUS_ENUM.FACE_TO_FACE;
                    update();
                } else {
                    Log.v("ford", "Monitor eltunt");
                    status = STATUS_ENUM.SCAN_SQUARES;
                    mDEBUG_TEXT = "Monitor eltunt";
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
                if (closerToMonitor()) {
                    status = STATUS_ENUM.END;
                    mDEBUG_TEXT = "Kesz";
                } else {
                    status = STATUS_ENUM.UPDATE_IMAGE;
                }
                break;
            case UPDATE_IMAGE:
                if (mCam.getMonitor()) {
                    status = STATUS_ENUM.ALIGN_CENTER;
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

        mCam.drawSquares();

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

        //Draw position
        Core.putText(mCam.getDebugFrame(), "Dist: " + Double.toString(mCam.mDistF) + "cm Dir: " + Double.toString(90 - mCam.mDir * 57.2957795d) + "deg", new Point(0, height - 60), 1, 2,
                new Scalar(0, 255, 0));

        Core.putText(mCam.getDebugFrame(), "h1: " + Double.toString(Math.round(mCam.mWidth)) + " h2: " + Double.toString(Math.round(mCam.mHeight)) + " size:" + Double.toString(Math.round(mCam.mSize)) + "col", new Point(0, height - 30), 1, 2,
                new Scalar(0, 255, 0));

        return mCam.getDebugFrame();
    }

    public boolean alignCenter() {
        double distX = mCam.scrCenter.x - mCam.mCenter.x; // a képernyõ közepe a monitor közepétõl
        // double distY = mCam.scrCenter.y - mCam.mCenter.y;
        /*double heightLeft = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]); // a monitor
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
		Log.v("ford", "Monitor-kamera tavolsag: " + Double.toString(tav));*/

        if (Math.abs(distX) > faultLimit) {
            // TODO: Robot forgatas
                /*mDEBUG_TEXT = "Elfordulok " + Double.toString(Math.round(_dir)) + " fokot";
             mRobot.rot(_dir);*/
        } else {
            Log.v("ford", "A monitor a kep kozepen van");
            return true;
        }
        return false;
    }

    public boolean frontBestSquare() {
        // TODO: CameraManager adatainak hasznalata
        /*double heightLeft = mCam
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

        int ford = (int) Math.round(Math.asin(currentWidth / maxWidth) * (360 / (2 * Math.PI)));
        Log.v("ford", Double.toString(ford));
        double oldal = Math.round(Math.tan(90 - ford) * tav);
        ford = 90 - ford;

        if (heightLeft < heightRight) {
            mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.RIGHT, Math.abs(oldal));
            mRobot.rot(ford);
            mDEBUG_TEXT += " ,elfordulok " + Double.toString(ford);
        } else {
            mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.LEFT, Math.abs(oldal));
            mRobot.rot(-ford);
            mDEBUG_TEXT += " ,elfordulok " + Double.toString(-ford);
        }*/
        return false;
    }

    public boolean closerToMonitor() {
        if (mCam.mDistF > 60)// Ha messzebb van mint 60cm akkor kozelebb megy
        {
            mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.FORWARD, mCam.mDistF - 60);
            return false;
        } else {
            Log.v("ford", "A monitor a megfelelo tavolsagban van");
            return true;
        }
    }

    public void goSpiral() {
        spiralVar += 10;
        mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.RIGHT, spiralVar);
    }
}
