[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![CLA](https://img.shields.io/badge/CLA%3F-No-lightgrey.svg)](https://mycroft.ai/cla) ![Team](https://img.shields.io/badge/Team-Community-violet.svg) ![Status](https://img.shields.io/badge/-Experimental-orange.svg)

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com)
[![Join chat](https://img.shields.io/badge/Mattermost-join_chat-brightgreen.svg)](https://chat.mycroft.ai/community/channels/android)


## Modifications for COALA

We added these features:
- separate toggle audio output for user utterances and bot utterances
- image upload to a cloud folder (requires access credentials)
- QR code reading to send hard-to-spell information to Mycroft
- write-only message support (silent utters) for the Rasa chat skill.
- client token instead of IP address of the mycroft server
- links are clickable

# Mycroft-Android

This is the Android companion app to Mycroft-core. It works by opening a websocket connection to the Mycroft-core messagebus
and sending and receiving messages from there.

It implements voice recognition and Text To Speech (TTS) via Google API's at the moment, but that may change soon.

## New Features

The companion app was modified by adding new features. Each feature is explained in a subsection below.

### Toggle mute (for assistant and user utterances)

The app has had already a voice switch button (at the left bottom) that reads out loud or mutes answers from assistant. Normally inputs given by user is always being read out loud. This feature adds another voice switch button to the right bottom of the screen to toggle loud reading of inputs from user.

### QR code scan

This feature allows scanning of barcode (Code 128) and QR code by using camera of the device. A new button with a barcode icon is added to microphone input interface, in order to start scan operation. When a barcode or QR code is placed into camera frame, scan is automatically performed.
If scan works successfully, information of the barcode/QR code is sent to assistant. Otherwise user is informed with a message. This feature requires camera permission.

### Image upload

Image upload feature adds a new interface to configure upload settings and a new button with an upload icon to perform uploads. Currently firebase and nextcloud cloud services are available to store images.
Cloud service, username, password is configured in the upload settings interface ( Settings -> Uploads -> Images ).
The upload button starts camera to capture an image. When image is captured and confirmed, image is uploaded to cloud service. If something goes wrong or image is uploaded successfully, user is informed with a message accordingly. This feature requires camera permission.

### Silent utter messages

This feature is added to mute messages like links, long product IDs and so. This feature is related to *chat with rasa* skill because messages are tagged with its type "write" or "speak". This feature hasn't been tested yet due to *chat with rasa* skill side implementation doesn't to seem to function well.

### Client token

Client token feature modifies the existing implementation of Mycroft connection. Normally IP was needed to create connection to Mycroft instance. Now the app accepts client token instead of IP for convenience (Settings -> General -> Assistant client token) .

### Clickable links

Messages are received from assistant is shown in message cards as regular texts. This feature allows recognition of links in the messages. Links are now clickable and highlighted with a different color with this feature.

## To Install

Import the repo into Android Studio, or your IDE of choice.
Build and deploy to a device

Once the app is running on a device, you will need to set the assistant client token to "biba" (without quotes) in the Settings -> General Options menu. That will then create a websocket connection to your Mycroft. Push the button and talk to Mycroft.
