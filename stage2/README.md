# Stage 2: download a file

The Java server shares files from `shared/`. The client saves received files
in `downloads/`. File data is sent as bytes, so binary files are supported.
Only the JDK is required.

On the server computer, put a file in `shared/`, compile, then start:

```sh
javac Server.java Client.java
java Server 5002 shared
```

The server arguments are the TCP port (`5002`) and the folder to share
(`shared`).

On another computer on the same network, run:

```sh
java Client 192.168.1.20 5002 stage2_test.txt downloads
```

The client arguments are the server IP address, server port, filename inside
the server's shared folder, and the local folder where the download is saved.
Here, the client requests the included `stage2_test.txt` fixture and saves it
in `downloads/`.

Replace the example IP if the programs are on different computers. The server
listens on all interfaces.
Allow Java through the firewall for TCP port 5002. The conversation follows
the assignment's `rdy`, `fName`, `fsize`, `rdyD`, file bytes, and `close` steps.
