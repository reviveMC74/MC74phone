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
package org.linphone.dialer;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.ibm.ssm.SSMutil;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import org.linphone.*;
import org.linphone.R;
import org.linphone.activities.*;
import org.linphone.call.views.CallButton;
import org.linphone.contacts.ContactsActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.*;
import org.linphone.core.tools.Log;
import org.linphone.dialer.views.AddressText;
import org.linphone.dialer.views.Digit;
import org.linphone.dialer.views.EraseButton;
import org.linphone.settings.LinphonePreferences;
import ribo.phone.*;

public class DialerActivity extends MainActivity implements AddressText.AddressChangedListener {
    private static final String ACTION_CALL_LINPHONE = "org.linphone.intent.action.CallLaunched";
    public static String NAME = "dialer";

    private AddressText mAddress;
    private CallButton mStartCall, mAddCall, mTransferCall;
    private ImageView mAddContact, mBackToCall;

    private boolean mIsTransfer;
    private CoreListenerStub mListener;
    private boolean mInterfaceLoaded;
    private String mAddressToCallOnLayoutReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prt(SSMutil.getTimestamp()+" DialerActivity onCreate");

        mInterfaceLoaded = false;
        // Uses the fragment container layout to inflate the dialer view instead of using a fragment
        try {
            View dialerView = LayoutInflater.from(this).inflate(R.layout.dialer, null, false);
            LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
            fragmentContainer.addView(dialerView, params);
            // setBg(); // Call super, MainActivity.setBg to set R.id.bg per ctx obj

            initUI(dialerView);
            mInterfaceLoaded = true;
            if (mAddressToCallOnLayoutReady != null) {
                mAddress.setText(mAddressToCallOnLayoutReady);
                mAddressToCallOnLayoutReady = null;
            }

            // AsyncLayoutInflator cannot be used with org.linphone.AddressText
            // because AddressText needs to create a Handler which requires
            // a looper, which the AsyncLaoyoutInflator will not have.
            // new AsyncLayoutInflater(this).inflate(R.layout.dialer,
            //     null, new AsyncLayoutInflater.OnInflateFinishedListener() {
            //          @Override
            //          public void onInflateFinished(
            //              @NonNull View view, int resid, @Nullable ViewGroup parent) {
            //              LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
            //              LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            //                  ViewGroup.LayoutParams.MATCH_PARENT,
            //                  ViewGroup.LayoutParams.MATCH_PARENT);
            //              fragmentContainer.addView(view, params);
            //              initUI(view);
            //              mInterfaceLoaded = true;
            //              if (mAddressToCallOnLayoutReady != null) {
            //                  mAddress.setText(mAddressToCallOnLayoutReady);
            //                  mAddressToCallOnLayoutReady = null;
            //              }
            //              }
            //          });
        } catch (Exception ex) {
            prt("DialerActivity.onCreate.inflate ex " + ex + "\n" + SSMutil.stackTrace(ex));
        }

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        updateLayout();
                    }
                };

        // On dialer we ask for all permissions
        mPermissionsToHave =
                new String[] {
                    // This one is to allow floating notifications
                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                    // Required starting Android 9 to be able to start a foreground service
                    "android.permission.FOREGROUND_SERVICE",
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CONTACTS
                };

        mIsTransfer = false;
        if (getIntent() != null) {
            mIsTransfer = getIntent().getBooleanExtra("isTransfer", false);
        }

        handleIntentParams(getIntent());
    } // end of onCreate


    @Override
    protected void onNewIntent(Intent intent) {
        prt(SSMutil.getTimestamp()+" DialerActivity.onNewIntent "+intent);
        super.onNewIntent(intent);

        handleIntentParams(intent);

        if (intent != null) {
            mIsTransfer = intent.getBooleanExtra("isTransfer", mIsTransfer);
            if (mAddress != null && intent.getStringExtra("SipUri") != null) {
                mAddress.setText(intent.getStringExtra("SipUri"));
            }
        }
    } // end of onNewIntent


    public static long lastVoipSettingRedirect = 0;  // 0 or time of last redirect in seconds
    @Override
    protected void onResume() {
        super.onResume();
        // if (mDialerSelected != null) mDialerSelected.setVisibility(View.VISIBLE); // added if

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }

        // If reviveMC74 app starting with no account defined, go to voidSettings page
        ProxyConfig[] proxCfgList = core.getProxyConfigList();
        long now = Calendar.getInstance().getTimeInMillis()/1000;  // Seconds since 1970
        prt(SSMutil.getTimestamp()+" DialerActivity.onResume, "+(now-lastVoipSettingRedirect)+' '+proxCfgList);
        // If no voip account is setup AND it is at least 2 minutes since last redirecto...
        if ((proxCfgList==null || proxCfgList.length==0)
            && (lastVoipSettingRedirect==0 || now>lastVoipSettingRedirect+10*60))  {
            lastVoipSettingRedirect = now;

            Intent intent = new Intent();
            intent.setClass(DialerActivity.this, WebAct.class);
            intent.putExtra("path", "sideMenu.voipSettings");
            prt("  DialerActivity.onResume, starting voipSettings: "+intent);
            startActivity(intent);
        }

        if (mInterfaceLoaded) {
            updateLayout();
            enableVideoPreviewIfTablet(true);
        }
    }

    @Override
    protected void onPause() {
        enableVideoPreviewIfTablet(false);
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mInterfaceLoaded) {
            mAddress = null;
            mStartCall = null;
            mAddCall = null;
            mTransferCall = null;
            mAddContact = null;
            mBackToCall = null;
        }
        if (mListener != null) mListener = null;

        super.onDestroy();
    }

    private void initUI(View view) {
        View vw = view.findViewById(R.id.address);
        mAddress = (AddressText)vw; // temp
        mAddress.setAddressListener(this);

        EraseButton erase = view.findViewById(R.id.erase);
        erase.setAddressWidget(mAddress);

        mStartCall = view.findViewById(R.id.start_call);
        mStartCall.setAddressWidget(mAddress);

        mAddCall = view.findViewById(R.id.add_call);
        mAddCall.setAddressWidget(mAddress);

        mTransferCall = view.findViewById(R.id.transfer_call);
        mTransferCall.setAddressWidget(mAddress);
        mTransferCall.setIsTransfer(true);

        mAddContact = view.findViewById(R.id.add_contact);
        mAddContact.setEnabled(false);
        mAddContact.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(DialerActivity.this, ContactsActivity.class);
                        intent.putExtra("EditOnClick", true);
                        intent.putExtra("SipAddress", mAddress.getText().toString());
                        startActivity(intent);
                    }
                });

        mBackToCall = view.findViewById(R.id.back_to_call);
        mBackToCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBackToCall();
                    }
                });

        if (getIntent() != null) {
            mAddress.setText(getIntent().getStringExtra("SipUri"));
        }

        setUpNumpad(view);
        updateLayout();
        enableVideoPreviewIfTablet(true);
    }

    private void enableVideoPreviewIfTablet(boolean enable) {
        Core core = LinphoneManager.getCore();
        TextureView preview = findViewById(R.id.video_preview);
        ImageView changeCamera = findViewById(R.id.video_preview_change_camera);

        if (preview != null && changeCamera != null && core != null) {
            if (enable && isTablet() && LinphonePreferences.instance().isVideoPreviewEnabled()) {
                preview.setVisibility(View.VISIBLE);
                core.setNativePreviewWindowId(preview);
                core.enableVideoPreview(true);

                if (core.getVideoDevicesList().length > 1) {
                    changeCamera.setVisibility(View.VISIBLE);
                    changeCamera.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    LinphoneManager.getCallManager().switchCamera();
                                }
                            });
                }
            } else {
                preview.setVisibility(View.GONE);
                changeCamera.setVisibility(View.GONE);
                core.setNativePreviewWindowId(null);
                core.enableVideoPreview(false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("isTransfer", mIsTransfer);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mIsTransfer = savedInstanceState.getBoolean("isTransfer");
    }

    @Override
    public void onAddressChanged() {
        mAddContact.setEnabled(!mAddress.getText().toString().isEmpty());
    }

    private void updateLayout() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }

        boolean atLeastOneCall = core.getCallsNb() > 0;
        mStartCall.setVisibility(atLeastOneCall ? View.GONE : View.VISIBLE);
        mAddContact.setVisibility(atLeastOneCall ? View.GONE : View.VISIBLE);
        mAddContact.setEnabled(!mAddress.getText().toString().isEmpty());

        if (!atLeastOneCall) {
            if (core.getVideoActivationPolicy().getAutomaticallyInitiate()) {
                mStartCall.setImageResource(R.drawable.call_video_start);
            } else {
                mStartCall.setImageResource(R.drawable.call_audio_start);
            }
        }

        mBackToCall.setVisibility(atLeastOneCall ? View.VISIBLE : View.GONE);
        mAddCall.setVisibility(atLeastOneCall && !mIsTransfer ? View.VISIBLE : View.GONE);
        mTransferCall.setVisibility(atLeastOneCall && mIsTransfer ? View.VISIBLE : View.GONE);
    }

    private void handleIntentParams(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        String addressToCall = null;
        if (ACTION_CALL_LINPHONE.equals(action)
          && (intent.getStringExtra("NumberToCall") != null)) {
            String numberToCall = intent.getStringExtra("NumberToCall");
            Log.i("[Dialer] ACTION_CALL_LINPHONE with number: " + numberToCall);
            LinphoneManager.getCallManager().newOutgoingCall(numberToCall, null);
        } else {
            Uri uri = intent.getData();
            if (uri != null) {
                Log.i("[Dialer] Intent data is: " + uri.toString());
                if (Intent.ACTION_CALL.equals(action)) {
                    String dataString = intent.getDataString();

                    try {
                        addressToCall = URLDecoder.decode(dataString, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        Log.e("[Dialer] Unable to decode URI " + dataString);
                        addressToCall = dataString;
                    }

                    if (addressToCall.startsWith("sip:")) {
                        addressToCall = addressToCall.substring("sip:".length());
                    } else if (addressToCall.startsWith("tel:")) {
                        addressToCall = addressToCall.substring("tel:".length());
                    }
                    Log.i("[Dialer] ACTION_CALL with number: " + addressToCall);
                } else {
                    addressToCall =
                      ContactsManager.getInstance()
                        .getAddressOrNumberForAndroidContact(getContentResolver(), uri);
                    Log.i("[Dialer] " + action + " with number: " + addressToCall);
                }
            } else {
                Log.w("[Dialer] Intent data is null for action " + action);
            }
        }

        if (addressToCall != null) {
            if (mAddress != null) {
                mAddress.setText(addressToCall);
            } else {
                mAddressToCallOnLayoutReady = addressToCall;
            }
        }

        // From SSMservice ribo.phone.ssm.Phone, when offHook occurs (with no call in progress)
        String dialtoneFid = intent.getStringExtra("dialtone");
        if (dialtoneFid != null) {
            if (dialtoneFid.length() < 4) {
                dialtoneFid = "dialtone.wav";
            }
            prt("Play dialtone: " + dialtoneFid);
            Info.dialtonePlaying = true;
            VoicemailAct.playMsg(dialtoneFid, true);
        }

        // If 'startHide' extra, load another application now that we are initialized
        // This used by SSM ribo.ssm.Phone.startMC74 to get the MC74 app initialized
        // at boot time to save time when the first phone call comes in
        String hideApp = intent.getStringExtra("startHide");
        if (hideApp != null) {
            Intent it = new Intent();
            prt("DialerActivity.startHide: starting act: "+hideApp);
            String cls="", pkg=hideApp;
            int ii = hideApp.indexOf('/');  // Find end of package, start of class
            if (ii>0) {
                cls = hideApp.substring(ii+1);
                pkg = hideApp.substring(0, ii);
                if (cls.charAt(0)=='.')  { cls = pkg+cls; } // Full name for class
            }
            prt("  pkg '"+pkg+"', cls '"+cls+"'");
            it.setClassName(pkg, cls);
            String url = intent.getStringExtra("url");
            if (url != null) {
                it.putExtra("url", url);
            }
            startActivity(it);

        }
    }  // end of handleIntentParams


    private void setUpNumpad(View view) {
        if (view == null) return;
        for (Digit v : retrieveChildren((ViewGroup) view, Digit.class)) {
            v.setAddressWidget(mAddress);
        }
    }

    private <T> Collection<T> retrieveChildren(ViewGroup viewGroup, Class<T> clazz) {
        final Collection<T> views = new ArrayList<>();
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ViewGroup) {
                views.addAll(retrieveChildren((ViewGroup) v, clazz));
            } else {
                if (clazz.isInstance(v)) views.add(clazz.cast(v));
            }
        }
        return views;
    } // end of retrieveChildren

    public static void prt(String str) {
        System.out.println(str);
    }
} // end of DialerActivity
