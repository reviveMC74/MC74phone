package ribo.phone;

import static com.ibm.ssm.SSM.configOid;
import static org.andr.RAUtil.showViewTree;
import static org.linphone.activities.MainActivity.svrCmdReply;
import static org.linphone.fragments.StatusBarFragment.updateSmsVm;
import static ribo.ssm.EvalExp.displayObj;
import static ribo.ssm.EvalExp.evalExp;
import static ribo.ssm.SSMcmd.ssmCmdReply;

import android.app.*;
import android.content.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.view.*;
import com.ibm.ssm.*;
import com.ibm.ssm.tree.Bytes;
import com.ibm.ssm.tree.Tree;
import com.ibm.ssm.tree.TrunkFactory;
import com.ibm.ssm.tree.nob.*;
import com.meraki.androidapi.media.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.linphone.*;
import org.linphone.call.*;
import org.linphone.core.*;
import ribo.ssm.*;
import static ribo.phone.WebPanel.jsQ;
import static ribo.phone.WebPanel.jsRespQ;
import static ribo.ssm.HTMLact.jsCmdSN;

public class Info {
    public static String pkgName;
    public static Activity curAct; // Current Activity
    public static String curActName;
    public static String prevActName;
    public static String localSSM = "localhost"; // Host for local TCP requests (not HTTP)
    public static String centralSvr;  // Host for household central (SMS/Voicemail) server
    public static Tree ctxTr;  // MC74 configuration tree
    public static PHSSMHndCmd phSHC;
    public static AudioManager audSvc;

    public static String curActSect = "MAINFOOTER"; // Section of MC74 obj of last selected action
    public static String curActTag;  // View tag, CONFIG.FOOTER name, of selected button
    public static String baseDir = "/sdcard/ssm";
    public static String defObjStore = "FILE " + baseDir + "/store .nob";
    public static String defObjStore2 = "FILE " + baseDir + "/store .mp";
    public static Activity mainAct;
    public static boolean dialtonePlaying = false;
    public static String mc74StartTime = "(not set)";

    static {
        mc74StartTime = SSMutil.getTimestamp();
        prt(SSMutil.getTimestamp()+" Info.static: Set SSMcmd.cmdHndlr");
        phSHC = new Info.PHSSMHndCmd();
        SSMcmd.cmdHndlr = (SSMcmd.SSMHndCmd) phSHC;
    }

    public static void initMC74environ(Context ctx) {
        minimalSSMinit(ctx);
        ctxTr = Info.getConfig();  // Get NC74 config info
        centralSvr = ctxTr.value("CENTRALSVR");  // Get configured IP address of household central SMS/Vm svr
    } // end of initMC74environ


      // getConfig -- Load reviveMC74 config info, and overlay local config over that
    public static Tree getConfig() {
        Tree tr = new NB("MC74");
        if (tr == null) {
            prt("Info.getConfig, could not find MC74 object is SSM service up?");
            return new NB("MC74local");  // Hmm, perhap they are using MC74local as config
        }

        Tree trLcl = new NB("MC74local");  // Get local overrides to config info
        if (trLcl != null) {
            tr.merge(trLcl);
            trLcl.release();
        }
        return tr;
    } // end of getConfig


    public static Drawable getDrw(String fid) {
        Drawable drw;
        if (fid.charAt(0) == '/') {
            drw = Drawable.createFromPath(fid);

        } else if (fid.charAt(0) == '.') { // fid is the name of a drawable resource
            try {Uri imgUrl = Uri.parse("android.resource://"+ pkgName+ "/drawable/"
                + fid.substring(1)); // Remove leading '.' from name
                InputStream inputStream = mainAct.getContentResolver().openInputStream(imgUrl);
                drw = Drawable.createFromStream(inputStream, imgUrl.toString());
            } catch (FileNotFoundException ex) {
                drw = mainAct.getResources().getDrawable(android.R.drawable.ic_lock_lock);
            }

        } else {
            drw = Drawable.createFromPath(findFile(fid, "image"));
        }
        return drw;
    } // end of getDrw


    public static String findFile(String fid, String type) {
        // Look in various places for a file depending on the type: image or audio
        if (fid.charAt(0) == '/') {
            return fid;
        } else {
            return Info.baseDir + "/" + type + "/" + fid;
        }
    } // end of findFile


