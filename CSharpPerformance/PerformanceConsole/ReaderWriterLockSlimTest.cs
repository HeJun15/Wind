using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace PerformanceConsole
{
	public class ReaderWriterLockSlimTest
	{
		static private object _lock1 = new object();
		static private ReaderWriterLockSlim rwl = new ReaderWriterLockSlim();

		public static void PeformanceTest()
		{
			Stopwatch sw = new Stopwatch();
			sw.Start();
			List<Task> lstTask = new List<Task>();
			for (int i = 0; i < 500; i++)
			{
				if (i % 25 != 0)
				{
					var t = Task.Factory.StartNew(ReadSomething);
					lstTask.Add(t);
				}
				else
				{
					var t = Task.Factory.StartNew(WriteSomething);
					lstTask.Add(t);
				}
			}
			Task.WaitAll(lstTask.ToArray());
			sw.Stop();
			Console.WriteLine("使用ReaderWriterLockSlim方式，耗时：" + sw.Elapsed);
			sw.Restart();
			lstTask = new List<Task>();
			for (int i = 0; i < 500; i++)
			{
				if (i % 25 != 0)
				{
					var t = Task.Factory.StartNew(ReadSomething_lock);
					lstTask.Add(t);
				}
				else
				{
					var t = Task.Factory.StartNew(WriteSomething_lock);
					lstTask.Add(t);
				}
			}
			Task.WaitAll(lstTask.ToArray());
			sw.Stop();
			Console.WriteLine("使用lock方式，耗时：" + sw.Elapsed);
		}

		static public void ReadSomething_lock()
		{
			lock (_lock1)
			{
				//Console.WriteLine("{0} Thread ID {1} reading sth...", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
				Thread.Sleep(10);//模拟读取信息
				//Console.WriteLine("{0} Thread ID {1} reading end.", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
			}
		}

		static public void WriteSomething_lock()
		{
			lock (_lock1)
			{
				//Console.WriteLine("{0} Thread ID {1} writing sth...", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
				Thread.Sleep(100);//模拟写入信息
				//Console.WriteLine("{0} Thread ID {1} writing end.", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
			}
		}

		static public void ReadSomething()
		{
			rwl.EnterReadLock();
			try
			{
				//Console.WriteLine("{0} Thread ID {1} reading sth...", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
				Thread.Sleep(10);//模拟读取信息
				//Console.WriteLine("{0} Thread ID {1} reading end.", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
			}
			finally
			{
				rwl.ExitReadLock();
			}
		}

		static public void WriteSomething()
		{
			rwl.EnterWriteLock();
			try
			{
				//Console.WriteLine("{0} Thread ID {1} writing sth...", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
				Thread.Sleep(100);//模拟写入信息
				//Console.WriteLine("{0} Thread ID {1} writing end.", DateTime.Now.ToString("hh:mm:ss fff"), Thread.CurrentThread.GetHashCode());
			}
			finally
			{
				rwl.ExitWriteLock();
			}
		}
	}
}
