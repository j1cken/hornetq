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
package org.hornetq.jms.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.hornetq.common.example.HornetQExample;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.SendAcknowledgementHandler;
import org.hornetq.core.message.Message;
import org.hornetq.jms.client.HornetQSession;

/**
 * 
 * Asynchronous Send Acknowledgements are an advanced feature of JBoss Messaging which allow you to
 * receive acknowledgements that messages were successfully received at the server in a separate stream
 * to the stream of messages being sent to the server.
 * For more information please see the readme.html file
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class SendAcknowledgementsExample extends HornetQExample
{
   public static void main(String[] args)
   {
      new SendAcknowledgementsExample().run(args);
   }

   public boolean runExample() throws Exception
   {
      Connection connection = null;
      InitialContext initialContext = null;
      try
      {
         // Step 1. Create an initial context to perform the JNDI lookup.
         initialContext = getContext(0);

         // Step 2. Perfom a lookup on the queue
         Queue queue = (Queue)initialContext.lookup("/queue/exampleQueue");

         // Step 3. Perform a lookup on the Connection Factory
         ConnectionFactory cf = (ConnectionFactory)initialContext.lookup("/ConnectionFactory");

         // Step 4. Create a JMS Connection
         connection = cf.createConnection();

         // Step 5. Define a SendAcknowledgementHandler which will receive asynchronous acknowledgements
         class MySendAcknowledgementsHandler implements SendAcknowledgementHandler
         {
            int count = 0;

            public void sendAcknowledged(final Message message)
            {
               System.out.println("Received send acknowledgement for message " + count++);
            }
         }

         // Step 6. Create a JMS Session
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Step 7. Set the handler on the underlying core session

         ClientSession coreSession = ((HornetQSession)session).getCoreSession();

         coreSession.setSendAcknowledgementHandler(new MySendAcknowledgementsHandler());

         // Step 6. Create a JMS Message Producer
         MessageProducer producer = session.createProducer(queue);

         producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         // Step 7. Send 5000 messages, the handler will get called asynchronously some time later after the messages
         // are sent.

         final int numMessages = 5000;

         for (int i = 0; i < numMessages; i++)
         {
            javax.jms.Message jmsMessage = session.createMessage();

            producer.send(jmsMessage);

            System.out.println("Sent message " + i);
         }

         return true;
      }
      finally
      {
         // Step 12. Be sure to close our JMS resources!
         if (initialContext != null)
         {
            initialContext.close();
         }

         if (connection != null)
         {
            connection.close();
         }
      }
   }

}
