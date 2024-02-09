package ribo.phone;

import static java.lang.Math.max;
import static org.andr.RAUtil.showViewTree;
import static ribo.phone.Info.curAct;
import static ribo.phone.Info.findFile;
import static ribo.phone.Info.localSSM;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.ibm.ssm.*;
import com.ibm.ssm.tree.*;
import com.ibm.ssm.tree.nob.*;
import java.io.*;
import org.linphone.R;
import org.linphone.activities.*;

public class VoicemailAct extends MainActivity implements View.OnTouchListener {
    public static VoicemailAct vmAct;
    public static LinearLayout scrVw;
    public static LinearLayout vmLay; // List of voicemail lines, from addVmLine()
    public String ctxPath;
    public static Tree vmTr;
    public static String NAME = "voicemail"; // Name used by MainActivity for -select
    public static MediaPlayer mp;
    public static PlayMsg pm;
    public static Handler mpHandler;
    public static String tmpFid;
    public static FileInputStream tmpVoicemailFIS;
    public static int primaryColor; // Light blue
    //public static int bgColor;  // White
    public static int transparentColor = 0x00000000;
    public static String vmMailbox = "1"; // Default mailbox
    public static View selectedVmLn;
    public static AudioManager audMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prt("VoicemailAct.onCreate");
        getIntent().putExtra("Activity", "Voicemail");
        super.onCreate(savedInstanceState);