  // setConfigValue -- Set a persistent configuration setting in the MC74local.mp object
public static void setConfigValue(String name, String val) {
    Tree tr = ssmCmdReply(null, "WRT MC74local SET "+name+" "+val);
    // MC74 can't directly access SSM objects in SSM app
} // end of setConfigValue


public static class PHSSMHndCmd extends ribo.ssm.SSMcmd.SSMHndCmd {
    public Tree hndCmd(Tree cmdTr) {
        Bytes args = new Bytes();
        cmdTr.valueBytes(args);
        Bytes cmd = new Bytes();
        args.parseTok(cmd);
        String respStr = null;
        Tree respTr = new NB();

        prt("PHSSMHndCmd.hndCmd: " + cmd + " " + args);
        if (cmd.compareToIgnoreCase("silence") == 0) {
            // Stop audio being played by the VoicemailAct Media Player (includes dialtome)
            prt("Info.PHSSMHndCmd.silence, stopping mediaPlayer");
            Info.dialtonePlaying = false;
            if (VoicemailAct.mp == null) {
                prt("PHSSMHndCmd.silence: mediaPlayer, VoicemilaAct.mp, is null, nop");
            } else {
                VoicemailAct.mp.stop();
            }

        } else if (cmd.compareToIgnoreCase("sendCmd") == 0) {  // ribo.ssm.HTMLact.cmdJS is now used.
            // To be replaced with ribo.ssm.Phone.cmdCmdJs
            // Enqueue a JS command to the webView in WebPanel
            if (jsQ == null) {
                respStr = "(WebPanel web page javascript didn't call debugPoll() )";
            } else {
                jsQ.add("C"+jsCmdSN++ +' '+args.trim().toString());
                // Wait for a couple sec for a response
                synchronized (jsRespQ) {
                    if (
                      jsRespQ.size() == 0) try {
                        jsRespQ.wait(4000);
                    } catch (InterruptedException iEx) {}
                }
                if (jsRespQ.size() == 0) {
                    respStr = "(no resp)";
                } else {
                    respStr = "";
                    while (true) {  // Accumulate all responses into one (in case of sync problem)
                        respStr += jsRespQ.remove();
                        if (jsRespQ.size() == 0) break;
                        respStr += '\n';
                    }
                }
            }

        } else if (cmd.compareToIgnoreCase("updateStatusBar") == 0) {
            // Central(household) phone server reports change in SMS or Voicemail count
            int newSmsCnt=-1, newVmCnt=-1, newMissedCallCnt=-1;   // -1 means: has not been set
            if (cmdTr.select("NEWSMSCNT")) {
                newSmsCnt = Integer.parseInt(cmdTr.value());
                cmdTr.parent();
            }
            if (cmdTr.select("NEWVMCNT")) {
                newVmCnt = Integer.parseInt(cmdTr.value());
                cmdTr.parent();
            }
            updateSmsVm(newSmsCnt, newVmCnt);  // In UI thread, set the SMS/VM indications in statusBar

            if (cmdTr.select("NEWVMISSEDCALLNT")) {
                newMissedCallCnt = Integer.parseInt(cmdTr.value());
                cmdTr.parent();
            }
            if (cmdTr.select("LED")) {  // Set the LED as requested
                Tree tr = svrCmdReply(localSSM, "WRT MC74 LED "+cmdTr.value());
                cmdTr.parent();
            }

            // Extra feature, play an audio file is requested
            if (cmdTr.select("PLAY")) {
                String audioFid = cmdTr.value();
                prt("Info.updateStatus play: "+audioFid);
                VoicemailAct.playMsg(audioFid, false);
                cmdTr.parent();
            }

            respStr = "Set newSmsCnt "+newSmsCnt+", newVmCnt "+newVmCnt+", newMissedCallCnt "+newMissedCallCnt;

        } else if (cmd.compareToIgnoreCase("finish") == 0) {
            // Finish(close) one of out activities (from the 'back' stack
            curAct.finish();
            respStr = "Finished: "+curAct.getComponentName();

        } else if (cmd.compareToIgnoreCase("acceptCall")==0
          || cmd.compareToIgnoreCase("endCall")==0) {
            if ((cmd.charAt(0)|0x20) == 'a') {  // acceptCall cmd
                if (CallIncomingActivity.mCall == null) {
                    respStr = "No incoming call.";
                } else {
                    curAct.runOnUiThread(
                      () -> {
                          prt("LC.PHSSMCmdHnd.acceptCall mainThread, before acceptCall");
                          boolean rc = LinphoneManager.getCallManager().acceptCall(CallIncomingActivity.mCall);
                          prt("LC.PHSSMCmdHnd.acceptCall mainThread, after acceptCall " + rc);
                      });

                    respStr = "Sent acceptCall to main thread";
                }

            } else {  // endCall command
                //call.terminate();
                curAct.runOnUiThread(
                  () -> {
                      prt("LC.PHSSMCmdHnd.endCall before termiantCurrentCallOrConferencOrAll");
                      LinphoneManager.getCallManager().terminateCurrentCallOrConferenceOrAll();
                      prt("LC.PHSSMCmdHnd.endCall after termiantCurrentCallOrConferencOrAll");
                  });
                curAct.finish();
            }

        } else if (cmd.compareToIgnoreCase("eval") == 0) {
            StringBuffer sb = new StringBuffer();
            Object obj = null;
            try {
                obj = evalExp(args.toString(), sb);
            } catch (Throwable th) {
                sb.append("evalExp ex: "+th+"\n"+SSMutil.stackTrace(th));
            }
            respTr.changeValue(displayObj(obj));
            if (sb.length() > 0) {  // Was there an error message (or eeVerbose)?
                prt(sb.toString());  // If there was other stdout/err output, put that in the log
                respTr.add("err", sb.toString());
            }

        } else if (cmd.compareToIgnoreCase("sip") == 0) {
            StringBuffer sb = new StringBuffer();
            ArrayList proxies = new ArrayList<>();
            Core core = LinphoneManager.getCore();
            for (ProxyConfig proxCfg : core.getProxyConfigList()) {
                proxies.add(proxCfg);
                sb.append(proxCfg+"\n");
            }
            respStr = sb.length()==0 ? "(none)" : sb.toString();
            //AndroidPlatformHelper aph;
            //aph = new AndroidPlatformHelper();
            //ArrayList<String> dnsList = new ArrayList<>();
            //aph.updateDnsServers(dnsList);

        } else if (cmd.compareToIgnoreCase("vibrate") == 0) {
            Vibrator mVibrator = (Vibrator)curAct.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {1000, 500, 1000, 500, 1000, 500, 1000, 500};
            if (mVibrator != null) {
                //&& LinphonePreferences.instance().isIncomingCallVibrationEnabled()) {
                //mVibrator.vibrate(pattern, 0);
            }
            respStr = "Vibrate";

        } else if (cmd.compareToIgnoreCase("viewTree") == 0) {
            try {
                respStr = org.andr.RAUtil.showViewTree(
                  curAct.getWindow().getDecorView().getRootView());
            } catch (Exception ex) {
                respStr = "(viewTree failed, curAct="+ curAct+")";
            }

        } else if (cmd.compareToIgnoreCase("seqn") == 0) {
            respStr = "Seqn ";

        } else if (cmd.compareToIgnoreCase("sensorTest") == 0) {
            respStr = "Did sensorTest";

        } else if (cmd.compareToIgnoreCase("HELLO") == 0) {
            respStr = "Bye";

        } else {
            // Try processing this using the normal SSM cmd resolution mechanism
            SSMSessCtx sctx = new SSMSessCtx();
            sctx.com = new SSMComTr();  // Cause sendResp to save response in sctx.com.tr
            SSM.procCmds(sctx, cmdTr);
            respTr = ((SSMComTr)sctx.com).tr;
            sctx.release();
        }

        if (respStr != null) {
            respTr.changeValue(respStr);
        }
        return respTr;
    } // end of hndCmd
} // end of PHSSMHndCmd class


public static void setAudioOutput(
  ForceUseModes.MediaStrategy mode, ForceUseModes.ForceUse dev) { // WG
    try {
        ForceUseModes fum = AudMgr.getAllForceUses(audSvc);
        prt("orig fum: " + fum);
        fum.setForceUse(mode, dev);
        prt("set FUM speaker: " + fum);
        AudMgr.setAllForceUses(audSvc, fum);
    } catch (Throwable ex) {
        prt("getAllForceUses ex: " + ex + "\n" + SSMutil.stackTrace(ex));
    }
} // end of setAudioOutput


// Access VOIP.MS for information about account/logs, code copied from
    // SSM /script/voip.js  Javascript!  And ssm/X10.java voipCmd
    public static CallLog[] getCallLogs() {
        CallLog[] cl;
        try {
            // args is a space separted string of name=value pairs.  It can contain:
            // timezone, date_to, date_from, answered, noanswer, busy, failed,
            // calltype, callbilling, account
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            String dtTo = "" + cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1)
              + "-" + cal.get(Calendar.DAY_OF_MONTH);
            cal.add(Calendar.DATE, -7); // Get the date a week ago
            String dtFrom = "" + cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1)
              + "-"+ cal.get(Calendar.DAY_OF_MONTH);

