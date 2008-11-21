/*
 * JBoss, Home of Professional Open Source Copyright 2005-2008, Red Hat
 * Middleware LLC, and individual contributors by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */

package org.jboss.messaging.tests.integration.cluster.distribution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.messaging.core.client.ClientConsumer;
import org.jboss.messaging.core.client.ClientMessage;
import org.jboss.messaging.core.client.ClientProducer;
import org.jboss.messaging.core.client.ClientSession;
import org.jboss.messaging.core.client.ClientSessionFactory;
import org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl;
import org.jboss.messaging.core.config.TransportConfiguration;
import org.jboss.messaging.core.config.cluster.MessageFlowConfiguration;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.impl.invm.InVMRegistry;
import org.jboss.messaging.core.server.MessagingService;
import org.jboss.messaging.util.SimpleString;

/**
 * 
 * A ActivationTimeoutTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 4 Nov 2008 16:54:50
 *
 *
 */
public class BasicMessageFlowTest extends MessageFlowTestBase
{
   private static final Logger log = Logger.getLogger(BasicMessageFlowTest.class);

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testStaticListOutflow() throws Exception
   {     
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();
      
      Map<String, Object> service2Params = new HashMap<String, Object>();      
      MessagingService service2 = createMessagingService(2, service2Params);      
      service2.start();
      
      Map<String, Object> service3Params = new HashMap<String, Object>();      
      MessagingService service3 = createMessagingService(3, service3Params);      
      service3.start();
      
      Map<String, Object> service4Params = new HashMap<String, Object>();      
      MessagingService service4 = createMessagingService(4, service4Params);      
      service4.start();

      List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();
      
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors.add(server1tc);
      
      TransportConfiguration server2tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service2Params);
      connectors.add(server2tc);
      
