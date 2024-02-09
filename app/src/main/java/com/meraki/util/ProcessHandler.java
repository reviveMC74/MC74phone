package com.meraki.util;

import android.os.Process;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class ProcessHandler implements Callable<Boolean> {
    static final /* synthetic */ boolean $assertionsDisabled =
            (!ProcessHandler.class.desiredAssertionStatus());
    private String TAG;
    private String cmd;

    public ProcessHandler(String c, String tag) {
        if ($assertionsDisabled || Process.myUid() == 1000) {
            this.cmd = "su -c " + c;
            this.TAG = tag;
            return;
        }
        throw new AssertionError();
    }

    /* access modifiers changed from: protected */
    public void handleError(int exitValue, String output) {
        prt(TAG + " Got non-zero exit code: " + exitValue + " (" + output + ")");
    }

    public Boolean call() {
        ProcessBuilder pb = new ProcessBuilder(this.cmd.split(" "));
        pb.redirectErrorStream(true);
        try {
            java.lang.Process proc = pb.start();
            proc.waitFor();
            int exitValue = proc.exitValue();
            if (exitValue == 0) {
                return true;
            }
            Scanner s = new Scanner(proc.getInputStream()).useDelimiter("\\A");
            handleError(exitValue, s.hasNext() ? s.next() : "");
            return false;
        } catch (IOException ex) {
            prt(this.TAG + " Caught exception running: " + this.cmd + ", ex: " + ex);
        } catch (InterruptedException ex) {
            prt(this.TAG + " Caught exception running: " + this.cmd + ", ex: " + ex);
        }
        return false;
    } // end of call

    public static void prt(String str) {
        System.out.println(str);
    }
}
