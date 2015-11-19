using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading;

namespace Performance
{
	public class TestBase
	{
		public TestBase()
		{
            Process.GetCurrentProcess().PriorityClass = ProcessPriorityClass.High;
            Thread.CurrentThread.Priority = ThreadPriority.Highest;

			CodeTimer.Time(String.Empty, 1, () => { Thread.Sleep(0); });
		}

		protected void TestName()
		{
			StackFrame[] frames = new StackTrace().GetFrames();
			StringBuilder testName = new StringBuilder("****************************************\n");
			testName.Append(frames[1].GetMethod().Name);
			testName.Replace('_', ':');
			for (Int32 index = 1; index < testName.Length; index++)
			{
				// If this is an uppercase character, insert a space before it
				if (Char.IsUpper(testName[index]))
				{
					testName.Insert(index, ' ');
					index++;
				}
			}
			testName.Append("\n****************************************");
			TestName(testName.ToString());
		}

		protected static void TestName(String testName)
		{
			Console.WriteLine();
			Console.WriteLine(testName);
		}
	}
}