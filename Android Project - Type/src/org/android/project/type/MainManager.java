package org.android.project.type;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import android.util.Log;

public class MainManager {
    // SETTINGS:
    static int DIST_STEP = 5;
    static int ALIGN_THRESH = 10; // % ( CAM_WIDTH / 100 * ALIGN_TRESH tavolsag ( px ))
    static int ALIGN_ROT = 5;  // degrees

    private CamManager mCam;
    private RobotManager mRobot;
    private STATUS_ENUM status = STATUS_ENUM.SCAN_MONITOR;
    private double spiralVar = 0;
    private String mDEBUG_TEXT;

    public MainManager(int _w, int _h) {
        mCam = new CamManager(_w, _h, this);
        mRobot = new RobotManager(this);
        mDEBUG_TEXT = "0. Kattints a kepernyore es hajtsd vegre az utasitasokat";
    }

    public void update() {
        mRobot.nextAction();

        if (!mRobot.mDEBUG.isEmpty())
            Log.v("ford", mRobot.mDEBUG);
    }

    public void arrived(double distance, double angle) {
        if (mCam.isReady())
            mCam.scanMonitor(distance, angle);
        else
            Log.v("ford", "Camera foglalt");
    }

    public void nextStatus() {
        // Megerkezett-e a celhoz
        if (!mRobot.isArrived())
            return;

        if (!mCam.isReady())
            return;

        // Ha nincs kamera elkezd keresni
        if (!mCam.isFound())
            status = STATUS_ENUM.SCAN_MONITOR;

        switch (status) {
            case SCAN_MONITOR:
                mDEBUG_TEXT = "1. Monitor keresese";
                if (mCam.isFound()) {
                    status = STATUS_ENUM.ALIGN_CENTER;
                    spiralVar = 0;
                }
                else
                    goSpiral();
                break;
            case ALIGN_CENTER:
                mDEBUG_TEXT = "2. Monitor kozepre igazitasa";
                if (alignCenter()) {
                    status = STATUS_ENUM.DISTANCE;
                    mDEBUG_TEXT = "3. Monitor tavolsaganak becslese";
                    mRobot.forward(DIST_STEP);
                }
                break;
            case DISTANCE:

                if (mCam.getDistance() != 0)
                    status = STATUS_ENUM.CLOSE_TO;
                else {
                    mDEBUG_TEXT = "3.1. Monitor tavolsaganak ujra becslese (elozo sikertelen)";
                    mRobot.forward(DIST_STEP);
                }
                break;
            case CLOSE_TO:
                mDEBUG_TEXT = "4. Monitor megkozelitese (becsult tavolsag: " + Double.toString(Math.round(mCam.getDistance())) + " cm)";
                if (closerToMonitor()) {
                       status = STATUS_ENUM.FACE_TO_FACE;
                }
                break;
            case FACE_TO_FACE:
                mDEBUG_TEXT = "6. Monitor szembe allitasa";
                if (faceToFace()) {
                    status = STATUS_ENUM.END;
                    mDEBUG_TEXT = "Kesz";
                }
                break;
            default:
                break;
        }

        Log.v("ford", mDEBUG_TEXT);
        if (mRobot.isArrived())
            nextStatus();
        else if (status != STATUS_ENUM.END)
            update();
    }

    public void clear() {
        mCam.clear();
    }

    public Mat drawDebug(CvCameraViewFrame inputFrame) {
        mCam.setFrame(inputFrame);

        Mat _img = mCam.getDebugFrame();

        // Draw robot parameters
        Core.putText(_img, mDEBUG_TEXT, new Point(0, 20), 1, 2, new Scalar(0, 255, 0));
        Core.putText(_img, mRobot.mDEBUG, new Point(0, 60), 1, 3, new Scalar(255, 128, 255));

        //Draw position
        /*Core.putText(mCam.getDebugFrame(), "Dist: " + Double.toString(mCam.mDist) + "cm Dir: " + Double.toString(mCam.mDir * 57.2957795d) + "deg", new Point(0, height - 60), 1, 2,
                new Scalar(0, 255, 0));

        Core.putText(mCam.getDebugFrame(), "h1: " + Double.toString(Math.round(mCam.mWidth)) + " h2: " + Double.toString(Math.round(mCam.mHeight)) + " size:" + Double.toString(Math.round(mCam.mSize)) + "col", new Point(0, height - 30), 1, 2,
                new Scalar(0, 255, 0));*/
        return _img;
    }

    public boolean alignCenter() {
        double distX = mCam.getMonitorXDistance();
        if (Math.abs(distX) > CamManager.CAM_WIDTH / 100 * ALIGN_THRESH) {
            if (distX > 0)
                mRobot.rot(-ALIGN_ROT);
            else
                mRobot.rot(ALIGN_ROT);
        } else
            return true;

        return false;
    }

    // mukodik!
    public boolean faceToFace() {
        double szogFault = 20;

        if (Math.abs(Math.toDegrees(mCam.getDirection()) - 90) < szogFault) {
            Log.v("ford", "A kamera szemben van a monitorral");
            return true;
        }

        double oldalra = Math.tan(Math.PI / 2 - mCam.getDirection()) * mCam.getDistance();

        mRobot.rot(90);
        mRobot.forward(oldalra);
        mRobot.rot((int) Math.round(-Math.toDegrees(mCam.getDirection())));
        return false;
    }

    public boolean closerToMonitor() {
        if (mCam.getDistance() > 60)// Ha messzebb van mint 60cm akkor kozelebb megy
        {
            mRobot.forward(mCam.getDistance() - 60);
            return false;
        } else {
            Log.v("ford", "A monitor a megfelelo tavolsagban van");
            return true;
        }
    }

    public void goSpiral() {
        spiralVar += 10;
        mRobot.rot(90);//elfordul jobbra
        mRobot.forward(spiralVar);//megy elore
    }

    private enum STATUS_ENUM {
        SCAN_MONITOR, ALIGN_CENTER, DISTANCE, CLOSE_TO, DISTANCE2, FACE_TO_FACE, END, UPDATE_IMAGE
    }
}