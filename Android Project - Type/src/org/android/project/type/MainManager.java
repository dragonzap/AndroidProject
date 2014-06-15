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
                if (mCam.getMonitor()) {
                    spiralVar = 0;
                    status = STATUS_ENUM.ALIGN_CENTER;
                } else {
                    Log.v("ford", "Nincs monitor, spiralban haladas");
                    goSpiral();
                }
                break;
            case ALIGN_CENTER:
                Log.v("ford", "Kozepre navigalas");
                if (alignCenter()) {
                    status = STATUS_ENUM.DISTANCE;
                    mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.FORWARD, DIST_STEP);
                } else {
                    status = STATUS_ENUM.UPDATE_IMAGE;
                }
                break;
            case DISTANCE:
                Log.v("ford", "Monitor meretenek es tavolsaganak kiszamolasa");
                if (mCam.getDistances(DIST_STEP)) {
                    mDEBUG_TEXT = "Monitor becsult tavolsaga : " + Double.toString(mCam.mDist);
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
        Core.putText(mCam.getDebugFrame(), "Dist: " + Double.toString(mCam.mDist) + "cm Dir: " + Double.toString(mCam.mDir * 57.2957795d) + "deg", new Point(0, height - 60), 1, 2,
                new Scalar(0, 255, 0));

        Core.putText(mCam.getDebugFrame(), "h1: " + Double.toString(Math.round(mCam.mWidth)) + " h2: " + Double.toString(Math.round(mCam.mHeight)) + " size:" + Double.toString(Math.round(mCam.mSize)) + "col", new Point(0, height - 30), 1, 2,
                new Scalar(0, 255, 0));

        return mCam.getDebugFrame();
    }

    public boolean alignCenter() {
        double distX = mCam.getMonitorXDistance();

        int rotVar = 5;
        if (Math.abs(distX) > faultLimit) {
            // TODO: Robot forgatas
            mDEBUG_TEXT = "Elfordulok " + Integer.toString(rotVar) + " fokot";
            Log.v("ford", "Elfordulok " + Integer.toString(rotVar) + " fokot");
            if(distX > 0)
            {
                mRobot.rot(rotVar);
            }
            else
            {
                mRobot.rot(0-rotVar);
            }

        } else {
            Log.v("ford", "A monitor a kep kozepen van");
            return true;
        }
        return false;
    }


    // m�k�dik!
    public boolean frontBestSquare() {

        //double tav = 100;
        //double szog = 45;

        double tav = mCam.mDist;
        double szog = mCam.mDir;
        double szogFault = 20;

        if (Math.abs(szog-90) < szogFault) {
            Log.v("ford", "A kamera szemben van a monitorral");
            return true;
        }

        double oldalra = Math.tan(Math.toRadians(90-szog))*tav;
        double ford = 90-szog;

        mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.RIGHT, oldalra);
        mRobot.rot((int)ford);

        Log.v("ford", "Oldalra mentem: " + Double.toString(oldalra));
        Log.v("ford", "Fordultam: " + Double.toString(ford));

        return true;

    }

   
    public boolean closerToMonitor() {
        if (mCam.mDist > 60)// Ha messzebb van mint 60cm akkor kozelebb megy
        {
            mDEBUG_TEXT = mRobot.moveTo(RobotManager.DIR_ENUM.FORWARD, mCam.mDist - 60);
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