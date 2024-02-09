package ribo.phone;

import static org.linphone.activities.MainActivity.setBg;
import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.curAct;
import static ribo.phone.Info.curActSect;
import static ribo.phone.Info.mainAct;
import static ribo.phone.Info.pkgName;
import static ribo.phone.Info.prevActName;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.drawerlayout.widget.DrawerLayout;
import com.ibm.ssm.SSMutil;
import com.ibm.ssm.tree.*;
import org.linphone.activities.*;

public class MultiAct extends Activity implements View.OnTouchListener, View.OnClickListener {
    public static Tree actCtxTr;
    public static String ctxPath;
    public static DrawerLayout drwLay;
    public static Tree smTr;
    public static int idSeqn = 2048;

    public String actName;  // PER INSTANCE activity name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String path = getIntent().getStringExtra("path");
        prt("MultiAct.onCreate "+path+" "+hashCode());
        // SSM.consoleOS = System.out; // Make SSM.prt output go to Android stdout, logcat
        // SSM.logServer = null; // Make SSM.prt output go to System.out
        super.onCreate(savedInstanceState);
    } // end of onCreate

    private void createView() {
        pkgName = getPackageName();
        if (ctxTr == null)  try {
            prt("MultiAct: ctxTr was null?!");
            ctxTr = Info.getConfig();
        } catch (Exception ex) {
            prt("MultiAct.craeteView ex: "+ex+"\n"+SSMutil.stackTrace(ex));
        }
        // Create a common DrawerLayout for all the windows.
        drwLay = new DrawerLayout(getApplicationContext());
        drwLay.setTag("multiAct");
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
        // String bgPath = actCtxTr.value("bg");
        // if (bgPath != null && bgPath.length() > 0) {
        //    Drawable drw = Drawable.createFromPath(bgPath);
        //    drwLay.setBackground(drw);
        // }
        drwLay.setOnTouchListener(this);

        actCtxTr = (Tree) ctxTr.clone();
        actCtxTr.selectMulti(new Bytes(ctxPath), false);
        // Create ONE viewgroup for the body of the DrawerLayout
        if (actName.compareToIgnoreCase("FINISH") == 0) {
            createFinishAct();
        } else {
            // ?? Use reflection to find class to execute?
            prt("MultiAct: Unimplemented activity '" + actName + "'");
        }
        actCtxTr.release();
        actCtxTr = null;

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
    public void onClick(View v) {
        prt("MultiAct.onlick: "+actName);
    }

    public boolean onTouch(View vw, MotionEvent mev) {
        switch (mev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prt("MultiAct.DOWN " + mev.getX() + "," + mev.getY());
                if (mev.getY() > 700) {
                    finish();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                prt("MultiAct.MOVE " + mev.getX() + "," + mev.getY());
                break;
            case MotionEvent.ACTION_UP:
                prt("MultiAct.UP " + mev.getX() + "," + mev.getY());
                vw.performClick();
                break;
            default:
                prt("MultiAct.onTouch, unrecognized action: " + mev);
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
            if (miTr.select(curActSect) && miTr.select((String) miVw.getTag())) {
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
                    prt("MultiAct.doAction: Switch to different sub act in same act: "+clsName
                      +" "+name);
                }
                Intent intent = new Intent(curAct, targClass);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("path", sect + "." + name.toString());
                curAct.startActivity(intent);
            } catch (Exception ex) {
                prt("MultiAct.doAction ex: " + ex);
            }

        } else if (itTr.select("CALL")) {
            parseAction(itTr, clsName, sect, name);
            prt("MultiAct.doAction UIF CALL: " + name + " " + itTr.value() + "\n" + itTr.list());
            if (name.compareToIgnoreCase("QUIT") == 0) {
                ((MainActivity) curAct).quit();
            }

        } else {
            prt("MultiAct.doAction: No action for menu item '" + itTr.name() + "'");
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

void createFinishAct() {  // Not an activity; causes MultiAct to finish
    // This is used by SSM sleep/wakeup function to cause MultiAct to
    // finish, thus showing the previous task/activity/application to appear
    prt("createFinishAct");
    finish();
} // end of createFinishAct


@Override
protected void onNewIntent(Intent ni) {
    super.onNewIntent(ni);
    prt("MultiAct.onNewIntent: "+actName+", new " + ni);
    setIntent(ni);
    prevActName = actName;
    ctxPath = ni.getStringExtra("path");
    if (ctxPath != null) {
        int ii = ctxPath.lastIndexOf('.');
        prevActName = actName;
        actName = ctxPath.substring(ii + 1);
    }
    prt("newIntent " + prevActName + "->" + actName + " path: " + ctxPath);
} // end of onNewIntent


@Override
protected void onResume() {
    super.onResume();
    Intent it = getIntent();
    ctxPath = it.getStringExtra("path");
    prt("MultiAct.onResume: was " + actName + ", will be " + ctxPath+" "+hashCode());
    curAct = this;

    if (ctxPath == null) {
        prt("  ribo.phone.MultiAct, ctxPath==null, setting to sideMenu.weather");
        ctxPath = "sideMenu.weather";
    }
    int ii = ctxPath.lastIndexOf('.');
    prevActName = actName;
    actName = ii >= 0 ? ctxPath.substring(ii + 1) : ctxPath;
    createView();
} // end of onResume


@Override
protected void onPause() {
    super.onPause();
    prt("MultiAct.onPause: "+actName+", prev "+prevActName+" "+hashCode());
}


@Override
protected void onStop() {
    super.onStop();
    prt("MultiAct..onStop: "+actName+", prev "+prevActName+" "+hashCode());
} // end of onStop


@Override
protected void onDestroy() {
    prt("MultiAct.onDestroy: "+actName+", prev "+prevActName+" "+hashCode());
    super.onDestroy();
    if (actName.compareToIgnoreCase("WeatherAct") == 0) {
        prt("destroy weatherAct");
    } else if (actName.compareToIgnoreCase("BEEP") == 0) {
        prt("destroy beepAct");
    }
} // end of onDestroy


public static void prt(String str) {
        System.out.println(str);
    }
} // end of MultiAct class
