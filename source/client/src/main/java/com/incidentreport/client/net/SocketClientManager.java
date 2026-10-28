package com.incidentreport.client.net;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SocketClientManager {

    public interface PacketListener {
        void onPacketReceived(Packet packet);
        void onDisconnected();
    }

    private static SocketClientManager instance;

    public static synchronized SocketClientManager getInstance() {
        if (instance == null) {
            instance = new SocketClientManager();
        }
        return instance;
    }

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;

    private final BlockingQueue<Packet> outgoingQueue = new LinkedBlockingQueue<>();
    private final List<PacketListener> listeners = new ArrayList<>();
    private Thread readThread;
    private Thread writeThread;

    private SocketClientManager() {}

    public synchronized void addListener(PacketListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(PacketListener listener) {
        listeners.remove(listener);
    }

    public synchronized void clearListeners() {
        listeners.clear();
    }

    public synchronized boolean connect(String host, int port) {
        if (connected) return true;
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            connected = true;

            // Luồng ghi dữ liệu ra Socket
            writeThread = new Thread(() -> {
                while (connected) {
                    try {
                        Packet p = outgoingQueue.take();
                        p.writeTo(out);
                    } catch (InterruptedException e) {
                        break;
                    } catch (IOException e) {
                        disconnect();
                        break;
                    }
                }
            }, "TCP-Client-Sender");
            writeThread.setDaemon(true);
            writeThread.start();

            // Luồng đọc dữ liệu từ Socket
            readThread = new Thread(() -> {
                try {
                    while (connected) {
                        Packet p = Packet.readFrom(in);
                        // Đẩy lên JavaFX Application Thread an toàn
                        Platform.runLater(() -> {
                            synchronized (listeners) {
                                for (PacketListener l : new ArrayList<>(listeners)) {
                                    l.onPacketReceived(p);
                                }
                            }
                        });
                    }
                } catch (EOFException ignored) {
                } catch (Exception e) {
                    if (connected) {
                        System.err.println("Mất kết nối tới Server: " + e.getMessage());
                    }
                } finally {
                    disconnect();
                }
            }, "TCP-Client-Reader");
            readThread.setDaemon(true);
            readThread.start();

            return true;
        } catch (Exception e) {
            System.err.println("Không thể kết nối Server: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public void sendPacket(Packet packet) {
        if (connected && packet != null) {
            outgoingQueue.offer(packet);
        }
    }

    public synchronized void disconnect() {
        if (!connected) return;
        connected = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        if (writeThread != null) writeThread.interrupt();
        if (readThread != null) readThread.interrupt();

        Platform.runLater(() -> {
            synchronized (listeners) {
                for (PacketListener l : new ArrayList<>(listeners)) {
                    l.onDisconnected();
                }
            }
        });
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}

