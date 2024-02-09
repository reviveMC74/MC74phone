// WebAct -- Activity for HTML WebView based pages for 'sidemenu' items, as defined in MC74.mp
//  sideMenu:
//    img: 'menu_weather'
//    act: 'ribo.phone.WebAct'
//    cmd: 'http://localhost:1808/voipsettings'
//  wRadar: 'Weather Radar'
//    img: 'menu_weather'
//    act: 'ribo.phone.WebAct'
//    cmd: 'http://localhost:1808/weather/radar'
//  wGraph: 'Weather Forecast Graph'
//    img: 'menu_weather'
//    act: 'ribo.phone.WebAct'
//    cmd: 'http://localhost:1808/weather/graph'

package ribo.phone;

import static org.andr.RAUtil.showViewTree;
import static org.linphone.activities.MainActivity.svrCmdReply;
import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.setConfigValue;
import static ribo.ssm.Phone.doKeyEvent;
import static ribo.ssm.SSMcmd.ssmCmdReply;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.http.*;
import android.os.*;
import android.telephony.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.ibm.ssm.tree.*;
import com.ibm.ssm.tree.nob.*;
import com.ibm.ssm.util.*;
import org.linphone.*;
import org.linphone.activities.*;
import org.linphone.assistant.*;
import org.linphone.core.*;
import org.linphone.dialer.*;
import org.linphone.settings.*;
import java.util.*;
import javax.net.ssl.*;
import ribo.ssm.*;

public class WebAct extends AppCompatActivity {
  public static String ctxPath;
  public static Tree actCtxTr;
  public static WebView webVw;
  public static WebSettings ws;
  public static WebViewClient wvc;
  public static WebChromeClient wcc;
  public static String NAME = "web";
  // WebView doesn't seem to use java HttpsURLConnection
  //static {  // Override default Kitkat ssl socket factory to use TLS 1.2 (not use 1.1)
  //  try {
  //    HttpsURLConnection.setDefaultSSLSocketFactory(new TLSSocketFactory());
  //  } catch (Exception ex) {
  //    prt("WebAct.setDefaultSSLSocketFactor ex: "+ex);
  //  }
  //}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

      ctxPath = getIntent().getStringExtra("path");

