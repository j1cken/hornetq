/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */


package org.jboss.messaging.core.local;

import org.jboss.messaging.core.Router;
import org.jboss.messaging.core.DeliveryObserver;
import org.jboss.messaging.core.Routable;
import org.jboss.messaging.core.Receiver;
import org.jboss.messaging.core.Delivery;
import org.jboss.messaging.core.tx.Transaction;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 * $Id$
 */
public class PointToMultipointRouter implements Router
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(PointToMultipointRouter.class);

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   List receivers;

   // Constructors --------------------------------------------------

   public PointToMultipointRouter()
   {
      receivers = new ArrayList();
   }

   // Router implementation -----------------------------------------

   public Set handle(DeliveryObserver observer, Routable routable, Transaction tx)
   {
      Set deliveries = new HashSet();

      synchronized(receivers)
      {
         for(Iterator i = receivers.iterator(); i.hasNext(); )
         {
            Receiver receiver = (Receiver)i.next();

            try
            {
               Delivery d = receiver.handle(observer, routable, tx);

               if (log.isTraceEnabled()) { log.trace("receiver " + receiver + " handled  " + routable + " and returned " + d); }

               if (d != null)
               {
                  deliveries.add(d);
               }
            }
            catch(Throwable t)
            {
               // broken receiver - log the exception and ignore it
               log.error("The receiver " + receiver + " is broken", t);
            }
         }
      }
      return deliveries;
   }

   public boolean add(Receiver r)
   {
      synchronized(receivers)
      {
         if (receivers.contains(r))
         {
            return false;
         }
         receivers.add(r);
      }
      return true;
   }


   public boolean remove(Receiver r)
   {
      synchronized(receivers)
      {
         return receivers.remove(r);
      }
   }

   public void clear()
   {
      synchronized(receivers)
      {
         receivers.clear();
      }
   }

   public boolean contains(Receiver r)
   {
      synchronized(receivers)
      {
         return receivers.contains(r);
      }
   }

   public Iterator iterator()
   {
      synchronized(receivers)
      {
         return receivers.iterator();
      }
   }


   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------   
}
