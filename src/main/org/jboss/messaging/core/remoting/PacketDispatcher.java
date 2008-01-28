/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.core.remoting;

import static org.jboss.messaging.core.remoting.Assert.assertValidID;
import static org.jboss.messaging.core.remoting.wireformat.AbstractPacket.NO_ID_SET;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.messaging.core.remoting.wireformat.AbstractPacket;
import org.jboss.messaging.core.remoting.wireformat.SetSessionIDMessage;
import org.jboss.messaging.util.Logger;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>.
 * 
 * @version <tt>$Revision$</tt>
 */
public class PacketDispatcher
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(PacketDispatcher.class);

   // Attributes ----------------------------------------------------

   private Map<String, PacketHandler> handlers;

   // Static --------------------------------------------------------

   public static final PacketDispatcher client = new PacketDispatcher();
   public static final Map<String, String> sessions = new ConcurrentHashMap<String, String>();

   // Constructors --------------------------------------------------

   public PacketDispatcher()
   {
      handlers = new ConcurrentHashMap<String, PacketHandler>();
   }

   // Public --------------------------------------------------------

   public void register(PacketHandler handler)
   {
      assertValidID(handler.getID());
      assert handler != null;
      
      handlers.put(handler.getID(), handler);

      if (log.isDebugEnabled())
      {
         log.debug("registered " + handler + " with ID " + handler.getID());
      }
   }

   public void unregister(String handlerID)
   {
      assertValidID(handlerID);

      handlers.remove(handlerID);
      
      if (log.isDebugEnabled())
      {
         log.debug("unregistered handler for " + handlerID);
      }
   }

   public PacketHandler getHandler(String handlerID)
   {
      assertValidID(handlerID);

      return handlers.get(handlerID);
   }
   
   public void dispatch(AbstractPacket packet, PacketSender sender)
   {
      //FIXME better separation between client and server PacketDispatchers
      if (this != client)
      {
         if (packet instanceof SetSessionIDMessage)
         {
            String clientSessionID = ((SetSessionIDMessage)packet).getSessionID();
            if (log.isDebugEnabled())
               log.debug("associated server session " + sender.getSessionID() + " to client " + clientSessionID);
            sessions.put(sender.getSessionID(), clientSessionID);
            return;
         }
      }
      String targetID = packet.getTargetID();
      if (NO_ID_SET.equals(targetID))
      {
         log.error("Packet is not handled, it has no targetID: " + packet);
         return;
      }
      PacketHandler handler = getHandler(targetID);
      if (handler != null)
      {
         if (log.isTraceEnabled())
            log.trace(handler + " handles " + packet);

         handler.handle(packet, sender);
      } else
      {
         log.error("Unhandled packet " + packet);
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
