/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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

package org.hornetq.tests.unit.core.server.impl.fakes;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.hornetq.core.filter.Filter;
import org.hornetq.core.postoffice.PostOffice;
import org.hornetq.core.server.Queue;
import org.hornetq.core.server.QueueFactory;
import org.hornetq.core.server.impl.QueueImpl;
import org.hornetq.utils.SimpleString;

/**
 * 
 * A FakeQueueFactory
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class FakeQueueFactory implements QueueFactory
{
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	
	private PostOffice postOffice;

	public Queue createQueue(long persistenceID, final SimpleString address, SimpleString name, Filter filter,
			                   boolean durable, boolean temporary)
	{
		return new QueueImpl(persistenceID, address, name, filter, durable, temporary, scheduledExecutor, postOffice, null, null);
	}
	
   public void setPostOffice(PostOffice postOffice)
   {
      this.postOffice = postOffice;
      
   }

}