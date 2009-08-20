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


package org.hornetq.tests.integration.cluster.failover;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.client.impl.ClientSessionImpl;
import org.hornetq.core.client.impl.ClientSessionInternal;
import org.hornetq.core.exception.MessagingException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.RemotingConnectionImpl;
import org.hornetq.core.remoting.impl.invm.InVMConnector;
import org.hornetq.tests.util.ServiceTestBase;

/**
 * A MultiThreadFailoverSupport
 *
 * @author <a href="mailto:time.fox@jboss.org">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created Mar 17, 2009 11:15:02 AM
 *
 *
 */
public abstract class MultiThreadFailoverSupport extends ServiceTestBase
{

   // Constants -----------------------------------------------------
   
   private final Logger log = Logger.getLogger(this.getClass());

   // Attributes ----------------------------------------------------

   private Timer timer;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected abstract void start() throws Exception;

   protected abstract void stop() throws Exception;
   
   protected abstract ClientSessionFactoryInternal createSessionFactory();
   
   protected void setUp() throws Exception
   {
      super.setUp();
      timer = new Timer();
   }
   
   protected void tearDown() throws Exception
   {
      timer.cancel();
      timer = null;
      super.tearDown();
   }
   
   protected boolean shouldFail()
   {
      return true;
   }
  
   protected void runMultipleThreadsFailoverTest(final RunnableT runnable,
                                       final int numThreads,
                                       final int numIts,
                                       final boolean failOnCreateConnection,
                                       final long failDelay) throws Exception
   {
      for (int its = 0; its < numIts; its++)
      {
         log.info("Beginning iteration " + its);
         
         start();

         final ClientSessionFactoryInternal sf = createSessionFactory();

         final ClientSession session = sf.createSession(false, true, true);

         Failer failer = startFailer(failDelay, session, failOnCreateConnection);

         class Runner extends Thread
         {
            private volatile Throwable throwable;

            private final RunnableT test;

            private final int threadNum;

            Runner(final RunnableT test, final int threadNum)
            {
               this.test = test;

               this.threadNum = threadNum;
            }

            @Override
            public void run()
            {
               try
               {
                  test.run(sf, threadNum);
               }
               catch (Throwable t)
               {
                  throwable = t;

                  log.error("Failed to run test", t);

                  // Case a failure happened here, it should print the Thread dump
                  // Sending it to System.out, as it would show on the Tests report
                  System.out.println(threadDump(" - fired by MultiThreadRandomFailoverTestBase::runTestMultipleThreads (" + t.getLocalizedMessage() + ")"));
               }
            }
         }

         do
         {
            List<Runner> threads = new ArrayList<Runner>();

            for (int i = 0; i < numThreads; i++)
            {
               Runner runner = new Runner(runnable, i);

               threads.add(runner);

               runner.start();
            }

            for (Runner thread : threads)
            {
               thread.join();

               if (thread.throwable != null)
               {
                  throw new Exception("Exception on thread " + thread, thread.throwable);
               }
            }

            runnable.checkFail();

         }
         while (!failer.isExecuted());

         InVMConnector.resetFailures();

         session.close();

         assertEquals(0, sf.numSessions());

         assertEquals(0, sf.numConnections());
         
         sf.close();

         stop();
               }
   }


   // Private -------------------------------------------------------

   private Failer startFailer(final long time, final ClientSession session, final boolean failOnCreateConnection)
   {
      Failer failer = new Failer(session, failOnCreateConnection);

      // This is useful for debugging.. just change shouldFail to return false, and Failer will not be executed
      if (shouldFail())
      {
         timer.schedule(failer, (long)(time * Math.random()), 100);
      }

      return failer;
   }


   // Inner classes -------------------------------------------------

 
   protected abstract class RunnableT extends Thread
   {
      private volatile String failReason;

      private volatile Throwable throwable;

      public void setFailed(final String reason, final Throwable throwable)
      {
         failReason = reason;
         this.throwable = throwable;
      }

      public void checkFail()
      {
         if (throwable != null)
         {
            log.error("Test failed: " + failReason, throwable);
         }
         if (failReason != null)
         {
            fail(failReason);
         }
      }

      public abstract void run(final ClientSessionFactory sf, final int threadNum) throws Exception;
   }

   

   private class Failer extends TimerTask
   {
      private final ClientSession session;

      private boolean executed;

      private final boolean failOnCreateConnection;

      public Failer(final ClientSession session, final boolean failOnCreateConnection)
      {
         this.session = session;

         this.failOnCreateConnection = failOnCreateConnection;
      }

      @Override
      public synchronized void run()
      {
         log.info("** Failing connection");

         RemotingConnectionImpl conn = (RemotingConnectionImpl)((ClientSessionInternal)session).getConnection();

         if (failOnCreateConnection)
         {
            InVMConnector.numberOfFailures = 1;
            InVMConnector.failOnCreateConnection = true;
         }
         else
         {
            conn.fail(new MessagingException(MessagingException.NOT_CONNECTED, "blah"));
         }

         log.info("** Fail complete");

         cancel();

         executed = true;
      }

      public synchronized boolean isExecuted()
      {
         return executed;
      }
   }

   

}
