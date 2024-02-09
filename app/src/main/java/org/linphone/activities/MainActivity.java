/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.activities;

import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.curAct;
import static ribo.phone.Info.curActName;
import static ribo.phone.Info.curActSect;
import static ribo.phone.Info.curActTag;
import static ribo.phone.Info.getDrw;
import static ribo.phone.Info.mainAct;
import static ribo.phone.Info.pkgName;
import static ribo.phone.Info.prevActName;
import static ribo.phone.MultiAct.doAction;
import static ribo.ssm.SSMcmd.ssmCmdReply;

import android.Manifest;
import android.app.*;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.ibm.ssm.SSMCom;
import com.ibm.ssm.SSMutil;
import com.ibm.ssm.tree.Bytes;
import com.ibm.ssm.tree.Tree;
import com.ibm.ssm.tree.nob.NB;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.*;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.dialer.DialerActivity;
import org.linphone.fragments.EmptyFragment;
import org.linphone.fragments.StatusBarFragment;
import org.linphone.menu.SideMenuFragment;
import org.linphone.service.LinphoneService;
import org.linphone.settings.LinphonePreferences;
import org.linphone.settings.SettingsActivity;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.LinphoneUtils;
import ribo.phone.*;

public abstract class MainActivity extends LinphoneGenericActivity
        implements StatusBarFragment.MenuClikedListener, SideMenuFragment.QuitClikedListener {
    private static final int MAIN_PERMISSIONS = 1;
    protected static final int FRAGMENT_SPECIFIC_PERMISSION = 2;

    private TextView mMissedCalls;
    private TextView mMissedMessages;
    // protected View mContactsSelected;
    // protected View mHistorySelected;
    // protected View mDialerSelected;
    // protected View mChatSelected;
    private LinearLayout mTopBar;
    private TextView mTopBarTitle;
    private LinearLayout mTabBar;

    private SideMenuFragment mSideMenuFragment;
    private StatusBarFragment mStatusBarFragment;

    protected boolean mOnBackPressGoHome;
    protected boolean mAlwaysHideTabBar;
    protected String[] mPermissionsToHave;

    private CoreListenerStub mListener;
    public static DisplayMetrics dispMet;
    public static float dispDens; // Real pix = dp * dispDens;
    public Map<String, Object> footerView = new HashMap<>();
    public View footerSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkgName = getPackageName();
        dispMet = getApplicationContext().getResources().getDisplayMetrics();
        dispDens = dispMet.density;

        try {
            setContentView(R.layout.main);
        } catch (Exception ex) {
            prt("MainActivity.onCreate.setContentView ex: " + ex + "\n" + SSMutil.stackTrace(ex));
        }
        prt("linphone app dir: " + getApplicationInfo().dataDir); // /data/data/org.linphone.debug
        prt("linphone pkg name: " + getPackageName());

        mTabBar = findViewById(R.id.footer);
        mOnBackPressGoHome = true;
        mAlwaysHideTabBar = false;

        mTopBar = findViewById(R.id.top_bar);
        mTopBarTitle = findViewById(R.id.top_bar_title);

        new PopulateFooter().execute(mTabBar);

        ImageView back = findViewById(R.id.cancel);
        back.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBack();
                    }
                });

        mStatusBarFragment =
                (StatusBarFragment) getFragmentManager().findFragmentById(R.id.status_fragment);

        DrawerLayout mSideMenu = findViewById(R.id.side_menu);
        RelativeLayout mSideMenuContent = findViewById(R.id.side_menu_content);
        mSideMenuFragment =
                (SideMenuFragment)
                        getSupportFragmentManager().findFragmentById(R.id.side_menu_fragment);
        mSideMenuFragment.setDrawer(mSideMenu, mSideMenuContent);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Released) {
                            displayMissedCalls();
                        }
                    }

                    @Override
                    public void onMessageReceived(Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onChatRoomRead(Core core, ChatRoom room) {
                        displayMissedChats();
                    }

                    @Override
                    public void onMessageReceivedUnableDecrypt(
                            Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            Core core,
                            ProxyConfig proxyConfig,
                            RegistrationState state,
                            String message) {
                        mSideMenuFragment.displayAccountsInSideMenu();

                        if (state == RegistrationState.Ok) {
                            // For push notifications to work on some devices,
                            // app must be in "protected mode" in battery settings...
                            // https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
                            DeviceUtils
                                    .displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(
                                            MainActivity.this);

                            if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
                                AuthInfo authInfo =
                                        core.findAuthInfo(
                                                proxyConfig.getRealm(),
                                                proxyConfig.getIdentityAddress().getUsername(),
                                                proxyConfig.getDomain());
                                if (authInfo != null
                                        && authInfo.getDomain()
                                                .equals(getString(R.string.default_domain))) {
                                    LinphoneManager.getInstance().isAccountWithAlias();
                                }
                            }

                            if (!Compatibility.isDoNotDisturbSettingsAccessGranted(
                                    MainActivity.this)) {
                                displayDNDSettingsDialog();
                            }
                        }
                    }

                    @Override
                    public void onLogCollectionUploadStateChanged(
                            Core core, Core.LogCollectionUploadState state, String info) {
                        Log.d("[Main Activity] Log upload state: " + state.toString()
                          + ", info = " + info);
                        if (state == Core.LogCollectionUploadState.Delivered) {
                            ClipboardManager clipboard =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Logs url", info);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(MainActivity.this,
                              getString(R.string.logs_url_copied_to_clipboard), Toast.LENGTH_SHORT).show();
                            shareUploadedLogsUrl(info);
                        }
                    }
                };
    } // end of onCreate

    public static int sSeqn = 0x1000;

    class PopulateFooter extends AsyncTask<LinearLayout, Void, Tree> {
        public LinearLayout parentLay;

        protected Tree doInBackground(@NotNull LinearLayout... ll) {
            parentLay = ll[0];
            return ctxTr;
        }

        protected void onPostExecute(Tree cTr) {
            // Executed in UI thread, after the background operation to get footer info
            if (ctxTr == null) return;
            Tree tr = (Tree) ctxTr.clone();
            curActSect = "MAINFOOTER";
            if (tr.select(curActSect) == false) {
                tr.release();
                return;
            }

            tr.prepChildScan();
            while (tr.next()) { // Scan list of button entries, construct each button
                String btnName = tr.name();
                if (btnName.charAt(0) == '-') continue; // Skip entries that start with -

                // Create RelativeLayout that will hold the ImageView, View and possible TextVieww
                RelativeLayout rl = new RelativeLayout(getApplicationContext());
                footerView.put(btnName, rl);
                rl.setBackground(getResources().getDrawable(R.drawable.footer_button));
                LinearLayout.LayoutParams llLP =
                        new LinearLayout.LayoutParams((int) (60 * dispDens), 0);
                llLP.weight = 1;
                rl.setLayoutParams(llLP);

                ImageView iv = new ImageView(getApplicationContext());
                RelativeLayout.LayoutParams rlLP = new RelativeLayout.LayoutParams(
                  LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlLP.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                iv.setLayoutParams(rlLP);
                iv.setPadding(15, 15, 15, 15);

                String drwName = tr.value("img");
                Uri imgUrl = Uri.parse("android.resource://" + pkgName + "/drawable/" + drwName);
                try {
                    iv.setImageURI(imgUrl);
                } catch (Exception ex) {
                    prt("MainActivity.PupulateFooter.onPostExecute: setImageURI ex for "
                      +btnName+" ex: "+ex);
                }
                // iv.setImageDrawable(getResources().getDrawable(R.drawable.footer_history));
                // Could att 'contentDescription'
                rl.addView(iv);

                View vw = new View(getApplicationContext());
                vw.setId(sSeqn++);
                footerView.put(btnName + "-select", vw);
                rlLP = new RelativeLayout.LayoutParams(8, LinearLayout.LayoutParams.MATCH_PARENT);
                rlLP.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                vw.setBackgroundColor(getResources().getColor(R.color.primary_color));
                Drawable bgC = vw.getBackground();
                vw.setLayoutParams(rlLP);
                vw.setVisibility(View.GONE);
                rl.addView(vw);

                TextView tv = new TextView(getApplicationContext());
                footerView.put(btnName + "-count", tv);
                tv.setTextAppearance(getApplicationContext(), R.style.unread_count_font);
                rlLP = new RelativeLayout.LayoutParams(
                  ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlLP.setMargins(15, 15, 15, 15);
                tv.setLayoutParams(rlLP);
                tv.setBackground(getResources().getDrawable(R.drawable.unread_message_count_bg));
                tv.setGravity(Gravity.CENTER);
                tv.setVisibility(View.GONE);

                parentLay.addView(rl); // Add this new RelativeLayout to the parent layout

                Bytes line = tr.valueBytes("ACT", new Bytes());
                Bytes clsName = line.parseTok(new Bytes());
                Bytes name = new Bytes();
                if (line.parseTok(name) == null) {
                    name.set(tr.name());
                }
                rl.setTag(name.toString());

                try {
                    // If this is the class that is being created, turn on the -select view
                    if (MainActivity.this.getClass().getName() == clsName.toString()) {
                        vw.setVisibility(View.VISIBLE);
                    }

                    rl.setOnClickListener( new View.OnClickListener() {
                        @Override
                        public void onClick(View vw) {
                            Tree tr = (Tree) ctxTr.clone();
                            curActSect = "MAINFOOTER";
                            curActTag = (String)vw.getTag();
                            if (tr.select(curActSect) && tr.select(curActTag)) {
                                doAction(tr);
                            }
                            tr.release();
                        }
                    });
                } catch (Exception ex) {
                    prt("populateMainFooter: Can't find "+btnName+" class '"+clsName+"'");
                }
            } // end of while loop adding RelativeLayout with images etc for each tab
            tr.release();

            mMissedCalls = (TextView) footerView.get("history-count");
            mMissedMessages = (TextView) footerView.get("chat-count");

            // mHistorySelected = (View) footerView.get("history-select");
            // mContactsSelected = (View) footerView.get("contacts-select");
            // mDialerSelected = (View) footerView.get("dialer-select");
            // mChatSelected = (View) footerView.get("chat-select");
            // mHistorySelected.setVisibility(View.VISIBLE);

            displayMissedCalls();
            displayMissedChats();

            // Change the 'selected' footer button indication
            if (footerSelected != null) footerSelected.setVisibility(View.GONE);
            footerSelected = (View) footerView.get(curActTag + "-select");
            if (footerSelected != null) {
                footerSelected.setVisibility(View.VISIBLE);
            }
        } // end of onPostExecute
    } // end of PopulateFooter class

    public static Tree svrCmdReply(String svrOid, String cmd) {
        Tree cmdTr = new NB();
        cmdTr.changeValue(cmd);
        Tree respTr = svrCmdReply(svrOid, cmdTr);
        cmdTr.release();
        return respTr;
    }

    public static Tree svrCmdReply(String svrOid, Tree cmdTr) { // RAF
        String svr = svrOid;
        // NB tr = new NB(svrOid); // Is this string a NOB?  (Rather than hostname)
        // if (tr != null && tr.validTree()) {
        //    String host = tr.value("HOST"), port = tr.value("PORT");
        //    svr = port != null ? host + ":" + port : host;
        //    tr.release();
        // }
        if (svr == null) {
            prt("svrCmdReply, SSM server oid: " + svrOid + " not found, or no HOST node");
        }
        NB tr = (NB) SSMCom.cmdReply(svr, cmdTr);
        return tr;
    } // end of svrCmdReply

    @Override
    protected void onStart() {
        super.onStart();
        prt(getClass().getSimpleName() + "(MainActivity).onStart");
        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Class cl = Class.forName(curAct.getClass().getName());
            // Class cl = getClass();

            Field fl = getClass().getDeclaredField("NAME");
            prevActName = curActName;
            curActName = ((String) fl.get(null)).toLowerCase();
            prt("MainActivity.onResume: "+curActName+", prev "+prevActName+", tag "+curActTag);
        } catch (Exception ex) {
            prt("MainActivity.onCreate ex: " + ex + "\n" + SSMutil.stackTrace(ex));
        }
        setBg(R.id.main_bg); // Set the background image

        LinphoneContext.instance().getNotificationManager()
            .removeForegroundServiceNotificationIfPossible();

        hideTopBar();
        if (!mAlwaysHideTabBar && (getFragmentManager().getBackStackEntryCount() == 0
            || !getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
            showTabBar();
        }

        mStatusBarFragment.setMenuListener(this);
        mSideMenuFragment.setQuitListener(this);
        mSideMenuFragment.displayAccountsInSideMenu();

        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.closeDrawer();
        }

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
            displayMissedChats();
            displayMissedCalls();
        }

        // Change the 'selected' footer button indication
        if (footerSelected != null) footerSelected.setVisibility(View.GONE);
        footerSelected = (View) footerView.get(curActTag + "-select");
        if (footerSelected != null) {
            footerSelected.setVisibility(View.VISIBLE);
        }
    } // end of onResume

    @Override
    protected void onPause() {
        prt(getClass().getSimpleName() + "(MainActivity).onPause");
        mStatusBarFragment.setMenuListener(null);
        mSideMenuFragment.setQuitListener(null);

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        prt(getClass().getSimpleName() + "(MainActivity).onDestroy");
        mMissedCalls = null;
        mMissedMessages = null;
        // mContactsSelected = null;
        // mHistorySelected = null;
        // mDialerSelected = null;
        // mChatSelected = null;
        mTopBar = null;
        mTopBarTitle = null;
        mTabBar = null;

        mSideMenuFragment = null;
        mStatusBarFragment = null;

        mListener = null;

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    // Child classes should call this to set their backgrounds
    public static void setBg(int vwId) {
        String bgName = null; // Allow background image to be set from MC74 NOB
        if (ctxTr != null) {
            Tree tr = (Tree) ctxTr.clone();
            View vw = mainAct.findViewById(vwId == 0 ? R.id.bg : vwId);
            if (curActSect!=null && tr.select(curActSect)
              && curActTag!=null && tr.select(curActTag)) {
                if ((bgName = tr.value("BG")) == null) {
                    tr.parent();
                    tr.parent();
                    bgName = tr.value("BG");
                }
            }
            tr.release();
            if (vw != null && bgName != null) {
                if (vw instanceof ImageView) {
                    ((ImageView) vw).setImageDrawable(getDrw(bgName));
                } else {
                    vw.setBackground(getDrw(bgName));
                }
            }
        }
    } // end of setBg

    @Override
    public void onMenuClicked() {
        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.openOrCloseSideMenu(false, true);
        } else {
            mSideMenuFragment.openOrCloseSideMenu(true, true);
        }
        AsyncTask.execute(new Runnable() {  // Allow testing of initiating SSMcmd connection
            @Override
            public void run() {
                Tree tr = ssmCmdReply(null, "hello side menu button clicked");
                if (tr == null) {
                    prt("onMenuClicked.ssmCmdReply: resp tr is null");
                } else {
                    prt("sideMenuButton.ssmCmdReply: " + tr.list());
                    tr.release();
                }

            }
        });
    }

    @Override
    public void onQuitClicked() {
        quit();
    }

    public boolean popBackStack() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
            if (!mAlwaysHideTabBar
                    && (getFragmentManager().getBackStackEntryCount() == 0
                            && getResources()
                                    .getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
                showTabBar();
            }
            return true;
        }
        return false;
    }

    public void goBack() {
        finish();
    }

    protected boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    private static void goHomeAndClearStack() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            curAct.startActivity(intent);
        } catch (IllegalStateException ise) {
            Log.e("[Main Activity] Can't start home activity: ", ise);
        }
    }

    public static void quit() {
        goHomeAndClearStack();
        if (LinphoneService.isReady()
                && LinphonePreferences.instance().getServiceNotificationVisibility()) {
            LinphoneService.instance().stopSelf();
        }
    }

    // Tab, Top and Status bars

    public void hideStatusBar() {
        findViewById(R.id.status_fragment).setVisibility(View.GONE);
    }

    public void showStatusBar() {
        findViewById(R.id.status_fragment).setVisibility(View.VISIBLE);
    }

    public void hideTabBar() {
        if (!isTablet()) { // do not hide if tablet, otherwise won't be able to navigate...
            mTabBar.setVisibility(View.GONE);
        }
    }

    public void showTabBar() {
        mTabBar.setVisibility(View.VISIBLE);
    }

    protected void hideTopBar() {
        mTopBar.setVisibility(View.GONE);
        mTopBarTitle.setText("");
    }

    private void showTopBar() {
        mTopBar.setVisibility(View.VISIBLE);
    }

    protected void showTopBarWithTitle(String title) {
        showTopBar();
        mTopBarTitle.setText(title);
    }

    // Permissions

    public boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissions(String[] permissions) {
        boolean allGranted = true;
        for (String permission : permissions) {
            allGranted &= checkPermission(permission);
        }
        return allGranted;
    }

    public void requestPermissionIfNotGranted(String permission) {
        if (!checkPermission(permission)) {
            Log.i("[Permission] Requesting " + permission + " permission");

            String[] permissions = new String[] {permission};
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                // This is to workaround an infinite loop of pause/start in Activity issue
                // if incoming call ends while screen if off and locked
                ActivityCompat.requestPermissions(this, permissions, FRAGMENT_SPECIFIC_PERMISSION);
            }
        }
    }

    public void requestPermissionsIfNotGranted(String[] perms) {
        requestPermissionsIfNotGranted(perms, FRAGMENT_SPECIFIC_PERMISSION);
    }

    private void requestPermissionsIfNotGranted(String[] perms, int resultCode) {
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        if (perms != null) { // This is created (or not) by the child activity
            for (String permissionToHave : perms) {
                if (!checkPermission(permissionToHave)) {
                    permissionsToAskFor.add(permissionToHave);
                }
            }
        }

        if (permissionsToAskFor.size() > 0) {
            for (String permission : permissionsToAskFor) {
                Log.i("[Permission] Requesting " + permission + " permission");
            }
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                // This is to workaround an infinite loop of pause/start in Activity issue
                // if incoming call ends while screen if off and locked
                ActivityCompat.requestPermissions(this, permissions, resultCode);
            }
        }
    }

    private void requestRequiredPermissions() {
        requestPermissionsIfNotGranted(mPermissionsToHave, MAIN_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0) return;

        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
            if (permissions[i].equals(Manifest.permission.READ_CONTACTS)
                    || permissions[i].equals(Manifest.permission.WRITE_CONTACTS)) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (LinphoneContext.isReady()) {
                        ContactsManager.getInstance().enableContactsAccess();
                        ContactsManager.getInstance().initializeContactManager();
                    }
                }
            } else if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                boolean enableRingtone = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
            } else if (permissions[i].equals(Manifest.permission.CAMERA)
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                LinphoneUtils.reloadVideoDevices();
            }
        }
    }

    // Missed calls & chat indicators

    protected void displayMissedCalls() {
        if (mMissedCalls == null) return; // RAF, populating the footer is asynchronous
        int count = 0;
        Core core = LinphoneManager.getCore();
        if (core != null) {
            count = core.getMissedCallsCount();
        }

        if (count > 0) {
            mMissedCalls.setText(String.valueOf(count));
            mMissedCalls.setVisibility(View.VISIBLE);
        } else {
            mMissedCalls.clearAnimation();
            mMissedCalls.setVisibility(View.GONE);
        }
    }

    public void displayMissedChats() {
        if (mMissedMessages == null) return; // RAF, populating the footer is asynchronous
        int count = 0;
        Core core = LinphoneManager.getCore();
        if (core != null) {
            count = core.getUnreadChatMessageCountFromActiveLocals();
        }

        if (count > 0) {
            mMissedMessages.setText(String.valueOf(count));
            mMissedMessages.setVisibility(View.VISIBLE);
        } else {
            mMissedMessages.clearAnimation();
            mMissedMessages.setVisibility(View.GONE);
        }
    }

    // Navigation between actvities

    public void goBackToCall() {
        boolean incoming = false;
        boolean outgoing = false;
        Call[] calls = LinphoneManager.getCore().getCalls();

        for (Call call : calls) {
            Call.State state = call.getState();
            switch (state) {
                case IncomingEarlyMedia:
                case IncomingReceived:
                    incoming = true;
                    break;
                case OutgoingEarlyMedia:
                case OutgoingInit:
                case OutgoingProgress:
                case OutgoingRinging:
                    outgoing = true;
                    break;
            }
        }

        if (incoming) {
            startActivity(new Intent(this, CallIncomingActivity.class));
        } else if (outgoing) {
            startActivity(new Intent(this, CallOutgoingActivity.class));
        } else {
            startActivity(new Intent(this, CallActivity.class));
        }
    }

    public void newOutgoingCall(String to) {
        if (LinphoneManager.getCore().getCallsNb() > 0) {
            Intent intent = new Intent(this, DialerActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("SipUri", to);
            this.startActivity(intent);
        } else {
            LinphoneManager.getCallManager().newOutgoingCall(to, null);
        }
    }

    private void addFlagsToIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    protected void changeFragment(Fragment fragment, String name, boolean isChild) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (transaction.isAddToBackStackAllowed()) {
            int count = fragmentManager.getBackStackEntryCount();
            if (count > 0) {
                FragmentManager.BackStackEntry entry =
                        fragmentManager.getBackStackEntryAt(count - 1);

                if (entry != null && name.equals(entry.getName())) {
                    fragmentManager.popBackStack();
                    if (!isChild) {
                        // We just removed it's duplicate from the back stack
                        // And we want at least one in it
                        transaction.addToBackStack(name);
                    }
                }
            }

            if (isChild) {
                transaction.addToBackStack(name);
            }
        }

        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            if (isChild) {
                if (!isTablet()) {
                    hideTabBar();
                }
            } else {
                showTabBar();
            }
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        if (isChild && isTablet()) {
            transaction.replace(R.id.fragmentContainer2, fragment, name);
            findViewById(R.id.fragmentContainer2).setVisibility(View.VISIBLE);
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, name);
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    public void showEmptyChildFragment() {
        changeFragment(new EmptyFragment(), "Empty", true);
    }

    public void showAccountSettings(int accountIndex) {
        Intent intent = new Intent(this, SettingsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("Account", accountIndex);
        startActivity(intent);
    }

    public void showContactDetails(LinphoneContact contact) {
        Intent intent = new Intent(this, ContactsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("Contact", contact);
        startActivity(intent);
    }

    public void showContactsListForCreationOrEdition(Address address) {
        if (address == null) return;

        Intent intent = new Intent(this, ContactsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("CreateOrEdit", true);
        intent.putExtra("SipUri", address.asStringUriOnly());
        if (address.getDisplayName() != null) {
            intent.putExtra("DisplayName", address.getDisplayName());
        }
        startActivity(intent);
    }

    public void showChatRoom(Address localAddress, Address peerAddress) {
        Intent intent = new Intent(this, ChatActivity.class);
        addFlagsToIntent(intent);
        if (localAddress != null) {
            intent.putExtra("LocalSipUri", localAddress.asStringUriOnly());
        }
        if (peerAddress != null) {
            intent.putExtra("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        startActivity(intent);
    }

    // Dialogs

    public Dialog displayDialog(String text) {
        return LinphoneUtils.getDialog(this, text);
    }

    public void displayChatRoomError() {
        final Dialog dialog = displayDialog(getString(R.string.chat_room_creation_failed));
        dialog.findViewById(R.id.dialog_delete_button).setVisibility(View.GONE);
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(getString(R.string.ok));
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    private void displayDNDSettingsDialog() {
        if (!LinphonePreferences.instance().isDNDSettingsPopupEnabled()) return;
        Log.w("[Permission] Asking user to grant us permission to read DND settings");

        final Dialog dialog =
                displayDialog(getString(R.string.pref_grant_read_dnd_settings_permission_desc));
        dialog.findViewById(R.id.dialog_do_not_ask_again_layout).setVisibility(View.VISIBLE);
        final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
        dialog.findViewById(R.id.doNotAskAgainLabel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                            }
                        });
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableDNDSettingsPopup(false);
                        }
                        dialog.dismiss();
                    }
                });
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setVisibility(View.VISIBLE);
        ok.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(
                                    new Intent(
                                            "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                        } catch (ActivityNotFoundException anfe) {
                            Log.e("[Main Activity] Activity not found exception: ", anfe);
                        }
                        dialog.dismiss();
                    }
                });
        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        dialog.show();
    }

    // Logs

    private void shareUploadedLogsUrl(String info) {
        final String appName = getString(R.string.app_name);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.about_bugreport_email)});
        i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
        i.putExtra(Intent.EXTRA_TEXT, info);
        i.setType("application/zip");

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(ex);
        }
    }

    // Others

    public SideMenuFragment getSideMenuFragment() {
        return mSideMenuFragment;
    }

    @Override
    public boolean onKeyDown(int kc, KeyEvent kev) {
        if (kc == KeyEvent.KEYCODE_BACK) { // RAF From original MainActivity onKeyDown
            if (mOnBackPressGoHome) {
                if (getFragmentManager().getBackStackEntryCount() == 0) {
                    goHomeAndClearStack();
                    return true;
                }
            }
            goBack();
            return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        prt("MainActivity.onNewIntent: " + intent);
    }
}
