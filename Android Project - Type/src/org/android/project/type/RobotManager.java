package org.android.project.type;

import android.util.Log;

/* 
 * A telefon mozgatasa
 */

public class RobotManager {
	public double posX = 0, posY = 0;
	public float rot = 0;

	public double targetX = 0, targetY = 0;
	public double targetR = 0;

	public String toMove(String irany, double tav)
	{
		if(irany == "jobbra")
		{
			rot(90.);
			forward(tav);
			rot(-90.);
		}
		else if(irany == "balra")
		{
			rot(-90.);
			forward(tav);
			rot(90.);
		}
		else if(irany == "elore")
		{
			forward(tav);
		}
		else
		{
			forward(-tav);
		}
		return "mentem " + irany + "-ra " + Double.toString(tav) + "-cmt";
	}
	
	public void forward(double tav)
	{
		//TODO: elõre megy tavot
	}

	public void rot(double r)
	{
		// TODO : robot forgatasa
	}
}
