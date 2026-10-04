# Music Assistant Sendspin source

Vesper compiles the official Music Assistant local Sendspin player engine from the pinned
`music-assistant/mobile-app` Git submodule at:

`05858e80abc637e3bf3f20b94125305c325616f5`

Only the upstream `sendspin/src/commonMain` and `sendspin/src/androidMain` source sets are
compiled by `:ma-sendspin`. Vesper owns the Android audio sink, MediaSession/service,
settings and UI integration.

Upstream licence: Apache License 2.0 (the pinned submodule contains the upstream LICENSE file).

Do not advance the submodule casually. Treat an upstream bump like a dependency update:
review the Sendspin API/protocol changes and run Build, Test and Lint before accepting it.
