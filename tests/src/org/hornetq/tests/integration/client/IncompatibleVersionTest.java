/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration.client;

import static org.hornetq.tests.util.RandomUtil.randomString;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.protocol.core.Channel;
import org.hornetq.core.protocol.core.CoreRemotingConnection;
import org.hornetq.core.protocol.core.Packet;
import org.hornetq.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.hornetq.core.protocol.core.impl.wireformat.CreateSessionResponseMessage;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.remoting.server.impl.RemotingServiceImpl;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.VersionLoader;

/**
 * A IncompatibleVersionTest
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 *
 *
 */
public class IncompatibleVersionTest extends ServiceTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private HornetQServer server;

   private CoreRemotingConnection connection;
   private ServerLocator locator;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      server = createServer(false, false);
      server.getConfiguration().setConnectionTTLOverride(500);
      server.start();

      locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(ServiceTestBase.INVM_CONNECTOR_FACTORY));
      ClientSessionFactory csf = locator.createSessionFactory();

      connection = csf.getConnection();
   }

   @Override
   protected void tearDown() throws Exception
   {
      connection.destroy();

      locator.close();

      server.stop();
   }

   public void testCompatibleClientVersion() throws Exception
   {
      doTestClientVersionCompatibility(true);
   }

   public void testIncompatibleClientVersion() throws Exception
   {
      doTestClientVersionCompatibility(false);
   }

   private void doTestClientVersionCompatibility(boolean compatible) throws Exception
   {
      Channel channel1 = connection.getChannel(1, -1);
      long sessionChannelID = connection.generateChannelID();
      int version = VersionLoader.getVersion().getIncrementingVersion();
      if (!compatible)
      {
         version = -1;
      }
      Packet request = new CreateSessionMessage(randomString(),
                                                sessionChannelID,
                                                version,
                                                null,
                                                null,
                                                HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                                                false,
                                                true,
                                                true,
                                                false,
                                                HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                                                null);

      if (compatible)
      {
         Packet packet = channel1.sendBlocking(request);
         assertNotNull(packet);
         assertTrue(packet instanceof CreateSessionResponseMessage);
         // 1 connection on the server
         assertEquals(1, server.getConnectionCount());
      }
      else
      {
         try
         {
            channel1.sendBlocking(request);
            fail();
         }
         catch (HornetQException e)
         {
            assertEquals(HornetQException.INCOMPATIBLE_CLIENT_SERVER_VERSIONS, e.getCode());
         }
         long start = System.currentTimeMillis();
         while (System.currentTimeMillis() < start + 3 * RemotingServiceImpl.CONNECTION_TTL_CHECK_INTERVAL)
         {
            if (server.getConnectionCount() == 0)
            {
               break;
            }
         }
         // no connection on the server
         assertEquals(0, server.getConnectionCount());
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
