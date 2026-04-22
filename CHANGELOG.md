# TheHerobrine-OG Changelog

## 1.5.2
- made `MyWorlds` a required dependency for the Purpur 1.19.4 target
- fail startup cleanly when SQL or Redis cannot initialize
- validate lobby resources before registering new lobbies
- rebuild active lobbies when `/hbreloadconfigs` runs so refreshed lobby configs take effect immediately
- remove ProtocolLib lobby listeners during shutdown and close Redis pools on plugin disable
