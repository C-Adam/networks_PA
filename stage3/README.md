# Stage 3: random chunk download

The Java server divides each file into at least 100 chunks and sends them in a
random order. Every chunk includes its index and byte size. The client puts
the chunks back in index order before writing the original file. Only the JDK
is required.

Put a file with at least 100 bytes in the server's `shared/` folder, compile,
then run:

```sh
javac Server.java Client.java
java Server 5003 shared 100
```

The server arguments are the TCP port (`5003`), the folder containing files
(`shared`), and the number of chunks to create (`100`). The final argument is
a chunk count, not a file size: `100` means the file is split into 100 pieces.
It must be at least 100, and the selected file must contain at least that many
bytes.

From another computer on the same network:

```sh
java Client 192.168.1.20 5003 stage3_test.txt downloads
```

The client arguments are the server IP address, server port, filename inside
the server's shared folder, and local folder for the reassembled download.

This uses the included `stage3_test.txt` fixture. Replace the example IP if
the programs are on different computers. The server listens on all interfaces.
Allow Java through the firewall for TCP port 5003. The exchange follows the
assignment's `rdy`, `fName`, chunk count, `rdyD`, chunk index/size/data, `next`,
and `close` steps.