      webVw = new WebView(getApplicationContext());
        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webVw.setLayoutParams(lllp);
        View.OnClickListener ocl = new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            prt("WebAct onclick");
            finish();
          }
        };
        webVw.setOnClickListener(ocl);
        webVw.setInitialScale(150);  // Needed to prevent soft keyboard from zooming in
          // when it is displayed.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        ws = webVw.getSettings();
        ws.setJavaScriptEnabled(true);
        webVw.setWebChromeClient(wcc = new WCC());
        webVw.setWebViewClient(wvc = new WVC());
        webVw.addJavascriptInterface(new JSInterface(getApplicationContext()), "host");

        setContentView(webVw);
        ((ViewGroup) webVw.getParent()).setOnClickListener(ocl);
        //prt(showViewTree(webVw.getRootView()));

        String html = "<body>\n" + "  <center style='color:gray'>(Loading)</center>\n" + "</body>";
        webVw.loadData(html, "text/html; charset=UTF-8", null);
    } // end of onCreate


    public void onResume() {
        super.onResume();
        Info.curAct = this;
        if (ctxTr == null) {
            ctxTr = Info.getConfig();
        }
        actCtxTr = (Tree)ctxTr.clone();
        prt("  webAct.onResume: scale "+webVw.getScaleX()+","+webVw.getScaleY()
          +", supportZoom "+ws.supportZoom()+", useWideViewPort "+ws.getUseWideViewPort()
          +", defaultZoom "+ws.getDefaultZoom()+", builtInZoomControls "+ws.getBuiltInZoomControls()
          +", displayZoomControls "+ws.getDisplayZoomControls());
      if (actCtxTr.selectMulti(new Bytes(ctxPath), false)) {
            String cmd = actCtxTr.value("CMD");
            if (cmd != null && cmd.length() > 0) {
                if (cmd.substring(0, 4).compareToIgnoreCase("WRT ") == 0) {
                    new LoadHTML().execute(cmd);
                } else {
                    webVw.loadUrl(cmd);
                }
            }
        } else {  // For testings, if ctxPath is not in CtxTr, just use it as a url
            webVw.clearCache(true);
            ws.setSupportZoom(false);
            ws.setUseWideViewPort(false);
            WebSettings.ZoomDensity zd = ws.getDefaultZoom();
            ws.setDefaultZoom(zd);
            ws.setBuiltInZoomControls(false);
            ws.setDisplayZoomControls(false);
            webVw.loadUrl(ctxPath);
        }
        //webVw.setInitialScale(100);  // must be done on UI thread
        //webVw.setScaleX(1);
        //webVw.setScaleY(1);
        actCtxTr.release();
        actCtxTr = null;
    } // end of onResume


    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        ctxPath = it.getExtras().getString("path");
        prt("WebAct.onNewIntent "+ctxPath+"\n"+it);
    } // end of onNewIntent


    static class LoadHTML extends AsyncTask<String, Void, Tree> {
        public String cmd;

        protected Tree doInBackground(String... cmds) {
            cmd = cmds[0];
            if (cmd.substring(0, 4).compareToIgnoreCase("WRT ") != 0) {
                cmd = "WRT MC74 " + cmd;
            }
            //Tree tr = svrCmdReply(localSSM, cmd);  -- port 80 not open, use msgs
            Tree tr = ssmCmdReply(null, cmd);
            if (tr == null) {
                prt("WebAct.LoadHTML.doInBackground.svrCmdReply: resp null from localSSM to cmd: "+cmd);
            }
            return tr;
        }

        protected void onPostExecute(Tree tr) {
            webVw.loadData(tr.value(), "text/html; charset=UTF-8", null);
            tr.release();
        }
    } // end of LoadHTML


    static class WCC extends WebChromeClient {
        public boolean onConsoleMessage(ConsoleMessage cMsg) {
            prt("wcc msg "+cMsg.lineNumber()+": "+cMsg.message());
            return true;
        }

        public boolean onJsAlert(WebView view, String url, String msg, JsResult result) {
            prt("wcc jsAlert: "+msg);
            result.cancel(); // This simulates the user clicking the cancel button on an alert
            return true;
        }
    } // end of WCC

    static class WVC extends WebViewClient {
        public void onLoadResource(WebView view, String url) {
            prt("onLoadResource: "+url);
        }

        public void onPageFinished(WebView view, String url) {
            prt("onPageFinished: "+url);
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            prt("onPageStarted: "+url);
        }

        public void onReceivedError(
                WebView view, int errorCode, String description, String failingUrl) {
            prt("onRecievedError: " + errorCode + " " + description + " " + failingUrl);
        }

        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            prt("onRecievedSslError: " + error);
        }

        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            prt("shouldOverrideUrlLoading: " + url);
            return false;
        }
    } // end of WVC


    // JSInterface allows javascript (though the 'host' variable) to access Java methods below.
    // Use of webVw.evaluateJavascript() is only supported in API level 19, not level 17 that we have
    public class JSInterface {
      Context ctx;


      JSInterface(Context ctx) {
        this.ctx = ctx;
      }


      @android.webkit.JavascriptInterface
      public void setSipAddr(String username, String domain, String password, String transport) {
        prt("setSipAddr " + username + "@" + domain + " " + password + " " + transport);
        Core core = LinphoneManager.getCore();
        if (core != null) {
          prt("[Generic Connection Assistant] Reloading configuration with default");
          //--calls reloadAccountCreatorCongif  --- reloadDefaultAccountCreatorConfig();
          //-- calls below --reloadAccountCreatorConfig(LinphonePreferences.instance().getDefaultDynamicConfigFile());
          String path = LinphonePreferences.instance().getDefaultDynamicConfigFile();
          core = LinphoneManager.getCore();
          if (core != null) {
            core.loadConfigFromXml(path);
            AccountCreator accountCreator = LinphoneManager.getInstance().getAccountCreator();
            accountCreator.reset();
            accountCreator.setLanguage(Locale.getDefault().getLanguage());
          }
        }

        AccountCreator accountCreator = LinphoneManager.getInstance().getAccountCreator();
        accountCreator.setUsername(username);
        accountCreator.setDomain(domain);
        accountCreator.setPassword(password);
        accountCreator.setDisplayName(username);

        if ("udp".compareToIgnoreCase(transport) == 0) {
          accountCreator.setTransport(TransportType.Udp);
        } else if ("tls".compareToIgnoreCase(transport) == 0) {
          accountCreator.setTransport(TransportType.Tls);
        } else {
          accountCreator.setTransport(TransportType.Tcp);
        }

        //AssistantActivity.createProxyConfigAndLeaveAssistant(true);
        createProxyConfig(core);
      } // end of setSipAddr


      @android.webkit.JavascriptInterface
      public String getConfig(String node) {
        // Returns JSON version of a node in the MC74 config obj
        prt("WebAct.javascript.getConfig: " + node);
        // First check the up to date value in MC74local
        Tree tr = new NB("MC74local");
        String json;
        if (tr.selectMulti(new Bytes(node), false)) {
          json = tr.toJSON();
        } else {
          // Not found in MC74local, check the defaults in MC74
          tr.release();
          tr = (Tree) ctxTr.clone();
          if (tr.selectMulti(new Bytes(node), false)) {
            json = tr.toJSON();
          } else {
            json = null;
          }
        }
        tr.release();
        return json;
      } // end of getConfig


      @android.webkit.JavascriptInterface
      public void setConfig(String node, String val) {
        // Sets a local config object (MC74local.mp) node to a value
        Tree tr = ssmCmdReply(null, "WRT MC74local SET "+node+" "+val);
        prt("WebAct.javascript.setConfig: " + node + " '" + val + "'");
      } // end of setConfig


      @android.webkit.JavascriptInterface
      public void back() {
        prt("WebAct.javascript.back: scale " + webVw.getScaleX() + "," + webVw.getScaleY()
          + ", supportZoom " + ws.supportZoom() + ", useWideViewPort " + ws.getUseWideViewPort()
          + ", defaultZoom " + ws.getDefaultZoom() + ", builtInZoomControls " + ws.getBuiltInZoomControls()
          + ", displayZoomControls " + ws.getDisplayZoomControls());
        doKeyEvent("BACK");
      } // end of back


      @android.webkit.JavascriptInterface
      public void startActivity(String pkg, String cls) {
        Intent intent = new Intent();
        intent.setClassName(pkg, cls);
        //intent.putExtra("path","sideMenu.voipSettings");
        prt("  WebAct.JS.startActivity: "+intent);
        Info.curAct.startActivity(intent);
      } // end of startActivity


      @android.webkit.JavascriptInterface
      public void qZoom() {
        prt("WebAct.qZoom: scale " + webVw.getScaleX() + "," + webVw.getScaleY()
          + ", supportZoom " + ws.supportZoom() + ", useWideViewPort " + ws.getUseWideViewPort()
          + ", defaultZoom " + ws.getDefaultZoom() + ", builtInZoomControls " + ws.getBuiltInZoomControls()
          + ", displayZoomControls " + ws.getDisplayZoomControls());
        //webVw.setInitialScale(150);
        //ws.setSupportZoom(false);
        //ws.setBuiltInZoomControls(false);
        //ws.setLoadWithOverviewMode(false);
        //ws.setUseWideViewPort(false);
        //ws.setJavaScriptCanOpenWindowsAutomatically(true);
      } // end of qZoom


      @android.webkit.JavascriptInterface
      public String getSipInfo() {
        Core core = LinphoneManager.getCore();
        ProxyConfig prox = core.getDefaultProxyConfig();
        prt("WebAct.javascript.getSipInfo " + prox);
        if (prox == null) return "{}";

        return "{\"userid\": \"" + prox.getContact().getUsername() + "\", \"sipServer\": \"" + prox.getDomain()
          + "\", \"password\": \"" + "\", \"transport\": \"" + prox.getTransport() + "\"}";
      } // end of back


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


public void createProxyConfig(Core core) {
    prt("WA[Assistant] Third party domain found, keeping default values");

    ProxyConfig proxyConfig = LinphoneManager.getInstance().getAccountCreator().createProxyConfig();

    // If this isn't a sip.linphone.org account, disable push notifications and enable
    // service notification, otherwise incoming calls won't work (most probably)
    if (proxyConfig != null) {
        proxyConfig.setPushNotificationAllowed(false);
    }
    prt( "WA[Assistant] Unknown domain used, push probably won't work, enable service mode");
    LinphonePreferences.instance().setServiceNotificationVisibility(true);
    LinphoneContext.instance().getNotificationManager().startForeground();

    if (proxyConfig == null) {
        prt("WA[Assistant] Account creator couldn't create proxy config");
        // TODO: display error message
    } else {
        if (proxyConfig.getDialPrefix() == null) {
            DialPlan dialPlan = getDialPlanForCurrentCountry();
            if (dialPlan != null) {
                proxyConfig.setDialPrefix(dialPlan.getCountryCallingCode());
            }
        }

        LinphonePreferences.instance().firstLaunchSuccessful();
        //goToLinphoneActivity();
    }
} // end of createProxyConfig


public DialPlan getDialPlanForCurrentCountry() {
    try {
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String countryIso = tm.getNetworkCountryIso();
        return getDialPlanFromCountryCode(countryIso);
    } catch (Exception e) {
        prt("WA[Assistant] " + e);
    }
    return null;
}


public DialPlan getDialPlanFromCountryCode(String countryCode) {
    if (countryCode == null || countryCode.isEmpty()) return null;

    for (DialPlan c : Factory.instance().getDialPlans()) {
        if (countryCode.equalsIgnoreCase(c.getIsoCountryCode())) return c;
    }
    return null;
}

public static void prt(String str) {
        System.out.println(str);
    }
} // end of WebAct
