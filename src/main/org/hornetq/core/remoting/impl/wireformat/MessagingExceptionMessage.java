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

package org.hornetq.core.remoting.impl.wireformat;

import org.hornetq.core.exception.MessagingException;
import org.hornetq.core.remoting.spi.MessagingBuffer;
import org.hornetq.utils.DataConstants;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public class MessagingExceptionMessage extends PacketImpl
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private MessagingException exception;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public MessagingExceptionMessage(final MessagingException exception)
   {
      super(EXCEPTION);

      this.exception = exception;
   }

   public MessagingExceptionMessage()
   {
      super(EXCEPTION);
   }

   // Public --------------------------------------------------------

   public boolean isResponse()
   {
      return true;
   }

   public MessagingException getException()
   {
      return exception;
   }
   
   public int getRequiredBufferSize()
   {
      return BASIC_PACKET_SIZE + DataConstants.SIZE_INT + nullableStringEncodeSize(exception.getMessage());
   }


   public void encodeBody(final MessagingBuffer buffer)
   {
      buffer.writeInt(exception.getCode());
      buffer.writeNullableString(exception.getMessage());
   }

   public void decodeBody(final MessagingBuffer buffer)
   {
      int code = buffer.readInt();
      String msg = buffer.readNullableString();
      exception = new MessagingException(code, msg);
   }

   @Override
   public String toString()
   {
      return getParentString() + ", exception= " + exception + "]";
   }

   public boolean equals(Object other)
   {
      if (other instanceof MessagingExceptionMessage == false)
      {
         return false;
      }

      MessagingExceptionMessage r = (MessagingExceptionMessage)other;

      return super.equals(other) && this.exception.equals(r.exception);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
