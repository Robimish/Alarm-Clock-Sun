# Alarm Clock Sun

Alarm clock for Android that wakes you up gently: in the minutes before the alarm,
the screen lights up little by little, like a sunrise, then the alarm rings.

**Alarm Clock Sun is a fork of [NFC Alarm Clock](https://github.com/gabeg805/NFC-Alarm-Clock)
by Gabriel Gonzalez**, version 12.7.2-beta025. All the features of NFC Alarm Clock are
still there (NFC tag to dismiss, own music, gradual volume, text-to-speech, reminders,
statistics…). Modified by Robimish from September 2026.

## What the fork adds

* **Sunrise**: the screen goes from black to a colour or an image of your choice,
  from 5 to 60 minutes before the alarm, with an analog or digital clock
* **Say the time**: shake the phone or wave a hand over it to hear the time,
  within the hours you choose
* **Timers** that come over the lock screen when they ring, and a ready-made one
* Volume keys and power button to snooze or dismiss, statistics that can be turned off
* The app is in **English, French and Spanish**
* **100 % offline**: no internet permission, no cloud backup, nothing is sent anywhere

The detail of every version is in the app, under *What's new*.

## Build

Open the project in Android Studio and build the `fossRelease` (or `fossDebug`)
variant. A release build is signed only if a `keystore.properties` file is present
at the root of the project (not in the repository).

## License

    NFC Alarm Clock
    Copyright (C) 2026  Gabriel Gonzalez

    Alarm Clock Sun, modifications
    Copyright (C) 2026  Robimish

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
