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
package org.jboss.jms.server.endpoint.delegate;

import javax.jms.JMSException;
import javax.jms.Message;

import org.jboss.jms.server.endpoint.BrowserEndpoint;

/**
 * Delegate class for BrowserEndpoint
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 */
public class BrowserEndpointDelegate extends EndpointDelegateBase implements BrowserEndpoint
{
   protected BrowserEndpoint del;
   
   public BrowserEndpointDelegate(BrowserEndpoint del)
   {
      this.del = del;
   }

   public Object getEndpoint()
   {
      return del;
   }
   
   public void close() throws JMSException
   {
      del.close();
   }

   public void closing() throws JMSException
   {
      del.closing();
   }

   public boolean hasNextMessage() throws JMSException
   {
      return del.hasNextMessage();
   }

   public Message nextMessage() throws JMSException
   {
      return del.nextMessage();
   }

   public Message[] nextMessageBlock(int maxMessages) throws JMSException
   {
      return del.nextMessageBlock(maxMessages);
   }
   

}
