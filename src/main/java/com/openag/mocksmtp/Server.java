package com.openag.mocksmtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Main class responsible for opening server socket and handling incoming requests
 *
 * @author Andrei Maus
 */
public class Server {
  private static final Logger LOG = LoggerFactory.getLogger(Server.class);

  private static final int DEFAULT_PORT = 2525;
  private static final String DEFAULT_HOST = "localhost";

  private String host = DEFAULT_HOST;

  private int port = DEFAULT_PORT;

  /**
   * Main server socket thread that will wait for connections and submit client connections to shared thread pool
   */
  private Thread serverSocketThread;

  /**
   * Server socket instance
   */
  private ServerSocket serverSocket;

  /**
   * Handles every child connection in separate thread
   */
  private final ExecutorService childConnectionsService = Executors.newCachedThreadPool(new DaemonThreadFactory());

  /**
   * Storage for incoming mail messages
   */
  private final SimpleMailStore mailStore = new SimpleMailStore();

  /**
   * Starts the SMTP server (binds the SMTP server socket, prepares necessary threads, ...). This method MUST be called
   * before any SMTP operation. This method can be called again on the same instance after {@link #stop()} method is
   * called
   *
   * @throws IOException
   */
  public void start() throws IOException {
    mailStore.clear();

    final InetSocketAddress address = new InetSocketAddress(host, port);

    serverSocket = new ServerSocket();
    serverSocket.bind(address);

    LOG.debug("SMTP server socket bound to {}", address);

    serverSocketThread = new Thread(new ServerSocketLoop(), "smtp-server-socket-loop");
    serverSocketThread.start();

  }

  /**
   * Kills the SMTP server and releases resources
   */
  public void stop() throws IOException {
    LOG.debug("Closing the server socket...");
    serverSocket.close();

    serverSocketThread.interrupt();
  }

  /**
   * Simple runnable that will accept incoming server socket connections and delegate them to separate thread. The
   * runnable will quit on socket exception
   */
  private class ServerSocketLoop implements Runnable {
    public void run() {
      LOG.debug("Started server socket connection loop");

      while (true) {
        try {
          final Socket socket = serverSocket.accept();

          LOG.debug("Accepted connection from {}", socket.getRemoteSocketAddress());

          childConnectionsService.submit(new Connection(socket, mailStore));

        } catch (IOException e) {
          LOG.debug("Exception in server socket, expected if socket was closed", e);
          return;
        }
      }
    }
  }

  /**
   * Extension to default {@link ThreadFactory}, will produce daemon threads
   */
  private static class DaemonThreadFactory implements ThreadFactory {
    private final ThreadFactory factory = Executors.defaultThreadFactory();

    public Thread newThread(final Runnable runnable) {
      final Thread thread = factory.newThread(runnable);
      thread.setDaemon(true); //make child threads daemon
      return thread;
    }
  }

  /**
   * @param host the host name (or IP) that will be used to bind server socket to, defaults to "localhost"
   */
  public void setHost(String host) {
    this.host = host;
  }

  /**
   * @param port the porn number to bind server socket to, defaults to "2525"
   */
  public void setPort(int port) {
    this.port = port;
  }

  public MailStore getMailStore() {
    return mailStore;
  }
}