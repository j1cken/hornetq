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

import junit.framework.Assert;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.ServiceTestBase;

/**
 * A RedeliveryConsumerTest
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created Feb 17, 2009 6:06:11 PM
 *
 *
 */
public class RedeliveryConsumerTest extends ServiceTestBase
{

   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(RedeliveryConsumerTest.class);

   // Attributes ----------------------------------------------------

   HornetQServer server;

   final SimpleString ADDRESS = new SimpleString("address");

   ClientSessionFactory factory;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testRedeliveryMessageStrict() throws Exception
   {
      testDedeliveryMessageOnPersistent(true);
   }

   public void testRedeliveryMessageSimpleCancel() throws Exception
   {
      testDedeliveryMessageOnPersistent(false);
   }

   public void testDeliveryNonPersistent() throws Exception
   {
      testDelivery(false);
   }

   public void testDeliveryPersistent() throws Exception
   {
      testDelivery(true);
   }

   public void testDelivery(final boolean persistent) throws Exception
   {
      setUp(true);
      ClientSession session = factory.createSession(false, false, false);
      ClientProducer prod = session.createProducer(ADDRESS);

      for (int i = 0; i < 10; i++)
      {
         prod.send(createTextMessage(session, Integer.toString(i), persistent));
      }

      session.commit();
      session.close();

      session = factory.createSession(null, null, false, true, true, true, 0);

      session.start();
      for (int loopAck = 0; loopAck < 5; loopAck++)
      {
         ClientConsumer browser = session.createConsumer(ADDRESS, null, true);
         for (int i = 0; i < 10; i++)
         {
            ClientMessage msg = browser.receive(1000);
            Assert.assertNotNull("element i=" + i + " loopAck = " + loopAck + " was expected", msg);
            msg.acknowledge();
            Assert.assertEquals(Integer.toString(i), getTextMessage(msg));

            // We don't change the deliveryCounter on Browser, so this should be always 0
            Assert.assertEquals(0, msg.getDeliveryCount());
         }

         session.commit();
         browser.close();
      }

      session.close();

      session = factory.createSession(false, false, false);
      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      for (int loopAck = 0; loopAck < 5; loopAck++)
      {
         for (int i = 0; i < 10; i++)
         {
            ClientMessage msg = consumer.receive(1000);
            Assert.assertNotNull(msg);
            Assert.assertEquals(Integer.toString(i), getTextMessage(msg));

            // No ACK done, so deliveryCount should be always = 1
            Assert.assertEquals(1, msg.getDeliveryCount());
         }
         session.rollback();
      }

      if (persistent)
      {
         session.close();
         server.stop();
         server.start();
         session = factory.createSession(false, false, false);
         session.start();
         consumer = session.createConsumer(ADDRESS);
      }

      for (int loopAck = 1; loopAck <= 5; loopAck++)
      {
         for (int i = 0; i < 10; i++)
         {
            ClientMessage msg = consumer.receive(1000);
            Assert.assertNotNull(msg);
            msg.acknowledge();
            Assert.assertEquals(Integer.toString(i), getTextMessage(msg));
            Assert.assertEquals(loopAck, msg.getDeliveryCount());
         }
         if (loopAck < 5)
         {
            if (persistent)
            {
               session.close();
               server.stop();
               server.start();
               session = factory.createSession(false, false, false);
               session.start();
               consumer = session.createConsumer(ADDRESS);
            }
            else
            {
               session.rollback();
            }
         }
      }

      session.close();
   }

   protected void testDedeliveryMessageOnPersistent(final boolean strictUpdate) throws Exception
   {
      setUp(strictUpdate);
      ClientSession session = factory.createSession(false, false, false);

      RedeliveryConsumerTest.log.info("created");

      ClientProducer prod = session.createProducer(ADDRESS);
      prod.send(createTextMessage(session, "Hello"));
      session.commit();
      session.close();

      session = factory.createSession(false, false, false);
      session.start();
      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage msg = consumer.receive(1000);
      Assert.assertEquals(1, msg.getDeliveryCount());
      session.stop();

      // if strictUpdate == true, this will simulate a crash, where the server is stopped without closing/rolling back
      // the session
      if (!strictUpdate)
      {
         // If non Strict, at least rollback/cancel should still update the delivery-counts
         session.rollback(true);

         session.close();
      }

      server.stop();

      // once the server is stopped, we close the session
      // to clean up its client resources
      session.close();

      server.start();

      factory = createInVMFactory();

      session = factory.createSession(false, true, false);
      session.start();
      consumer = session.createConsumer(ADDRESS);
      msg = consumer.receive(1000);
      Assert.assertNotNull(msg);
      Assert.assertEquals(2, msg.getDeliveryCount());
      session.close();
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   /**
    * @param persistDeliveryCountBeforeDelivery
    * @throws Exception
    * @throws HornetQException
    */
   private void setUp(final boolean persistDeliveryCountBeforeDelivery) throws Exception, HornetQException
   {
      Configuration config = createDefaultConfig();
      config.setPersistDeliveryCountBeforeDelivery(persistDeliveryCountBeforeDelivery);

      server = createServer(true, config);

      server.start();

      factory = createInVMFactory();

      ClientSession session = factory.createSession(false, false, false);
      session.createQueue(ADDRESS, ADDRESS, true);

      session.close();
   }

   @Override
   protected void tearDown() throws Exception
   {

      if (factory != null)
      {
         factory.close();
      }

      if (server != null && server.isStarted())
      {
         server.stop();
      }

      factory = null;

      server = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
