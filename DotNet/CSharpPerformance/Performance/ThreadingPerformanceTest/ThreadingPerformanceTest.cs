using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Threading;
using System.Text;
using System.Collections.Generic;
using System.Runtime.CompilerServices;

namespace Performance
{
	[TestClass]
	public class ThreadingPerformanceTest : TestBase
	{
		int iterations = 1000000;

		[TestMethod]
		public void LockVSReaderWriterLockVSReaderWriterLockSlimVSHandle()
		{
			TestName("Lock vs. ReaderWriterLock vs. ReaderWriterLockSlim vs. Handle-based");

			Object o = new Object();
			
			CodeTimer.Time("Lock perf: Monitor", iterations,
			   () =>
			   {
				   Monitor.Enter(o);
				   //
				   Monitor.Exit(o);
			   });

			CodeTimer.Time("Lock perf: Lock", iterations,
			   () =>
			   {
				   lock (o)
				   {
				   }
				  
			   });

			ReaderWriterLockSlim rwls = new ReaderWriterLockSlim();
			CodeTimer.Time("Lock perf: ReaderWriterLockSlim", iterations,
			   () =>
			   {
				   rwls.EnterWriteLock();
				   rwls.ExitWriteLock();
			   });

			ReaderWriterLock rwl = new ReaderWriterLock();
			CodeTimer.Time("Lock perf: ReaderWriterLock", iterations,
			   () =>
			   {
				   rwl.AcquireWriterLock(Timeout.Infinite);
				   rwl.ReleaseWriterLock();
			   });

			Mutex mutex = new Mutex();
			CodeTimer.Time("Lock perf: Mutex", iterations,
			   () =>
			   {
				   mutex.WaitOne();
				   mutex.ReleaseMutex();
			   });
		}
	}
}
