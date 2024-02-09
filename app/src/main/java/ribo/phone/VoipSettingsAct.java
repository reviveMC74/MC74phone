package ribo.phone;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.linphone.*;
import org.linphone.activities.*;

public class VoipSettingsAct extends MainActivity {
    public String actName;
    public String ctxPath;
    // public Tree actCtxTr;
    public static String NAME = "voipsettings"; // Name used by MainActivity for -select

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prt("VoipSettingsAct.onCreate");
        getIntent().putExtra("Activity", "VoipSettingsAct");
        super.onCreate(savedInstanceState);

        Intent it = getIntent();
        ctxPath = it.getStringExtra("path");
    } // end of onCreate

    protected void onStart() {
        prt("VoipSettingsAct.onStart");
        super.onStart();

        VoipSettingsFragment fragment = new VoipSettingsFragment();
        ViewGroup cont = findViewById(R.id.main_bg);
        View vw = fragment.onCreateView(null, cont, null);
        cont.addView(vw);
    }

    protected void onResume() {
        prt("VoipSettingsAct.onResume");
        Info.curAct = this;
        super.onResume();
    }

    public static class VoipSettingsFragment extends Fragment {
        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // View view = inflater.inflate(R.layout.vhistory, container, false);
            prt("VoipSetttingsFragment.onCreateView: " + container);
            RelativeLayout rLay = new RelativeLayout(container.getContext());
            ViewGroup.LayoutParams vgLp =
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
            rLay.setLayoutParams(vgLp);

            TextView tv = new TextView(container.getContext());
            tv.setTextColor(0xff00ff00);
            tv.setTextSize(40);
            tv.setText("(Place where SIP settings will be set)");
            tv.setGravity(Gravity.CENTER);
            rLay.addView(tv);

            return rLay;
        } // end of onCreateView

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onPause() {
            super.onPause();
        }
    }

    public static void prt(String str) {
        System.out.println(str);
    }
}
