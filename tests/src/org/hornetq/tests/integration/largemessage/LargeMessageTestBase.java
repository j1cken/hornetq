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

package org.hornetq.tests.integration.largemessage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.core.buffers.ChannelBuffers;
import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.MessageHandler;
import org.hornetq.core.exception.MessagingException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.message.impl.MessageImpl;
import org.hornetq.core.remoting.spi.MessagingBuffer;
import org.hornetq.core.server.MessagingServer;
import org.hornetq.core.server.Queue;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.DataConstants;
import org.hornetq.utils.SimpleString;

/**
 * A LargeMessageTestBase
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created Oct 29, 2008 11:43:52 AM
 *
 *
 */
public class LargeMessageTestBase extends ServiceTestBase
{

   // Constants -----------------------------------------------------
   private static final Logger log = Logger.getLogger(LargeMessageTestBase.class);

   protected final SimpleString ADDRESS = new SimpleString("SimpleAddress");

   protected MessagingServer server;

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------
   
   protected void tearDown() throws Exception
   {
      if (server != null && server.isStarted())
      {
         try
         {
            server.stop();
         }
         catch (Exception e)
         {
            log.warn(e.getMessage(), e);
         }
      }
      
      server = null;
      
      super.tearDown();
   }

   protected void testChunks(final boolean isXA,
                             final boolean rollbackFirstSend,
                             final boolean useStreamOnConsume,
                             final boolean realFiles,
                             final boolean preAck,
                             final boolean sendingBlocking,
                             final boolean testBrowser,
                             final boolean useMessageConsumer,
                             final int numberOfMessages,
                             final long numberOfBytes,
                             final int waitOnConsumer,
                             final long delayDelivery) throws Exception
   {
      testChunks(isXA,
                 rollbackFirstSend,
                 useStreamOnConsume,
                 realFiles,
                 preAck,
                 sendingBlocking,
                 testBrowser,
                 useMessageConsumer,
                 numberOfMessages,
                 numberOfBytes,
                 waitOnConsumer,
                 delayDelivery,
                 -1,
                 10 * 1024);
   }

