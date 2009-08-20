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

import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.server.MessagingServer;
import org.hornetq.core.server.Queue;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.SimpleString;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class SendTest extends ServiceTestBase
{
   public final SimpleString addressA = new SimpleString("addressA");

   public final SimpleString queueA = new SimpleString("queueA");

   public final SimpleString queueB = new SimpleString("queueB");

   public final SimpleString queueC = new SimpleString("queueC");


   public void testSendWithCommit() throws Exception
   {
      MessagingServer server = createServer(false);
      try
      {
         server.start();
         ClientSessionFactory cf = createInVMFactory();
         ClientSession session = cf.createSession(false, false, false);
         session.createQueue(addressA, queueA, false);
         ClientProducer cp = session.createProducer(addressA);
         int numMessages = 100;
         for (int i = 0; i < numMessages; i++)
         {
            cp.send(session.createClientMessage(false));
         }
         Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
         assertEquals(q.getMessageCount(), 0);
         session.commit();
         assertEquals(q.getMessageCount(), numMessages);
         // now send some more
         for (int i = 0; i < numMessages; i++)
         {
            cp.send(session.createClientMessage(false));
         }
         assertEquals(q.getMessageCount(), numMessages);
         session.commit();
         assertEquals(q.getMessageCount(), numMessages * 2);
         session.close();
      }
      finally
      {
         if (server.isStarted())
         {
            server.stop();
         }
      }
   }

   public void testSendWithRollback() throws Exception
   {
      MessagingServer server = createServer(false);
      try
      {
         server.start();
         ClientSessionFactory cf = createInVMFactory();
         ClientSession session = cf.createSession(false, false, false);
         session.createQueue(addressA, queueA, false);
         ClientProducer cp = session.createProducer(addressA);
         int numMessages = 100;
         for (int i = 0; i < numMessages; i++)
         {
            cp.send(session.createClientMessage(false));
         }
         Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
         assertEquals(q.getMessageCount(), 0);
         session.rollback();
         assertEquals(q.getMessageCount(), 0);
         // now send some more
         for (int i = 0; i < numMessages; i++)
         {
            cp.send(session.createClientMessage(false));
         }
         assertEquals(q.getMessageCount(), 0);
         session.commit();
         assertEquals(q.getMessageCount(), numMessages);
         session.close();
      }
      finally
      {
         if (server.isStarted())
         {
            server.stop();
         }
      }
   }

}
