package io.github.nwalker.vnc.server.simple;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple VNC server that streams a {@link BufferedImage} to connected clients.
 *
 * <p>Any number of VNC viewers can connect simultaneously. Each sees the same
 * shared framebuffer. When a viewer requests a framebuffer update the server
 * encodes the requested region of the current image as a RAW rectangle and
 * sends it immediately.</p>
 *
 * <h2>Pixel format</h2>
 * <p>All updates are sent in a fixed 32&nbsp;bpp little-endian RGB format
 * ({@link SimpleRfbServer#PIXEL_FORMAT}). {@code SetPixelFormat} messages from
 * clients are accepted but ignored; the server always sends in its native format.
 * Similarly, only RAW encoding is used regardless of any {@code SetEncodings}
 * preferences the client expresses.</p>
 *
 * <h2>Security</h2>
 * <p>No authentication is required. Only {@code SecurityType.None} is offered
 * during the handshake.</p>
 *
 * <h2>Image updates</h2>
 * <p>The server operates on a pull model: clients periodically send
 * {@code FramebufferUpdateRequest} messages and the server responds with the
 * current image. Call {@link #setImage(BufferedImage)} at any time to swap in a
 * new framebuffer; the next request from each client will receive the updated
 * pixels. The new image must have the same dimensions as the original; throw
 * {@link IllegalArgumentException} if it does not.</p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * BufferedImage canvas = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
 * // ... draw something onto canvas ...
 *
 * SimpleVncServer server = new SimpleVncServer(5900, canvas);
 * server.start();
 *
 * // Later, update the framebuffer:
 * BufferedImage next = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
 * // ... draw something new onto next ...
 * server.setImage(next);
 *
 * // Shut down cleanly:
 * server.stop();
 * }</pre>
 */
public final class SimpleVncServer {

    /** Default VNC port. */
    public static final int DEFAULT_PORT = 5900;

    private final int port;
    private final String desktopName;
    private final AtomicReference<BufferedImage> image;
    private final int initialWidth;
    private final int initialHeight;
    private final AtomicInteger clientCount = new AtomicInteger();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connectionExecutor;

    /**
     * Creates a server that listens on the given port and serves the given image.
     *
     * @param port        the TCP port to listen on (e.g. {@link #DEFAULT_PORT})
     * @param initialImage the initial framebuffer contents; must not be {@code null}
     * @param desktopName the desktop name shown to connecting viewers
     */
    public SimpleVncServer(int port, BufferedImage initialImage, String desktopName) {
        if (initialImage == null) throw new IllegalArgumentException("initialImage must not be null");
        if (desktopName == null)  throw new IllegalArgumentException("desktopName must not be null");
        this.port          = port;
        this.desktopName   = desktopName;
        this.image         = new AtomicReference<>(initialImage);
        this.initialWidth  = initialImage.getWidth();
        this.initialHeight = initialImage.getHeight();
    }

    /**
     * Creates a server on {@link #DEFAULT_PORT} with desktop name {@code "SimpleVncServer"}.
     *
     * @param initialImage the initial framebuffer contents
     */
    public SimpleVncServer(BufferedImage initialImage) {
        this(DEFAULT_PORT, initialImage, "SimpleVncServer");
    }

    /**
     * Creates a server on the given port with desktop name {@code "SimpleVncServer"}.
     *
     * @param port         the TCP port to listen on
     * @param initialImage the initial framebuffer contents
     */
    public SimpleVncServer(int port, BufferedImage initialImage) {
        this(port, initialImage, "SimpleVncServer");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Binds the server socket and begins accepting client connections.
     *
     * <p>This method returns immediately; client connections are handled on
     * background threads from an internal thread pool.</p>
     *
     * @throws IOException      if the server socket cannot be bound
     * @throws IllegalStateException if the server is already running
     */
    public synchronized void start() throws IOException {
        if (running) throw new IllegalStateException("Server is already running");

        serverSocket       = new ServerSocket(port);
        connectionExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "vnc-client-" + clientCount.get());
            t.setDaemon(true);
            return t;
        });
        running = true;

        acceptThread = new Thread(this::acceptLoop, "vnc-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Stops accepting new connections, closes the server socket, and waits up to
     * 5 seconds for active client sessions to finish.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;

        try {
            serverSocket.close();
        } catch (IOException ignored) {}

        connectionExecutor.shutdown();
        try {
            if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                connectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Framebuffer
    // -------------------------------------------------------------------------

    /**
     * Replaces the current framebuffer image.
     *
     * <p>The new image must have the same width and height as the image supplied
     * at construction time. Connected clients will receive the updated pixels on
     * their next {@code FramebufferUpdateRequest}.</p>
     *
     * @param newImage the new framebuffer image
     * @throws IllegalArgumentException if the image dimensions differ from the original
     */
    public void setImage(BufferedImage newImage) {
        if (newImage == null) throw new IllegalArgumentException("newImage must not be null");
        if (newImage.getWidth()  != initialWidth ||
            newImage.getHeight() != initialHeight) {
            throw new IllegalArgumentException(String.format(
                    "Image dimensions %dx%d do not match original %dx%d",
                    newImage.getWidth(), newImage.getHeight(),
                    initialWidth, initialHeight));
        }
        image.set(newImage);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the TCP port this server listens on. */
    public int getPort() { return port; }

    /** Returns the desktop name advertised to connecting clients. */
    public String getDesktopName() { return desktopName; }

    /** Returns the width of the framebuffer in pixels. */
    public int getWidth() { return initialWidth; }

    /** Returns the height of the framebuffer in pixels. */
    public int getHeight() { return initialHeight; }

    /** Returns the number of currently connected clients. */
    public int getClientCount() { return clientCount.get(); }

    /** Returns {@code true} if the server has been started and not yet stopped. */
    public boolean isRunning() { return running; }

    // -------------------------------------------------------------------------
    // Package-private accessors used by SimpleRfbServer
    // -------------------------------------------------------------------------

    /** Returns the current framebuffer image. */
    BufferedImage getImage() { return image.get(); }

    /** Called by {@link SimpleRfbServer} when its session ends. */
    void onClientDisconnected(SimpleRfbServer session, Exception cause) {
        clientCount.decrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Accept loop
    // -------------------------------------------------------------------------

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (SocketException e) {
                // Normal shutdown: ServerSocket.close() causes accept() to throw.
                break;
            } catch (IOException e) {
                if (running) {
                    // Transient error — log and continue accepting.
                    System.err.println("[SimpleVncServer] accept error: " + e.getMessage());
                }
                continue;
            }

            clientCount.incrementAndGet();
            final Socket accepted = socket;
            connectionExecutor.submit(() -> serveClient(accepted));
        }
    }

    private void serveClient(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            SimpleRfbServer session = new SimpleRfbServer(this);
            session.run(socket.getInputStream(), socket.getOutputStream());
        } catch (IOException e) {
            // Session ended with an I/O error; onDisconnected already notified the session.
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
