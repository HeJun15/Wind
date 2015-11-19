using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Threading;
using System.Text;
using System.Collections.Generic;
using System.Runtime.CompilerServices;
using System.Reflection;

namespace Performance
{
	[TestClass]
	public class MethodAndReflectionPerformanceTest : TestBase
	{
        int iterations = 500 * 1000 * 1000;

        [TestMethod]
        public void MethodCalling()
        {
            CodeTimer.Time("Calling static", iterations, () => Methods.StaticMethod());

            Methods methods = new Methods();
            IInterface iface = methods;

            CodeTimer.Time("Calling instance", iterations, () => methods.InstanceMethod());
            CodeTimer.Time("Calling virtual", iterations, () => methods.VirtualMethod());
            CodeTimer.Time("Calling interface", iterations, () => iface.InterfaceMethod());

            DelegateMethod dm = new DelegateMethod(Methods.DelegateStaticMethod);
            CodeTimer.Time("Calling static via delegate", iterations, () => dm());

            dm = new DelegateMethod(methods.DelegateInstanceMethod);
            CodeTimer.Time("Calling instance via delegate", iterations, () => dm());

            CodeTimer.Time("Calling static generic", iterations, () => Methods.StaticGenericMethod<Object>());
            CodeTimer.Time("Calling instance generic", iterations, () => methods.InstanceGenericMethod<Object>());           
        }


        [TestMethod]
        public void Reflection()
        {
            CodeTimer.Time("Calling via Reflection, bind and Invoke (time x 1000)", iterations / 1000,
              () => typeof(Methods).GetMethod("ReflectionMethod").Invoke(null, null));

            MethodInfo mi = typeof(Methods).GetMethod("ReflectionMethod");
            CodeTimer.Time("Calling via reflection Invoke (time x 1000)", iterations / 1000,
               () => mi.Invoke(null, null));

            DelegateMethod dm = (DelegateMethod)Delegate.CreateDelegate(typeof(DelegateMethod), mi);
            CodeTimer.Time("Calling reflection via delegate", iterations / 1000, () => dm());
        }


        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M0A() { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M1A(Object o1) { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M2A(Object o1, Object o2) { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M3A(Object o1, Object o2, Object o3) { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M4A(Object o1, Object o2, Object o3, Object o4) { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void M5A(Object o1, Object o2, Object o3, Object o4, Object o5) { }

        [MethodImpl(MethodImplOptions.NoInlining)]
        private static void MVA(params Object[] os) { }

        private interface IInterface
        {
            void InterfaceMethod();
        }

        private class Methods : IInterface
        {
            [MethodImpl(MethodImplOptions.NoInlining)]
            public static void StaticMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public void InstanceMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public virtual void VirtualMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public void InterfaceMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public static void DelegateStaticMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public void DelegateInstanceMethod() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public static void StaticGenericMethod<T>() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public void InstanceGenericMethod<T>() { }

            [MethodImpl(MethodImplOptions.NoInlining)]
            public static void ReflectionMethod() { }
        }

        private delegate void DelegateMethod();
	}
}
