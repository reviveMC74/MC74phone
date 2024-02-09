// WebPanel -- Activity for HTML, WevView based pages for the 'footer' (side buttons)
// as defined in MC74.mp:
//  mainFooter:
//    history:
//      act: 'ribo.phone.WebPanel'
//      img: 'footer_log'
//      url: 'http://localhost:1808/logpanel'
//    sms:
//      act: 'ribo.phone.WebPanel'
//      img: 'footer_sms'
//      url: 'http://localhost:1808/smspanel'

package ribo.phone;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import com.ibm.ssm.tree.*;
import com.ibm.ssm.tree.nob.*;
import org.linphone.*;
import org.linphone.activities.*;
import java.util.*;
import static com.ibm.ssm.SSM.flatToTree;
import static com.ibm.ssm.SSM.treeToFlat;
import static org.andr.RAUtil.*;
import static ribo.phone.Info.*;
import static ribo.ssm.SSMcmd.ssmCmdReply;

public class WebPanel extends MainActivity {
    public static WebPanel webP;
    public static String ctxPath;
    public static String layoutOid;  // Overrides ctxPath if specified
    public Tree wpCtxTr;
    public static WebView webVw;
    public static WebSettings ws;
    public static WebViewClient wvc;
    public static WebChromeClient wcc;
    public static String NAME = "WebPanel"; // Name used by MainActivity for -select
    //public static int primaryColor; // Light blue
    public static Queue<String> jsQ = new LinkedList<String>();  // JS command queue for webView
    public static Queue<String> jsRespQ = new LinkedList<String>();  // JS command response queue
    public static long jsQLastPoll;  // System.currentTimeMillis() of last JS getReq poll


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prt("WebPanelAct.onCreate");
        getIntent().putExtra("Activity", "WebPanel");
        super.onCreate(savedInstanceState);
        webP = this;

        Intent it = getIntent();
        ctxPath = it.getStringExtra("path");
        layoutOid = it.getStringExtra("layoutOid");
    } // end of onCreate

    protected void onStart() {
        prt("WebPanelAct.onStart");
        super.onStart();
        webP = this;  // curAct doesn't seem to get restored in onResume some times.

        // Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.main_bg);
        if (currentFragment == null) {
            WebPanelFragment fragment = new WebPanelFragment();
            // changeFragment(fragment, "WebPanel", false);
            ViewGroup cont = findViewById(R.id.main_bg);
            View vw = fragment.onCreateView(null, cont, null);
            cont.addView(vw);
        }
    } // end of onStart


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ctxPath = intent.getStringExtra("path");
        layoutOid = intent.getStringExtra("layoutOid");
        prt("WebPanel.onNewIntent: "+intent+", ctxPath: "+ctxPath+", layoutOid: "+ layoutOid);
    } // end of onNewIntent


    protected void onResume() {
        prt("WebPanelAct.onResume");
        curAct = this;
        super.onResume();

        Tree wpCtxTr = (Tree)ctxTr.clone();
        prt("WebPanel.onResume ctxPath: "+ctxPath+", layoutOid: "+ layoutOid);
        String url = "http://localhost:1808/launcher";  // Default launcher URL
        if (layoutOid != null) {
           url = "http://localhost:1808/launcher/get/"+ layoutOid;

        } else if (wpCtxTr.selectMulti(new Bytes(ctxPath), false)) {
            url = wpCtxTr.value("URL");
        }
        prt("  url: "+url);
        if (url != null && url.length() > 0) {
            if (url.substring(0, 4).compareToIgnoreCase("WRT ") == 0) {
                new WebAct.LoadHTML().execute(url);
            } else {
                webVw.loadUrl(url);
            }
        }
        wpCtxTr.release();
    } // end of onResume

    @Override
    protected void onPause() {
        super.onPause();
    }

    protected void onDestroy() {
        prt("WebPanelAct.onDestroy");
        webP = null;
        super.onDestroy();
    }

    public static class WebPanelFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
            prt("WebPanelFragment.onCreateView: " + container);

            webVw = new WebView(webP.getApplicationContext());
            LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            webVw.setLayoutParams(lllp);
            View.OnClickListener ocl = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prt("WebPanel onclick");
                    webP.finish();
                }
            };
            webVw.setOnClickListener(ocl);
            webVw.setInitialScale(150);
            webP.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            ws = webVw.getSettings();
            ws.setJavaScriptEnabled(true);
            webVw.setWebChromeClient(wcc = new WebAct.WCC());
            webVw.setWebViewClient(wvc = new WebAct.WVC());
            webVw.addJavascriptInterface(new JSInterface(webP.getApplicationContext()), "host");

            //((ViewGroup) webVw.getParent()).setOnClickListener(ocl);
            //prt(showViewTree(webVw.getRootView()));

            String html = "<body>\n" + "  <center style='color:gray'>(Loading)</center>\n" + "</body>";
            webVw.loadData(html, "text/html; charset=UTF-8", null);
            prt("\nwebPanel returned from onCreateView:\n" + showViewTree(webVw));
            return webVw;
        } // end of onCreateView
    } // end of WebPanelFragment class


    public static String minSecStr(int sec) {
        // Convert number of seconds into a string of the form: 'mm:ss'
        return String.format("%02d:%02d", sec / 60, sec % 60);
    } // end of minSecStr

    static class VoipCmd extends AsyncTask<Object, Void, String> {
        public View wpLnVw;

        protected String doInBackground(Object... objs) {
            String cmd = (String) objs[0];
            if (cmd.substring(0, 4).compareToIgnoreCase("WRT ") != 0) {
                cmd = "WRT MC74 " + cmd;
            }
            Tree tr = svrCmdReply(localSSM, cmd);
            prt("VoipCmd resp: " + tr.value() + "\n" + tr.list());
            tr.release();
            return null;
        }

        protected void onPostExecute(String resp) {
            if (resp == null) {
                ((ViewGroup) wpLnVw.getParent()).removeView(wpLnVw);
            } else {
                prt("VoipCmd resp: " + resp);
            }
        }
    } // end of VoipCmd

