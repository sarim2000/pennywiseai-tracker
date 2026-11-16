# Bundled webfonts

These fonts are self-hosted rather than loaded from Google Fonts. PennyWise's whole
claim is that your data stays on your device, and a third-party font request would
hand every visitor's IP address, User-Agent and Referer to Google before the page even
finished rendering — on the marketing site for a privacy-first app. Self-hosting also
removes a render-blocking cross-origin round trip, which matters for the mobile
connections most of these pages are read on.

All three families are licensed under the SIL Open Font License 1.1, which permits
redistribution and bundling.

| File | Family | Upstream |
|---|---|---|
| `archivo-variable-latin.woff2` | Archivo (variable, wght 600–800, latin subset) | https://github.com/Omnibus-Type/Archivo |
| `ibm-plex-mono-400-latin.woff2` | IBM Plex Mono Regular (latin subset) | https://github.com/IBM/plex |
| `ibm-plex-mono-500-latin.woff2` | IBM Plex Mono Medium (latin subset) | https://github.com/IBM/plex |
| `ibm-plex-sans-arabic-400-arabic.woff2` | IBM Plex Sans Arabic Regular (arabic subset) | https://github.com/IBM/plex |
| `ibm-plex-sans-arabic-700-arabic.woff2` | IBM Plex Sans Arabic Bold (arabic subset) | https://github.com/IBM/plex |

Latin-subset builds, as served by the Google Fonts CDN. The Arabic faces (used by
shared pages in Arabic, `/s/{id}`) are the Arabic-subset builds from Fontsource
(`@fontsource/ibm-plex-sans-arabic` 5.2.8).

## SIL Open Font License 1.1

Copyright (c) Omnibus-Type (Archivo)
Copyright (c) IBM Corp. (IBM Plex Mono)
Copyright 2019 IBM Corp. (IBM Plex Sans Arabic)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
The full licence text is available at: https://scripts.sil.org/OFL

Permission is hereby granted, free of charge, to any person obtaining a copy of the
Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell
modified and unmodified copies of the Font Software, subject to the conditions of the
OFL: the fonts may not be sold by themselves, must retain this notice, and any Modified
Version must use a different Reserved Font Name.

THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
