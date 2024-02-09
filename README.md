

# MC74 -- the softphone Android app for reviveMC74

This repository is for the software phone application which is supplied with the reviveMC74 repository.  To 'root' and revive a Cisco/Meraki MC74 phone, follow the instructions in the reviveMC74/reviveMC74 github repository.

This app provides the Voice Over IP tlephone functionality for the revived MC74.  It is a significantly modified version of the Linphone SoftPhone Android app. See the linphoneREADME.md file in this repo for information about how to use the original Linphone app.

## Differences betweeen standard softphone and MC74 app

The Linphone app is written for a standard cell phone sort of environment.  The MC74 differs from a cell phone in that it has a larger, 
landscape display and, importantly, it has a handset.
Aside from using the MC74s 'speakerphone' microphone and speaker, the handset handset has another 
microphone and speaker that are used when the user lifts that handset from the 'hookswitch'
(For you youngsters, a telephone hookswitch is the thing that the handset rests on on and old dial (or earlier) phone.  Going 'off hook' meant that you pickup up the handset and wanted to start a phone call or answer the phone.) 
In the MC74 the 'hookswitch is actually just a light sensor hidden on the phone under the place that the speaker end of the handset rests on the phone.
The Linphone app had to me modified to detect when the handset was lifted and: 1) switch to using the handset microphone and speaker, 2) provide a 'dialtone', or 3) initiate the reception of an incoming call.

The MC74 app also manages having several phones as 'extensions', where there are several MC74s that share the same phone number and in a house.
The MC74 app handles displaying and playing back voicemails and text messages associated with your VOIP connection.  

Because of the landscape orientation of the MC74 display, some of the screens/pages or the Linphone app were changed to show content on a two pane, side by side display rather than a single pane.
These pages were also modified to be HTML pages in a webview (a browser component of Android) rather as Android Views/Widgets.

The app was also changed to be based on the SSM, Shared State Manager, server to allow the app to access services and to allow other apps to use the phone as a 'service'. 
It also allows information about the state of phone calls, voicemails, and text messages to be shared among the several phone 'extensions' in a house, and to allow the phone/VOIP/internet to controll 'smart home' services in the house.