            int tz = -5; // NewYork
            Bytes args = new Bytes("date_from=" + dtFrom + "&date_to=" + dtTo
              + "&timezone=" + tz + "&answered=1&noanswer=1&busy=1&failed=1");

            if (CL.clTr != null) {
                CL.clTr.release();
            }
            CL.clTr = com.ibm.ssm.X10.voipCmd("getCDR", args);
            CL.clTr.select("CDR");
            CL.clTr.prepChildScan();
            int cnt = 0;
            while (CL.clTr.next()) {
                cnt++;
            }
            CL.clTr.parent();

            cl = new CallLog[cnt];
            CL.clTr.prepChildScan();
            for (int ii = 0; ii < cnt && CL.clTr.next(); ii++) {
                cl[ii] = new CL(CL.clTr.name());
            }
            CL.clTr.parent(); // Back to the CALLLOG node
        } catch (Exception ex) {
            prt("Info.getCallLog ex: " + ex + "\n" + SSMutil.stackTrace(ex));
            cl = new CallLog[0];
        }
        return cl;
    } // end of getCallLog

    public static String showVw() { // Function for calling in Evaluate Expression for debug
        return curActName+" "+curAct.toString()+"\n"
          +showViewTree(curAct.getWindow().getDecorView());
    }

    public static String showVw(View vw) {
        return showViewTree(vw);
    }

    static class CL implements CallLog {
        public static Tree clTr;
        public String entryId;

        public CL(String id) {
            entryId = id;
        }

        public Bytes entryValue(String nodeName, Bytes bt) {
            if (clTr.select(entryId)) {
                clTr.valueBytes(nodeName, bt);
                clTr.parent();
                return bt;
            }
            return null;
        }

        @Override
        public String getCallId() {
            Bytes fr = new Bytes();
            entryValue("CALLERID", fr);
            return fr.toString();
        }

        @Override
        public Call.Dir getDir() {
            Bytes dir = new Bytes();
            entryValue("DESCRIPTION", dir);
            int cc;
            if (dir.length() > 0 && ((cc = dir.charAt(0)) == 'I' || cc == 'U')) {
                // Description can be Uunited States for inbound, or Inbound Local
                return Call.Dir.Incoming;
            } else {
                return Call.Dir.Outgoing;
            }
        }

        @Override
        public int getDuration() {
            Bytes dur = new Bytes();
            try {
                entryValue("SECONDS", dur);
                return Integer.parseInt(dur.toString());
            } catch (Exception ex) {
                return 0;
            }
        }

        @Override
        public ErrorInfo getErrorInfo() {
            return null;
        }

        @Override
        public Address getFromAddress() {
            Bytes fr = new Bytes();
            entryValue("CALLERID", fr);
            return new Addr(fr.toString(), entryId);
        }

        @Override
        public Address getLocalAddress() {
            Bytes to = new Bytes();
            entryValue("DESTINATION", to);
            return new Addr(to.toString(), entryId);
        }

        @Override
        public float getQuality() {
            return 0;
        }

        @Override
        public String getRefKey() {
            return null;
        }

        @Override
        public void setRefKey(String refkey) {}

        @Override
        public Address getRemoteAddress() {
            return null;
        }

        SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        @Override
        public long getStartDate() {
            Bytes dt = new Bytes();
            entryValue("DATE", dt);
            try {
                Date dtObj = tsFormat.parse(dt.toString());
                return dtObj.getTime() / 1000;
            } catch (Exception ex) {
                prt("Info.CL.getStartDate ex: " + ex);
            }
            return 0;
        }

        @Override
        public Call.Status getStatus() {
            Bytes stat = new Bytes();
            entryValue("DISPOSITION", stat);
            Call.Status cs = Call.Status.Aborted;
            if (stat.compareTo("ANSWERED") == 0) {
                cs = Call.Status.Success;
            } else if (stat.compareTo("NO ANSWER") == 0) {
                cs = Call.Status.Missed;
            }
            return cs;
        }

        @Override
        public Address getToAddress() {
            Bytes to = new Bytes();
            entryValue("DESTINATION", to);
            return new Addr(to.toString(), entryId);
        }

        @Override
        public boolean videoEnabled() {
            return false;
        }

        @Override
        public String toStr() {
            return null;
        }

        @Override
        public boolean wasConference() {
            return false;
        }

        @Override
        public void setUserData(Object data) {}

        @Override
        public Object getUserData() {
            return null;
        }
    } // end of CL

    static class Addr implements Address {
        public String phNumb;
        public String entryId; // Name of NOB entry for this log record

        public Addr(String phNumb, String entryId) {
            this.phNumb = phNumb;
            this.entryId = entryId;
        }

        @Override
        public String getDisplayName() {
            return phNumb;
        }

        @Override
        public int setDisplayName(String displayName) {
            return 0;
        }

        @Override
        public String getDomain() {
            return null;
        }

        @Override
        public int setDomain(String domain) {
            return 0;
        }

        @Override
        public boolean isSip() {
            return false;
        }

        @Override
        public String getMethodParam() {
            return null;
        }

        @Override
        public void setMethodParam(String methodParam) {}

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public void setPassword(String password) {}

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public int setPort(int port) {
            return 0;
        }

        @Override
        public String getScheme() {
            return null;
        }

        @Override
        public boolean getSecure() {
            return false;
        }

        @Override
        public void setSecure(boolean enabled) {}

        @Override
        public TransportType getTransport() {
            return null;
        }

        @Override
        public int setTransport(TransportType transport) {
            return 0;
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public int setUsername(String username) {
            return 0;
        }

        @Override
        public String asString() {
            return "(riboAddr " + phNumb + ")";
        }

        @Override
        public String asStringUriOnly() {
            return null;
        }

        @Override
        public void clean() {}

        @Override
        public Address clone() {
            return null;
        }

        @Override
        public boolean equal(Address address2) {
            return false;
        }

        @Override
        public String getHeader(String headerName) {
            return null;
        }

        @Override
        public String getParam(String paramName) {
            return null;
        }

        @Override
        public String getUriParam(String uriParamName) {
            return null;
        }

        @Override
        public boolean hasParam(String paramName) {
            return false;
        }

        @Override
        public boolean hasUriParam(String uriParamName) {
            return false;
        }

        @Override
        public void removeUriParam(String uriParamName) {}

        @Override
        public void setHeader(String headerName, String headerValue) {}

        @Override
        public void setParam(String paramName, String paramValue) {}

        @Override
        public void setUriParam(String uriParamName, String uriParamValue) {}

        @Override
        public boolean weakEqual(Address address2) {
            return false;
        }

        @Override
        public void setUserData(Object data) {}

        @Override
        public Object getUserData() {
            return null;
        }
    } // end of Addr

    public static void minimalSSMinit(Context ctx) {
        prt(SSMutil.getTimestamp()+" minimalSSMinit, objStoreTr="+SSMutil.objStoreTr);
        if (SSMutil.objStoreTr != null) return; // Already done, leave

        SSMutil.objStoreTr = TrunkFactory.newTree();
        SSMutil.objStoreTr.add("nob", defObjStore);
        SSMutil.objStoreTr.add("mp", defObjStore2);
        SSM.consoleOS = System.out; // Make SSM.prt output go to Android stdout, logcat
        SSM.logServer = null;  // Use normal console output (logcat)

        SSMcmd.ctx = ctx;
        // Load the ANDRCONFIG obj for SSM.rootTr and SSM.configTr, for SSMcmd to find ANDRMAP
        AsyncTask.execute(new Runnable() { @Override public void run() {
            prt("Info.minimalSSMinit.execute: sending QRT CONFIG to ssm");
            SSM.configTr = ssmCmdReply(null, "QRT CONFIG");  // Copy SSM's config info
            prt("QRT CONFIG resp: "+SSM.configTr.list());
        }});
    } // end of minimalSSMinit

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of Info class
