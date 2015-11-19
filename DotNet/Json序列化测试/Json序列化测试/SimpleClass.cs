using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace JsonTest
{
    [Serializable]
    public class SimpleClass
    {
        public string Name { set; get; }
        public string No { set; get; }
        public int Age { set; get; }

    }
}
