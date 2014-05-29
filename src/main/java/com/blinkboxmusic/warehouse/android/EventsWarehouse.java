package com.blinkboxmusic.warehouse.android;

import com.google.common.io.ByteStreams;
import com.we7.events.BBMUserEvent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Random;

import static com.blinkboxmusic.warehouse.android.EventsWarehouse.HttpURLConnectionFactory.DefaultHttpURLConnectionFactory;
import static com.blinkboxmusic.warehouse.android.EventsWarehouse.UuidGenerator.DefaultUuidGenerator;

public class EventsWarehouse {

  public static final String STAGING_ENDPOINT = "http://eventsapi.stagingb.blinkboxmusic.com/events/user";

  private long mUserId = -1;
  private String mSessionId;

  private final Authoriser mAuthoriser;
  private final EventCache mEventCache;
  private final HttpURLConnectionFactory mConnectionFactory;
  private final UuidGenerator mUuidGenerator;
  private final SystemMonitor mSystemMonitor;

  public EventsWarehouse(Authoriser authoriser, EventCache eventCache) {
    this(authoriser, eventCache, new DefaultHttpURLConnectionFactory(), new DefaultUuidGenerator(), new SystemMonitor.DefaultSystemMonitor());
  }

  /** For tests */
  EventsWarehouse(Authoriser authoriser, EventCache eventCache, HttpURLConnectionFactory connectionFactory, UuidGenerator uuidGenerator, SystemMonitor systemMonitor) {
    mAuthoriser = authoriser;
    mEventCache = eventCache;
    mConnectionFactory = connectionFactory;
    mUuidGenerator = uuidGenerator;
    mSystemMonitor = systemMonitor;
  }

  /**
   * Sets the user id to attach to all subsequently sent events.
   */
  public void setUserId(long userId) {
    mUserId = userId;
  }

  /**
   * Sets the session id to attach to all subsequently sent events.
   */
  public void setSessionId(String sessionId) {
    mSessionId = sessionId;
  }

  /**
   * Send a user event
   * <p>
   * If the event has no userId set upon it, then this method will append a
   * userId from the last call to {@link #setUserId(long)}, or -1 if no call has been made.
   * <p>
   * If the event has no UUID set upon it, then this method will append a random UUID
   * <p>
   * If the event has no sessionId set upon it, then this method will append a
   * sessionId from the last call to {@link #setSessionId(String)}, or else an UninitializedMessageException
   * will be thrown.
   * <p>
   * If the event has no client timestamp set upon it, then this method will append the current system time.
   * @param message
   */
  public void send(BBMUserEvent message) {

    if (!message.hasUserId()) {
      message = message.toBuilder().setUserId(mUserId).build();
    }

    if (!message.hasUuid()) {
      message = message.toBuilder().setUuid(mUuidGenerator.createUuid()).build();
    }

    if (!message.hasSessionId() && mSessionId != null) {
      message = message.toBuilder().setSessionId(mSessionId).build();
    }

    if (!message.hasClientTimestamp()) {
      message = message.toBuilder().setClientTimestamp(mSystemMonitor.currentTimeMillis()).build();
    }

    // Now, re-build in case this is a partial message to ensure it is initialised correctly.
    message.toBuilder().build();

    try {
      HttpURLConnection connection = mConnectionFactory.createConnection(new URL(STAGING_ENDPOINT));

      connection.setRequestMethod("POST");

      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "application/protobuf");
      connection.setRequestProperty("Authorization", mAuthoriser.getAuthorisation());

      connection.setDoOutput(true);

      OutputStream wr = connection.getOutputStream();
      wr.write(message.toByteArray());
      wr.flush();
      wr.close();
    } catch (IOException e) {
      // TODO Do something meaningful.
    }
  }


  private class Server implements Runnable {

    public void run() {
      try {
        ServerSocket serverSock = new ServerSocket(62666);
        for (;;)
        {
          Socket sock = serverSock.accept();
          InputStream in = new BufferedInputStream(sock.getInputStream()));
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          ByteStreams.copy(in, out);
          in.close();
          mEventCache.put(out.toByteArray());
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public interface HttpURLConnectionFactory {
    HttpURLConnection createConnection(URL url) throws IOException;

    public class DefaultHttpURLConnectionFactory implements HttpURLConnectionFactory {

      @Override
      public HttpURLConnection createConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
      }
    }
  }

  public interface Authoriser {
    String getAuthorisation();
  }


  public interface UuidGenerator {
    int createUuid();

    public class DefaultUuidGenerator implements UuidGenerator {
      private static final Random RANDOM = new Random();

      @Override
      public int createUuid() {
        return RANDOM.nextInt();
      }
    }
  }

  public interface SystemMonitor {
    long currentTimeMillis();

    public class DefaultSystemMonitor implements SystemMonitor {
      @Override
      public long currentTimeMillis() {
        return System.currentTimeMillis();
      }
    }
  }

  public interface EventCache extends Iterable<byte[]> {
    public void put(byte[] bytes);
  }

}
