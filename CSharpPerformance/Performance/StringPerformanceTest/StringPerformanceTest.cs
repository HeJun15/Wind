using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Threading;
using System.Text;
using System.Collections.Generic;

namespace Performance
{
	[TestClass]
	public class StringPerformanceTest : TestBase
	{
		private const string STR = "0123456789";

		/// <summary>
		/// 
		/// </summary>
		[TestMethod]
		public void StringConcatenationTest1()
		{
			TestName();
			int iterations = 100000;

			CodeTimer.Time("String + to a String", 1, ()=>StringConcateOperate(iterations)); 
			CodeTimer.Time("String.Concat", 1, () => StringConcat(iterations)); 
			CodeTimer.Time("StringBuilder", 1, ()=>StringBuilder(iterations)); 
		}
		
		[TestMethod]
		public void StringConcatenationTest2()
		{
			TestName();
			int iterations = 100*100000;
			
			CodeTimer.Time("String.Concat", 1, () => StringConcat(iterations));
			CodeTimer.Time("StringListBuilder", 1, () => StringListBuilder(iterations)); 
		}

		[TestMethod]
		public void StringConcatenationTest3()
		{
			TestName();
			int iterations = 100 * 100000;
			
			CodeTimer.Time("StringBuilder", 1, () => StringBuilder(iterations)); 
			CodeTimer.Time("NewStringBuilder", 1, () => NewStringBuilder(iterations)); 
		}

		/// <summary>
		/// 少量字符串拼接
		/// </summary>
		[TestMethod]
		public void StringConcatenationTest4()
		{
			TestName();
			int iterations = 100 * 100000;
			
			CodeTimer.Time("String.Concat1", iterations, () =>{
				string s = string.Concat("A", "B", "C", "D");
			});
			CodeTimer.Time("String.Concat2", iterations, () =>
			{
				string s = string.Concat("ABC", "DE");
			});	
			CodeTimer.Time("StringBuilder1", iterations, () =>
			{
				StringBuilder sb = new System.Text.StringBuilder();
				sb.Append("A").Append("B").Append("C").Append("D");
				string s = sb.ToString();
			});
			CodeTimer.Time("StringBuilder2", iterations, () =>
			{
				StringBuilder sb = new System.Text.StringBuilder(5);
				sb.Append("ABC").Append("DE");
				string s = sb.ToString();
			}); 
		}

		[TestMethod]
		public void StringConcatenationTest5()
		{
			TestName();
			int iterations = 100 * 100000;

			CodeTimer.Time("String.Concat", iterations, () => string.Concat(
				"A123456789", "B123456789", "C123456789", "D123456789",
				"E12345678", "F12345678", "G123456789", "H123456789",
				"J123456789", "K123456789", "L123456789"));
			CodeTimer.Time("StringBuilder", iterations, () =>
			{
				StringBuilder sb = new System.Text.StringBuilder(110);
				sb.Append("A123456789").Append("B123456789").Append("C123456789").Append("D123456789")
					.Append("E123456789").Append("F123456789").Append("G123456789").Append("H123456789")
					.Append("J123456789").Append("J123456789").Append("L123456789");
			});
		}

		private static string StringConcateOperate(int count)
		{
			String s = String.Empty;
			for (int i = 0; i < count; i++) s += STR;
			return s;
		}

		private static string StringConcat(int count, int strType=0)
		{
			List<string> stringList = new List<string>(count);
			for (int i = 0; i < count; i++) stringList.Add(STR);
			return string.Concat(stringList.ToArray());
		}

		private static string StringListBuilder(int count)
		{
			var builder = new StringListBuilder(count);
			for (int i = 0; i < count; i++) builder.Append(STR);
			return builder.ToString();
		}

		private static string StringBuilder(int count)
		{
			var builder = new StringBuilder();
			for (int i = 0; i < count; i++)
				builder.Append(STR);
			return builder.ToString();
		}

		private static string NewStringBuilder(int count)
		{
			var builder = new StringBuilder(count * STR.Length);
			for (int i = 0; i < count; i++)
				builder.Append(STR);
			return builder.ToString();
		}
	}
}
