package com.blinkboxmusic.warehouse.android
import com.google.protobuf.UninitializedMessageException
import com.we7.events.BBMUserEvent
import com.we7.events.UserEventInitiator
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static com.blinkboxmusic.warehouse.android.EventsWarehouse.*
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.equalTo
import static org.mockito.Matchers.argThat
import static org.mockito.Mockito.mock as mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class EventsWarehouseTest {

    @Test
    void usesHTTPPostMethod() {

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(new ByteArrayOutputStream())

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        warehouse.send createFullUserEventBuilder()

        verify(connection).setRequestMethod(argThat(equalTo("POST")))
    }

    @Test
    void sendsRequiredHTTPHeaders() {
        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(new ByteArrayOutputStream())

        def authoriser = mock(Authoriser.class)
        when(authoriser.authorisation).thenReturn('dummy authorisation')

        def warehouse = new EventsWarehouse(authoriser, new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        warehouse.send createFullUserEventBuilder()

        verify(connection).setRequestProperty(argThat(equalTo('Accept')), argThat(equalTo('application/json')))
        verify(connection).setRequestProperty(argThat(equalTo('Content-Type')), argThat(equalTo('application/protobuf')))
        verify(connection).setRequestProperty(argThat(equalTo('Authorization')), argThat(equalTo('dummy authorisation')))
    }

    @Test
    void sendsEvent() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        def event = createFullUserEventBuilder()

        warehouse.send event

        assert Arrays.equals(stream.toByteArray(), event.toByteArray())
    }

    @Test
    void appendsUserIdWhenNotAlreadySetInEvent() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        def event = createUserEventWithNoUserId()

        warehouse.setUserId 123
        warehouse.send event

        assert Arrays.equals(stream.toByteArray(), event.toBuilder().setUserId(123).build().toByteArray())
    }

    @Test
    void appendsUUIDWhenNotAlreadySetInEvent() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def uuidGenerator = mock(UuidGenerator.class)
        when(uuidGenerator.createUuid()).thenReturn(12345);

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, uuidGenerator, mock(SystemMonitor.class))

        def event = createUserEventWithNoUUID()

        warehouse.send event

        assert Arrays.equals(stream.toByteArray(), event.toBuilder().setUuid(12345).build().toByteArray())
    }

    @Test
    void appendsSessionIdWhenNotAlreadySetInEvent() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        warehouse.setSessionId 'session id'
        def event = createUserEventWithNoSessionId()

        warehouse.send event

        assert Arrays.equals(stream.toByteArray(), event.toBuilder().setSessionId("session id").build().toByteArray())
    }

    @Test
    void throwsUninitializedMessageExceptionIfNoSessionIdIsSet() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), mock(SystemMonitor.class))

        def event = createUserEventWithNoSessionId()

        expectedException.expect UninitializedMessageException.class
        expectedException.expectMessage containsString('session_id')
        warehouse.send event
    }


    @Test
    void appendsClientTimestampWhenNotAlreadySetInEvent() {
        def stream = new ByteArrayOutputStream()

        def connection = mock(HttpURLConnection.class)
        when(connection.outputStream).thenReturn(stream)

        def systemMonitor = mock(SystemMonitor.class)
        when(systemMonitor.currentTimeMillis()).thenReturn(999l);

        def warehouse = new EventsWarehouse(mock(Authoriser.class), new HttpURLConnectionFactory() {
            @Override
            HttpURLConnection createConnection(URL url) {
                return connection
            }
        }, mock(UuidGenerator.class), systemMonitor)

        def event = createUserEventWithNoClientTimestamp()

        warehouse.send event

        assert Arrays.equals(stream.toByteArray(), event.toBuilder().setClientTimestamp(999).build().toByteArray())
    }

    private static BBMUserEvent createFullUserEventBuilder() {
        createPartialUserEvent()
                .toBuilder()
                .setSessionId('foo')
                .setClientTimestamp(1234)
                .setUuid(2)
                .setUserId(1)
                .build()
    }

    private static BBMUserEvent createUserEventWithNoUserId() {
        createPartialUserEvent()
                .toBuilder()
                .setSessionId('foo')
                .setClientTimestamp(3)
                .setUuid(2)
                .buildPartial()
    }

    private static BBMUserEvent createUserEventWithNoUUID() {
        createPartialUserEvent()
                .toBuilder()
                .setSessionId('foo')
                .setClientTimestamp(3)
                .setUserId(1)
                .buildPartial()
    }

    private static BBMUserEvent createUserEventWithNoSessionId() {
        createPartialUserEvent()
                .toBuilder()
                .setClientTimestamp(3)
                .setUuid(2)
                .setUserId(1)
                .buildPartial()
    }


    private static BBMUserEvent createUserEventWithNoClientTimestamp() {
        createPartialUserEvent()
                .toBuilder()
                .setSessionId('foo')
                .setUuid(2)
                .setUserId(1)
                .buildPartial()
    }

    private static BBMUserEvent createPartialUserEvent() {
        BBMUserEvent.newBuilder()
                .setInitiator(UserEventInitiator.APP)
                .setEventGroup(BBMUserEvent.EventGroup.PLAYLIST)
                .setEventCategory('cat123')
                .setEventName('name123')
                .setEventData(BBMUserEvent.EventData.newBuilder().addAlbumId(101))
                .buildPartial()

    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
}
