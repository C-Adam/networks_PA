# Testing the networking assignment

The stages are separate programs. Run commands from the matching stage folder because Stages 1–3 reuse the Java class names `Server` and `Client`.

## Included test files

| Stage | File | Size | Purpose |
|---|---|---:|---|
| 2 | `stage2/shared/stage2_test.txt` | 4,096 bytes | Checks that the text file arrives intact. |
| 3 | `stage3/shared/stage3_test.txt` | 262,267 bytes | Checks that 100 shuffled text chunks are reordered and reassembled. |
| 4 | `stage4/stage4_test_source.txt` | 2,560,317 bytes | Provides 250 full 10 KB chunks plus a final partial chunk for the five-peer transfer. |

Stage 1 sends text and does not need a test file.

## Local checks on one computer

Open a separate VS Code terminal for each server that must stay running. Run each client from another terminal. `127.0.0.1` is correct only when both programs are on the same computer.

### Stage 1: uppercase text

Terminal 1:

```sh
cd stage1
javac Server.java Client.java
java Server 5001
```

Terminal 2:

```sh
cd stage1
java Client 127.0.0.1 5001 hello network
```

Expected response: `HELLO NETWORK`.

### Stage 2: whole-file transfer

Terminal 1:

```sh
cd stage2
javac Server.java Client.java
java Server 5002 shared
```

Terminal 2:

```sh
cd stage2
java Client 127.0.0.1 5002 stage2_test.txt downloads
cmp shared/stage2_test.txt downloads/stage2_test.txt
```

If `cmp` prints nothing and returns to the prompt, the files match.

### Stage 3: randomized chunk transfer

Terminal 1:

```sh
cd stage3
javac Server.java Client.java
java Server 5003 shared 100
```

Terminal 2:

```sh
cd stage3
java Client 127.0.0.1 5003 stage3_test.txt downloads
cmp shared/stage3_test.txt downloads/stage3_test.txt
```

The client should report receiving 100 chunks and reassembling the file. A silent `cmp` means the downloaded file matches.

### Stage 4: tracker and five-peer ring

Compile and start the tracker in Terminal 1:

```sh
cd stage4
javac Protocol.java Tracker.java Peer.java
java Tracker ./stage4_test_source.txt 9000 5
```

Start each peer in its own terminal. These commands use loopback addresses because all six processes are on the same computer:

```sh
cd stage4
java Peer 1 127.0.0.1 9000 9101 127.0.0.1 9102
```

```sh
cd stage4
java Peer 2 127.0.0.1 9000 9102 127.0.0.1 9103
```

```sh
cd stage4
java Peer 3 127.0.0.1 9000 9103 127.0.0.1 9104
```

```sh
cd stage4
java Peer 4 127.0.0.1 9000 9104 127.0.0.1 9105
```

```sh
cd stage4
java Peer 5 127.0.0.1 9000 9105 127.0.0.1 9101
```

Wait until all peers report `finished`. Each peer writes a `summary.txt` showing 250 chunks. The peers running on one computer share the default `completed/` output folder, so they each write the same reconstructed filename there. Compare it with the source:

```sh
cmp stage4_test_source.txt completed/stage4_test_source.txt
```

A silent `cmp` means the rebuilt file matches the tracker’s source.

## Running with your group over a local network

Copy the `stage4` source files and `stage4_test_source.txt` to the tracker computer. Copy the `stage4` source files to every peer computer. The source file only needs to be on the tracker computer.

Use the tracker computer's local IP in every peer command. For each peer, replace the final IP with the local IP of the next peer in the ring:

`Peer 1 → Peer 2 → Peer 3 → Peer 4 → Peer 5 → Peer 1`

Run the tracker on the tracker computer as above. On each peer computer, run the matching command below from `stage4`, replacing the uppercase IP names:

```sh
java Peer 1 TRACKER_IP 9000 9101 PEER2_IP 9102
```

```sh
java Peer 2 TRACKER_IP 9000 9102 PEER3_IP 9103
```

```sh
java Peer 3 TRACKER_IP 9000 9103 PEER4_IP 9104
```

```sh
java Peer 4 TRACKER_IP 9000 9104 PEER5_IP 9105
```

```sh
java Peer 5 TRACKER_IP 9000 9105 PEER1_IP 9101
```

On macOS, find the Wi-Fi IP with `ipconfig getifaddr en0`. On Windows, find the IPv4 address with `ipconfig`. Do not use `127.0.0.1` between different computers. Keep the tracker and all five peer processes running until transfers finish. Allow TCP port 9000 on the tracker and 9101–9105 on the peers through the computer firewalls; the network must permit devices to connect directly to one another.

Each peer should finish with 250 chunk IDs in its summary and a reconstructed file under `completed/`. For a remote comparison, calculate `shasum -a 256 stage4_test_source.txt` on the tracker and `shasum -a 256 completed/stage4_test_source.txt` on each peer; the hashes should match.
