package ribo.phone;

import static org.andr.RAUtil.*;
import static org.linphone.activities.MainActivity.*;
import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.localSSM;

import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.fragment.app.*;
import com.ibm.ssm.tree.*;
import org.linphone.*;
import org.linphone.activities.*;

public class WebFrag extends Fragment {
    public String ctxPath;
    public Tree actCtxTr;
    public WebView webVw;
    public static String NAME = "web";

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webVw = new WebView(container.getContext());
        LinearLayout.LayoutParams lllp =
                new LinearLayout.LayoutParams(1000, ViewGroup.LayoutParams.MATCH_PARENT);
        webVw.setLayoutParams(lllp);
        webVw.setOnClickListener(
                vw -> {
                    prt("WebFrag onclick");
                });
        // webVw.setWebChromeClient(wcc);
        // webVw.setWebViewClient(wvc);

        prt(showViewTree(webVw.getRootView()));

        String html = "<body>\n" + "  <center style='color:gray'>(Loading)</center>\n" + "</body>";
        webVw.loadData(html, "text/html; charset=UTF-8", null);

        // Intent it = getIntent();
        // ctxPath = it.getStringExtra("path");

        actCtxTr = (Tree) ctxTr.clone();
        if (actCtxTr.selectMulti(new Bytes(ctxPath), false)) {
            String cmd = actCtxTr.value("CMD");
            if (cmd != null && cmd.length() > 0) {
                new LoadHTML().execute(cmd);
            }
        }
        actCtxTr.release();
        actCtxTr = null;

        return webVw;
    } // end of onCreate

    public void onDetach() {
        super.onDetach();
    }

    class LoadHTML extends AsyncTask<String, Void, Tree> {
        public String cmd;

        protected Tree doInBackground(String... cmds) {
            cmd = cmds[0];
            if (cmd.substring(0, 4).compareToIgnoreCase("WRT ") != 0) {
                cmd = "WRT MC74 " + cmd;
            }
            Tree tr = svrCmdReply(localSSM, cmd);
            return tr;
        }

        protected void onPostExecute(Tree tr) {
            webVw.loadData(tr.value(), "text/html; charset=UTF-8", null);
            tr.release();
        }
    } // end of LoadHTML

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of WebAct
