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
package org.hornetq.tests.integration.jms.consumer;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.server.Messaging;
import org.hornetq.core.server.MessagingServer;
import org.hornetq.core.server.Queue;
import org.hornetq.jms.HornetQQueue;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.client.HornetQSession;
import org.hornetq.jms.server.impl.JMSServerManagerImpl;
import org.hornetq.tests.integration.jms.server.management.NullInitialContext;
import org.hornetq.tests.util.UnitTestCase;
import org.hornetq.utils.SimpleString;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class ConsumerTest extends UnitTestCase
{
   private MessagingServer server;

   private JMSServerManagerImpl jmsServer;

   private HornetQConnectionFactory cf;

   private static final String Q_NAME = "ConsumerTestQueue";

   private HornetQQueue jBossQueue;

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      Configuration conf = new ConfigurationImpl();
      conf.setSecurityEnabled(false);
      conf.setJMXManagementEnabled(true);
      conf.getAcceptorConfigurations()
          .add(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory"));
      server = Messaging.newMessagingServer(conf, false);
      jmsServer = new JMSServerManagerImpl(server);
      jmsServer.setContext(new NullInitialContext());
      jmsServer.start();
      jmsServer.createQueue(Q_NAME, Q_NAME, null, true);
      cf = new HornetQConnectionFactory(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));
      cf.setBlockOnPersistentSend(true);
      cf.setPreAcknowledge(true);
   }

   @Override
   protected void tearDown() throws Exception
   {
      jmsServer.stop();
      
      cf = null;
      if (server != null && server.isStarted())
      {
         try
         {
            server.stop();
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
         server = null;
      }

      server = null;
      jmsServer = null;
      cf = null;
      jBossQueue = null;

      super.tearDown();
   }

   public void testPreCommitAcks() throws Exception
   {
      Connection conn = cf.createConnection();
      Session session = conn.createSession(false, HornetQSession.PRE_ACKNOWLEDGE);
      jBossQueue = new HornetQQueue(Q_NAME);
      MessageProducer producer = session.createProducer(jBossQueue);
      MessageConsumer consumer = session.createConsumer(jBossQueue);
      int noOfMessages = 100;
      for (int i = 0; i < noOfMessages; i++)
      {
         producer.send(session.createTextMessage("m" + i));
      }

      conn.start();
      for (int i = 0; i < noOfMessages; i++)
      {
         Message m = consumer.receive(500);
         assertNotNull(m);
      }
      // assert that all the messages are there and none have been acked
      SimpleString queueName = new SimpleString(HornetQQueue.JMS_QUEUE_ADDRESS_PREFIX + Q_NAME);
      assertEquals(0, ((Queue)server.getPostOffice().getBinding(queueName).getBindable()).getDeliveringCount());
      assertEquals(0, ((Queue)server.getPostOffice().getBinding(queueName).getBindable()).getMessageCount());
      conn.close();
   }

   public void testPreCommitAcksWithMessageExpiry() throws Exception
   {
      Connection conn = cf.createConnection();
      Session session = conn.createSession(false, HornetQSession.PRE_ACKNOWLEDGE);
      jBossQueue = new HornetQQueue(Q_NAME);
      MessageProducer producer = session.createProducer(jBossQueue);
      MessageConsumer consumer = session.createConsumer(jBossQueue);
      int noOfMessages = 1000;
      for (int i = 0; i < noOfMessages; i++)
      {
         TextMessage textMessage = session.createTextMessage("m" + i);
         producer.setTimeToLive(1);
         producer.send(textMessage);
      }

      conn.start();
      for (int i = 0; i < noOfMessages; i++)
      {
         Message m = consumer.receive(500);
         assertNotNull(m);
      }
      // assert that all the messages are there and none have been acked
      SimpleString queueName = new SimpleString(HornetQQueue.JMS_QUEUE_ADDRESS_PREFIX + Q_NAME);
      assertEquals(0, ((Queue)server.getPostOffice().getBinding(queueName).getBindable()).getDeliveringCount());
      assertEquals(0, ((Queue)server.getPostOffice().getBinding(queueName).getBindable()).getMessageCount());
      conn.close();
   }

   public void testClearExceptionListener() throws Exception
   {
      Connection conn = cf.createConnection();
      Session session = conn.createSession(false, HornetQSession.PRE_ACKNOWLEDGE);
      jBossQueue = new HornetQQueue(Q_NAME);
      MessageConsumer consumer = session.createConsumer(jBossQueue);
      consumer.setMessageListener(new MessageListener()
      {
         public void onMessage(Message msg)
         {
         }
      });

      consumer.setMessageListener(null);
      consumer.receiveNoWait();
   }
   
   public void testCantReceiveWhenListenerIsSet() throws Exception
   {
      Connection conn = cf.createConnection();
      Session session = conn.createSession(false, HornetQSession.PRE_ACKNOWLEDGE);
      jBossQueue = new HornetQQueue(Q_NAME);
      MessageConsumer consumer = session.createConsumer(jBossQueue);
      consumer.setMessageListener(new MessageListener()
      {
         public void onMessage(Message msg)
         {
         }
      });

      try
      {
         consumer.receiveNoWait();
         fail("Should throw exception");
      }
      catch (JMSException e)
      {
         //Ok
      }
   }
}
