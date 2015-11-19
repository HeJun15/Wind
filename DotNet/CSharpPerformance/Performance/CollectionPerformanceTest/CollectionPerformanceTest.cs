using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Threading;
using System.Text;
using System.Collections.Generic;
using System.Runtime.CompilerServices;

namespace Performance
{
	[TestClass]
	public class CollectionPerformanceTest : TestBase
	{
		int  iterations = 5000;
		int numElements = 250;

		[TestMethod]
		public void ReferenceVSValueTypeArrays()
		{
			TestName("Reading and writing reference vs. value type arrays");

			int dummy = 0;
			int[] aValues = new int[numElements * numElements];
			CodeTimer.Time("Reading array of values", iterations,
			   () =>
			   {
				   for (int i = 0; i < aValues.Length; i++)
					   dummy = aValues[i];
			   });

			CodeTimer.Time("Writing array of values", iterations,
			   () =>
			   {
				   for (int i = 0; i < aValues.Length; i++)
					   aValues[i] = dummy;
			   });

			Object o = null;
			Object[] aRefs = new Object[numElements * numElements];
			CodeTimer.Time("Reading array of references", iterations,
			   () =>
			   {
				   for (int i = 0; i < aRefs.Length; i++)
					   o = aRefs[i];
			   });

			o = null;
			CodeTimer.Time("Writing array of references", iterations,
			   () =>
			   {
				   for (int i = 0; i < aRefs.Length; i++)
					   aRefs[i] = o;
			   });
		}

		[TestMethod]
		public void JaggedArrayAccess()
		{
			TestName("Zero-based one, two, and jagged array access tests");

			Int32 sum = 0;

			Int32[] aint = new Int32[numElements * numElements];
			CodeTimer.Time("1-dim zero-based access: " + aint.GetType(), iterations,
			   () =>
			   {
				   for (Int32 e = 0; e < aint.Length; e++)
					   sum += aint[e];
			   });

			Int32[,] a = new Int32[numElements, numElements];
			CodeTimer.Time("2-dim zero-based access: " + a.GetType(), iterations,
			   () =>
			   {
				   for (Int32 e = 0; e < numElements; e++)
					   for (Int32 f = 0; f < numElements; f++)
						   sum += a[e, f];
			   });

			Int32[][] aJagged = new Int32[numElements][];
			for (Int32 i = 0; i < numElements; i++)
				aJagged[i] = new Int32[numElements];
			CodeTimer.Time("2-dim zero-based jagged access: " + aJagged.GetType(), iterations,
			   () =>
			   {
				   for (Int32 e = 0; e < numElements; e++)
					   for (Int32 f = 0; f < numElements; f++)
						   sum += aJagged[e][f];
			   });
		}

		[TestMethod]
		public void ColumnVSRowAccess()
		{
			TestName("Column vs. Row Access Tests");

			Int32[,] a8 = new Int32[numElements, numElements];
			CodeTimer.Time("Column access test", iterations,
			   () =>
			   {
				   for (int col = 0; col < numElements; col++)
					   for (int row = 0; row < numElements; row++)
						   a8[row, col] = 6;
			   });

			CodeTimer.Time("Row access test", iterations,
			   () =>
			   {
				   for (int row = 0; row < numElements; row++)
					   for (int col = 0; col < numElements; col++)
						   a8[row, col] = 6;
			   });
		}

		[TestMethod]
		public void GrowingVSPreszing()
		{
			TestName("Growing vs. Presizing Collections Tests");

			iterations = 10000000;

			List<Object> lo1 = new List<Object>();
			CodeTimer.Time("On-demand collection growth", iterations,
			   () => lo1.Add(null));

			List<Object> lo2 = new List<Object>(iterations);
			CodeTimer.Time("Pre-sized collection", iterations,
			   () => lo2.Add(null));
		}

		[TestMethod]
		public void AddVSAddRange()
		{
			TestName("Adding One Element at a Time vs AddRange Collections Tests");

			iterations = 10000000;
			Object[] oa = new Object[iterations];

			List<Object> lo3 = new List<Object>();
			CodeTimer.Time("One element at a time", 1,
			   () =>
			   {
				   for (int i = 0; i < iterations; i++)
					   lo3.Add(oa[i]);
			   }); ;

			List<Object> lo4 = new List<Object>();
			CodeTimer.Time("Add range all at once", 1,
			   () => lo4.AddRange(oa));
		}

		[TestMethod]
		public void ForVSForeach()
		{
			TestName("foreach vs. for vs. ForEach Tests");

			iterations = 100 * 1000 * 1000;
			List<Object> l5 = new List<Object>(iterations);
			for (Int32 x = 0; x < iterations; x++)
				l5.Add(null);

			CodeTimer.Time("Using C# foreach statement", 1,
			   () =>
			   {
				   foreach (Object o2 in l5)
					   M(o2);
			   });

			CodeTimer.Time("Using C# foreach statement with cast to IEnumerable", 1,
			   () =>
			   {
				   foreach (Object o3 in (IEnumerable<Object>)l5)
					   M(o3);
			   });

			CodeTimer.Time("Using C# for statement", 1,
			   () =>
			   {
				   for (Int32 x = 0; x < iterations; x++)
					   M(l5[x]);
			   });

			CodeTimer.Time("Using Lists's ForEach method", 1,
			   () => l5.ForEach(M));
		}

		[MethodImpl(MethodImplOptions.NoInlining)]
		private static void M(Object o) { }
	}
}
