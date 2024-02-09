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
package org.linphone.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.*;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.ibm.ssm.*;
import com.ibm.ssm.tree.*;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Event;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import static org.linphone.activities.LinphoneLauncherActivity.prt;
import static org.linphone.activities.MainActivity.svrCmdReply;
import static ribo.phone.Info.centralSvr;
import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.curAct;
import static ribo.phone.Info.curActSect;
import static ribo.phone.MultiAct.doAction;

public class StatusBarFragment extends Fragment {
    private static TextView mStatusText, mVoicemailCount, mSmsCount;
    private ImageView mStatusLed;
    private static ImageView mVoicemail, mSms;
    private CoreListenerStub mListener;
    private MenuClikedListener mMenuListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.status_bar, container, false);

        mStatusText = view.findViewById(R.id.status_text);
        mStatusLed = view.findViewById(R.id.status_led);
        ImageView menu = view.findViewById(R.id.side_menu_button);
        mVoicemail = view.findViewById(R.id.voicemail);
        mVoicemailCount = view.findViewById(R.id.voicemail_count);
        mSms = view.findViewById(R.id.sms);
        mSmsCount = view.findViewById(R.id.sms_count);
        prt("StatusBarFragment.createView, mSms "+mSms);

        mMenuListener = null;
        menu.setOnClickListener( new OnClickListener() {
          @Override
          public void onClick(View v) {
            if (mMenuListener != null) {
              mMenuListener.onMenuClicked();
            }
          }
        });

        ViewOCL viewOCL = new ViewOCL();
        mVoicemail.setOnClickListener(viewOCL);
        mVoicemailCount.setOnClickListener(viewOCL);
        mSms.setOnClickListener(viewOCL);
        mSmsCount.setOnClickListener(viewOCL);

        // We create it once to not delay the first display
        populateSliderContent();

        mListener = new CoreListenerStub() {
            @Override
            public void onRegistrationStateChanged(
              final Core core,
              final ProxyConfig proxy,
              final RegistrationState state,
              String smessage) {
                if (core.getProxyConfigList() == null) {
                    showNoAccountConfigured();
                    return;
                }

                if ((core.getDefaultProxyConfig() != null
                  && core.getDefaultProxyConfig().equals(proxy))
                  || core.getDefaultProxyConfig() == null) {
                    mStatusLed.setImageResource(getStatusIconResource(state));
                    mStatusText.setText(getStatusIconText(state));
                }

                try {
                    mStatusText.setOnClickListener(
                      new OnClickListener() {
                          @Override
                          public void onClick(View v) {
                              Core core = LinphoneManager.getCore();
                              if (core != null) {
                                  core.refreshRegisters();
                              }
                          }
                      });
                } catch (IllegalStateException ise) {
                    Log.e(ise);
                }
            }

            @Override
            public void onNotifyReceived(
              Core core, Event ev, String eventName, Content content) {

                if (!content.getType().equals("application")) return;
                if (!content.getSubtype().equals("simple-message-summary")) return;

                if (content.getSize() == 0) return;

                int unreadCount = 0;
                String data = content.getStringBuffer().toLowerCase();
                String[] voiceMail = data.split("voice-message: ");
                if (voiceMail.length >= 2) {
                    final String[] intToParse = voiceMail[1].split("/", 0);
                    try {
                        unreadCount = Integer.parseInt(intToParse[0]);
                    } catch (NumberFormatException nfe) {
                        Log.e("[Status Fragment] " + nfe);
                    }
                    // Where do these voicemail messages come from?  Some remove SIP Voicemail server?
                    if (false && unreadCount > 0) {  // WG Disable this
                        mVoicemailCount.setText(String.valueOf(unreadCount));
                        mVoicemail.setVisibility(View.VISIBLE);
                        mVoicemailCount.setVisibility(View.VISIBLE);
                    } else {
                        mVoicemail.setVisibility(View.GONE);
                        mVoicemailCount.setVisibility(View.GONE);
                    }
                }
            }
        };  // end of newCoreListenerStub

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {  // (Done after curAct has been set by DialerActivity)
                // Request the (household) central server to send us an SMS/Voicemail updateStatus cmd
                prt("StatusBarFragment.createView.AsyncTask send requestUpdate to " + centralSvr);
                if (centralSvr == null) {
                    prt("centralSvr null");
                }
                Tree tr = svrCmdReply(centralSvr, "WRT VOIP REQUESTUPDATE");
                if (tr != null) {
                    prt("  StatusBarFragment.createView.AsyncTask: requestUpdate resp: " + tr.list());
                    tr.release();
                } else {
                    prt("StatusBarFragment.createView: centralSvr, "+centralSvr
                      +", didn't connect for requestUpdate cmd.");
                }
            }
        });

        return view;
    } // end of createView


    class ViewOCL implements OnClickListener {
        @Override
        public void onClick (View vw){
            int id = vw.getId();
            String tab = "history";
            prt("StatusBarFragment onClick: "+id);
            if (id==R.id.voicemail || id==R.id.voicemail_count) {  // For a click on voicemail/Count, go to voicemail page
                tab = "voicemail";
            } else if (id==R.id.sms || id==R.id.sms_count) { // For click on sms, show SMS webpanel
                tab = "sms";
            }

            // Switch to the panel for this type of info (voicemail or sms)
            Tree tr = (Tree) ctxTr.clone();
            if (tr.select("MAINFOOTER") && tr.select(tab)) {
                doAction(tr);
            }
            tr.release();
        }
    } // end of ViewOCL


    @Override
    public void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
            ProxyConfig lpc = core.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(core, lpc, lpc.getState(), null);
            } else {
                showNoAccountConfigured();
            }
        } else {
            mStatusText.setVisibility(View.VISIBLE);
        }


    } // end of onResume


    @Override
    public void onPause() {
        super.onPause();

        if (LinphoneContext.isReady()) {
            Core core = LinphoneManager.getCore();
            if (core != null) {
                core.removeListener(mListener);
            }
        }
    }

    public void setMenuListener(MenuClikedListener listener) {
        mMenuListener = listener;
    }

    private void populateSliderContent() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            mVoicemailCount.setVisibility(View.VISIBLE);

            if (core.getProxyConfigList().length == 0) {
                showNoAccountConfigured();
            }
        }
    }

    private void showNoAccountConfigured() {
        mStatusLed.setImageResource(R.drawable.led_disconnected);
        mStatusText.setText(getString(R.string.no_account));
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    private String getStatusIconText(RegistrationState state) {
        Context context = getActivity();
        try {
            if (state == RegistrationState.Ok) {
                return context.getString(R.string.status_connected);
            } else if (state == RegistrationState.Progress) {
                return context.getString(R.string.status_in_progress);
            } else if (state == RegistrationState.Failed) {
                return context.getString(R.string.status_error);
            } else {
                return context.getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return context.getString(R.string.status_not_connected);
    }

    public interface MenuClikedListener {
        void onMenuClicked();
    }


    public static void updateSmsVm(int newSmsCnt, int newVmCnt) {
        if (mSms == null) {
            prt("StatusBarFragment.updateSmsVm: mSms is null.  ignoring update request;");
        } else {
            curAct.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    prt(SSMutil.getTimestamp() + " StatusBarFragment.updateSmsVm: newSmsCnt " + newSmsCnt + ", newVmCnt " + newVmCnt);
                    if (newSmsCnt == -1) {  // -1 means value was not set, hasn't changed
                    } else if (newSmsCnt > 0) {
                        mSmsCount.setText(String.valueOf(newSmsCnt));
                        mSms.setVisibility(View.VISIBLE);
                        mSms.setVisibility(View.VISIBLE);
                    } else {
                        mSms.setVisibility(View.GONE);
                        mSmsCount.setVisibility(View.GONE);
                    }


                    if (newVmCnt == -1) {  // -1 means value was not set, hasn't changed
                    } else if (newVmCnt > 0) {
                        mVoicemailCount.setText(String.valueOf(newVmCnt));
                        mVoicemail.setVisibility(View.VISIBLE);
                        mVoicemailCount.setVisibility(View.VISIBLE);
                    } else {
                        mVoicemail.setVisibility(View.GONE);
                        mVoicemailCount.setVisibility(View.GONE);
                    }
                }
            }); // end of runOnUiThread Runnable class
        }
    } // end of updateSmsVm
}