// JSInterface allows javascript (though the 'host' variable) to access Java methods below.
// Use of webVw.evaluateJavascript() is only supported in API level 19, not level 17 that we have
public static class JSInterface {
    Context ctx;

    JSInterface(Context ctx) {
        this.ctx = ctx;
    }

    @android.webkit.JavascriptInterface
    public void setTitle(String title) {  // Let javascript set the MainActivity StatusBar title
        prt("WebPanel.javascript.setTitle: "+title);
        webP.runOnUiThread(new Runnable() { @Override public void run() {
            TextView tv = webP.findViewById(R.id.status_text);
            if (tv != null) {
                tv.setText(title);
            }
        }});
    } // end of setTitle


    @android.webkit.JavascriptInterface
    public void back() {
        prt("WebPanel.javascript.back: ");
        webP.runOnUiThread(new Runnable() { @Override public void run() {
            webVw.clearCache(true);
        }});
    } // end of back


    @android.webkit.JavascriptInterface
    public String queryPhone(String attrPath) {
        prt("WebPanel.javascript.queryPhone: '"+attrPath+"'");
        Tree tr = svrCmdReply(localSSM, "QRT PHONE."+attrPath);

        // If repsonse is a simple string value, return it, else control the
        // SSM Tree object into a complicated nest of objs and arrays
        return tr.hasChildren() ? tr.toJSON() : tr.value();
    } // end of queryPhone


    @android.webkit.JavascriptInterface
    public String getReq(String tag, String msg) {
        // This method is used to allow commands to be sent to javascript
        // (The version of WebView in JellyBean doesn't support sending commands
        // through JavaScriptInterface (as later version do)) and get responses,
        // and to allow MC74util.js cmdResp to send commands to the SSM with responses.
        // jsQ and jsRespQ are managed by ribo.phone.Info PHSSMCmd 'sendCmd'
        // The web page has to call debugPoll() from /ssm/script/MC74.js to poll
        // Commands are usually sent with:
        //  gmpr -shphcom -cjcmd wrt mc74 recent
        // -s means enter a command session(loop)
        // -c jcmd means prefix each line type in in the session with the 'jcmd' command
        jsQLastPoll = System.currentTimeMillis();   // Know when WdbView reqPoll() is last active
        if (tag!=null) {  // Did we get a message (a new command, or a response)?
            prt("getReq, tag: "+tag+", msg: "+msg);
            if (tag.charAt(0) == 'C') {
                // Execute this command, return the response
                String host = "localhost";
                if (msg.length()>3 && msg.substring(0, 3).compareToIgnoreCase("AT ")==0) {
                    int ii = msg.indexOf(' ', 3);
                    host = msg.substring(3, ii);
                    msg = msg.substring(ii+1);
                }
                Tree cmdTr = new NB();
                flatToTree(new Bytes(msg), cmdTr);  // Parse possible subnodes, and set base value to the cmd str
                //!!! need to change cmdTr to allow cmds to be forwarded to other server??
                Tree respTr = ssmCmdReply(null, cmdTr);
                cmdTr.release();
                String resp = "(No response, connection failed?)";
                if (respTr != null) {
                    resp = treeToFlat(respTr);  // Convert tree to a JSON like string, reverse of flatToTree
                    respTr.release();
                }
                jsQ.add("R"+tag.substring(1)+" "+resp);

            } else {  // This is a response, put it on the response objct
                synchronized (jsRespQ) {
                    jsRespQ.add(msg);
                    jsRespQ.notify();
                }
            }
        }

        if (jsQ.isEmpty() == false) {  // Is this really a request for the next cmd?
            prt("HTMLact.javascript.getReq " + jsQ.size() + ", " + msg);
            return jsQ.remove();
        }
        return null;
    } // end of getReq


