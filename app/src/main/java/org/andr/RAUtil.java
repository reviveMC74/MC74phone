/**
 * RiboAndrUtil -- Bob Flavin's Utilities for Android (c) 2011 Copyright IBM Corporation, R A Flavin
 */
package org.andr;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import com.ibm.ssm.SSMutil;
import com.ibm.ssm.tree.Bytes;
import com.ibm.ssm.tree.Tree;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import org.xmlpull.v1.XmlPullParser;

public class RAUtil {

    public static String spaces = "                                               ";

    public static String xmlToTree(Tree tr, XmlResourceParser pr) {
        Bytes line = new Bytes();
        try {
            loop:
            while (true) {
                int ndType = pr.getEventType();
                switch (ndType) {
                    case XmlPullParser.START_TAG:
                        tr.addSelect(pr.getName(), null);
                        for (int ii = 0; ii < pr.getAttributeCount(); ii++) {
                            tr.add("+" + pr.getAttributeName(ii), pr.getAttributeValue(ii));
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        tr.parent();
                        break;
                    case XmlPullParser.TEXT:
                        if (tr.valueBytes(line).length() > 0) {
                            tr.changeValue(line + pr.getText());
                        } else {
                            tr.changeValue(pr.getText());
                        }
                        break;
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        break loop;
                    default:
                        tr.add(
                                "(unknownNode)",
                                ndType + " at " + pr.getLineNumber() + ":" + pr.getColumnNumber());
                } // end of switch ndType
                ndType = pr.next();
            }
            return null; // No exceptions, no response needed

        } catch (Exception ex) {
            // Could throw XmlPullParserException or IOException (from pr.next())
            return "xmlToTree ex: " + ex + "\n" + stackTrace(ex);
        }
    } // end of xmlToTree

    public static int execOSCmd(
            String cmd, StringBuffer sb // Place to return stdout/stderr (or null to ignore)
            ) {
        return execOSCmd(cmd, sb, null, null);
    }

    @SuppressWarnings("unused")
    public static int execOSCmd(
            String cmd,
            StringBuffer sb, // Place to return stdout/stderr (or null to ignore)
            String[] env,
            File cwd) {
        String line;
        int rc = -999;
        Process proc;
        try {
            // Launch command in a separate process
            proc = Runtime.getRuntime().exec(cmd, env, cwd);

            rc = proc.waitFor(); // Wait for the process to complete
            // prt("Command '"+cmd+"' returned rc="+rc);
            InputStreamReader isr = new InputStreamReader(proc.getErrorStream());
            BufferedReader rdr = new BufferedReader(isr);
            while ((line = rdr.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            isr = new InputStreamReader(proc.getInputStream());
            rdr = new BufferedReader(isr);
            while ((line = rdr.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (Throwable ex) {
            String exMsg;
            if (ex == null) { // ?? On Android, ex may null
                exMsg = "ex is null for cmd: " + cmd;
            } else {
                exMsg = ex + "\n" + stackTrace(ex);
                Throwable cause = ex.getCause();
                if (ex != cause) {
                    exMsg += "\n\ncause ex: " + cause + "\n" + stackTrace(cause);
                }
            }
            System.err.println("  execOSCmd ex: " + exMsg);
            sb.append("(");
            sb.append(exMsg);
            sb.append(")");
        }
        return rc;
    } // end of execOSCmd

    public static String stackTrace(Throwable t) {
        StringBuffer sb = new StringBuffer();
        StackTraceElement[] ste = t.getStackTrace();
        for (int i = 0; i < ste.length; i++) {
            sb.append(ste[i].toString());
            sb.append("\n");
        }
        return sb.toString();
    } // end of stackTrace

    public static String stackTrace() {
        Exception ex = new Exception();
        ex.fillInStackTrace();
        return stackTrace(ex);
    } // end of stackTrace

    public static String getInfo(Activity act, View vw) {
        StringBuffer sb = new StringBuffer();

        try {
            WindowManager wm = act.getWindowManager();

            Display disp = wm.getDefaultDisplay();
            sb.append("display:\n");
            sb.append("  dispId=" + disp.getDisplayId() + '\n');
            sb.append("  orient=" + disp.getOrientation() + '\n');
            sb.append("  WxH=" + disp.getWidth() + "x" + disp.getHeight() + '\n');
            sb.append("  pxFormat=" + disp.getPixelFormat() + '\n');
            sb.append("  refreshRate=" + disp.getRefreshRate() + '\n');
            sb.append("  rotation=" + disp.getRotation() + '\n');
            DisplayMetrics met = new DisplayMetrics();
            disp.getMetrics(met);
            sb.append("  dispMetrics=" + met + '\n');
            sb.append("\n");

            Application app = act.getApplication();
            sb.append("  application=" + app + '\n');
            Context appCtx = act.getApplicationContext();
            sb.append("  appCtx=" + appCtx + '\n');
            AssetManager am = act.getAssets();
            sb.append("  assetMgr=" + am + '\n');
            Context baseCtx = act.getBaseContext();
            sb.append("  baseCtx=" + baseCtx + '\n');
            sb.append("  cacheDir=" + act.getCacheDir() + '\n');
            sb.append("  callingAct=" + act.getCallingActivity() + '\n');
            sb.append("  callingPkg=" + act.getCallingPackage() + '\n');
            sb.append("  changingConfigs=" + act.getChangingConfigurations() + '\n');
            ClassLoader cl = act.getClassLoader();
            sb.append("  classLoader=" + cl + '\n');
            sb.append("  componentName=" + act.getComponentName() + '\n');
            ContentResolver cr = act.getContentResolver();
            sb.append("  contentResolover=" + cr + '\n');
            sb.append("  currentFocus=" + act.getCurrentFocus() + '\n');
            sb.append("  extCacheDir=" + act.getExternalCacheDir() + '\n');
            sb.append("  filesDir=" + act.getFilesDir() + '\n');
            sb.append("  intent=" + act.getIntent() + '\n');
            sb.append("  localClassName=" + act.getLocalClassName() + '\n');
            sb.append("  mainLooper" + act.getMainLooper() + '\n');
            sb.append("  pgkCodePath=" + act.getPackageCodePath() + '\n');
            sb.append("  pkgMgr=" + act.getPackageManager() + '\n');
            sb.append("  PkgResPath=" + act.getPackageResourcePath() + '\n');
            sb.append("  parent=" + act.getParent() + '\n');
            sb.append("  requestedOrient=" + act.getRequestedOrientation() + '\n');
            sb.append("  resources=" + act.getResources() + '\n');
            sb.append("  taskId=" + act.getTaskId() + '\n');
            sb.append("  theme=" + act.getTheme() + '\n');
            sb.append("  title=" + act.getTitle() + '\n');
            sb.append("  titleColor=" + act.getTitleColor() + '\n');
            try {
                Drawable wp = act.getWallpaper();
                sb.append("  wallPaper=" + wp + '\n');
                sb.append(
                        "  wpDesMinSize="
                                + act.getWallpaperDesiredMinimumWidth()
                                + "x"
                                + act.getWallpaperDesiredMinimumHeight()
                                + '\n');
            } catch (Exception ex) {
            }
            Window ww = act.getWindow();
            sb.append("  window=" + ww + '\n');
            // not found  sb.append("  instCnt=" + Activity.getInstanceCount());
        } catch (Exception ex) {
            prt("showInfo ex " + ex + "\n" + SSMutil.stackTrace(ex));
        }

        return sb.toString();
    } // getInfo

    public static StringBuffer showView(String cmt, Object vwObj) {
        if (vwObj instanceof View == false) {
            return new StringBuffer("(vwObj is not a View)");
        }
        View vw = (View) vwObj;
        StringBuffer sb = new StringBuffer();

        Rect rect = new Rect();
        vw.getDrawingRect(rect);
        sb.append("\n" + cmt);
        sb.append(
                "    rect "
                        + rect.left
                        + ","
                        + rect.top
                        + " "
                        + rect.width()
                        + "x"
                        + rect.height()
                        + "\n");

        Point pos = new Point(vw.getLeft(), vw.getRight());
        Point wh = new Point(vw.getWidth(), vw.getHeight());
        sb.append(
                "VW: "
                        + Integer.toHexString(vw.getId())
                        + " "
                        + vw.getClass().getName()
                        + " "
                        + pos.x
                        + ","
                        + pos.y
                        + " "
                        + wh.x
                        + "x"
                        + wh.y
                        + " "
                        + visibilityStr(vw)
                        + "\n");

        sb.append("\n");

        ViewParent vp = vw.getParent();
        while (vp != null) {
            sb.append("-- ");
            sb.append(vp instanceof View ? ((View) vp).getId() : "<>");
            sb.append(" " + vp + "\n");
            vp = vp.getParent();
        }

        LayoutParams lp = vw.getLayoutParams();
        if (lp != null) {
            sb.append("  LP wxh: " + lp.width + " " + lp.height);
        }
        sb.append("\n");

        ViewGroup root = (ViewGroup) (vw instanceof ViewGroup ? vw : vw.getRootView());
        showViewTree((ViewGroup) root, sb, 1).toString();
        return sb;
    } // end of showView

    public static String showViewTree(View vw) {
        return showViewTree(vw, new StringBuffer(), 0).toString();
    } // end of showViewTree

    public static StringBuffer showViewTree(View vw, StringBuffer sb, int indent) {
        sb.append(spaces.substring(0, indent*2)+vw.getClass().getName()
          +" "+Integer.toHexString(vw.getId())+" "+vw.getTag()
          +" "+vw.getLeft()+","+vw.getTop()+" "+vw.getWidth()+"x"+vw.getHeight()
          +" "+vw.getContentDescription()
          +(vw instanceof TextView ? "-"+((TextView) vw).getText() : "")
          +" "+visibilityStr(vw)+"\n");

        if (vw instanceof ViewGroup) {
            for (int ii = 0; ii < ((ViewGroup) vw).getChildCount(); ii++) {
                showViewTree(((ViewGroup) vw).getChildAt(ii), sb, indent + 1);
            }
        }
        return sb;
    } // end of showViewTree

    public static String visibilityStr(View vw) {
        int vis = vw.getVisibility();
        return vis == 0 ? "VIS" : (vis == 4 ? "INVIS" : "GONE");
    } // end of visibilityStr

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of class RAUtil
