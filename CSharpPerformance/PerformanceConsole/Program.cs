using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace PerformanceConsole
{
	class Program
	{
		static void Main(string[] args)
		{
			ReaderWriterLockSlimTest.PeformanceTest();
			Console.Read();
		}
	}
}
