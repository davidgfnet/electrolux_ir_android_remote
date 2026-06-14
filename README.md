
Electrolux ChillFlex IR remote
==============================

This is mostly Claude generated code.

The IR protocol is quite simple but stateful (the remote holds a state which
is blasted to the device on every button press).

Reversing the protocol was also mostly Claude led. I used a logic analyzer and
hooked it to the remote LED to capture the raw signal which was later fed to
a Claude-generated script for decoding. A few known traces where enough to
reverse the protocol. My unit can't do heat, so that mode is not spec'ed here.


