# vertx-bittorrent

BitTorrent Client with DHT support written in Java using Eclipse Vert.x.

## Features

- [x] Leeching
- [x] Seeding
- [x] Single-file torrents
- [x] Multi-file torrents
- [x] Optimistic Unchoking
- [x] Endgame
- [x] DHT
- [ ] UDP Tracker
- [ ] Magnet Links

## Implemented Specs

- [x] [BEP 0003 (BitTorrent Protocol)](https://www.bittorrent.org/beps/bep_0003.html)
- [x] [BEP 0005 (Distributed Hash Table)](https://www.bittorrent.org/beps/bep_0005.html)
- [x] [BEP 0007 (IPv6 Tracker Extension)](https://www.bittorrent.org/beps/bep_0007.html)

## Run

The application can be started using the `run` script with a path to torrent file as an argument:

```bash
./run <torrent_file>
```

The script will build the application using `gradle installDist`.
To trigger a rebuild with the script, `gradle clean` needs to be run to delete the build files.

Alternatively the gradle run task can be used:

```bash
gradle run --args="<torrent_file>"
```

## Build

To build a distributable application:

```bash
./gradlew clean build
```

This will create a zip file at `build/distributions/vertx-bittorrent-*.zip`.
The archive includes the jar files and start scripts for Windows and Unix systems.

## Why Vert.x?

Vert.x works in a reactive way using an event loop.
Therefore multiple sockets can be managed with the same single thread in contrast to a concurrent model where each connection is handled by a new thread.
The advantage is that no synchronization needs to happen between threads since there is only one thread.
This makes it easy to work with data from multiple connections without running into race conditions.

And BitTorrent requires to connect to many other peers and creating a new thread for each peer would also be expensive (this applies to OS threads, not virtual threads).
