[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![CLA](https://img.shields.io/badge/CLA%3F-No-lightgrey.svg)](https://mycroft.ai/cla) ![Team](https://img.shields.io/badge/Team-Community-violet.svg) ![Status](https://img.shields.io/badge/-Experimental-orange.svg)

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com)
[![Join chat](https://img.shields.io/badge/Mattermost-join_chat-brightgreen.svg)](https://chat.mycroft.ai/community/channels/android)

# Adapted Mycroft Android App for Manufacturing Assistants

This is an adapted version of Mycroft's Android companion app. We extended the base version during
the Horizon 2020 Research and Innovation Action "COALA" (Grant no. 957296). You can visit our
website to learn more about COALA www.coala-h2020.eu.

This App opens a secured websocket connection to Jarbas' HiveMind
service (https://github.com/JarbasHiveMind/HiveMind-core). HiveMind handles the communication with
the Mycroft core service.

The following list contains an outline of our changes:

- Updated the code base (parts of the original code were from 2017)
- Added toggle for user's and assistant's audio output
- Added QR code reading to send hard-to-spell information to Mycroft (e.g. serial numbers)
- Added three conversation modes (walkie-talkie, converse, wake-word)
- Added clickable links
- Added a custom COALA theme
- Added KeyCloak authentication to ensure that only authorized users access the App and Mycrofts
  message bus.

## New Features

The following sections contain an outline of the new features and the ideas behind them.

### Authentication via KeyCloak

In corporate environments, only some users may use an assistant. Also, some cases require a user
profile with information about the users experience (e.g., novice or expert) and role (e.g.,
supervisor) with elevated access rights. This App requires a KeyCloak service and connection data (
set variables in Config.kt).

### Conversation modes

There are three buttons to start conversations with the assistant. This is helpful to give users
full control over the bot's listening behavior - a critical aspect for privacy and trustworthiness.

Walkie-Talkie. The bot listens to the user for some seconds and responds to the transcribed
utterance. Converse. The bot listens to the user for some seconds and responds to the transcribed
utterance. After the response the bot listens again for some seconds and responds to utterances.
This patter repeats until the user remains silent or clicks somewhere. Wake word. Clicking the
button activates the wake word mode. A small icon indicates the mode is active (bot listens and
waits for wake word). When active, the bot listens for some seconds when the user speaks the
keyword (e.g., "Hey Coala"). This feature requires getting an access key for Porcupine (set
ACCESS_KEY in MainActivity.kt)!

### Rich media responses

Responses can contain more than text (rich responses). This App supports in-dialog buttons,
clickable links, images, and tables.

### Toggle mute (for assistant and user utterances)

We realized that repeating user utterances loud might annoy or confuse users. Likewise, some users
may need to turn off spoken assistant responses, e.g. if you don't want others to hear what the
assistant says to you. Therefore, users should be able to silence the repetition of user utterances
and the assistant's responses.

### QR code scanning

Manufacturing information may be hard to communicate by voice, e.g., a product's serialnumber. The
STT services we tried had trouble transcribing this information and introduced errors that prohibit
effective conversations about products (and other technical systems). Therefore, users should be
able to scan QR codes with their Android device to communicate hard-to-transcribe information. We
added a new button in the interface, to start the scanning process. When the user places the QR code
into the camera's frame, the App recognizes the code automatically. The App sends the decrypted text
to Mycroft where skills can further process it.

### Image upload (WIP)

Sometimes, users must document events or situations with images. A common example is creating an
issue report, where the users attaches photos to document the issue. Users are able to take images
and upload them in a cloud storage (e.g., Nextcloud). This feature is work in progress.

### Session generation

This App generates a session ID to identify individual conversations. It passes this ID to HiveMind.

### SSML support

Some Text-to-Speech solutions support SSML. Responses that contain SSML tags can substantially
improve the user experience, for instance, because the bot can emphasise words or avoid reading
links.

## Next steps

- Complete code base update
- Complete image upload feature
- Improve rich responses
- Improve configuration
- Implement chat history
- Implement notifications (proactive responses)
- Improve documentation

## Contributors to Version 3

This is the list of known contributors to version 3 developed in the Horizon 2020 Research and
Innovation action COALA.
<https://www.coala-h2020.eu/>
<https://cordis.europa.eu/project/id/957296>

### BIBA - Bremer Insitut für Produktion und Logistik GmbH

<https://www.biba.uni-bremen.de>

- Huzaifa Mehdi
- Stefan Wellsandt
- Elizaveta Kotova
- Abdulvahap Calikoglu

### Ubitech

<https://ubitech.eu>

- Georgia Chatzimarkaki
- Petros Petrou
