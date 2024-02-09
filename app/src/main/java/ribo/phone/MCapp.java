package ribo.phone;

import android.annotation.*;
import android.app.*;
import android.view.*;
import com.ibm.ssm.*;
import org.linphone.activities.*;

public class MCapp extends Application {
    public static String startTime = SSMutil.getTimestamp();
    private Thread.UncaughtExceptionHandler defaultUEH;

    // Our handler to catch any exception in this app
    Thread.UncaughtExceptionHandler vpUEH =
            new Thread.UncaughtExceptionHandler() {
                // @Override
                @SuppressLint("NewApi")
                public void uncaughtException(Thread th, Throwable ex) {
                    String stkTr = SSMutil.stackTrace(ex);
                    prt("!!!!MCapp uncaught exception: thread " + th + ", ex " + ex + "\n" + stkTr);
                    Info.curAct.finishAffinity(); // Close all other activities of this app?
                    // System.exit(0);

                    // re-throw critical exception further to the os (important)
                    defaultUEH.uncaughtException(th, ex);
                }
            };

    public MCapp() {
        prt("MCapp constructor");
        // Save current uncaught exception handler
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        // Now insert our own
        Thread.setDefaultUncaughtExceptionHandler(vpUEH);
    } // end of MCapp constructor

    public void onCreate() {
        super.onCreate();
        prt("MCapp create!");

        // minimalSSMinit();
    } // end of onCreate

    public enum actionType {
        click,
        longClick,
        N,
        NE,
        E,
        SE,
        S,
        SW,
        W,
        NW, // Swipe directions
        downLow // MouseDown on the lower 20% of view (for scrollbar)
    }

    public static class Action {
        actionType type;
        float x, y;

        public String toString() {
            return type.name() + " " + x + ", " + y;
        }
    } // end of Action class

    static long touchDownTime;
    static float downX, downY;

    @SuppressLint("NewApi")
    public static Action handleTouch(View vw, MotionEvent evt) {
        int actInt = evt.getAction();
        // String act = MotionEvent.actionToString(actInt);
        Action action = new Action(); // Action caller should perform for touch
        int pId = evt.getActionIndex();
        if (actInt == MotionEvent.ACTION_DOWN) {
            touchDownTime = evt.getDownTime();
            downX = evt.getX();
            downY = evt.getY();
            action.x = downX / vw.getWidth();
            action.y = downY / vw.getHeight();
            if (action.y > .8) {
                action.type = actionType.downLow;
            }
            return action;
        }

        action.x = downX / vw.getWidth();
        action.y = downY / vw.getHeight();
        double dt = (evt.getEventTime() - touchDownTime) / 1000.;
        // .x and .y are a fraction from 0 to .999 of the position of the down
        // touch within the view.
        if (actInt == MotionEvent.ACTION_UP) {
            int dx = (int) (downX - evt.getX());
            int dy = (int) (downY - evt.getY());
            int adx = Math.abs(dx);
            int ady = Math.abs(dy);
            double dist = Math.sqrt(dx * dx + dy * dy);
            String vid = (String) vw.getTag();
            // If down and up positions are close, and delta down time is short...
            if (adx < 30 && ady < 30) {
                // prt("  VMthumbs performClick "+Integer.toHexString(vw.getId())+", "
                //  +dt+" sec");
                if (dt < 1.) {
                    action.type = actionType.click;
                } else {
                    action.type = actionType.longClick;
                }

            } else if (dist > 200) {
                boolean n = false, e = false, s = false, w = false;
                if (ady > adx / 2) {
                    if (dy > 0) {
                        n = true;
                    } else {
                        s = true;
                    }
                }
                if (adx > ady / 2) {
                    if (dx > 0) {
                        w = true;
                    } else {
                        e = true;
                    }
                }

                if (n) {
                    if (e) { // NE
                        action.type = actionType.NE;
                    } else if (w) { // NW
                        action.type = actionType.NW;
                    } else { // N
                        action.type = actionType.N;
                    }
                } else if (s) {
                    if (e) { // SE
                        action.type = actionType.SE;
                    } else if (w) { // SW
                        action.type = actionType.SW;
                    } else { // S
                        action.type = actionType.S;
                    }
                } else {
                    if (e) { // E
                        action.type = actionType.E;
                    } else if (w) { // W
                        action.type = actionType.W;
                    }
                }
                // end of swiping some distance
            } else {
                prt("onTouch intermediate distance?");
            }
        } // end of ACTION_UP

        evt.findPointerIndex(pId);
        // int eFlags = evt.getEdgeFlags();
        // int flags = evt.getFlags();
        // int hSize = evt.getHistorySize();
        // int pCnt = evt.getPointerCount();
        // float press = evt.getPressure(pId);
        // float size = evt.getSize(pId);
        // prt(" tMotion "+Integer.toHexString(vw.getId())+" "+act+" "
        //  +(int)evt.getX()+","+(int)evt.getY()
        //  +" t"+dt+"\t"+Integer.toHexString(flags)+"-"+Integer.toHexString(eFlags)
        //  +" "+hSize+" "+press+"/"+size+" "+pCnt);
        return action;
    } // end of handleTouch

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of MCapp class
