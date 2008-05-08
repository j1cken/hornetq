/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.messaging.tests.integration.core.remoting.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.messaging.core.exception.MessagingException;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.Interceptor;
import org.jboss.messaging.core.remoting.Packet;
import org.jboss.messaging.core.remoting.impl.wireformat.ReceiveMessage;
import org.jboss.messaging.util.SimpleString;

public class DummyInterceptor implements Interceptor
{
   protected Logger log = Logger.getLogger(DummyInterceptor.class);

   boolean sendException = false;
   boolean changeMessage = false;
   AtomicInteger syncCounter = new AtomicInteger(0);
   
   public int getCounter()
   {
      return syncCounter.get();
   }
   
   public void clearCounter()
   {
      syncCounter.set(0);
   }
   
   public void intercept(Packet packet) throws MessagingException
   {
      log.info("DummyFilter packet = " + packet.getClass().getName());
      syncCounter.addAndGet(1);
      if (sendException)
      {
         throw new MessagingException(MessagingException.INTERNAL_ERROR);
      }
      if (changeMessage)
      {
         if (packet instanceof ReceiveMessage)
         {
            ReceiveMessage deliver = (ReceiveMessage)packet;
            log.info("msg = " + deliver.getServerMessage().getClass().getName());
            deliver.getServerMessage().putStringProperty(new SimpleString("DummyInterceptor"), new SimpleString("was here"));
         }
      }
   }

}
