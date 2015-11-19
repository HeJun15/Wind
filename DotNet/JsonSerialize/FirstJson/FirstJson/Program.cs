using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace FirstJson
{
    class Program
    {
        static void Main(string[] args)
        {
            JsonSerialize serialize = new JsonSerialize();
            serialize.JSON_Test();
            JsonDeserialize deserialize = new JsonDeserialize();
            deserialize.JSON_Test();
            Console.ReadLine();
        }
    }
}
