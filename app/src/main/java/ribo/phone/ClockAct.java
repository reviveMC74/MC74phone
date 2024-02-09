package ribo.phone;

import android.annotation.*;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.drawerlayout.widget.*;
import com.ibm.ssm.*;
import com.ibm.ssm.tree.*;
import com.ibm.ssm.tree.nob.*;
import org.linphone.activities.*;
import java.text.*;
import java.util.*;
import static org.linphone.LinphoneContext.*;
import static org.linphone.activities.MainActivity.*;
import static ribo.phone.Info.*;

public class ClockAct extends Activity implements View.OnTouchListener, View.OnClickListener {
    public static Thread clockTh;
    public static DrawerLayout drwLay;
    public static TextView timeTv, dateTv;  // Set in createClockAct
    public static boolean hour24Mode = false;
    public static Tree smTr;
    public static int idSeqn = 3072;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String path = getIntent().getStringExtra("path");
        prt("ClockAct.onCreate "+SSMutil.getTimestamp()+", path: "+path);
        // SSM.consoleOS = System.out; // Make SSM.prt output go to Android stdout, logcat
        // SSM.logServer = null; // Make SSM.prt output go to System.out
        super.onCreate(savedInstanceState);
    } // end of onCreate


    private void createView() {
        pkgName = getPackageName();
        if (ctxTr == null)  try {
            prt("ClockAct: ctxTr was null?!");
            ctxTr = Info.getConfig();
        } catch (Exception ex) {
            prt("ClockAct.craeteView ex: "+ex+"\n"+SSMutil.stackTrace(ex));
        }
        // Create a common DrawerLayout for all the windows.
        drwLay = new DrawerLayout(getApplicationContext());
        drwLay.setTag("ClockAct");
        int drwId = idSeqn++;
        drwLay.setId(drwId);
        LinearLayout.LayoutParams llp =
          new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        drwLay.setLayoutParams(llp);
        if (mainAct == null) {
            mainAct = this;  // Needed for setBg
        }
        setBg(drwId);
        drwLay.setOnClickListener(this);
        drwLay.setOnTouchListener(this);

        createClockAct();
        //if (ctxTr == null) {   -- clock is no longer used in sidemenu
        //    prt("!!ClockAct.createView  ctxTr is null");
        //} else {
        //    Tree actCtxTr = (Tree) ctxTr.clone();
        //    actCtxTr.selectMulti(new Bytes("SIDEMENU.CLOCK"), false);
        //    createClockAct();
        //    actCtxTr.release();
        //    actCtxTr = null;
        //}

        // Now create the drawer
        // LinearLayout dLay = new LinearLayout((getApplicationContext()));
        LinearLayout dLay = new LinearLayout(getApplicationContext());
        DrawerLayout.LayoutParams dlp = new DrawerLayout.LayoutParams(
           400, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        dLay.setLayoutParams(dlp);
        dLay.setOrientation(LinearLayout.VERTICAL);

        if (ctxTr != null) {
            if (smTr != null) { smTr.release(); }
            smTr = (Tree) ctxTr.clone();
            if (smTr.select("SIDEMENU")) {
                smTr.prepChildScan();
                while (smTr.next()) {
                    if (smTr.name().charAt(0) == '-') continue; // Skip items markd with a '-'
                    dLay.addView(addSideMenuItem(smTr));
                } // end of while scanning next nodes in sideMenu list
                smTr.parent();
            } // end of selected SIDEMENU node
            // smTr releaseed in onDetach
        }

        drwLay.addView(dLay);

        setContentView(drwLay);
    } // end of createView

    @Override
    public void onClick(View vw) {
        prt("ClockAct.onlick: "+SSMutil.getTimestamp());
    }

    public boolean onTouch(View vw, MotionEvent mev) {
        switch (mev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prt("ClockAct.DOWN " + mev.getX() + "," + mev.getY());
                //if (mev.getY() > 700) {  --make any touch finish the activity
                //    finish();
                //}
                break;
            case MotionEvent.ACTION_MOVE:
                prt("ClockAct.MOVE " + mev.getX() + "," + mev.getY());
                break;
            case MotionEvent.ACTION_UP:
                prt("ClockAct.UP " + mev.getX() + "," + mev.getY());
                vw.performClick();
                break;
            default:
                prt("ClockAct.onTouch, unrecognized action: " + mev);
        }
        return false;
    } // end of onTouch


    public View addSideMenuItem(Tree smTr) {
        LinearLayout itemLay = new LinearLayout(getApplicationContext());
        itemLay.setTag(smTr.name()); // Tag so onClick knows what to do
        LinearLayout.LayoutParams ilp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, 0, 0, 2);
        itemLay.setBackgroundColor(0xffffffff);
        itemLay.setLayoutParams(ilp);

        ImageView iv = new ImageView(getApplicationContext());
        ilp = new LinearLayout.LayoutParams(45, 45);
        ilp.setMargins(10, 10, 10, 10);
        iv.setLayoutParams(ilp);
        String drwName = smTr.value("img");
        Uri imgUrl = Uri.parse("android.resource://" + pkgName + "/drawable/" + drwName);
        iv.setImageURI(imgUrl);
        // iv.setImageDrawable(getResources().getDrawable(R.drawable.menu_about));
        itemLay.addView(iv);

        TextView tv = new TextView(getApplicationContext());
        ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 68);
        ilp.setMargins(8, 0, 24, 0);
        tv.setLayoutParams(ilp);
        tv.setText(smTr.value());
        tv.setTextSize(20);
        tv.setTextColor(0xff000000);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setId(idSeqn++);
        itemLay.addView(tv);

        View vw = new View(getApplicationContext());
        ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        vw.setLayoutParams(ilp);
        vw.setBackgroundColor(0xff000000);
        itemLay.addView(vw);

        // mSideMenuItemList.setOnItemClickListener handle click action for
        // for the SideMenu created on MainActivity by SideMenuFragment
        itemLay.setOnClickListener( miVw -> {
            prt("riboSide menu click " + miVw.getTag());
            drwLay.closeDrawers();
            Tree miTr = (Tree) ctxTr.clone();
            curActSect = "SIDEMENU";
            curActTag = (String) miVw.getTag();
            if (miTr.select(curActSect) && miTr.select(curActTag)) {
                doAction(miTr);
                miTr.release();
            }
        });
        return itemLay;
    } // end of addSideMenuItem


    // Perform action for menu item (defined in MC74 sideMenu or mainFooter)
    public static void doAction(Tree itTr) {
        Bytes clsName = new Bytes();
        Bytes sect = new Bytes();
        Bytes name = new Bytes();
        if (itTr.select("ACT")) {
            parseAction(itTr, clsName, sect, name);
            try {
                Class targClass = Class.forName(clsName.toString());
                if (curAct.getClass() == targClass) {
                    prt("ClockAct.doAction: Switch to different sub act in same act: "+clsName
                      +" "+name);
                }
                Intent intent = new Intent(curAct, targClass);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("path", sect + "." + name.toString());
                curAct.startActivity(intent);
            } catch (Exception ex) {
                prt("ClockAct.doAction ex: " + ex);
            }

        } else if (itTr.select("CALL")) {
            parseAction(itTr, clsName, sect, name);
            prt("ClockAct.doAction UIF CALL: " + name + " " + itTr.value() + "\n" + itTr.list());
            if (name.compareToIgnoreCase("QUIT") == 0) {
                MainActivity.quit();
            }

        } else {
            prt("ClockAct.doAction: No action for menu item '" + itTr.name() + "'");
        }
    } // end of doAction

    public static void parseAction(Tree itTr, Bytes clsName, Bytes sect, Bytes name) {
        Bytes line = itTr.valueBytes(new Bytes());
        line.parseTok(clsName);
        if (line.parseTok(name) == null) {
            Tree tr = (Tree) itTr.clone();
            tr.parent();
            name.set(tr.name());
            tr.parent();
            sect.set(tr.name());
            tr.release();
        }
    }

    void createClockAct() {
        LinearLayout lLay = new LinearLayout(getApplicationContext());
        lLay.setTag("clockLay");
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lLay.setLayoutParams(llp);
        Tree tr = (Tree)ctxTr.clone();
        String bgPath = tr.value("bg");
        if (tr.select("CONFIG") && tr.select("SCREENSAVER")) {
          if (tr.select("IMG")) {
              bgPath = tr.value();
              tr.parent();
          }
          if (tr.select("24HOUR")) {
              hour24Mode = true;
          }
        }
        tr.release();
        if (bgPath != null && bgPath.length() > 0) {
            lLay.setBackground(getDrw(bgPath));
        }
        lLay.setOrientation(LinearLayout.VERTICAL);

        timeTv = new TextView(getApplicationContext());
        LinearLayout.LayoutParams llLP = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 600);
        //llLP.setMargins(0, 0, 100, 100);
        timeTv.setLayoutParams(llLP);
        timeTv.setTextSize(200);
        timeTv.setTextColor(0xFFffffff);
        timeTv.setGravity(Gravity.CENTER);
        timeTv.setId(idSeqn++);
        lLay.addView(timeTv);

        dateTv = new TextView(getApplicationContext());
        llLP = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
        llLP.setMargins(0, 100, 50, 0);
        dateTv.setLayoutParams(llLP);
        dateTv.setTextSize(20);
        dateTv.setTextColor(0xFFffffff);
        dateTv.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        dateTv.setId(idSeqn++);
        lLay.addView(dateTv);

        prt("createClockAct: dateTv "+dateTv.hashCode()+", timeTv "+timeTv.hashCode());

        drwLay.addView(lLay);

        // Start background clock updater thread
        if (clockTh != null) {  // If a previous Clock Act existed, remove it
            clockTh.interrupt();
        }

        clockTh = new Thread() {
            String dtPattern = "EEEEE, MMMMM dd";
            SimpleDateFormat dtSdf = new SimpleDateFormat(dtPattern,
              new Locale("en", "US"));


            public void run() {
                prt("clockActUpdater thread runs "+SSMutil.getTimestamp());
                while (true) {
                    try {
                        Calendar cal = Calendar.getInstance();
                        int sec = cal.get(Calendar.SECOND);
                        int millis = cal.get(Calendar.MILLISECOND);
                        String was = cal.toString();

                        // cal.add(Calendar.MILLISECOND, delay);
                        String dt = dtSdf.format(cal.getTime());
                        int hr = cal.get(Calendar.HOUR_OF_DAY);
                        String fmt = "%02d:%02d";
                        if (!hour24Mode) {
                            hr = hr%12;
                            fmt = "%2d:%02d";
                        }  // Not 24 hour mode
                        @SuppressLint("DefaultLocale")
                        String tm = String.format(fmt, hr, cal.get(Calendar.MINUTE));

                        runOnUiThread(
                          () -> {
                              dateTv.setText(dt);
                              timeTv.setText(tm);
                          });

                        // Delay until the end of the minute
                        int delay = 60000 - (sec * 1000 + millis);
                        sleep(delay);

                        prt("clock update delay "+delay/1000.+", set: "+dt+" "+tm);
                        //prt("clockActUpdater: dateTv " + dateTv.hashCode() + ", timeTv " + timeTv.hashCode());
                    } catch (InterruptedException iex) {
                        prt("clockActUpdater Interupted, ending");
                        return;
                    } catch (Exception ex) {
                        prt("clockActUpdater ex: " + ex + "\n" + SSMutil.stackTrace(ex));
                    }
                } // end of while true
            } // end of run
        }; // end of new Thread
        clockTh.setName("clockActUpdater");
        prt("clockTh thread started ");
        clockTh.start();
    } // end of createCLockAct


@Override
protected void onNewIntent(Intent ni) {
    super.onNewIntent(ni);
    prt("ClockAct.onNewIntent: new " + ni);
    setIntent(ni);
} // end of onNewIntent


@Override
protected void onResume() {
    super.onResume();
    Intent it = getIntent();
    prt("ClockAct.onResume: "+it);
    curActName = "CLOCK";  // Name in MC74.sideMenu.clock to get config info, like bg
    curAct = this;

    createView();
} // end of onResume


@Override
protected void onPause() {
    super.onPause();
    prt("ClockAct.onPause: "+SSMutil.getTimestamp());
}


@Override
protected void onStop() {
    super.onStop();
    prt("ClockAct.onStop: "+SSMutil.getTimestamp());
    if (clockTh != null) {
        clockTh.interrupt();
        clockTh = null;
    }
} // end of onStop


@Override
protected void onDestroy() {
    prt("ClockAct.onDestroy: "+SSMutil.getTimestamp());
    super.onDestroy();
    //if (clockTh != null) {
    //    clockTh.interrupt();
    //    clockTh = null;
    //}
} // end of onDestroy


public static void prt(String str) {
    System.out.println(str);
}
} // end of ClockAct class
