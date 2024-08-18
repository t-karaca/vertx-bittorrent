# vertx-bittorrent

Simple BitTorrent Client written in Java using Eclipse Vert.x.

## Features

- [x] Leeching
- [x] Seeding
- [x] Single-file torrents
- [x] Multi-file torrents
- [x] Optimistic Unchoking
- [ ] Endgame
- [ ] UDP Tracker
- [ ] DHT
- [ ] Magnet Links

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

Vert.x works in reactive way using an event loop.
Therefore multiple sockets can be managed with the same single thread in contrast to a concurrent model where each connection is handled by a new thread.
The advantage is that no synchronization needs to happen between threads since there is only one thread.

And BitTorrent requires to connect to many other peers and creating a new thread for each peer would also be expensive (this applies to OS threads, not virtual threads).
