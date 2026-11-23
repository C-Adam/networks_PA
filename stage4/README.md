# Stage 4: tracker and five-peer ring

This stage uses `Tracker.java` and `Peer.java`. The tracker splits the source
file into separate 10 KB chunk files, requires at least 250 chunks, and waits
for five peers. It shuffles the chunk IDs and assigns nearly equal sets. Each
peer writes its chunks and `summary.txt`, then runs an upload server and a
download client on separate threads. Peers request updated chunk lists until
they can reassemble the complete file.

Only the JDK is required. Compile the programs:

```sh
javac Protocol.java Tracker.java Peer.java
```

## Start the tracker

Put the original file on the tracker computer. It must be at least 2,560,000
bytes (250 full chunks of 10 KB). The included `stage4_test_source.txt` is large
enough for a test. From the `stage4/` folder, run:

```sh
java Tracker ./stage4_test_source.txt 9000 5
```

The tracker arguments are the source file path, the TCP listening port
(`9000`), and the number of peers to wait for (`5`). The port and peer count
are optional; they default to `9000` and `5`.

The tracker listens on all network interfaces. It creates a `tracker_chunks/`
folder containing its chunk files. The original and reassembled file can be
plain text; the `.bin` extension is used only for the tracker's internal raw
chunk files.

## Start five peers, one at a time

Use these example addresses if all peers run on one computer. For different
computers, replace each `127.0.0.1` with the LAN address of the named machine.
Start one command in each terminal, in order. Peers wait at the tracker until
all five have connected, then the tracker begins distribution.

```sh
java Peer 1 127.0.0.1 9000 9101 127.0.0.1 9102
java Peer 2 127.0.0.1 9000 9102 127.0.0.1 9103
java Peer 3 127.0.0.1 9000 9103 127.0.0.1 9104
java Peer 4 127.0.0.1 9000 9104 127.0.0.1 9105
java Peer 5 127.0.0.1 9000 9105 127.0.0.1 9101
```

The peer arguments are:

1. Peer ID, unique from `1` to `5`.
2. Tracker computer's IP address.
3. Tracker's TCP port, which must match the port used to start the tracker.
4. This peer's upload port, where it listens for its upload neighbor.
5. Download neighbor's IP address, usually the next peer in the ring.
6. Download neighbor's upload port.
7. Optional local folder for this peer's chunks and `summary.txt` (defaults to
   `peer_<id>`).
8. Optional local folder for the reassembled file (defaults to `completed`).

For peers on different computers, replace the example `127.0.0.1` addresses
with the machines' LAN IP addresses. Each peer's upload port belongs to the
computer running it. Open TCP port 9000 on the tracker and each peer upload
port in the firewall. The computers must be able to reach each other directly
on the local network.

Each peer creates `peer_<id>/chunks/` and `peer_<id>/summary.txt`. Once it has
every chunk it writes the assembled source file under `completed/`. After a
peer receives its upload neighbor's `done` message, it exits. Compare the
completed file with the source to verify the transfer.