    @android.webkit.JavascriptInterface
    public String getReq2(String reqIn) {
        // This method is used to allow commands to be sent to javascript
        // (The version of WebView in JellyBean doesn't support sending commands
        // through JavaScriptInterface (as later version do))
        // jsQ and jsRespQ are managed by ribo.phone.Info PHSSMCmd 'sendCmd'
        // The web page has to call debugPoll() from /ssm/script/MC74.js to poll
        // Commands are usually sent with:
        //  gmpr -shphcom -cjcmd wrt mc74 recent
        // -s means enter a command session(loop)
        // -c jcmd means prefix each line type in in the session with the 'jcmd' command
        if (reqIn!=null && reqIn.length()>0) {  // Is this a call to send us a response?
          prt("getReq, resp: "+reqIn);
          synchronized(jsRespQ) {
              jsRespQ.add(reqIn);
              jsRespQ.notify();
          }
          return null;  // Don't send a new command in response call

        } else if (jsQ.size() > 0) {  // Is this really a request for the next cmd?
            prt("WebPanel.javascript.getReq " + jsQ.size() + ", " + reqIn);
            return jsQ.remove();
        }
        return null;
    } // end of getReq


    @android.webkit.JavascriptInterface
    public void resetCnt(String cntName) {  // Reset the SMS, Voicemail or MissedCalls counters
        Tree tr = svrCmdReply(centralSvr, "WRT VOIP RESETCNT "+cntName);
        prt("WebPanel.resetCnt to "+centralSvr+" reset "+cntName+", resp: "+tr.list());
        tr.release();
    } // end of resetCnt


    @android.webkit.JavascriptInterface
    public String getLog(String oid) {  // Return the LOG subtree of the named object, from centralSvr
        oid = oid.split(" ")[0];  // Prevent command injection attacks

        // 'oid' may include source host and tree subnode as in:  [host/]oid[.node]
        String host = centralSvr, node = "log";
        int ii = oid.indexOf('/');
        if (ii>0) {
            host = oid.substring(0, ii);
            oid = oid.substring(ii+1);
        }
        ii = oid.indexOf('.');
        if (ii>0) {
            node = oid.substring(ii+1);
            oid = oid.substring(0, ii);
        }

        Tree tr = svrCmdReply(host, "GETJSON "+oid+"."+node);
        prt("WebPanel.getLog to "+host+" get "+oid+"."+node);
        String resp = tr.value();
        tr.release();
        return resp;
    } // end of getLog


    @android.webkit.JavascriptInterface
    public void qZoom() {
        prt("WebPanel.qZoom: scale "+webVw.getScaleX()+","+webVw.getScaleY()
          +", supportZoom "+ws.supportZoom()+", useWideViewPort "+ws.getUseWideViewPort()
          +", defaultZoom "+ws.getDefaultZoom()+", builtInZoomControls "+ws.getBuiltInZoomControls()
          +", displayZoomControls "+ws.getDisplayZoomControls());
        //webVw.setInitialScale(150);
        //ws.setSupportZoom(false);
        //ws.setBuiltInZoomControls(false);
        //ws.setLoadWithOverviewMode(false);
        //ws.setUseWideViewPort(false);
        //ws.setJavaScriptCanOpenWindowsAutomatically(true);
    } // end of qZoom


    @android.webkit.JavascriptInterface
    public void showToast(String msg) {
        // myHandler.post(new Runnable() {
        //    @Override
        //    public void run() {
        //        // This gets executed on the UI thread so it can safely modify Views
        //        myTextView.setText(msgeToast);
        //    }
        // });

        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    } // end of showToast
} // end of JSInterface


public static void prt(String str) {
        System.out.println(str);
    }
} // end of WebPanelAct class
