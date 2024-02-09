package com.meraki.androidapi.media;

import android.os.Parcel;
import android.os.Parcelable;

public class ForceUseModes implements Parcelable {
    public static final Parcelable.Creator<ForceUseModes> CREATOR =
            new Parcelable.Creator<ForceUseModes>() {
                public ForceUseModes createFromParcel(Parcel in) {
                    return new ForceUseModes(in);
                }

                public ForceUseModes[] newArray(int size) {
                    return new ForceUseModes[size];
                }
            };
    ForceUse[] forceUseModes;

    public enum MediaStrategy {
        FOR_COMMUNICATION(0),
        FOR_MEDIA(1),
        FOR_RECORD(2),
        FOR_DOCK(3),
        FOR_SYSTEM(4),
        NUM_FORCE_USE(5);

        private int value;

        private MediaStrategy(int value2) {
            this.value = value2;
        }

        public int getValue() {
            return this.value;
        }

        public static MediaStrategy fromInteger(int value2) {
            switch (value2) {
                case 0:
                    return FOR_COMMUNICATION;
                case 1:
                    return FOR_MEDIA;
                case 2:
                    return FOR_RECORD;
                case 3:
                    return FOR_DOCK;
                case 4:
                    return FOR_SYSTEM;
                case 5:
                    return NUM_FORCE_USE;
                default:
                    return null;
            }
        }
    }

    public enum ForceUse {
        FORCE_NONE(0),
        FORCE_SPEAKER(1),
        FORCE_HEADPHONES(2),
        FORCE_BT_SCO(3),
        FORCE_BT_A2DP(4),
        FORCE_WIRED_ACCESSORY(5),
        FORCE_BT_CAR_DOCK(6),
        FORCE_BT_DESK_DOCK(7),
        FORCE_ANALOG_DOCK(8),
        FORCE_DIGITAL_DOCK(9),
        FORCE_NO_BT_A2DP(10),
        FORCE_SYSTEM_ENFORCED(11),
        FORCE_HANDSET(12),
        FORCE_USB_HEADSET(13),
        NUM_FORCE_CONFIG(14);

        private int value;

        private ForceUse(int value2) {
            this.value = value2;
        }

        public int getValue() {
            return this.value;
        }

        public static ForceUse fromInteger(int value2) {
            switch (value2) {
                case 0:
                    return FORCE_NONE;
                case 1:
                    return FORCE_SPEAKER;
                case 2:
                    return FORCE_HEADPHONES;
                case 3:
                    return FORCE_BT_SCO;
                case 4:
                    return FORCE_BT_A2DP;
                case 5:
                    return FORCE_WIRED_ACCESSORY;
                case 6:
                    return FORCE_BT_CAR_DOCK;
                case 7:
                    return FORCE_BT_DESK_DOCK;
                case 8:
                    return FORCE_ANALOG_DOCK;
                case 9:
                    return FORCE_DIGITAL_DOCK;
                case 10:
                    return FORCE_NO_BT_A2DP;
                case 11:
                    return FORCE_SYSTEM_ENFORCED;
                case 12:
                    return FORCE_HANDSET;
                case 13:
                    return FORCE_USB_HEADSET;
                case 14:
                    return NUM_FORCE_CONFIG;
                default:
                    return null;
            }
        }
    }

    protected ForceUseModes() {
        try {
            int NUM_FORCE_USE =
                    getClass()
                            .getClassLoader()
                            .loadClass("android.media.AudioSystem")
                            .getField("NUM_FORCE_USE")
                            .getInt((Object) null);
            this.forceUseModes = new ForceUse[NUM_FORCE_USE];
            for (int i = 0; i < NUM_FORCE_USE; i++) {
                this.forceUseModes[i] = ForceUse.FORCE_NONE;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected ForceUseModes(int[] uses) {
        this.forceUseModes = new ForceUse[uses.length];
        for (int i = 0; i < uses.length; i++) {
            this.forceUseModes[i] = ForceUse.fromInteger(uses[i]);
        }
    }

    public ForceUse getForceUse(MediaStrategy forStrategy) {
        return this.forceUseModes[forStrategy.getValue()];
    }

    public void setForceUse(MediaStrategy forStrategy, ForceUse forceUse) {
        this.forceUseModes[forStrategy.getValue()] = forceUse;
    }

    /* access modifiers changed from: protected */
    public int[] toIntArray() {
        int[] retval = new int[this.forceUseModes.length];
        for (int i = 0; i < this.forceUseModes.length; i++) {
            retval[i] = this.forceUseModes[i].getValue();
        }
        return retval;
    }

    public String toString() {
        if (this.forceUseModes == null) {
            return "";
        }
        String response = "" + "{";
        for (int i = 0; i < this.forceUseModes.length; i++) {
            response =
                    response
                            + (MediaStrategy.fromInteger(i) != null
                                    ? MediaStrategy.fromInteger(i).name()
                                    : "BAD_STRATEGY_NAME")
                            + " : "
                            + (this.forceUseModes[i] != null
                                    ? this.forceUseModes[i].name()
                                    : "BAD_FORCE_USE_NAME");
            if (i != this.forceUseModes.length - 1) {
                response = response + ", ";
            }
        }
        return response + "}";
    }

    protected ForceUseModes(Parcel src) {
        this(src.createIntArray());
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(toIntArray());
    }
}