        Intent it = getIntent();
        ctxPath = it.getStringExtra("path");
        primaryColor = getApplicationContext().getResources().getColor(R.color.light_primary_color);
        //bgColor = getApplicationContext().getResources().getColor(R.color.white_color);
    } // end of onCreate


    protected void onStart() {
        prt("VoicemailAct.onStart");
        super.onStart();

        audMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    } // end of onStart


    protected void onResume() {
        prt("VoicemailAct.onResume");
        curAct = this;
        vmAct = this;
        super.onResume();

        // Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.main_bg);
        if (currentFragment == null) {
            VoicemailFragment fragment = new VoicemailFragment();
            // changeFragment(fragment, "Voicemail", false);
            ViewGroup cont = findViewById(R.id.main_bg);
            View vw = fragment.onCreateView(null, cont, null);
            vw.setOnTouchListener(this);
            cont.addView(vw);
        }

        // Set background to standard white so VM lines show well (after MainActivity.onResume)
        setBg(R.id.main_bg);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mp != null) {
            mp.release();
            mp = null;
        }
        if (mpHandler != null) {
            mpHandler = null;
        }
        if (pm != null) {
            pm.cancel(true);
        }
    }


    protected void onDestroy() {
        prt("VoicemailAct.onDestroy");
        if (vmTr != null) {
            vmTr.release();
            vmTr = null;
        }
        if (tmpVoicemailFIS != null) {
            try {
                tmpVoicemailFIS.close();
            } catch (Exception ex) {
            }
            tmpVoicemailFIS = null;
        }
        vmAct = null;
        super.onDestroy();
    }

    public static class VoicemailFragment extends Fragment {
        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // View view = inflater.inflate(R.layout.vhistory, container, false);
            prt("VoicemailFragment.onCreateView: " + container);
            RelativeLayout fragLay = new RelativeLayout(container.getContext());
            fragLay.setTag("fragLayVM");

            ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            fragLay.setLayoutParams(vglp);

            // Create the ScrollView that the voicemail list layout will be in
            scrVw = new LinearLayout(container.getContext());
            scrVw.setId(sSeqn++);
            RelativeLayout.LayoutParams rllp = new RelativeLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            int playLayId = sSeqn++;;
            rllp.addRule(RelativeLayout.ABOVE, playLayId);
            scrVw.setLayoutParams(rllp);

            scrVw.setTag("vmScroll");
            fragLay.addView(scrVw);

            vmLay = new LinearLayout(container.getContext());
            vmLay.setId(sSeqn++);
            vmLay.setTag("vmLay");
            LinearLayout.LayoutParams llLP = new LinearLayout.LayoutParams(
               ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vmLay.setOrientation(LinearLayout.VERTICAL);
            scrVw.addView(vmLay, llLP);

            // 'Voicemail' text, first child of vmLay
            TextView tv = new TextView(container.getContext());
            llLP = new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setId(sSeqn++);
            tv.setTextColor(0xff000000);
            tv.setTextSize(40);
            tv.setText("Voicemail");
            tv.setGravity(Gravity.CENTER);
            vmLay.addView(tv, llLP);

            new PopulateVoicemail().execute(vmMailbox);

            View playLay = createAudioControllerView(vmAct.getApplicationContext());
            playLay.setId(playLayId);
            rllp = (RelativeLayout.LayoutParams) playLay.getLayoutParams();
            rllp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            rllp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            //rllp.addRule(RelativeLayout.BELOW, scrVw.getId());
            rllp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

            fragLay.addView(playLay);
            //prt("\nfrayLay returned from onCreateView:\n" + showViewTree(fragLay));
            return fragLay;
        } // end of onCreateView
    } // end of VoicemailFragment class


    static class PopulateVoicemail extends AsyncTask<String, Void, Tree> {
        public String cmd;

        protected Tree doInBackground(String... cmds) {
            // See if there is a cached copy of the voicemail on the local SSM server
            vmTr = svrCmdReply(localSSM, "QRT PHONE.VOICEMAIL");
            if (vmTr.hasChildren() == false) {
                vmTr.release();
                String cmd = "WRT MC74 GETVOICEMAILLOG mailbox=" + cmds[0];
                vmTr = svrCmdReply(localSSM, cmd);
            }
            return vmTr;
        }

        protected void onPostExecute(Tree tr) {
            if (tr == null) {
                TextView tv = new TextView(vmLay.getContext());
                tv.setText("(No voicemail messages)");
                tv.setTextSize(40);
                tv.setTextColor(0xff00ffff);
                vmLay.addView(tv);

            } else {
                tr.prepChildScan();
                while (tr.next()) {
                    addVmLine(vmLay, tr);
                }
                tr.parent();
                //scrVw.invalidate();  // DOESN'T WORK Cause layout to be recalculated after adding lines
                // Adding lines to vmLay doesn't cause it to update the height of vmLay
                // This hack, setting layout parms height explicitly, fixs it
                vmLay.measure(0, 0);
                ViewGroup.LayoutParams LP = vmLay.getLayoutParams();
                LP.height = vmLay.getMeasuredHeight();
                prt("  scrVw ht, "+scrVw.getMeasuredHeight()+", vmLay ht"+vmLay.getHeight()
                  +", LPht "+LP.height);

                // Scroll down to show most recent message, at bottom of vmLay
                int yy = LP.height-scrVw.getMeasuredHeight();
                scrVw.scrollTo(0, max(yy, 0));
            }
        } // end of onPostExecute
    } // end of PopulateVoicemail


    public static void addVmLine(ViewGroup vmLay, Tree tr) {
        // tr node looks like:
        //  1: ''
        //    mailbox: '1'
        //    folder: 'Old'
        //    message_num: '0'
        //    date: '2020-06-21 13:17:28'
        //    callerid: '8452254848 <8452254848>'
        //    duration: '00:00:21'
        //    urgent: 'no'
        //    listened: 'no'

        // Build a layout that looks like:
        // <urgent> <new> <date>  <callerid>    <duration>
        // Swipe right to delete

        Context ctx = vmLay.getContext();
        LinearLayout lnLay = new LinearLayout(ctx);
        lnLay.setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60));
        lnLay.setTag(tr.name());
        lnLay.setOnTouchListener((View.OnTouchListener) vmAct);
        lnLay.setBackgroundColor(transparentColor);

        ImageButton ib = new ImageButton(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(50, 50);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        boolean tf = tr.value("URGENT").compareToIgnoreCase("YES") == 0;
        int drwId = tf ? R.drawable.urgent_icon : R.drawable.blank_icon;
        // blank_icon is used rather than setVisiblity(View.INVISIBLE) so the blank icon
        // space can be clicked on to turn it on.
        ib.setImageDrawable(ctx.getResources().getDrawable(drwId));
        ib.setBackgroundColor(transparentColor);
        ib.setTag("urgent"+ (tf ? "Y" : "N"));
        ib.setOnClickListener(oclIcon);
        lnLay.addView(ib, lp);

        ib = new ImageButton(ctx);
        lp = new LinearLayout.LayoutParams(50, 50);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        tf = tr.value("LISTENED").compareToIgnoreCase("YES") == 0;
        drwId = tf ? R.drawable.blank_icon : R.drawable.listened_icon;
        ib.setImageDrawable(ctx.getResources().getDrawable(drwId));
        ib.setBackgroundColor(transparentColor);
        ib.setTag("listened"+ (tf ? "Y" : "N"));
        ib.setOnClickListener(oclIcon);
        lnLay.addView(ib, lp);

        TextView tv = new TextView(ctx);
        lp = new LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(20, 0, 0, 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        tv.setText(tr.value("DATE"));
        tv.setTextSize(20);
        tv.setTextColor(0xff000000);
        lnLay.addView(tv, lp);

        tv = new TextView(ctx);
        lp = new LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(20, 0, 0, 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        tv.setText(tr.value("CALLERID"));
        tv.setTextSize(20);
        tv.setTextColor(0xff000000);
        lnLay.addView(tv, lp);

        tv = new TextView(ctx);
        lp = new LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(20, 0, 0, 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        tv.setText(tr.value("DURATION").substring(3)); // Remove 00: hours
        tv.setTextSize(20);
        tv.setTextColor(0xff000000);
        lnLay.addView(tv, lp);

        ib = new ImageButton(ctx);
        lp = new LinearLayout.LayoutParams(50, 50);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        ib.setImageDrawable(ctx.getResources().getDrawable(R.drawable.delete_icon));
        ib.setBackgroundColor(transparentColor);
        ib.setTag("delete");
        ib.setOnClickListener(oclIcon);
        lnLay.addView(ib, lp);

        vmLay.addView(lnLay);
    } // end of addVMline

    public static ImageButton acPrev;
    public static ImageButton acRew;
    public static ImageButton acPause;
    public static ImageButton acFFwd;
    public static ImageButton acNext;
    public static TextView acCurTime;
    public static SeekBar acSeek;
    public static TextView acTime;

    public static View createAudioControllerView(Context ctx) {
        // Layout copied from: frameworks/base/core/res/res/layout/media_controller.xml
        LinearLayout acLay = new LinearLayout(ctx);
        RelativeLayout.LayoutParams rllp =
                new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acLay.setLayoutParams(rllp);
        acLay.setTag("audioController");
        acLay.setOrientation(LinearLayout.VERTICAL);

        // Create vertical LinearLayout for the buttons
        LinearLayout buttonLay = new LinearLayout(ctx);
        ViewGroup.LayoutParams vglp =
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLay.setTag("acButtons");
        buttonLay.setGravity(Gravity.CENTER);

        acPrev = new ImageButton(new ContextThemeWrapper(vmAct, R.style.MediaButton_Previous));
        acPrev.setOnClickListener(acOCL);
        buttonLay.addView(acPrev);
        acRew = new ImageButton(new ContextThemeWrapper(vmAct, R.style.MediaButton_Rew));
        acRew.setOnClickListener(acOCL);
        buttonLay.addView(acRew);
        acPause = new ImageButton(new ContextThemeWrapper(vmAct, R.style.MediaButton_Play));
        acPause.setOnClickListener(acOCL);
        buttonLay.addView(acPause);
        acFFwd = new ImageButton(new ContextThemeWrapper(vmAct, R.style.MediaButton_Ffwd));
        acFFwd.setOnClickListener(acOCL);
        buttonLay.addView(acFFwd);
        acNext = new ImageButton(new ContextThemeWrapper(vmAct, R.style.MediaButton_Next));
        acNext.setOnClickListener(acOCL);
        buttonLay.addView(acNext);

        acLay.addView(buttonLay, vglp);

        // Create second vertical LinearLayout for seekbar/timeline
        LinearLayout timelineLay = new LinearLayout(ctx);
        vglp =
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timelineLay.setTag("acTimeline");

        acCurTime = new TextView(ctx);
        acCurTime.setTextSize(14);
        // acCurTime.setTextStyle(bold);
        acCurTime.setPadding(4, 4, 4, 0);
        LinearLayout.LayoutParams lllp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lllp.gravity = Gravity.CENTER_HORIZONTAL;
        acCurTime.setLayoutParams(lllp);
        acCurTime.setTextSize(20);
        acCurTime.setTextColor(0xff000000);
        acCurTime.setText("00:00");
        timelineLay.addView(acCurTime);

        acSeek = new SeekBar(new ContextThemeWrapper(vmAct,
          R.style.Widget_AppCompat_ProgressBar_Horizontal));
        lllp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48, 1);
        acSeek.setOnSeekBarChangeListener(osbcl);
        timelineLay.addView(acSeek, lllp);

        acTime = new TextView(ctx);
        acTime.setTextSize(14);
        acTime.setPadding(4, 4, 4, 0);
        lllp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lllp.gravity = Gravity.CENTER_HORIZONTAL;
        acTime.setTextSize(20);
        acTime.setTextColor(0xff000000);
        acTime.setText("--:--");
        timelineLay.addView(acTime, lllp);

        acLay.addView(timelineLay, vglp);

        return acLay;
    } // end of createAudioControllerView

    static SeekBar.OnSeekBarChangeListener osbcl =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int pos, boolean fromUser) {
                    if (fromUser) {
                        mp.seekTo(pos);
                        acSeek.setProgress(pos);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                    prt("onStartTrackingTouch " + sb);
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                    prt("onStopTrackingTouch " + sb);
                }
            }; // end of OnSeekBarChangeListener


    static View.OnClickListener acOCL =
        new View.OnClickListener() {
            @Override
            public void onClick(View vw) {
                if (mp == null) {
                    prt("VoicemailAct.acOCL.onClick: mp is null.");
                } else if (vw == acPause) {
                    if (mp.isPlaying()) {
                        mp.pause();
                        acPause.setImageResource(R.drawable.ic_media_pause);
                    } else {
                        mp.start();
                        acPause.setImageResource(R.drawable.ic_media_play);
                    }
                } else if (vw == acRew) {
                    Toast.makeText(vmAct.getApplicationContext(), "Todd", Toast.LENGTH_LONG)
                            .show();
                } else if (vw == acFFwd) {
                    // PlaybackParams pp = new PlaybackParams();
                    // mp.setPlaybackParams(pp);
                } else if (vw == acPrev) {
                } else if (vw == acNext) {
                    Toast.makeText(vmAct.getApplicationContext(), "No Next", Toast.LENGTH_LONG)
                            .show();
                }
            }
        };


    public static int tdScrollY, tdY;
    public boolean onTouch(View vw, MotionEvent mev) {  // Touch to a line voicemail msg line
        String tag = (String) vw.getTag();
        int deltaY = (int)mev.getRawY() -tdY;
        switch (mev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prt("VmAct.DOWN " + tag + " " + mev.getRawX() + "," + mev.getRawY());
                tdScrollY = scrVw.getScrollY();
                tdY = (int)mev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                prt("VmAct.MOVE " + mev.getRawX() + "," + mev.getRawY()+", tdY "+tdY
                  +", dY "+deltaY);
                scrVw.scrollTo(0, tdScrollY-deltaY);
                break;
            case MotionEvent.ACTION_UP:
                prt("VmAct.UP " + vw.getTag() + " " + mev.getRawX() + "," + mev.getRawY()+", tdY "+tdY);
                if (tag != null) {
                    // vw.setBackgroundColor(bgColor);
                    // vw.performClick();  -- Don't call, this produces another click sound
                    if (deltaY<40 && deltaY>-40) {  // If this was not a scroll up/down touch, play msg
                        if (selectedVmLn != null) { // Deselect previous line
                            selectedVmLn.setBackgroundColor(transparentColor);
                        }
                        vw.setBackgroundColor(primaryColor);
                        selectedVmLn = vw;
                        audMgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                        playMsg(vw); // Call our onClick directly
                    }
                }
                break;
            default:
                prt("VoicemailAct.onTouch, unrecognized action: " + mev);
        }
        return true;
    } // end of onTouch


    // PLay a voicemail msg, based on voicemail message view
    public void playMsg(View vw) {
        Tree tr = (Tree) vmTr.clone();
        if (tr.select((String) vw.getTag())) {
            String msgNo = tr.value("MESSAGE_NUM");
            prt("Voicemail.playMsg " + vw.getTag() + " " + tr.value("CALLERID"));
            String cmd = "WRT VOIP GETVOICEMAIL "+msgNo
              +" "+tr.value("FOLDER")+" "+tr.value("MAILBOX");
            if (pm != null) {
                try {
                    pm.cancel(true);
                } catch (Exception ex) {
                }
            }
            (pm = new PlayMsg()).execute(cmd);

            // Mark this message as LISTENED to
            cmd = "WRT VOIP cmd markListenedVoicemailMessage mailbox="
              + tr.value("MAILBOX") + " folder=" + tr.value("FOLDER")
              + " message_num=" + msgNo + " listened=yes";
            new VoipCmd().execute(cmd, vw);

            // Make 'new' icon invisible, replace with blank_icon (so it is still clicable
            View listenedBtn = ((ViewGroup)vw).getChildAt(1);
            Context ctx = listenedBtn.getContext();
            ((ImageButton)listenedBtn).setImageDrawable(ctx.getResources().getDrawable(R.drawable.blank_icon));
        }

        tr.release();
    } // end of playMsg


    static class PlayMsg extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... cmds) {
            String cmd = cmds[0];
            Tree respTr = svrCmdReply(localSSM, cmd);
            Bytes aud = respTr.valueBytes(new Bytes());
            if (aud.length() < 200) { // Is this short response an error msg?
                return cmd + "\nerr: " + aud;
            } else {
                tmpFid = "/tmp/voicemailMsg.mp3";
                SSMutil.writeFile(tmpFid, aud);
                playMsg(tmpFid);
            }
            respTr.release();
            prt("PlayMsg.bg.thread " + Thread.currentThread().getName());
            return null;
        } // end of doInBackground

        protected void onPostExecute(String msg) {
            if (msg != null) {
                Toast.makeText(vmAct.getApplicationContext(), msg, Toast.LENGTH_LONG).show();

            } else if (mpHandler == null) {
                // Loop updating acSeek progress bar each second
                mpHandler = new Handler();
                vmAct.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mp != null && mpHandler != null) {
                            int pos = mp.getCurrentPosition();
                            acSeek.setProgress(pos);
                            acCurTime.setText(minSecStr(pos / 1000));
                            mpHandler.postDelayed(this, 1000);
                        } else {
                            prt("MP seek updated ends.");
                        }
                    }
                });
            }
        }
    } // end of PlayMsg

    public static String minSecStr(int sec) {
        // Convert number of seconds into a string of the form: 'mm:ss'
        return String.format("%02d:%02d", sec / 60, sec % 60);
    } // end of minSecStr


    public static View.OnClickListener oclIcon = new View.OnClickListener() {
        @Override
        public void onClick(View vw) {
            String cmd=null, tag = (String)vw.getTag();  // 'urgent' or 'delete' icon name
            Tree tr = (Tree) vmTr.clone();
            View vmLnVw = (View) vw.getParent();
            String tmStamp = (String) vmLnVw.getTag();
            if (tr.select(tmStamp)) {
                String msgNo = tr.value("MESSAGE_NUM");
                prt("Voicemail.onClick.oclIcon "+tag+" "+tmStamp+'/'+msgNo+" "+tr.value("CALLERID"));
                if (tag == null) {
                    prt("VoicemailAct.oclIcon: icon tag is null");
                } else if (tag.compareToIgnoreCase("delete") == 0) {
                     cmd = "WRT VOIP cmd delMessages mailbox=" + tr.value("MAILBOX")
                       + " folder=" + tr.value("FOLDER") + " message_num=" + msgNo;
                     // Make parent (the vm line) invisible
                     ((View) vw.getParent()).setVisibility(View.GONE);

                } else if (tag.charAt(0) == 'u') {  // Urgent icon
                    boolean urgent = tag.charAt(tag.length()-1) != 'Y';  // New state, opposite of current tag
                    String yesNo = urgent ? "yes" : "no";
                    vw.setTag("urgent"+ (urgent?"Y":"N"));
                    cmd = "WRT VOIP cmd markUrgentVoicemailMessage mailbox="
                       + tr.value("MAILBOX") + " folder=" + tr.value("FOLDER")
                       + " message_num=" + msgNo + " urgent="+yesNo;
                    Context ctx = vw.getContext();
                    int drwId = urgent ? R.drawable.urgent_icon : R.drawable.blank_icon;
                    ((ImageButton)vw).setImageDrawable(ctx.getResources().getDrawable(drwId));


                } else if (tag.charAt(0) == 'l') {
                    boolean listened = tag.charAt(tag.length()-1) != 'Y';  // New state, opposite of current tag
                    String yesNo = listened ? "yes" : "no";
                    vw.setTag("listened"+ (listened?"Y":"N"));
                    cmd = "WRT VOIP cmd markListenedVoicemailMessage mailbox="
                      + tr.value("MAILBOX") + " folder=" + tr.value("FOLDER")
                      + " message_num=" + msgNo + " listened="+yesNo;
                    Context ctx = vw.getContext();
                    int drwId = listened ? R.drawable.blank_icon : R.drawable.listened_icon;
                    ((ImageButton)vw).setImageDrawable(ctx.getResources().getDrawable(drwId));
                 } else {
                     prt("VoicemailAct.oclIcon: Unknown icon tag: "+tag);
                 }
                if (cmd != null) { new VoipCmd().execute(cmd, vmLnVw); }
            }
            tr.release();
        }
    };


    static class VoipCmd extends AsyncTask<Object, Void, String> {
        String cmd;
        public View vmLnVw;

        protected String doInBackground(Object... objs) {
            cmd = (String) objs[0];
            vmLnVw = (View) objs[1];
            if (cmd.substring(0, 4).compareToIgnoreCase("WRT ") != 0) {
                cmd = "WRT MC74 " + cmd;
            }
            prt("VoipCmd cmd: "+cmd);
            Tree tr = svrCmdReply(localSSM, cmd);
            prt("VoipCmd resp: " + tr.value() + "\n" + tr.list());
            tr.release();
            return null;
        }

        //protected void onPostExecute(String resp) {
        //    ViewGroup vg;
        //    if (resp==null && vmLnVw!=null
        //      && (vg = (ViewGroup)vmLnVw.getParent())!=null) {
        //        vg.removeView(vmLnVw);
        //    } else {
        //        prt("VoipCmd resp: " + resp);
        //    }
        //}
    } // end of VoipCmd

    public static void playMsg(String audFn) {
        playMsg(audFn, false);
    }

    public static void playMsg(String audFn, boolean loop) {
        try {
            if (tmpVoicemailFIS != null) {
                try {
                    tmpVoicemailFIS.close();
                } catch (Exception ex) {
                }
            }
            tmpVoicemailFIS = new FileInputStream(findFile(audFn, "audio"));
            FileDescriptor fd = tmpVoicemailFIS.getFD();

            int len = tmpVoicemailFIS.available();
            if (mp == null) {
                mp = new MediaPlayer();
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setOnPreparedListener(
                        new MediaPlayer.OnPreparedListener() {
                            public void onPrepared(MediaPlayer mp) {
                                prt("mp.onprepared");
                                mp.start();
                                int dur = mp.getDuration();
                                if (acSeek != null) {  // If called without audioPlay view, skip this
                                    acSeek.setMax(dur); // Time in mS
                                    acTime.setText(minSecStr(dur / 1000));
                                }
                            }
                        });
                mp.setOnErrorListener(
                        new MediaPlayer.OnErrorListener() {
                            public boolean onError(MediaPlayer mp, int i1, int i2) {
                                prt("mp.onerror " + i1 + " " + i2);
                                return false;
                            }
                        });
                mp.setOnInfoListener(
                        new MediaPlayer.OnInfoListener() {
                            public boolean onInfo(MediaPlayer mp, int i1, int i2) {
                                prt("mp.oninfo " + i1 + " " + i2);
                                return false;
                            }
                        });
                // end of mp was null
            } else {
                mp.reset(); // Must be reset before setting new data source
            }
            mp.setLooping(loop);
            mp.setDataSource(fd, 0, len);
            mp.prepare(); // onPrepared calls mp.start()
        } catch (Exception ex) {
            prt("VmAct.msgPlaying ex: " + ex + "\n" + SSMutil.stackTrace(ex));
        }
    } // end of playMsg

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of VoicemailAct class
