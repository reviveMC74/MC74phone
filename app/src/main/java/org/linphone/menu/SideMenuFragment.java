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
package org.linphone.menu;

import static ribo.phone.Info.ctxTr;
import static ribo.phone.Info.pkgName;
import static ribo.phone.MultiAct.doAction;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.ibm.ssm.tree.Bytes;
import com.ibm.ssm.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.MainActivity;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class SideMenuFragment extends Fragment {
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;
    private RelativeLayout mDefaultAccount;
    private ListView mAccountsList, mSideMenuItemList;
    private QuitClikedListener mQuitListener;
    public Tree smTr; // SideMenu item list

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.side_menu, container, false);

        List<SideMenuItem> sideMenuItems = new ArrayList<>();
        if (ctxTr != null) {
            smTr = (Tree) ctxTr.clone();
            if (smTr.select("SIDEMENU")) {
                smTr.prepChildScan();
                while (smTr.next()) {
                    if (smTr.name().charAt(0) == '-') continue; // Skip items markd with a '-'
                    String drwName = smTr.value("img");
                    int id = getResources().getIdentifier(drwName, "drawable", pkgName);
                    sideMenuItems.add(new SideMenuItem(smTr.value(), id));
                } // end of while scanning next nodes in sideMenu list
                smTr.parent();
            } // end of selected SIDEMENU node
            // smTr releaseed in onDetach
        }

        mSideMenuItemList = view.findViewById(R.id.item_list);
        mSideMenuItemList.setAdapter(
                new SideMenuAdapter(getActivity(), R.layout.side_menu_item_cell, sideMenuItems));

        mSideMenuItemList.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        String selectedItem = mSideMenuItemList.getAdapter().getItem(i).toString();
                        Tree tr = (Tree) smTr.clone();
                        Bytes line = new Bytes(), actName = new Bytes(), arg1 = new Bytes();
                        if (tr.selectValue(selectedItem)) {
                            doAction(tr);
                        }
                        tr.release();
                    }
                });

        mAccountsList = view.findViewById(R.id.accounts_list);
        mDefaultAccount = view.findViewById(R.id.default_account);

        RelativeLayout quitLayout = view.findViewById(R.id.side_menu_quit);
        quitLayout.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mQuitListener != null) {
                            mQuitListener.onQuitClicked();
                        }
                    }
                });

        return view;
    } // end of onCreateView

    @Override
    public void onDetach() {
        super.onDetach();
        if (smTr != null) smTr.release();
    }

    public void setQuitListener(QuitClikedListener listener) {
        mQuitListener = listener;
    }

    public void setDrawer(DrawerLayout drawer, RelativeLayout content) {
        mSideMenu = drawer;
        mSideMenuContent = content;
    }

    public boolean isOpened() {
        return mSideMenu != null && mSideMenu.isDrawerVisible(Gravity.LEFT);
    }

    public void closeDrawer() {
        openOrCloseSideMenu(false, false);
    }

    public void openOrCloseSideMenu(boolean open, boolean animate) {
        if (mSideMenu == null || mSideMenuContent == null) return;

        if (open) {
            mSideMenu.openDrawer(mSideMenuContent, animate);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent, animate);
        }
    }

    private void displayMainAccount() {
        mDefaultAccount.setVisibility(View.VISIBLE);
        ImageView status = mDefaultAccount.findViewById(R.id.main_account_status);
        TextView address = mDefaultAccount.findViewById(R.id.main_account_address);
        TextView displayName = mDefaultAccount.findViewById(R.id.main_account_display_name);

        if (!LinphoneContext.isReady() || LinphoneManager.getCore() == null) return;

        ProxyConfig proxy = LinphoneManager.getCore().getDefaultProxyConfig();
        if (proxy == null) {
            displayName.setText(getString(R.string.no_account));
            status.setVisibility(View.GONE);
            address.setText("");
            mDefaultAccount.setOnClickListener(null);
        } else {
            address.setText(proxy.getIdentityAddress().asStringUriOnly());
            displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
            status.setImageResource(getStatusIconResource(proxy.getState()));
            status.setVisibility(View.VISIBLE);

            if (!getResources().getBoolean(R.bool.disable_accounts_settings_from_side_menu)) {
                mDefaultAccount.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ((MainActivity) getActivity())
                                        .showAccountSettings(
                                                LinphonePreferences.instance()
                                                        .getDefaultAccountIndex());
                            }
                        });
            }
        }
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

    public void displayAccountsInSideMenu() {
        Core core = LinphoneManager.getCore();
        if (core != null
                && core.getProxyConfigList() != null
                && core.getProxyConfigList().length > 1) {
            mAccountsList.setVisibility(View.VISIBLE);
            mAccountsList.setAdapter(new SideMenuAccountsListAdapter(getActivity()));
            mAccountsList.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(
                                AdapterView<?> adapterView, View view, int i, long l) {
                            if (view != null && view.getTag() != null) {
                                int position = Integer.parseInt(view.getTag().toString());
                                ((MainActivity) getActivity()).showAccountSettings(position);
                            }
                        }
                    });
        } else {
            mAccountsList.setVisibility(View.GONE);
        }
        displayMainAccount();
    }

    public interface QuitClikedListener {
        void onQuitClicked();
    }

    public static void prt(String str) {
        System.out.println(str);
    }
}
