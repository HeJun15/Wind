using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;
using Microsoft.Win32.SafeHandles;

[assembly: CLSCompliant(true)]

namespace Performance
{
	public sealed class CodeTimer : IDisposable
	{
		// Holds the stopwatch time.
		private Int64 m_startTime;
		// Holds the cycle time.
		private UInt64 m_startCycles;

		private String m_text;
		private Int32 m_collectionCount0;
		private Int32 m_collectionCount1;
		private Int32 m_collectionCount2;


		private CodeTimer(Boolean startFresh, String text)
		{
			if (true == startFresh)
			{
				PrepareForOperation();
			}
			m_text = text;
			m_collectionCount0 = GC.CollectionCount(0);
			m_collectionCount1 = GC.CollectionCount(1);
			m_collectionCount2 = GC.CollectionCount(2);

			// Get the time before returning so that any code above doesn't impact the time.
			m_startTime = Stopwatch.GetTimestamp();
			m_startCycles = CycleTime.Thread();
		}

		public delegate void TimedOp();
		public static void Time(String text, Int32 numIterations, TimedOp op)
		{
			Time(false, text, numIterations, op);
		}

		public static void Time(Boolean startFresh, String text, Int32 numIterations, TimedOp op)
		{
			using (new CodeTimer(startFresh, text))
			{
				while (numIterations-- > 0) op();
			}
		}
		public static IDisposable Time(Boolean startFresh, String text)
		{
			return (new CodeTimer(startFresh, text));
		}

		public static IDisposable Time(String text)
		{
			return (Time(false, text));
		}


		public void Dispose()
		{
			UInt64 elapsedCycles = CycleTime.Thread() - m_startCycles;

			// Get the elapsed time now so that any code below doesn't impact the time.
			Int64 elapsedTime = Stopwatch.GetTimestamp() - m_startTime;

			Int64 milliseconds = (elapsedTime * 1000) / Stopwatch.Frequency;

			if (false == String.IsNullOrEmpty(m_text))
			{
				ConsoleColor defColor = Console.ForegroundColor;
				Console.ForegroundColor = ConsoleColor.Yellow;
				Console.WriteLine("   {0}", m_text);
				Console.ForegroundColor = defColor;
				Console.WriteLine("   {0,7:N0}ms {1,11:N0}Kc (G0={2,4}, G1={3,4}, G2={4,4})",
									milliseconds,
									elapsedCycles / 1000,
									GC.CollectionCount(0) - m_collectionCount0,
									GC.CollectionCount(1) - m_collectionCount1,
									GC.CollectionCount(2) - m_collectionCount2);
			}
		}

		private static void PrepareForOperation()
		{
			GC.Collect();
			GC.WaitForPendingFinalizers();
			GC.Collect();
		}
	}

	public sealed class CycleTime
	{
		private Boolean m_trackingThreadTime;
		private SafeWaitHandle m_handle;
		private UInt64 m_startCycleTime;

		private CycleTime(Boolean trackingThreadTime, SafeWaitHandle handle)
		{
			m_trackingThreadTime = trackingThreadTime;
			m_handle = handle;
			m_startCycleTime = m_trackingThreadTime ? Thread() : Process(m_handle);
		}

		[CLSCompliant(false)]
		public UInt64 Elapsed()
		{
			UInt64 now = m_trackingThreadTime ? Thread(/*m_handle*/) : Process(m_handle);
			return now - m_startCycleTime;
		}

		public static CycleTime StartThread(SafeWaitHandle threadHandle)
		{
			return new CycleTime(true, threadHandle);
		}

		public static CycleTime StartProcess(SafeWaitHandle processHandle)
		{
			return new CycleTime(false, processHandle);
		}

		/// <summary>
		/// Retrieves the cycle time for the specified thread.
		/// </summary>
		/// <param name="threadHandle">Identifies the thread whose cycle time you'd like to obtain.</param>
		/// <returns>The thread's cycle time.</returns>
		[CLSCompliant(false)]
		public static UInt64 Thread(SafeWaitHandle threadHandle)
		{
			UInt64 cycleTime;
			if (!QueryThreadCycleTime(threadHandle, out cycleTime))
				throw new Win32Exception();
			return cycleTime;
		}

		[CLSCompliant(false)]
		public static UInt64 Thread()
		{
			UInt64 cycleTime;
			if (!QueryThreadCycleTime((IntPtr)(-2), out cycleTime))
				throw new Win32Exception();
			return cycleTime;
		}

		/// <summary>
		/// Retrieves the sum of the cycle time of all threads of the specified process.
		/// </summary>
		/// <param name="processHandle">Identifies the process whose threads' cycles times you'd like to obtain.</param>
		/// <returns>The process' cycle time.</returns>
		[CLSCompliant(false)]
		public static UInt64 Process(SafeWaitHandle processHandle)
		{
			UInt64 cycleTime;
			if (!QueryProcessCycleTime(processHandle, out cycleTime))
				throw new Win32Exception();
			return cycleTime;
		}

		/// <summary>
		/// Retrieves the cycle time for the idle thread of each processor in the system.
		/// </summary>
		/// <returns>The number of CPU clock cycles used by each idle thread.</returns>
		[CLSCompliant(false)]
		public static UInt64[] IdleProcessors()
		{
			Int32 byteCount = Environment.ProcessorCount;
			UInt64[] cycleTimes = new UInt64[byteCount];
			byteCount *= 8;   // Size of UInt64
			if (!QueryIdleProcessorCycleTime(ref byteCount, cycleTimes))
				throw new Win32Exception();
			return cycleTimes;
		}

		[DllImport("Kernel32", ExactSpelling = true, SetLastError = true)]
		[return: MarshalAs(UnmanagedType.Bool)]
		private static extern Boolean QueryThreadCycleTime(IntPtr threadHandle, out UInt64 CycleTime);


		[DllImport("Kernel32", ExactSpelling = true, SetLastError = true)]
		[return: MarshalAs(UnmanagedType.Bool)]
		private static extern Boolean QueryThreadCycleTime(SafeWaitHandle threadHandle, out UInt64 CycleTime);

		[DllImport("Kernel32", ExactSpelling = true, SetLastError = true)]
		[return: MarshalAs(UnmanagedType.Bool)]
		private static extern Boolean QueryProcessCycleTime(SafeWaitHandle processHandle, out UInt64 CycleTime);

		[DllImport("Kernel32", ExactSpelling = true, SetLastError = true)]
		[return: MarshalAs(UnmanagedType.Bool)]
		private static extern Boolean QueryIdleProcessorCycleTime(ref Int32 byteCount, UInt64[] CycleTimes);

	}

	public sealed class GCBeep
	{
		private static Byte s_silence = 0;

		public static Boolean Silence
		{
			get { return (Thread.VolatileRead(ref s_silence) != 0); }
			set { Thread.VolatileWrite(ref s_silence, (Byte)(value ? 1 : 0)); }
		}

		// This is the Finalize method
		~GCBeep()
		{
			// We're being finalized, beep (unless silenced).
			if (!Silence) Console.Beep();

			// If the AppDomain isn't unloading and if the process isn’t
			// shutting down, create a new object that will get finalized 
			// at the next collection.
			if (!AppDomain.CurrentDomain.IsFinalizingForUnload() &&
				!Environment.HasShutdownStarted)
				new GCBeep();
		}
	}
}
