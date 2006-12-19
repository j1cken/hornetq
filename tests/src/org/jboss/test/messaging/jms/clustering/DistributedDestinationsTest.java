/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.messaging.jms.clustering;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.test.messaging.jms.clustering.base.ClusteringTestBase;

/**
 * 
 * A DistributedDestinationsTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public class DistributedDestinationsTest extends ClusteringTestBase
{

   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public DistributedDestinationsTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void testClusteredQueueLocalConsumerNonPersistent() throws Exception
   {
      clusteredQueueLocalConsumer(false);
   }

   public void testClusteredQueueLocalConsumerPersistent() throws Exception
   {
      clusteredQueueLocalConsumer(true);
   }

   public void testClusteredTopicNonDurableNonPersistent() throws Exception
   {
      clusteredTopicNonDurable(false);
   }

   public void testClusteredTopicNonDurablePersistent() throws Exception
   {
      clusteredTopicNonDurable(true);
   }

   public void testClusteredTopicNonDurableWithSelectorsNonPersistent() throws Exception
   {
      clusteredTopicNonDurableWithSelectors(false);
   }

   public void testClusteredTopicNonDurableWithSelectorsPersistent() throws Exception
   {
      clusteredTopicNonDurableWithSelectors(true);
   }

   public void testClusteredTopicDurableNonPersistent() throws Exception
   {
      clusteredTopicDurable(false);
   }

   public void testClusteredTopicDurablePersistent() throws Exception
   {
      clusteredTopicDurable(true);
   }

   public void testClusteredTopicSharedDurableLocalConsumerNonPersistent() throws Exception
   {
      clusteredTopicSharedDurableLocalConsumer(false);
   }

   public void testClusteredTopicSharedDurableLocalConsumerPersistent() throws Exception
   {
      clusteredTopicSharedDurableLocalConsumer(true);
   }

   public void testClusteredTopicSharedDurableNoLocalSubNonPersistent() throws Exception
   {
      clusteredTopicSharedDurableNoLocalSub(false);
   }

   public void testClusteredTopicSharedDurableNoLocalSubPersistent() throws Exception
   {
      clusteredTopicSharedDurableNoLocalSub(true);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected void setUp() throws Exception
   {
      nodeCount = 3;

      super.setUp();

      log.debug("setup done");
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   /*
    * Create a consumer on each queue on each node.
    * Send messages in turn from all nodes.
    * Ensure that the local consumer gets the message
    */
   protected void clusteredQueueLocalConsumer(boolean persistent) throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;

      try
      {
         //This will create 3 different connection on 3 different nodes, since
         //the cf is clustered
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         conn3 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn1, conn2, conn3});

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         log.info("Created sessions");

         MessageConsumer cons1 = sess1.createConsumer(queue[0]);
         MessageConsumer cons2 = sess2.createConsumer(queue[1]);
         MessageConsumer cons3 = sess3.createConsumer(queue[2]);
         
         log.info("Created consumers");

         conn1.start();
         conn2.start();
         conn3.start();

         // Send at node 0

         MessageProducer prod = sess1.createProducer(queue[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage("message" + i);

            prod.send(tm);
         }
         
         log.info("Sent messages");

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons1.receive(1000);

            assertNotNull(tm);
            
            assertEquals("message" + i, tm.getText());
         }                 

         Message m = cons2.receive(2000);

         assertNull(m);

         m = cons3.receive(2000);

         assertNull(m);

         // Send at node 1

         MessageProducer prod1 = sess2.createProducer(queue[1]);

         prod1.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess2.createTextMessage("message" + i);

            prod1.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons2.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         m = cons1.receive(2000);

         assertNull(m);

         m = cons3.receive(2000);

         assertNull(m);

         // Send at node 2

         MessageProducer prod2 = sess3.createProducer(queue[2]);

         prod2.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess3.createTextMessage("message" + i);

            prod2.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons3.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         m = cons1.receive(2000);

         assertNull(m);

         m = cons2.receive(2000);

         assertNull(m);
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }

         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   // Private -------------------------------------------------------

   /*
    * Create non durable subscriptions on all nodes of the cluster.
    * Ensure all messages are receive as appropriate
    */
   private void clusteredTopicNonDurable(boolean persistent) throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      try
      {
         //This will create 3 different connection on 3 different nodes, since
         //the cf is clustered
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         conn3 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn1, conn2, conn3});

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(topic[0]);
         MessageConsumer cons2 = sess2.createConsumer(topic[1]);
         MessageConsumer cons3 = sess3.createConsumer(topic[2]);

         MessageConsumer cons4 = sess1.createConsumer(topic[0]);

         MessageConsumer cons5 = sess2.createConsumer(topic[1]);

         conn1.start();
         conn2.start();
         conn3.start();

         // Send at node 0

         MessageProducer prod = sess1.createProducer(topic[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons1.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons2.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons3.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons4.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons5.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }

         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   /*
    * Create non durable subscriptions on all nodes of the cluster.
    * Include some with selectors
    * Ensure all messages are receive as appropriate
    */
   private void clusteredTopicNonDurableWithSelectors(boolean persistent) throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;

      try
      {
         //This will create 3 different connection on 3 different nodes, since
         //the cf is clustered
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         conn3 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn1, conn2, conn3});

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(topic[0]);
         MessageConsumer cons2 = sess2.createConsumer(topic[1]);
         MessageConsumer cons3 = sess3.createConsumer(topic[2]);

         MessageConsumer cons4 = sess1.createConsumer(topic[0], "COLOUR='red'");

         MessageConsumer cons5 = sess2.createConsumer(topic[1], "COLOUR='blue'");

         conn1.start();
         conn2.start();
         conn3.start();

         // Send at node 0

         MessageProducer prod = sess1.createProducer(topic[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage("message" + i);

            int c = i % 3;
            if (c == 0)
            {
               tm.setStringProperty("COLOUR", "red");
            }
            else if (c == 1)
            {
               tm.setStringProperty("COLOUR", "blue");
            }

            prod.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons1.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons2.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons3.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            int c = i % 3;

            if (c == 0)
            {
               TextMessage tm = (TextMessage)cons4.receive(1000);

               assertNotNull(tm);

               assertEquals("message" + i, tm.getText());
            }
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            int c = i % 3;

            if (c == 1)
            {
               TextMessage tm = (TextMessage)cons5.receive(1000);

               assertNotNull(tm);

               assertEquals("message" + i, tm.getText());
            }
         }
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }

         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   /**
    * Create durable subscriptions on all nodes of the cluster. Include a couple with selectors.
    * Ensure all messages are receive as appropriate. None of the durable subs are shared.
    */
   private void clusteredTopicDurable(boolean persistent) throws Exception
   {
      Connection conn0 = null;
      Connection conn1 = null;
      Connection conn2 = null;

      try
      {
         // This will create 3 different connection on 3 different nodes, since the cf is clustered
         conn0 = cf.createConnection();
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn0, conn1, conn2});

         conn0.setClientID("wib1");
         conn1.setClientID("wib1");
         conn2.setClientID("wib1");

         Session sess0 = conn0.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try
         {
            sess0.unsubscribe("alpha");
         }
         catch (Exception ignore) {}
         try
         {
            sess1.unsubscribe("beta");
         }
         catch (Exception ignore) {}
         try
         {
            sess2.unsubscribe("gamma");
         }
         catch (Exception ignore) {}
         try
         {
            sess0.unsubscribe("delta");
         }
         catch (Exception ignore) {}
         try
         {
            sess1.unsubscribe("epsilon");
         }
         catch (Exception ignore) {}

         MessageConsumer alpha = sess0.createDurableSubscriber(topic[0], "alpha");
         MessageConsumer beta = sess1.createDurableSubscriber(topic[1], "beta");
         MessageConsumer gamma = sess2.createDurableSubscriber(topic[2], "gamma");
         MessageConsumer delta = sess0.createDurableSubscriber(topic[0], "delta");
         MessageConsumer epsilon = sess1.createDurableSubscriber(topic[1], "epsilon");

         conn0.start();
         conn1.start();
         conn2.start();

         // Send at node 0

         MessageProducer prod = sess0.createProducer(topic[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 1;

         log.info("sending messages");

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            prod.send(sess0.createTextMessage("message" + i));
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)alpha.receive(1000);
            assertNotNull(tm);
            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)beta.receive(1000);
            assertNotNull(tm);
            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)gamma.receive(1000);
            assertNotNull(tm);
            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)delta.receive(1000);
            assertNotNull(tm);
            assertEquals("message" + i, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)epsilon.receive(1000);
            assertNotNull(tm);
            assertEquals("message" + i, tm.getText());
         }

         alpha.close();
         beta.close();
         gamma.close();
         delta.close();
         epsilon.close();

         sess0.unsubscribe("alpha");
         sess1.unsubscribe("beta");
         sess2.unsubscribe("gamma");
         sess0.unsubscribe("delta");
         sess1.unsubscribe("epsilon");

      }
      finally
      {
         if (conn0 != null)
         {
            conn0.close();
         }

         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }
      }
   }

   /*
    * Create shared durable subs on multiple nodes, the local instance should always get the message
    */
   private void clusteredTopicSharedDurableLocalConsumer(boolean persistent) throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;
      try

      {
         //This will create 3 different connection on 3 different nodes, since
         //the cf is clustered
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         conn3 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn1, conn2, conn3});
         conn1.setClientID("wib1");
         conn2.setClientID("wib1");
         conn3.setClientID("wib1");

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try
         {
            sess1.unsubscribe("sub");
         }
         catch (Exception ignore) {}
         try
         {
            sess2.unsubscribe("sub");
         }
         catch (Exception ignore) {}
         try
         {
            sess3.unsubscribe("sub");
         }
         catch (Exception ignore) {}

         MessageConsumer cons1 = sess1.createDurableSubscriber(topic[0], "sub");
         MessageConsumer cons2 = sess2.createDurableSubscriber(topic[1], "sub");
         MessageConsumer cons3 = sess3.createDurableSubscriber(topic[2], "sub");

         conn1.start();
         conn2.start();
         conn3.start();

         // Send at node 0

         MessageProducer prod = sess1.createProducer(topic[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons1.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         Message m = cons2.receive(2000);

         assertNull(m);

         m = cons3.receive(2000);

         assertNull(m);

         // Send at node 1

         MessageProducer prod1 = sess2.createProducer(topic[1]);

         prod1.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess3.createTextMessage("message" + i);

            prod1.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons2.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         m = cons1.receive(2000);

         assertNull(m);

         m = cons3.receive(2000);

         assertNull(m);

         // Send at node 2

         MessageProducer prod2 = sess3.createProducer(topic[2]);

         prod2.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess3.createTextMessage("message" + i);

            prod2.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = (TextMessage)cons3.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i, tm.getText());
         }

         m = cons1.receive(2000);

         assertNull(m);

         m = cons2.receive(2000);

         assertNull(m);

         cons1.close();
         cons2.close();
         cons3.close();

         // Need to unsubscribe on any node that the durable sub was created on

         sess1.unsubscribe("sub");
         sess2.unsubscribe("sub");
         sess3.unsubscribe("sub");
      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }

         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }



   /*
    * Create shared durable subs on multiple nodes, but without sub on local node
    * should round robin
    * note that this test assumes round robin
    */
   private void clusteredTopicSharedDurableNoLocalSub(boolean persistent) throws Exception
   {
      Connection conn1 = null;
      Connection conn2 = null;
      Connection conn3 = null;

      try
      {
         //This will create 3 different connection on 3 different nodes, since
         //the cf is clustered
         conn1 = cf.createConnection();
         conn2 = cf.createConnection();
         conn3 = cf.createConnection();
         
         log.info("Created connections");
         
         checkConnectionsDifferentServers(new Connection[] {conn1, conn2, conn3});
         
         conn2.setClientID("wib1");
         conn3.setClientID("wib1");

         Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session sess3 = conn3.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try
         {
            sess2.unsubscribe("sub");
         }
         catch (Exception ignore) {}
         try
         {
            sess3.unsubscribe("sub");
         }
         catch (Exception ignore) {}

         MessageConsumer cons1 = sess2.createDurableSubscriber(topic[1], "sub");
         MessageConsumer cons2 = sess3.createDurableSubscriber(topic[2], "sub");

         conn2.start();
         conn3.start();

         // Send at node 0

         //Should round robin between the other 2 since there is no active consumer on sub  on node 0

         MessageProducer prod = sess1.createProducer(topic[0]);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage("message" + i);

            prod.send(tm);
         }

         for (int i = 0; i < NUM_MESSAGES / 2; i++)
         {
            TextMessage tm = (TextMessage)cons1.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + i * 2, tm.getText());
         }

         for (int i = 0; i < NUM_MESSAGES / 2; i++)
         {
            TextMessage tm = (TextMessage)cons2.receive(1000);

            assertNotNull(tm);

            assertEquals("message" + (i * 2 + 1), tm.getText());
         }

         cons1.close();
         cons2.close();

         sess2.unsubscribe("sub");
         sess3.unsubscribe("sub");

      }
      finally
      {
         if (conn1 != null)
         {
            conn1.close();
         }

         if (conn2 != null)
         {
            conn2.close();
         }

         if (conn3 != null)
         {
            conn3.close();
         }
      }
   }

   class MyListener implements MessageListener
   {
      private int i;

      MyListener(int i)
      {
         this.i = i;
      }

      public void onMessage(Message m)
      {
         try
         {
            int count = m.getIntProperty("count");

            log.info("Listener " + i + " received message " + count);
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }

   }


   // Inner classes -------------------------------------------------

   

}
