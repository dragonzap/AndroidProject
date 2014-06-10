package org.android.project.type;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import android.util.Log;

public class MainManager {
	private CamManager mCam;
	private RobotManager mRobot;
	public String mDEBUG_TEXT; 
	private double spiralVar = 0;
	
	private short status = 0;
	/*
	 * 0 = négyszögek keresése
	 * 1 = legjobb négyszög kiválasztása, középre navigálása
	 * 2 = kép frissitése
	 * 3 = szembeállás a monitorral
	 * 4 = közelebb menni a monitorhoz
	 */
	
	public MainManager(int width, int height)
	{
		mCam = new CamManager(width, height);
		mRobot = new RobotManager();
	}
	
	public void update()
	{		
		switch (status)
		{
		case 0:	// négyszögek keresésse
			Log.v("ford", "Negyszogek keresese");
			if (mCam.scanSquare())
			{
				status = 1;
				spiralVar = 0;
			}
			else
			{
				Log.v("ford", "Nincs negyszog, spiralban haladas");
				goSpiral();
			}
			break;
		case 1: // legjobb négyszög kiválasztása 
			Log.v("ford", "Legjobb negyszog kivalasztasa");
			if(mCam.selectBestSquare())
			{
				Log.v("ford", "Kozepre navigalas");
				if (centerBestSquare()) // középre navigálása
				{
					status = 3;			// továbblép
				}
				else
				{
					status = 2;			// kép frissites
				}
			}
			else
			{
				Log.v("ford", "Nincs legjobb negyszog");
				status = 0;				// négyszögek keresése
			}	
			break;
		case 2: // kép frissités
			if (mCam.scanSquare())
				status = 1;
			break;
		case 3: // szembe vele
			Log.v("ford", "Szembe allitas");
			if (frontBestSquare())
				status = 4; // tovabblep
			else
				status = 2; // kozepre
			break;
		case 4:
			closerToTheSquare(); // közelebb menni, hogy 50 cm távolságra legyen
			status = 5;
			break;
		}
	}
	
	public void clear()
	{
		mCam.clear();
	}
	
