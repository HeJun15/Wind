using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Threading;
using System.Text;
using System.Collections.Generic;

namespace Performance
{
	[TestClass]
	public class VariableAccessPerformanceTest : TestBase
	{
		[TestMethod]
		public void CheckedVSUnchecked()
		{
			TestName();
			int iterations = 100 * 1000 * 1000;
			int i = 0;
			CodeTimer.Time("Checked operations", iterations,
			   () => i = checked(i + 1));

			i = 0;
			CodeTimer.Time("Unchecked operations", iterations,
			   () => i = unchecked(i + 1));
		}

		public void ReferenceFieldStaticVSInstance()
		{
			int iterations = 900000000;

			FieldAccess fa = new FieldAccess();
			Object o = null;

			TestName("Static Reference Field Access Tests");
			CodeTimer.Time("Read  static reference field", iterations, () => o = FieldAccess.s_refType);
			CodeTimer.Time("Write static reference field", iterations, () => FieldAccess.s_refType = o);

			TestName("Instance Reference Field Access Tests");
			CodeTimer.Time("Read  instance reference field", iterations, () => o = fa.m_refType);
			CodeTimer.Time("Write instance reference field", iterations, () => fa.m_refType = o);		
		}

		[TestMethod]
		public void ValueFieldStaticVSInstance()
		{
			TestName();
			int iterations = 500000000;
            IntPtr p = IntPtr.Zero;

			FieldAccess fa = new FieldAccess();

			TestName("Static Value Field Access Tests");			
			CodeTimer.Time("Read  static value field", iterations, () => p = FieldAccess.s_valType);
			CodeTimer.Time("Write static value field", iterations, () => FieldAccess.s_valType = p);

			TestName("Instance Value Field Access Tests");
			CodeTimer.Time("Read  instance value field", iterations, () => p = fa.m_valType);
			CodeTimer.Time("Write instance value field", iterations, () => fa.m_valType = p);			
		}

		[TestMethod]
		public void InstanceFieldNormalObjectVSMarshalByRefObject()
		{
			TestName("Instance Field Normal Object vs. MarshalByRefObject Tests");
			int iterations = 100000000;

			NormalObject no = new NormalObject();
			CodeTimer.Time("Normal object instance field", iterations,
			   () => no.m_field++);

			MBRObject mbro = new MBRObject();
			CodeTimer.Time("MarshalByRefObject instance field", iterations,
			   () => mbro.m_field++);
		}

		[TestMethod]
		public void NullableVSNonNullableInteger()
		{
			TestName("Nullable vs. Non-nullable Integer Tests");

			int iterations = 100000000;
			Int32 integer = 0;
			CodeTimer.Time("Non-nullable variable", iterations,
			   () => integer++);

			Int32? nullableInteger = 0;
			CodeTimer.Time("Nullable variable", iterations,
			   () => nullableInteger++);
		}

		private sealed class FieldAccess
		{
			public static IntPtr s_valType; // Value type static field
			public static Object s_refType; // Reference type static field

			public IntPtr m_valType = IntPtr.Zero; // Value type instance field
			public Object m_refType = null;        // Reference type instance field
		}

		private sealed class NormalObject : Object { public Int32 m_field = 0; }
		private sealed class MBRObject : MarshalByRefObject { public Int32 m_field = 0; }
	}
}
