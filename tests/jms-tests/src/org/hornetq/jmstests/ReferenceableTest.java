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

package org.hornetq.jmstests;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * 
 * A ReferenceableTest.
 * 
 * All administered objects should be referenceable and serializable as per spec 4.2
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version $Revision$
 *
 * $Id$
 */
public class ReferenceableTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   
   public void testSerializable() throws Exception
   {
      assertTrue(cf instanceof Serializable);
      
      assertTrue(queue1 instanceof Serializable);
      
      assertTrue(topic1 instanceof Serializable);            
   }

   /* http://jira.jboss.org/jira/browse/JBMESSAGING-395

   public void testReferenceable() throws Exception
   {
      assertTrue(cf instanceof Referenceable);
      
      assertTrue(queue instanceof Referenceable);
      
      assertTrue(topic instanceof Referenceable);
   }
   
   public void testReferenceCF() throws Exception
   {
      Reference cfRef = ((Referenceable)cf).getReference();
      
      String factoryName = cfRef.getFactoryClassName();
      
      Class factoryClass = Class.forName(factoryName);
      
      ConnectionFactoryObjectFactory factory = (ConnectionFactoryObjectFactory)factoryClass.newInstance();
      
      Object instance = factory.getObjectInstance(cfRef, null, null, null);
      
      assertTrue(instance instanceof HornetQRAConnectionFactory);
      
      HornetQRAConnectionFactory cf2 = (HornetQRAConnectionFactory)instance;
      
      simpleSendReceive(cf2, queue);
   }
   
   public void testReferenceQueue() throws Exception
   {
      Reference queueRef = ((Referenceable)queue).getReference();
      
      String factoryName = queueRef.getFactoryClassName();
      
      Class factoryClass = Class.forName(factoryName);
      
      DestinationObjectFactory factory = (DestinationObjectFactory)factoryClass.newInstance();
      
      Object instance = factory.getObjectInstance(queueRef, null, null, null);
      
      assertTrue(instance instanceof HornetQQueue);
      
      HornetQQueue queue2 = (HornetQQueue)instance;
      
      assertEquals(queue.getQueueName(), queue2.getQueueName());
      
      simpleSendReceive(cf, queue2);
      
   }
   
   public void testReferenceTopic() throws Exception
   {
      Reference topicRef = ((Referenceable)topic).getReference();
      
      String factoryName = topicRef.getFactoryClassName();
      
      Class factoryClass = Class.forName(factoryName);
      
      DestinationObjectFactory factory = (DestinationObjectFactory)factoryClass.newInstance();
      
      Object instance = factory.getObjectInstance(topicRef, null, null, null);
      
      assertTrue(instance instanceof HornetQTopic);
      
      HornetQTopic topic2 = (HornetQTopic)instance;
      
      assertEquals(topic.getTopicName(), topic2.getTopicName());
      
      simpleSendReceive(cf, topic2);
   }

   */
   
   
   protected void simpleSendReceive(ConnectionFactory cf, Destination dest) throws Exception
   {
      Connection conn = null;
      
      try
      {      
	      conn = cf.createConnection();
	      
	      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      
	      MessageProducer prod = sess.createProducer(dest);
	      
	      MessageConsumer cons = sess.createConsumer(dest);
	      
	      conn.start();
	      
	      TextMessage tm = sess.createTextMessage("ref test");
	      
	      prod.send(tm);
	      
	      tm = (TextMessage)cons.receive(1000);
	      
	      assertNotNull(tm);
	      
	      assertEquals("ref test", tm.getText());
      }
      finally
      {
      	if (conn != null)
      	{
      		conn.close();
      	}
      }
   }
}



