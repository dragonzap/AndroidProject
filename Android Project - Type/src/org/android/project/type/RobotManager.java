package org.android.project.type;

/* 
 * A telefon mozgatasa
 */

public class RobotManager {
    public enum DIR_ENUM {
        FORWARD, BACKWARD, RIGHT, LEFT
    }

    //public double posX = 0, posY = 0;	//cm
    public int rot = 0;    // deg

    public double targetX = 0, targetY = 0;    //cm
    public int targetR = 0;    //deg

    public String moveTo(DIR_ENUM direction, double distance) {
        String _t = "";
        int _r = 0;
        switch (direction) {
            case RIGHT:
                _r = 90;
                _t = "jobbra";
                break;
            case LEFT:
                _r = -90;
                _t = "balra";
                break;
            case FORWARD:
                _t = "elore";
                break;
            case BACKWARD:
                distance *= -1;
                _t = "hatra";
                break;
        }

        rot(_r);
        forward(distance);
        rot(-_r);

        return "mentem " + _t + " " + Double.toString(Math.abs(distance)) + "-cmt";
    }

    public void forward(double _dist) {
        //TODO: elore megy _dist tavot
        targetX += Math.cos(rot) * _dist;
        targetY += Math.sin(rot) * _dist;
    }

    public void rot(int _deg) {
        // TODO : robot forgatasa r
        targetR += _deg;
    }
}
