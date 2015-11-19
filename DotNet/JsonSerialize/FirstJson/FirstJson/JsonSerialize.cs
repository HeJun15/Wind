using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Newtonsoft.Json;

namespace FirstJson
{
    class JsonSerialize
    {
        public void JSON_Test()
        {
            Person person = new Person();
            person.Name = "HeJun";
            person.Age = 26;
            person.something = "xxxxxxxxxxxxx";
            string strSerializeJSON = JsonConvert.SerializeObject(person);
            Console.WriteLine(strSerializeJSON);
        }
    }
}
