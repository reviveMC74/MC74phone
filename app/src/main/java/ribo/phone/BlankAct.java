package ribo.phone;

import android.annotation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.drawerlayout.widget.*;
import com.ibm.ssm.*;
import com.ibm.ssm.tree.*;
import com.ibm.ssm.tree.nob.*;
import org.linphone.activities.*;
import static org.linphone.LinphoneContext.*;
import static org.linphone.activities.MainActivity.*;
import static ribo.phone.Info.*;

public class BlankAct extends Activity implements View.OnTouchListener, View.OnClickListener {
    public static Tree actCtxTr;
    public static String ctxPath;
    public static DrawerLayout drwLay;
    public static Tree smTr;
    public static int idSeqn = 2048;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String path = getIntent().getStringExtra("path");
        prt("BlankAct.onCreate "+path+" "+hashCode());
        // SSM.consoleOS = System.out; // Make SSM.prt output go to Android stdout, logcat
        // SSM.logServer = null; // Make SSM.prt output go to System.out
        super.onCreate(savedInstanceState);
    } // end of onCreate


    @SuppressLint("ClickableViewAccessibility")
    private void createView() {
        pkgName = getPackageName();
        if (ctxTr == null)  try {
            prt("BlankAct: ctxTr was null?!");
            ctxTr = Info.getConfig();
        } catch (Exception ex) {
            prt("BlankAct.craeteView ex: "+ex+"\n"+SSMutil.stackTrace(ex));
        }
        // Create a common DrawerLayout for all the windows.
        drwLay = new DrawerLayout(getApplicationContext());
        drwLay.setTag("BlankAct");
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
        drwLay.setOnTouchListener(this);

        actCtxTr = (Tree) ctxTr.clone();
        actCtxTr.selectMulti(new Bytes(ctxPath), false);
        createBlankAct();
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


void createBlankAct() {  // Create black screen -- for displaying in 'deep sleep'
    prt("createBlankAct");
    LinearLayout lLay = new LinearLayout(getApplicationContext());
    lLay.setTag("blankLay");
    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    lLay.setLayoutParams(llp);
    lLay.setBackgroundColor(Color.BLACK);

    drwLay.addView(lLay);
} // end of createBlankAct


@Override
    public void onClick(View vw) {
        prt("BlankAct.onlick: "+vw);
    }

    public boolean onTouch(View vw, MotionEvent mev) {
        switch (mev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prt("BlankAct.DOWN " + mev.getX() + "," + mev.getY());
                //if (mev.getY() > 700) {  -- Any touch on blankAct causes it to end
                    finish();
                //}
                break;
            case MotionEvent.ACTION_MOVE:
                prt("BlankAct.MOVE " + mev.getX() + "," + mev.getY());
                break;
            case MotionEvent.ACTION_UP:
                prt("BlankAct.UP " + mev.getX() + "," + mev.getY());
                vw.performClick();
                break;
            default:
                prt("BlankAct.onTouch, unrecognized action: " + mev);
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
                    prt("BlankAct.doAction: Switch to different sub act in same act: "+clsName
                      +" "+name);
                }
                Intent intent = new Intent(curAct, targClass);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("path", sect + "." + name.toString());
                curAct.startActivity(intent);
            } catch (Exception ex) {
                prt("BlankAct.doAction ex: " + ex);
            }

        } else if (itTr.select("CALL")) {
            parseAction(itTr, clsName, sect, name);
            prt("BlankAct.doAction UIF CALL: " + name + " " + itTr.value() + "\n" + itTr.list());
            if (name.compareToIgnoreCase("QUIT") == 0) {
                MainActivity.quit();
            }

        } else {
            prt("BlankAct.doAction: No action for menu item '" + itTr.name() + "'");
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



@Override
protected void onNewIntent(Intent ni) {
    super.onNewIntent(ni);
    prt("BlankAct.onNewIntent: new " + ni);
    setIntent(ni);
} // end of onNewIntent


@Override
protected void onResume() {
    super.onResume();
    Intent it = getIntent();
    ctxPath = it.getStringExtra("path");
    prt("BlankAct.onResume: (ctxPath " + ctxPath+")");
    curAct = this;
    curActName = "BlankAct";

    createView();
} // end of onResume


@Override
protected void onPause() {
    super.onPause();
    prt("BlankAct.onPause:");
}


@Override
protected void onStop() {
    super.onStop();
    prt("BlankAct..onStop:");
} // end of onStop


@Override
protected void onDestroy() {
    prt("BlankAct.onDestroy:");
    super.onDestroy();
} // end of onDestroy


public static void prt(String str) {
    System.out.println(str);
}
} // end of BlankAct class
