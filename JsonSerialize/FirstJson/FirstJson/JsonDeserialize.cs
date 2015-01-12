using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Newtonsoft.Json;

namespace FirstJson
{
    class JsonDeserialize
    {
        public void JSON_Test()
        {
            Person person = new Person();
            person.Name = "HeJun";
            person.Age = 26;
            person.something = "yyyyyyyyyyy";
            string strSerializeJSON = JsonConvert.SerializeObject(person);
            Person user = (Person)JsonConvert.DeserializeObject(strSerializeJSON, typeof(Person));
            Console.WriteLine(user.Name);
        }
    }
}
