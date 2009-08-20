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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.QueueConnection;
import javax.jms.QueueReceiver;
import javax.jms.QueueRequestor;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class QueueRequestorTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   // TestCase overrides -------------------------------------------

   // Public --------------------------------------------------------

   public void testQueueRequestor() throws Exception
   {
      // Set up the requestor
      QueueConnection conn1 = null;      
      QueueConnection conn2 = null;
      
      try
      {
	      
	      conn1 = cf.createQueueConnection();
	      QueueSession sess1 = conn1.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
	      QueueRequestor requestor = new QueueRequestor(sess1, queue1);
	      conn1.start();
	      
	      // And the responder
	      conn2 = cf.createQueueConnection();
	      QueueSession sess2 = conn2.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
	      TestMessageListener listener = new TestMessageListener(sess2);
	      QueueReceiver receiver = sess2.createReceiver(queue1);
	      receiver.setMessageListener(listener);
	      conn2.start();
	      
	      Message m1 = sess1.createMessage();
	      log.trace("Sending request message");
	      TextMessage m2 = (TextMessage)requestor.request(m1);	      	      
	      assertNotNull(m2);
	      
	      assertEquals("This is the response", m2.getText());
      }
      finally
      {      
	     if (conn1 != null) conn1.close();
	     if (conn2 != null) conn2.close();
      }      
   }  
   
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------

   class TestMessageListener implements MessageListener
   {
      private QueueSession sess;
      private QueueSender sender;
      
      public TestMessageListener(QueueSession sess)
         throws JMSException
      {
         this.sess = sess;
         this.sender = sess.createSender(null);
      }
      
      public void onMessage(Message m)
      {
         try
         {
            Destination queue = m.getJMSReplyTo();
            Message m2 = sess.createTextMessage("This is the response");
            sender.send(queue, m2);
         }
         catch (JMSException e)
         {
            log.error(e);
         }
      }
   }
}
