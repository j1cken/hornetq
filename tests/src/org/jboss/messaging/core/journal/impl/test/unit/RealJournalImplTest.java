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
package org.jboss.messaging.core.journal.impl.test.unit;

import java.io.File;
import java.util.ArrayList;

import org.jboss.messaging.core.journal.Journal;
import org.jboss.messaging.core.journal.RecordInfo;
import org.jboss.messaging.core.journal.SequentialFileFactory;
import org.jboss.messaging.core.journal.impl.JournalImpl;
import org.jboss.messaging.core.journal.impl.NIOSequentialFileFactory;
import org.jboss.messaging.core.logging.Logger;

/**
 * 
 * A RealJournalImplTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class RealJournalImplTest extends JournalImplTestBase
{
	private static final Logger log = Logger.getLogger(RealJournalImplTest.class);
	
	protected String journalDir = System.getProperty("user.home") + "/journal-test";
		
	protected void prepareDirectory() throws Exception
	{				
		File file = new File(journalDir);
		
		log.info("deleting directory " + journalDir);
		
		deleteDirectory(file);
		
		file.mkdir();		
	}
	
	protected SequentialFileFactory getFileFactory() throws Exception
	{
		return new NIOSequentialFileFactory(journalDir);
	}
	
	public void testSpeed() throws Exception
	{
		Journal journal =
			new JournalImpl(10 * 1024 * 1024, 10, 10, true, new NIOSequentialFileFactory(journalDir),
					5000, "jbm-data", "jbm");
		
		journal.start();
		
		journal.load(new ArrayList<RecordInfo>(), null);
		
		final int numMessages = 10000;
		
		byte[] data = new byte[1024];
		
		long start = System.currentTimeMillis();
		
		for (int i = 0; i < numMessages; i++)
		{
			journal.appendAddRecord(i, data);
		}
		
		long end = System.currentTimeMillis();
		
		double rate = 1000 * (double)numMessages / (end - start);
		
		log.info("Rate " + rate + " records/sec");

	}
	
}
