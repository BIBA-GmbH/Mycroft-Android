[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![CLA](https://img.shields.io/badge/CLA%3F-No-lightgrey.svg)](https://mycroft.ai/cla) ![Team](https://img.shields.io/badge/Team-Community-violet.svg) ![Status](https://img.shields.io/badge/-Experimental-orange.svg)

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com)
[![Join chat](https://img.shields.io/badge/Mattermost-join_chat-brightgreen.svg)](https://chat.mycroft.ai/community/channels/android)

# Adapted Mycroft Android App for Manufacturing Assistants

This is an adapted version of Mycroft's Android companion app. We extended the base version during the Horizon 2020 Research and Innovation Action "COALA" (Grant no. 957296). You can visit our website to learn more about COALA www.coala-h2020.eu.

This App opens a websocket connection to the Mycroft-core messagebus. The following list contains an outline of our changes:

- Updated the code base (parts of the original code were from 2017)
- Adde toggle for user's and assistant's audio output
- Added QR code reading to send hard-to-spell information to Mycroft (e.g. serial numbers)
- Added write-only message support (silent utters) for our custom "Talk to Rasa" skill.
- Added a client token instead of using the IP address of a Mycroft device/server
- Added clickable links
- Added a custom COALA theme
- Added Keycloak authentication to ensure that only authorized users access the App and Mycrofts message bus.

## New Features

The following sections contain an outline of the new features and the ideas behind them.

### Toggle mute (for assistant and user utterances)

We realized that repeating user utterances loud might annoy or confuse users. Likewise, some users may need to turn off spoken assistant responses, e.g. if you don't want others to hear what the assistant says to you. Therefore, users should be able to silence the repetition of user utterances and the assistan's responses.

### QR code scanning

Manufacturing information may be hard to communicate by voice, e.g., a product's serialnumber. The STT services we tried had trouble transcribing this information and introduced errors that prohibit effective conversations about products (and other technical systems). Therefore, users should be able to scan QR codes with their Android device to communicate hard-to-transcribe information.
We added a new button in the interface, to start the scanning process. When the user places the QR code into the camera's frame, the App recognizes the code automatically. The App sends the decrypted text to Mycroft where skills can further process it.

### Image upload (WIP)

Sometimes, users must document events or situations with images. A common example is creating an issue report, where the users attaches photos to document the issue. Users are able to take images and upload them in a cloud storage (e.g., Nextcloud). This feature is work in progress.

### Silent utter messages

Speaking cryptic information, such as URLs and product identifiers, is time-consuming and hard to understand. We added silent utterances to address this issue. Our custom "Talk to Rasa" skill can tag messages as "write" or "speak".