   protected void testChunks(final boolean isXA,
                             final boolean rollbackFirstSend,
                             final boolean useStreamOnConsume,
                             final boolean realFiles,
                             final boolean preAck,
                             final boolean sendingBlocking,
                             final boolean testBrowser,
                             final boolean useMessageConsumer,
                             final int numberOfMessages,
                             final long numberOfBytes,
                             final int waitOnConsumer,
                             final long delayDelivery,
                             final int producerWindow,
                             final int minSize) throws Exception
   {
      clearData();

      server = createServer(realFiles);
      server.start();

      try
      {
         ClientSessionFactory sf = createInVMFactory();

         if (sendingBlocking)
         {
            sf.setBlockOnNonPersistentSend(true);
            sf.setBlockOnPersistentSend(true);
            sf.setBlockOnAcknowledge(true);
         }

         if (producerWindow > 0)
         {
            sf.setProducerWindowSize(producerWindow);
         }

         sf.setMinLargeMessageSize(minSize);

         ClientSession session;

         Xid xid = null;
         session = sf.createSession(null, null, isXA, false, false, preAck, 0);

         if (isXA)
         {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         session.createQueue(ADDRESS, ADDRESS, null, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         if (rollbackFirstSend)
         {
            sendMessages(numberOfMessages, numberOfBytes, delayDelivery, session, producer);

            if (isXA)
            {
               session.end(xid, XAResource.TMSUCCESS);
               session.rollback(xid);
               xid = newXID();
               session.start(xid, XAResource.TMNOFLAGS);
            }
            else
            {
               session.rollback();
            }

            validateNoFilesOnLargeDir();
         }

         sendMessages(numberOfMessages, numberOfBytes, delayDelivery, session, producer);

         if (isXA)
         {
            session.end(xid, XAResource.TMSUCCESS);
            session.commit(xid, true);
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }
         else
         {
            session.commit();
         }

         session.close();

         if (realFiles)
         {
            server.stop();

            server = createServer(realFiles);
            server.start();

            sf = createInVMFactory();
         }

         session = sf.createSession(null, null, isXA, false, false, preAck, 0);

         if (isXA)
         {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         ClientConsumer consumer = null;

         for (int iteration = testBrowser ? 0 : 1; iteration < 2; iteration++)
         {

            log.debug("Iteration: " + iteration);

            session.stop();

            // first time with a browser
            consumer = session.createConsumer(ADDRESS, null, iteration == 0);

            if (useMessageConsumer)
            {
               final CountDownLatch latchDone = new CountDownLatch(numberOfMessages);
               final AtomicInteger errors = new AtomicInteger(0);

               MessageHandler handler = new MessageHandler()
               {
                  int msgCounter;

                  public void onMessage(final ClientMessage message)
                  {

                     try
                     {
                        log.debug("Message on consumer: " + msgCounter);

                        if (delayDelivery > 0)
                        {
                           long originalTime = (Long)message.getProperty(new SimpleString("original-time"));
                           assertTrue(System.currentTimeMillis() - originalTime + "<" + delayDelivery,
                                      System.currentTimeMillis() - originalTime >= delayDelivery);
                        }

                        if (!preAck)
                        {
                           message.acknowledge();
                        }

                        assertNotNull(message);

                        if (delayDelivery <= 0)
                        {
                           // right now there is no guarantee of ordered delivered on multiple scheduledMessages with
                           // the same
                           // scheduled delivery time
                           assertEquals(msgCounter,
                                        ((Integer)message.getProperty(new SimpleString("counter-message"))).intValue());
                        }

                        if (useStreamOnConsume)
                        {
                           final AtomicLong bytesRead = new AtomicLong(0);
                           message.saveToOutputStream(new OutputStream()
                           {

                              public void write(byte b[]) throws IOException
                              {
                                 if (b[0] == getSamplebyte(bytesRead.get()))
                                 {
                                    bytesRead.addAndGet(b.length);
                                    log.debug("Read position " + bytesRead.get() + " on consumer");
                                 }
                                 else
                                 {
                                    log.warn("Received invalid packet at position " + bytesRead.get());
                                 }
                              }

                              @Override
                              public void write(int b) throws IOException
                              {
                                 if (b == getSamplebyte(bytesRead.get()))
                                 {
                                    bytesRead.incrementAndGet();
                                 }
                                 else
                                 {
                                    log.warn("byte not as expected!");
                                 }
                              }
                           });

                           assertEquals(numberOfBytes, bytesRead.get());
                        }
                        else
                        {

                           MessagingBuffer buffer = message.getBody();
                           buffer.resetReaderIndex();
                           assertEquals(numberOfBytes, buffer.writerIndex());
                           for (long b = 0; b < numberOfBytes; b++)
                           {
                              if (b % (1024l * 1024l) == 0)
                              {
                                 log.debug("Read " + b + " bytes");
                              }
                              
                              assertEquals(getSamplebyte(b), buffer.readByte());
                           }
                        }
                     }
                     catch (Throwable e)
                     {
                        e.printStackTrace();
                        log.warn("Got an error", e);
                        errors.incrementAndGet();
                     }
                     finally
                     {
                        latchDone.countDown();
                        msgCounter++;
                     }
                  }
               };

               session.start();

               consumer.setMessageHandler(handler);

               assertTrue(latchDone.await(waitOnConsumer, TimeUnit.SECONDS));
               assertEquals(0, errors.get());

            }
            else
            {

               session.start();

               for (int i = 0; i < numberOfMessages; i++)
               {
                  System.currentTimeMillis();

                  ClientMessage message = consumer.receive(waitOnConsumer + delayDelivery);

                  assertNotNull(message);

                  log.debug("Message: " + i);

                  System.currentTimeMillis();

                  if (delayDelivery > 0)
                  {
                     long originalTime = (Long)message.getProperty(new SimpleString("original-time"));
                     assertTrue(System.currentTimeMillis() - originalTime + "<" + delayDelivery,
                                System.currentTimeMillis() - originalTime >= delayDelivery);
                  }

                  if (!preAck)
                  {
                     message.acknowledge();
                  }

                  assertNotNull(message);

                  if (delayDelivery <= 0)
                  {
                     // right now there is no guarantee of ordered delivered on multiple scheduledMessages with the same
                     // scheduled delivery time
                     assertEquals(i, ((Integer)message.getProperty(new SimpleString("counter-message"))).intValue());
                  }

                  MessagingBuffer buffer = message.getBody();
                  buffer.resetReaderIndex();

                  if (useStreamOnConsume)
                  {
                     final AtomicLong bytesRead = new AtomicLong(0);
                     message.saveToOutputStream(new OutputStream()
                     {

                        public void write(byte b[]) throws IOException
                        {
                           if (b[0] == getSamplebyte(bytesRead.get()))
                           {
                              bytesRead.addAndGet(b.length);
                           }
                           else
                           {
                              log.warn("Received invalid packet at position " + bytesRead.get());
                           }

                        }

                        @Override
                        public void write(int b) throws IOException
                        {
                           if (bytesRead.get() % (1024l * 1024l) == 0)
                           {
                              log.debug("Read " + bytesRead.get() + " bytes");
                           }
                           if (b == (byte)'a')
                           {
                              bytesRead.incrementAndGet();
                           }
                           else
                           {
                              log.warn("byte not as expected!");
                           }
                        }
                     });

                     assertEquals(numberOfBytes, bytesRead.get());
                  }
                  else
                  {
                     for (long b = 0; b < numberOfBytes; b++)
                     {
                        if (b % (1024l * 1024l) == 0l)
                        {
                           log.debug("Read " + b + " bytes");
                        }
                        assertEquals(getSamplebyte(b), buffer.readByte());
                     }
                  }

               }

            }
            consumer.close();

            if (iteration == 0)
            {
               if (isXA)
               {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.rollback(xid);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               }
               else
               {
                  session.rollback();
               }
            }
            else
            {
               if (isXA)
               {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.commit(xid, true);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               }
               else
               {
                  session.commit();
               }
            }
         }

         session.close();

         long globalSize = server.getPostOffice().getPagingManager().getTotalMemory();
         assertEquals(0l, globalSize);
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

         validateNoFilesOnLargeDir();

      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   /**
    * @param useFile
    * @param numberOfMessages
    * @param numberOfIntegers
    * @param delayDelivery
    * @param testTime
    * @param session
    * @param producer
    * @throws FileNotFoundException
    * @throws IOException
    * @throws MessagingException
    */
   private void sendMessages(final int numberOfMessages,
                             final long numberOfBytes,
                             final long delayDelivery,
                             final ClientSession session,
                             final ClientProducer producer) throws Exception
   {
      log.debug("NumberOfBytes = " + numberOfBytes);
      for (int i = 0; i < numberOfMessages; i++)
      {
         ClientMessage message = session.createClientMessage(true);

         // If the test is using more than 1M, we will only use the Streaming, as it require too much memory from the
         // test
         if (numberOfBytes > 1024 * 1024 || i % 2 == 0)
         {
            log.debug("Sending message (stream)" + i);
            message.setBodyInputStream(createFakeLargeStream(numberOfBytes));
         }
         else
         {
            log.debug("Sending message (array)" + i);
            byte[] bytes = new byte[(int)numberOfBytes];
            for (int j = 0; j < bytes.length; j++)
            {
               bytes[j] = getSamplebyte(j);
            }
            message.getBody().writeBytes(bytes);
         }
         message.putIntProperty(new SimpleString("counter-message"), i);
         if (delayDelivery > 0)
         {
            long time = System.currentTimeMillis();
            message.putLongProperty(new SimpleString("original-time"), time);
            message.putLongProperty(MessageImpl.HDR_SCHEDULED_DELIVERY_TIME, time + delayDelivery);

            producer.send(message);
         }
         else
         {
            producer.send(message);
         }
      }
   }

   protected MessagingBuffer createLargeBuffer(final int numberOfIntegers)
   {
      MessagingBuffer body = ChannelBuffers.buffer(DataConstants.SIZE_INT * numberOfIntegers);

      for (int i = 0; i < numberOfIntegers; i++)
      {
         body.writeInt(i);
      }

      return body;

   }

   protected ClientMessage createLargeClientMessage(final ClientSession session, final int numberOfBytes) throws Exception
   {
      return createLargeClientMessage(session, numberOfBytes, true);
   }

   protected ClientMessage createLargeClientMessage(final ClientSession session,
                                                    final long numberOfBytes,
                                                    final boolean persistent) throws Exception
   {

      ClientMessage clientMessage = session.createClientMessage(persistent);

      clientMessage.setBodyInputStream(createFakeLargeStream(numberOfBytes));

      return clientMessage;
   }

   /**
    * @param session
    * @param queueToRead
    * @param numberOfIntegers
    * @throws MessagingException
    * @throws FileNotFoundException
    * @throws IOException
    */
   protected void readMessage(final ClientSession session, final SimpleString queueToRead, final int numberOfBytes) throws MessagingException,
                                                                                                                   FileNotFoundException,
                                                                                                                   IOException
   {
      session.start();

      ClientConsumer consumer = session.createConsumer(queueToRead);

      ClientMessage clientMessage = consumer.receive(5000);

      assertNotNull(clientMessage);

      clientMessage.acknowledge();

      session.commit();

      consumer.close();
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir(int expect) throws Exception
   {
      File largeMessagesFileDir = new File(getLargeMessagesDir());

      // Deleting the file is async... we keep looking for a period of the time until the file is really gone
      for (int i = 0; i < 100; i++)
      {
         if (largeMessagesFileDir.listFiles().length != expect)
         {
            Thread.sleep(10);
         }
         else
         {
            break;
         }
      }

      assertEquals(expect, largeMessagesFileDir.listFiles().length);
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir() throws Exception
   {
      validateNoFilesOnLargeDir(0);
   }

   protected OutputStream createFakeOutputStream() throws Exception
   {

      return new OutputStream()
      {
         private boolean closed = false;

         private int count;

         @Override
         public void close() throws IOException
         {
            super.close();
            closed = true;
         }

         @Override
         public void write(final int b) throws IOException
         {
            if (count++ % 1024 * 1024 == 0)
            {
               log.debug("OutputStream received " + count + " bytes");
            }
            if (closed)
            {
               throw new IOException("Stream was closed");
            }
         }

      };

   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