	public Mat drawDebug(CvCameraViewFrame inputFrame)
	{
		mCam.setFrame(inputFrame);

		if (status == 0)
		{
			mDEBUG_TEXT = "Keres";
		}
		
		if (status == 1)
		{
			mCam.drawSquars();
			mDEBUG_TEXT = "Legjobb negyszog kivalasztas";
		}
		
		if (status == 4)
		{
			mDEBUG_TEXT = "Nagyon jok vagytok";
		}
		if(status == 5)
		{
			mDEBUG_TEXT = "A monitor a megfelelo tavolsagban van";
		}
		
		
		 //Debug draw
		if (mCam.mFound) {
			for (int i = 0; i < 4; i++) {
				Core.line(mCam.getDebugFrame(), mCam.mMonitor[i],
						mCam.mMonitor[(i + 1) % 4], new Scalar(255, 255, 0));
			}
		}
		
		//Draw robot parameters
		Core.putText(mCam.getDebugFrame(), mDEBUG_TEXT, new Point(0, 60), 1, 3, new Scalar(255, 0, 0));
		
		return mCam.getDebugFrame();
	}
	
	
	//konstans adatok:
	private double faultLimit = 100.;
	private double monitorHeight = 19; //cm
	private double monitorWidth = 34; //cm
	private double monitorHeightPx = 694;
	private double monitorWidthPx = 1254-36.6;
	private double pxToCm = 36.6; // 1 cm ennyi pixel 50 cmrõl
	
	
	public boolean centerBestSquare()
	{
		mCam.mCenter.x = 0;
		mCam.mCenter.y = 0;
		for (int p = 0; p < 4; p++) {
			mCam.mCenter.x += mCam.mMonitor[p].x;
			mCam.mCenter.y += mCam.mMonitor[p].y;
			}
		mCam.mCenter.x /= 4;
		mCam.mCenter.y /= 4;
			
		double distX = mCam.scrCenter.x - mCam.mCenter.x; // a képernyõ közepe a monitor közepétõl
		//double distY = mCam.scrCenter.y - mCam.mCenter.y;
		double heightLeft = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]); // a monitor bal oldalának magassága
		double heightRight = mCam.getDistance(mCam.mMonitor[1], mCam.mMonitor[2]); // a monitor jobb oldalának magassága
		
		double mainSide = (heightLeft+heightRight)/2;
		double tav;
		tav = (50*mainSide) / monitorHeightPx; // monitor-kamera távolság
		double pxToCmVar;
		pxToCmVar = (pxToCm * tav) / 50;
		double oldalra;
		oldalra = distX / pxToCmVar; // egy cm-et kell oldalra menni
		Log.v("ford", "Monitor-kamera tavolsag: "+Double.toString(tav));
		
		
		if(Math.abs(distX) > faultLimit)
		{
			if (distX < 0)
			{
				mDEBUG_TEXT = mRobot.toMove("jobbra", Math.abs(oldalra));//cDEBUG_TEXT = "Menj jobbra";
				Log.v("ford", "Jobbra: "+Double.toString(Math.abs(oldalra)));
			}
			else
			{
				mDEBUG_TEXT = mRobot.toMove("balra", Math.abs(oldalra));//cDEBUG_TEXT = "Menj balra";
				Log.v("ford", "Balra: "+Double.toString(Math.abs(oldalra)));
			}
		}
		else
		{
			Log.v("ford", "A monitor a kep kozepen van");
			mDEBUG_TEXT = "Kiraly";
			return true;
		}
		return false;
	}
	
	public boolean frontBestSquare()
	{		
		double heightLeft = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]);
		double heightRight = mCam.getDistance(mCam.mMonitor[1], mCam.mMonitor[2]);
		double widthTop = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[1]);
		double widthBottom = mCam.getDistance(mCam.mMonitor[2], mCam.mMonitor[3]);
		//double currentWidth = (mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[1]) + mCam.getDistance(mCam.mMonitor[2], mCam.mMonitor[3]))/2;
		double currentWidth = (widthTop+widthBottom)/2;
		/*
		if (widthTop > widthBottom)
		{
			currentWidth = widthTop;
		}
		else
		{
			currentWidth = widthBottom;
		}*/
		
		if (Math.abs(heightLeft - heightRight) < 20)
		{
			Log.v("ford", "A kamera szemben van a monitorral");
			return true;
		}
		
		double mainSide = (heightLeft+heightRight)/2;
		double tav;
		tav = (50*mainSide) / monitorHeightPx; // monitor-kamera távolság
		double maxWidth = (tav*monitorWidthPx)/50;
		
		//logok
		Log.v("ford","Monitor-kamera tavolsag: " + Double.toString(tav));
		Log.v("ford", "currentWidth: " + Double.toString(currentWidth));
		Log.v("ford", "maxWidth: " + Double.toString(maxWidth));
		
		
		double ford = Math.asin(currentWidth/maxWidth);
		ford = ford * (360/(2*Math.PI));
		Log.v("ford", Double.toString(ford));
		double oldal = Math.tan(90-ford)*tav;
		ford = 90 - ford;
		
		if (heightLeft < heightRight)
		{
			mDEBUG_TEXT = mRobot.toMove("Jobbra: ", Math.abs(oldal));
			mRobot.rot(ford);
			mDEBUG_TEXT += " ,elfordulok " + Double.toString(ford);
			Log.v("ford","Jobbra: "+Double.toString(Math.abs(oldal))+", elfordulok: "+Double.toString(ford));
		}
		else
		{
			mDEBUG_TEXT = mRobot.toMove("Balra: ", Math.abs(oldal));
			mRobot.rot(-ford);
			mDEBUG_TEXT += " ,elfordulok " + Double.toString(-ford);
			Log.v("ford","Balra: "+Double.toString(Math.abs(oldal))+", elfordulok: "+Double.toString(-ford));
		}
		//Log.v("ford", "Megyek oldalra " + Double.toString(oldal)+ " cmt, és fordulok " + Double.toString(ford)+" fokot" );
		return false;
	}
	
	public void closerToTheSquare()
	{
		double heightLeft = mCam.getDistance(mCam.mMonitor[0], mCam.mMonitor[3]);
		double heightRight = mCam.getDistance(mCam.mMonitor[1], mCam.mMonitor[2]);
		double mainSide = (heightLeft+heightRight)/2;
		double tav;
		tav = (50*mainSide) / monitorHeightPx; // monitor-kamera távolság
		double forward = 0;
		if(tav > 60)
		{
			forward = tav-60;
			mRobot.forward(forward);
			Log.v("ford", "Közelebb mentem "+Double.toString(forward)+"-cmt");
		}
		else
		{
			Log.v("ford", "A monitor a megfelelo tavolsagban van");
		}
	}
	
	public void goSpiral()
	{
		spiralVar += 10;
		mRobot.forward(spiralVar);
		mRobot.rot(90);
		//Log.v("ford", "Elore: "+Double.toString(spiralVar)+", fordul: "+Double.toString(90.));
	}
}
