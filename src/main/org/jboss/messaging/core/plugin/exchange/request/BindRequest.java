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
package org.jboss.messaging.core.plugin.exchange.request;

import java.io.Serializable;

/**
 * A BindRequest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public class BindRequest implements Serializable
{
   private static final long serialVersionUID = 6567558817192367353L;

   private String nodeId;   
   private String queueName;   
   private String condition;   
   private String filterString;   
   private boolean noLocal;   
   private long channelId;   
   private boolean durable;
   
   public BindRequest(String nodeId, String queueName, String condition, String filterString,
                      boolean noLocal, long channelId, boolean durable)
   {
      this.nodeId = nodeId;
      this.queueName = queueName;
      this.condition = condition;
      this.filterString = filterString;
      this.noLocal = noLocal;
      this.channelId = channelId;
      this.durable = durable;
   }

   public String getCondition()
   {
      return condition;
   }

   public boolean isDurable()
   {
      return durable;
   }

   public String getFilterString()
   {
      return filterString;
   }

   public String getNodeId()
   {
      return nodeId;
   }

   public boolean isNoLocal()
   {
      return noLocal;
   }

   public String getQueueName()
   {
      return queueName;
   }
   
   public long getChannelId()
   {
      return channelId;
   }
}
