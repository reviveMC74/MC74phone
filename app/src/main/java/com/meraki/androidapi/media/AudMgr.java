package com.meraki.androidapi.media;

import android.os.Parcel;
import android.os.Parcelable;

public class AudMgr {
    public static ForceUseModes getAllForceUses(android.media.AudioManager audioManager)
            throws Throwable {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            // Creates parcel containing 'AllForceUses' obj from framework AudioManager
            ((Parcelable)
                            audioManager
                                    .getClass()
                                    .getMethod("getAllForceUses", new Class[0])
                                    .invoke(audioManager, new Object[0]))
                    .writeToParcel(parcel, 0);

            // Use android/media/ForceUseModes.java CREATOR to create a ForceUseModes obj
            parcel.setDataPosition(0);
            ForceUseModes forceUseModes = ForceUseModes.CREATOR.createFromParcel(parcel);
            if (parcel != null) {
                parcel.recycle();
            }
            return forceUseModes;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } catch (Throwable th) {
            if (parcel != null) {
                parcel.recycle();
            }
            throw th;
        }
    }

    public static void setAllForceUses(
            android.media.AudioManager audioManager, ForceUseModes forceUseModes) throws Throwable {
        Parcel parcel = null;
        try {
            // Make a parcel copy of the ForceUseModes object
            parcel = Parcel.obtain();
            forceUseModes.writeToParcel(parcel, 0);

            // Create a ForceUseMode object from parcel
            parcel.setDataPosition(0);
            Class forceUseModesClass =
                    audioManager
                            .getClass()
                            .getClassLoader()
                            .loadClass("android.media.ForceUseModes");
            Object creator = forceUseModesClass.getField("CREATOR").get((Object) null);
            Object forceUseModesImpl =
                    creator.getClass()
                            .getMethod("createFromParcel", new Class[] {Parcel.class})
                            .invoke(creator, new Object[] {parcel});
            // How is this object different from forceUseModes argument?
            // Converts from com/meraki/androidapi/media/ForceUseModes obj to
            // android.media.ForceUseModes obj ?

            // Set the ForceUses
            audioManager
                    .getClass()
                    .getMethod("setAllForceUses", new Class[] {forceUseModesClass})
                    .invoke(audioManager, new Object[] {forceUseModesImpl});
            if (parcel != null) {
                parcel.recycle();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } catch (Throwable th) {
            if (parcel != null) {
                parcel.recycle();
            }
            throw th;
        }
    }
}