      TransportConfiguration server3tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service3Params);
      connectors.add(server3tc);
      
      TransportConfiguration server4tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service4Params);
      connectors.add(server4tc);

      final SimpleString testAddress = new SimpleString("testaddress");

      MessageFlowConfiguration ofconfig = new MessageFlowConfiguration("outflow1",
                                                                       testAddress.toString(),
                                                                       null,
                                                                       true,
                                                                       1,
                                                                       -1,
                                                                       null,
                                                                       connectors);
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig);
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);

      service0.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);

      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      ClientSessionFactory csf2 = new ClientSessionFactoryImpl(server2tc);
      ClientSession session2 = csf2.createSession(false, true, true);
      
      ClientSessionFactory csf3 = new ClientSessionFactoryImpl(server3tc);
      ClientSession session3 = csf3.createSession(false, true, true);
      
      ClientSessionFactory csf4 = new ClientSessionFactoryImpl(server4tc);
      ClientSession session4 = csf4.createSession(false, true, true);

      session0.createQueue(testAddress, testAddress, null, false, false, true);
      session1.createQueue(testAddress, testAddress, null, false, false, true);
      session2.createQueue(testAddress, testAddress, null, false, false, true);
      session3.createQueue(testAddress, testAddress, null, false, false, true);
      session4.createQueue(testAddress, testAddress, null, false, false, true);

      ClientProducer prod0 = session0.createProducer(testAddress);

      ClientConsumer cons0 = session0.createConsumer(testAddress);
      ClientConsumer cons1 = session1.createConsumer(testAddress);
      ClientConsumer cons2 = session2.createConsumer(testAddress);
      ClientConsumer cons3 = session3.createConsumer(testAddress);
      ClientConsumer cons4 = session4.createConsumer(testAddress);      

      session0.start();

      session1.start();
      session2.start();
      session3.start();
      session4.start();

      final int numMessages = 100;

      final SimpleString propKey = new SimpleString("testkey");

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session0.createClientMessage(false);
         message.putIntProperty(propKey, i);
         message.getBody().flip();

         prod0.send(message);
      }

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage rmessage0 = cons0.receive(1000);
         assertNotNull(rmessage0);
         assertEquals(i, rmessage0.getProperty(propKey));

         ClientMessage rmessage1 = cons1.receive(1000);
         assertNotNull(rmessage1);
         assertEquals(i, rmessage1.getProperty(propKey));
         
         ClientMessage rmessage2 = cons2.receive(1000);
         assertNotNull(rmessage2);
         assertEquals(i, rmessage2.getProperty(propKey));
         
         ClientMessage rmessage3 = cons3.receive(1000);
         assertNotNull(rmessage3);
         assertEquals(i, rmessage3.getProperty(propKey));
         
         ClientMessage rmessage4 = cons4.receive(1000);
         assertNotNull(rmessage4);
         assertEquals(i, rmessage4.getProperty(propKey));
      }
      
      session0.close();
      session1.close();
      session2.close();
      session3.close();
      session4.close();
      
      service0.stop();      
      service1.stop();
      service2.stop();
      service3.stop();
      service4.stop();
      
      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service2.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service3.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service4.getServer().getRemotingService().getConnections().size());
   }

   public void testStaticListRoundRobin() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();
      
      Map<String, Object> service2Params = new HashMap<String, Object>();      
      MessagingService service2 = createMessagingService(2, service2Params);      
      service2.start();
      
      Map<String, Object> service3Params = new HashMap<String, Object>();      
      MessagingService service3 = createMessagingService(3, service3Params);      
      service3.start();
      
      Map<String, Object> service4Params = new HashMap<String, Object>();      
      MessagingService service4 = createMessagingService(4, service4Params);      
      service4.start();

      List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();
      
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors.add(server1tc);
      
      TransportConfiguration server2tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service2Params);
      connectors.add(server2tc);
      
      TransportConfiguration server3tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service3Params);
      connectors.add(server3tc);
      
      TransportConfiguration server4tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service4Params);
      connectors.add(server4tc);

      final SimpleString testAddress = new SimpleString("testaddress");

      MessageFlowConfiguration ofconfig = new MessageFlowConfiguration("outflow1",
                                                                       testAddress.toString(),
                                                                       null,
                                                                       false,
                                                                       1,
                                                                       -1,
                                                                       null,
                                                                       connectors);
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig);
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);

      service0.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);

      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      ClientSessionFactory csf2 = new ClientSessionFactoryImpl(server2tc);
      ClientSession session2 = csf2.createSession(false, true, true);
      
      ClientSessionFactory csf3 = new ClientSessionFactoryImpl(server3tc);
      ClientSession session3 = csf3.createSession(false, true, true);
      
      ClientSessionFactory csf4 = new ClientSessionFactoryImpl(server4tc);
      ClientSession session4 = csf4.createSession(false, true, true);

      session0.createQueue(testAddress, testAddress, null, false, false, false);
      session1.createQueue(testAddress, testAddress, null, false, false, false);
      session2.createQueue(testAddress, testAddress, null, false, false, false);
      session3.createQueue(testAddress, testAddress, null, false, false, false);
      session4.createQueue(testAddress, testAddress, null, false, false, false);

      ClientProducer prod0 = session0.createProducer(testAddress);

      ClientConsumer cons0 = session0.createConsumer(testAddress);
      ClientConsumer cons1 = session1.createConsumer(testAddress);
      ClientConsumer cons2 = session2.createConsumer(testAddress);
      ClientConsumer cons3 = session3.createConsumer(testAddress);
      ClientConsumer cons4 = session4.createConsumer(testAddress);      

      session0.start();

      session1.start();
      session2.start();
      session3.start();
      session4.start();

      final int numMessages = 10;

      final SimpleString propKey = new SimpleString("testkey");

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session0.createClientMessage(false);
         message.putIntProperty(propKey, i);
         message.getBody().flip();

         prod0.send(message);
      }
      
      //Refs should be round-robin'd in the same order the connectors are specified in the outflow
      //With the local consumer being last since it was created last
      
      ArrayList<ClientConsumer> consumers = new ArrayList<ClientConsumer>();
           
      consumers.add(cons1);
      consumers.add(cons2);
      consumers.add(cons3);
      consumers.add(cons4);
      consumers.add(cons0);
      
      int count = 0;
      for (int i = 0; i < numMessages; i++)
      {
         ClientConsumer consumer = consumers.get(count);
         
         count++;
         if (count == consumers.size())
         {
            count = 0;
         }
         
         ClientMessage msg = consumer.receive(1000);
         
         assertNotNull(msg);
         
         assertEquals(i, msg.getProperty(propKey));
         
         msg.acknowledge();
      }
      
      session0.close();
      session1.close();
      session2.close();
      session3.close();
      session4.close();

      service0.stop();      
      service1.stop();
      service2.stop();
      service3.stop();
      service4.stop();
      
      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service2.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service3.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service4.getServer().getRemotingService().getConnections().size());
   }
   
   public void testMultipleFlows() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();
      
      Map<String, Object> service2Params = new HashMap<String, Object>();      
      MessagingService service2 = createMessagingService(2, service2Params);      
      service2.start();
      
      Map<String, Object> service3Params = new HashMap<String, Object>();      
      MessagingService service3 = createMessagingService(3, service3Params);      
      service3.start();
      
      Map<String, Object> service4Params = new HashMap<String, Object>();      
      MessagingService service4 = createMessagingService(4, service4Params);      
      service4.start();

      List<TransportConfiguration> connectors1 = new ArrayList<TransportConfiguration>();      
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors1.add(server1tc);
      
      List<TransportConfiguration> connectors2 = new ArrayList<TransportConfiguration>();
      TransportConfiguration server2tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service2Params);
      connectors2.add(server2tc);
      
      List<TransportConfiguration> connectors3 = new ArrayList<TransportConfiguration>();
      TransportConfiguration server3tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service3Params);
      connectors3.add(server3tc);
      
      List<TransportConfiguration> connectors4 = new ArrayList<TransportConfiguration>();
      TransportConfiguration server4tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service4Params);
      connectors4.add(server4tc);

      final SimpleString testAddress = new SimpleString("testaddress");

      MessageFlowConfiguration ofconfig1 = new MessageFlowConfiguration("flow1",
                                                                       testAddress.toString(),
                                                                       "beatle='john'",
                                                                       false,
                                                                       1,
                                                                       -1,
                                                                       null,
                                                                       connectors1);
      MessageFlowConfiguration ofconfig2 = new MessageFlowConfiguration("flow2",
                                                                        testAddress.toString(),
                                                                        "beatle='paul'",
                                                                        false,
                                                                        1,
                                                                        -1,
                                                                        null,
                                                                        connectors2);
      MessageFlowConfiguration ofconfig3 = new MessageFlowConfiguration("flow3",
                                                                        testAddress.toString(),
                                                                        "beatle='george'",
                                                                        false,
                                                                        1,
                                                                        -1,
                                                                        null,
                                                                        connectors3);
      MessageFlowConfiguration ofconfig4 = new MessageFlowConfiguration("flow4",
                                                                        testAddress.toString(),
                                                                        "beatle='ringo'",
                                                                        false,
                                                                        1,
                                                                        -1,
                                                                        null,
                                                                        connectors4);
                 
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig1);
      ofconfigs.add(ofconfig2);
      ofconfigs.add(ofconfig3);
      ofconfigs.add(ofconfig4);
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);

      service0.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);

      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      ClientSessionFactory csf2 = new ClientSessionFactoryImpl(server2tc);
      ClientSession session2 = csf2.createSession(false, true, true);
      
      ClientSessionFactory csf3 = new ClientSessionFactoryImpl(server3tc);
      ClientSession session3 = csf3.createSession(false, true, true);
      
      ClientSessionFactory csf4 = new ClientSessionFactoryImpl(server4tc);
      ClientSession session4 = csf4.createSession(false, true, true);

      session0.createQueue(testAddress, testAddress, null, false, false, false);
      session1.createQueue(testAddress, testAddress, null, false, false, false);
      session2.createQueue(testAddress, testAddress, null, false, false, false);
      session3.createQueue(testAddress, testAddress, null, false, false, false);
      session4.createQueue(testAddress, testAddress, null, false, false, false);

      ClientProducer prod0 = session0.createProducer(testAddress);

      ClientConsumer cons1 = session1.createConsumer(testAddress);
      ClientConsumer cons2 = session2.createConsumer(testAddress);
      ClientConsumer cons3 = session3.createConsumer(testAddress);
      ClientConsumer cons4 = session4.createConsumer(testAddress);      

      session1.start();
      session2.start();
      session3.start();
      session4.start();
      
      SimpleString propKey = new SimpleString("beatle");
      
      ClientMessage messageJohn = session0.createClientMessage(false);
      messageJohn.putStringProperty(propKey, new SimpleString("john"));
      messageJohn.getBody().flip();
      
      ClientMessage messagePaul = session0.createClientMessage(false);
      messagePaul.putStringProperty(propKey, new SimpleString("paul"));
      messagePaul.getBody().flip();
      
      ClientMessage messageGeorge = session0.createClientMessage(false);
      messageGeorge.putStringProperty(propKey, new SimpleString("george"));
      messageGeorge.getBody().flip();
      
      ClientMessage messageRingo = session0.createClientMessage(false);
      messageRingo.putStringProperty(propKey, new SimpleString("ringo"));
      messageRingo.getBody().flip();
      
      ClientMessage messageOsama = session0.createClientMessage(false);
      messageOsama.putStringProperty(propKey, new SimpleString("osama"));
      messageOsama.getBody().flip();

      prod0.send(messageJohn);
      prod0.send(messagePaul);
      prod0.send(messageGeorge);
      prod0.send(messageRingo);
      prod0.send(messageOsama); 
      
      ClientMessage r1 = cons1.receive(1000);
      assertNotNull(r1);
      assertEquals(new SimpleString("john"), r1.getProperty(propKey));
      r1 = cons1.receiveImmediate();
      assertNull(r1);
      
      ClientMessage r2 = cons2.receive(1000);
      assertNotNull(r2);
      assertEquals(new SimpleString("paul"), r2.getProperty(propKey));
      r2 = cons2.receiveImmediate();
      assertNull(r2);
      
      ClientMessage r3 = cons3.receive(1000);
      assertNotNull(r3);
      assertEquals(new SimpleString("george"), r3.getProperty(propKey));
      r3 = cons3.receiveImmediate();
      assertNull(r3);
      
      ClientMessage r4 = cons4.receive(1000);
      assertNotNull(r4);
      assertEquals(new SimpleString("ringo"), r4.getProperty(propKey));
      r4 = cons4.receiveImmediate();
      assertNull(r4);
            
      session0.close();
      session1.close();
      session2.close();
      session3.close();
      session4.close();
      
      service0.stop();      
      service1.stop();
      service2.stop();
      service3.stop();
      service4.stop();
      
      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service2.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service3.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service4.getServer().getRemotingService().getConnections().size());
   }
   
   public void testMessageFlowsSameName() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);
      
      List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors.add(server1tc);
      
      final SimpleString address1 = new SimpleString("testaddress");
                 
      MessageFlowConfiguration ofconfig1 = new MessageFlowConfiguration("flow1", address1.toString(), "car='saab'", true, 1, -1, null, connectors);
      MessageFlowConfiguration ofconfig2 = new MessageFlowConfiguration("flow1", address1.toString(),"car='bmw'", true, 1, -1, null, connectors);
                  
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig1);
      ofconfigs.add(ofconfig2);
      
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);
   
      //Only one of the flows should be deployed
      service0.start();
      
      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      session0.createQueue(address1, address1, null, false, false, false);
      session1.createQueue(address1, address1, null, false, false, false);
      ClientProducer prod0 = session0.createProducer(address1);

      ClientConsumer cons1 = session1.createConsumer(address1);
     
      session1.start();
      
      SimpleString propKey = new SimpleString("car");
      
      ClientMessage messageSaab = session0.createClientMessage(false);
      messageSaab.putStringProperty(propKey, new SimpleString("saab"));
      messageSaab.getBody().flip();
      
      ClientMessage messageBMW = session0.createClientMessage(false);
      messageBMW.putStringProperty(propKey, new SimpleString("bmw"));
      messageBMW.getBody().flip();
      
      prod0.send(messageSaab);
      prod0.send(messageBMW);
      
      ClientMessage r1 = cons1.receive(1000);
      assertNotNull(r1);
      
      SimpleString val = (SimpleString)r1.getProperty(propKey);
      assertTrue(val.equals(new SimpleString("saab")) || val.equals(new SimpleString("bmw")));
      r1 = cons1.receiveImmediate();
      assertNull(r1);
      
      session0.close();
      session1.close();
            
      service0.stop();      
      service1.stop();

      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
   }
   
   public void testMessageNullName() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);
      
      List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors.add(server1tc);
      
      final SimpleString address1 = new SimpleString("testaddress");
                 
      MessageFlowConfiguration ofconfig1 = new MessageFlowConfiguration(null, address1.toString(), null, true, 1, -1, null, connectors);
                    
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig1);
   
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);
   
      service0.start();
      
      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      session0.createQueue(address1, address1, null, false, false, false);
      session1.createQueue(address1, address1, null, false, false, false);
      ClientProducer prod0 = session0.createProducer(address1);

      ClientConsumer cons1 = session1.createConsumer(address1);
     
      session1.start();
      
      SimpleString propKey = new SimpleString("car");
      
      ClientMessage message = session0.createClientMessage(false);
      message.getBody().flip();
      
      prod0.send(message);
      
      ClientMessage r1 = cons1.receive(1000);
      assertNull(r1);
     
      session0.close();
      session1.close();
            
      service0.stop();      
      service1.stop();

      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
   }
   
   public void testMessageNullAdress() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();      
      MessagingService service0 = createMessagingService(0, service0Params);
      
      Map<String, Object> service1Params = new HashMap<String, Object>();      
      MessagingService service1 = createMessagingService(1, service1Params);      
      service1.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);
      
      List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params);
      connectors.add(server1tc);
      
      final SimpleString address1 = new SimpleString("testaddress");
                 
      MessageFlowConfiguration ofconfig1 = new MessageFlowConfiguration("blah", null, null, true, 1, -1, null, connectors);
                    
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig1);
   
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);
   
      service0.start();
      
      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);
      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);
      ClientSession session1 = csf1.createSession(false, true, true);
      
      session0.createQueue(address1, address1, null, false, false, false);
      session1.createQueue(address1, address1, null, false, false, false);
      ClientProducer prod0 = session0.createProducer(address1);

      ClientConsumer cons1 = session1.createConsumer(address1);
     
      session1.start();
      
      SimpleString propKey = new SimpleString("car");
      
      ClientMessage message = session0.createClientMessage(false);
      message.getBody().flip();
      
      prod0.send(message);
      
      ClientMessage r1 = cons1.receive(1000);
      assertNull(r1);
     
      session0.close();
      session1.close();
            
      service0.stop();      
      service1.stop();

      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
   }
   

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
   }

   @Override
   protected void tearDown() throws Exception
   {
      assertEquals(0, InVMRegistry.instance.size());
   }

   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------
}
