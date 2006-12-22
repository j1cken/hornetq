/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.jms.server.endpoint;


/**
 * 
 * A Cancel.
 * 
 * Used to send a cancel (NACK) to the server
 * 
 * When we cancel we send delivery count info, this means delivery count
 * can be updated on the server, and into storage if persistent, and the
 * message can be sent to DLQ if appropriate
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public class DefaultCancel implements Cancel
{
   // Constants -----------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   private long deliveryId;
   
   private int deliveryCount;      

   // Static --------------------------------------------------------
   
   // Constructors --------------------------------------------------
   
   public DefaultCancel()
   {      
   }
   
   public DefaultCancel(long deliveryId, int deliveryCount)
   {      
      this.deliveryId = deliveryId;
      
      this.deliveryCount = deliveryCount;
   }

   // Public --------------------------------------------------------
   
   public long getDeliveryId()
   {
      return deliveryId;
   }
   
   public int getDeliveryCount()
   {
      return deliveryCount;
   }

   // Class YYY overrides -------------------------------------------

   // Protected -----------------------------------------------------

   // Package Private -----------------------------------------------

   // Private -------------------------------------------------------
   
   // Inner Classes -------------------------------------------------
   
}

