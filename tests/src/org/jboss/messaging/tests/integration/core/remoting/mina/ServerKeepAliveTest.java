/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.tests.integration.core.remoting.mina;

import junit.framework.TestCase;
import org.jboss.messaging.core.client.RemotingSessionListener;
import org.jboss.messaging.core.config.impl.ConfigurationImpl;
import org.jboss.messaging.core.exception.MessagingException;
import org.jboss.messaging.core.remoting.Packet;
import org.jboss.messaging.core.remoting.PacketReturner;
import org.jboss.messaging.core.remoting.RemotingSession;
import org.jboss.messaging.core.remoting.impl.PacketDispatcherImpl;
import org.jboss.messaging.core.remoting.impl.RemotingServiceImpl;
import org.jboss.messaging.core.remoting.impl.mina.MinaConnector;
import org.jboss.messaging.core.remoting.impl.wireformat.EmptyPacket;
import static org.jboss.messaging.core.remoting.impl.wireformat.EmptyPacket.CREATECONNECTION;
import org.jboss.messaging.core.server.impl.ServerPacketHandlerSupport;
import org.jboss.messaging.tests.unit.core.remoting.impl.ConfigurationHelper;

import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @version <tt>$Revision$</tt>
 */
public class ServerKeepAliveTest extends TestCase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private RemotingServiceImpl service;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
   }

   @Override
   protected void tearDown() throws Exception
   {
      service.stop();
      service = null;
   }

   public void testKeepAliveWithServerNotResponding() throws Throwable
   {
      //set the server timeouts to be twice that of the server to force failure
      ConfigurationImpl config = ConfigurationHelper.newTCPConfiguration(
              "localhost", TestSupport.PORT);
      config.setKeepAliveInterval(TestSupport.KEEP_ALIVE_INTERVAL * 2);
      config.setKeepAliveTimeout(TestSupport.KEEP_ALIVE_TIMEOUT * 2);
      ConfigurationImpl clientConfig = ConfigurationHelper.newTCPConfiguration(
              "localhost", TestSupport.PORT);
      clientConfig.setKeepAliveInterval(TestSupport.KEEP_ALIVE_INTERVAL);
      clientConfig.setKeepAliveTimeout(TestSupport.KEEP_ALIVE_TIMEOUT);
      service = new RemotingServiceImpl(config);
      service.start();
      service.getDispatcher().register(new DummyServePacketHandler());
      MinaConnector connector = new MinaConnector(clientConfig.getLocation(), clientConfig.getConnectionParams(), new PacketDispatcherImpl(null));

      final AtomicLong sessionIDNotResponding = new AtomicLong(-1);
      final CountDownLatch latch = new CountDownLatch(1);

      RemotingSessionListener listener = new RemotingSessionListener()
      {
         public void sessionDestroyed(long sessionID, MessagingException me)
         {
            sessionIDNotResponding.set(sessionID);
            latch.countDown();
         }
      };
      connector.addSessionListener(listener);

      RemotingSession session = connector.connect();
      boolean firedKeepAliveNotification = latch.await(TestSupport.KEEP_ALIVE_INTERVAL
              + TestSupport.KEEP_ALIVE_TIMEOUT + 2000, MILLISECONDS);
      assertTrue(firedKeepAliveNotification);
      assertEquals(session.getID(), sessionIDNotResponding.longValue());

      connector.removeSessionListener(listener);
      connector.disconnect();
   }

   class DummyServePacketHandler extends ServerPacketHandlerSupport
   {
      public long getID()
      {
         //0 is reserved for this handler
         return 0;
      }

      public Packet doHandle(final Packet packet, final PacketReturner sender) throws Exception
      {
         Packet response = null;

         byte type = packet.getType();

         if (type == CREATECONNECTION)
         {
            /*CreateConnectionRequest request = (CreateConnectionRequest) packet;

            CreateConnectionResponse createConnectionResponse = server.createConnection(request.getUsername(), request.getPassword(),
                    request.getRemotingSessionID(),
                    sender.getRemoteAddress(),
                    request.getVersion(),
                    sender);
            response = createConnectionResponse;*/

         }
         else if (type == EmptyPacket.PING)
         {
            //do nothing
         }

         return response;
      }
   }
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------
   // Inner classes -------------------------------------------------
}