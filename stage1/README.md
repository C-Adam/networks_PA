# Stage 1: uppercase text

This Java program sends one line of lower-case text to a server. The server
returns it in uppercase. It uses only classes included with the JDK.

Compile and start the server on one computer:

```sh
javac Server.java Client.java
java Server 5001
```

`java Server 5001` starts the server on TCP port `5001`. The server stays
running and waits for clients.

Find the server computer's LAN IP address. On macOS, `ipconfig getifaddr en0`
usually shows the Wi-Fi address. On Windows, use `ipconfig`; on Linux, use
`hostname -I`. Run the client on another computer on the same network:

```sh
java Client 192.168.1.20 5001 hello from another computer
```

The client arguments are the server computer's IP address, the server's port,
and the text to send. In this example, `hello from another computer` is sent
as one line.

Replace the example IP with the server computer's address. The server listens
on all network interfaces. Allow Java through the firewall for the selected
TCP port. Each client sends one line and then disconnects.
