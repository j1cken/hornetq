/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.core;

import org.jboss.messaging.interfaces.Receiver;
import org.jboss.messaging.interfaces.Message;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * A simple Receiver implementation that consumes messages by storing them internally. Used for
 * testing.
 * <p>
 * The receiver can be configured to properly handle messages, to deny messages and to behave as
 * "broken" - to throw a RuntimeException during the handle() call.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class ReceiverImpl implements Receiver
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(ReceiverImpl.class);

   public static final String HANDLING = "HANDLING";
   public static final String DENYING = "DENYING";
   public static final String BROKEN = "BROKEN";

   private static final String INVOCATION_COUNT = "INVOCATION_COUNT";

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   private List messages;
   private String state;

   private Map waitingArea;

   // Constructors --------------------------------------------------

   public ReceiverImpl()
   {
      this(HANDLING);
   }

   public ReceiverImpl(String state)
   {
      if (!isValid(state))
      {
         throw new IllegalArgumentException("Unknown Receiver state: "+state);
      }
      this.state = state;
      messages = new ArrayList();
      waitingArea = new HashMap();
      waitingArea.put(INVOCATION_COUNT, new Integer(0));
   }

   // Recevier implementation ---------------------------------------

   public boolean handle(Message m)
   {
      try
      {
         if (BROKEN.equals(state))
         {
            // rogue receiver, throws unchecked exceptions
            throw new RuntimeException("THIS IS AN EXCEPTION THAT SIMULATES "+
                                       "THE BEHAVIOUR OF A BROKEN RECEIVER");
         }
         if (DENYING.equals(state))
         {
            // politely tells that it cannot handle the message
            return false;
         }
         // Handling receiver
         if (m == null)
         {
            return false;
         }
         messages.add(m);
         return true;
      }
      finally
      {
         synchronized(waitingArea)
         {
            Integer crt = (Integer)waitingArea.get(INVOCATION_COUNT);
            waitingArea.put(INVOCATION_COUNT, new Integer(crt.intValue() + 1));
            waitingArea.notifyAll();
         }
      }
   }

   // Public --------------------------------------------------------

   public void clear()
   {
      messages.clear();
   }

   /**
    * Blocks until handle() is called for the specified number of times.
    * @param count
    */
   public void waitForHandleInvocations(int count)
   {
      synchronized(waitingArea)
      {
         while(true)
         {
            Integer invocations = (Integer)waitingArea.get(INVOCATION_COUNT);
            if (invocations.intValue() == count)
            {
               return;
            }
            try
            {
               waitingArea.wait(1000);
            }
            catch(InterruptedException e)
            {
               // OK
            }
         }
      }
   }

   public void resetInvocationCount()
   {
      synchronized(waitingArea)
      {
         waitingArea.put(INVOCATION_COUNT, new Integer(0));
         waitingArea.notifyAll();
      }
   }

   public Iterator iterator()
   {
      return messages.iterator();
   }

   public List getMessages()
   {
      return messages;
   }

   public void setState(String state)
   {
      if (!isValid(state))
      {
         throw new IllegalArgumentException("Unknown Receiver state: "+state);
      }
      this.state = state;
   }

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------

   private static boolean isValid(String state)
   {
      if (HANDLING.equals(state) ||
          DENYING.equals(state) ||
          BROKEN.equals(state))
      {
         return true;
      }
      return false;
   }
}
