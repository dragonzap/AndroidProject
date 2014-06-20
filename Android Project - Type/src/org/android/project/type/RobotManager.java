package org.android.project.type;

import java.util.ArrayList;
import java.util.List;

// A telefon mozgatasa
public class RobotManager {
    public String mDEBUG = "";

    private enum ACT_ENUM {MOVE, ROTATION}

    private class Action {
        public ACT_ENUM type;
        public double value;// cm vagy radian
    }

    private List<Action> actions = new ArrayList<Action>();
    private MainManager mMain;

    public RobotManager(MainManager _m) {
        mMain = _m;
    }

    // Feladat hozzaadasa ( cm )
    public void forward(double _dist) {
        if (_dist == 0)
            return;

        Action a = new Action();
        a.type = ACT_ENUM.MOVE;
        a.value = _dist;
        actions.add(a);
    }

    // Feladat hozzaadasa ( fok )
    public void rot(int _deg) {
        if (_deg == 0)
            return;

        Action a = new Action();
        a.type = ACT_ENUM.ROTATION;
        a.value = Math.toRadians(_deg);
        actions.add(a);
    }

    private double _r = 0, x = 0, y = 0;

    // Robot elvegzet egy feladatot
    public boolean nextAction() {
        if (actions.isEmpty()) {
            mDEBUG = "";
            mMain.arrived(Math.sqrt(x*x+y*y), _r);
            x=0;
            y=0;
            _r=0;
            return true;
        }

        //TODO: mDEBUG helyere a robot mozgatasa
        Action a = actions.get(0);
        if (a.type == ACT_ENUM.MOVE) {
            if (a.value > 0)
                mDEBUG = "mentem elore " + Long.toString(Math.round(a.value)) + "cm-t";
            else
                mDEBUG = "mentem hatra " + Long.toString(-Math.round(a.value)) + "cm-t";

            x += Math.cos(_r) * a.value;
            y += Math.sin(_r) * a.value;
        } else {
            mDEBUG = "elfordultam " + Long.toString(Math.round(Math.toDegrees(a.value))) + " fokot";
            _r -= a.value;
        }

        actions.remove(0);
        return false;
    }

    // Megerkezett-e a celhoz
    public boolean isArrived() {
        return actions.isEmpty();
    }
}
